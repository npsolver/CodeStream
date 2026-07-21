# Deploy CodeStream API + Worker to Amazon EC2

The **frontend** is hosted on **Vercel**. EC2 runs **api-service**, **worker-service**, and **nginx** (HTTPS) at `api.codestream.npsolver.io`. The host **Docker engine** runs isolated Python sandboxes for the worker.

## Architecture

```text
Vercel (frontend)  ──HTTPS──►  api.codestream.npsolver.io
                                        │
                                        ▼
                                  nginx :443 (TLS)
                                        │
                                        ▼
                                 api-service :8082
                                        │
                                        ▼
                                  SQS (submissions)
                                        │
                                        ▼
                               worker-service (systemd)
                                        │
                                        ▼
                        docker run codestream-python-runner
                                        │
                                        ▼
                                  SQS (results) ──► api-service ──► PostgreSQL
```

**Vercel env:** set `API_SERVICE_URL=https://api.codestream.npsolver.io`. The browser calls `/api/*` on Vercel; Next.js rewrites proxy to the API (see [env.vercel.example](./env.vercel.example)). Redeploy after changing env vars.

**Why worker runs on the host:** it shells out to the `docker` CLI. Running natively with `SupplementaryGroups=docker` is simpler than mounting `/var/run/docker.sock` into a container.

Port **8082** stays on localhost only — **80/443** are public via nginx.

---

## Prerequisites (AWS)

| Resource | Notes |
|----------|-------|
| **SQS queues** | `code-submissions` and `execution-results` (see root [README](../README.md#amazon-sqs-setup-aws-production)) |
| **PostgreSQL** | Managed DB — set `DATABASE_URL` on EC2 |
| **IAM role** | EC2 instance profile with SQS send/receive/delete on both queues |
| **DNS** | `api.codestream.npsolver.io` A record → EC2 Elastic IP |
| **Vercel** | `API_SERVICE_URL=https://api.codestream.npsolver.io` (see [env.vercel.example](./env.vercel.example)) |
| **Gemini API key** | Optional |

---

## Step 1 — Launch EC2

1. **AMI:** Amazon Linux 2023 or Ubuntu 22.04 LTS.
2. **Instance type:** `t3.medium` or larger.
3. **Storage:** 20 GB+ gp3.
4. **Security group inbound:**
   - SSH `22` — your IP only
   - HTTP `80` — `0.0.0.0/0` (certbot + redirect)
   - HTTPS `443` — `0.0.0.0/0`
   - Do **not** open 8082 publicly.
5. **IAM instance profile:** attach the SQS role.
6. **Elastic IP:** assign and point `api.codestream.npsolver.io` at it.

---

## Step 2 — Clone and bootstrap

```bash
sudo git clone https://github.com/YOUR_ORG/CodeStream.git /opt/codestream-src
cd /opt/codestream-src
sudo chmod +x deploy/scripts/bootstrap-ec2.sh
sudo REPO_DIR=/opt/codestream-src ./deploy/scripts/bootstrap-ec2.sh
```

Installs Java 17, Maven, Docker, nginx; builds api + worker JARs; builds the `codestream-python-runner` image; registers systemd units.

---

## Step 3 — Configure environment

```bash
sudo nano /etc/codestream/env
```

Minimum required:

```bash
AWS_REGION=us-east-1
SQS_SUBMISSION_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/ACCOUNT/code-submissions
SQS_RESULT_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/ACCOUNT/execution-results

DATABASE_URL=postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require

# CORS — only required if the browser calls the API directly (NEXT_PUBLIC_API_BASE_URL).
# Not needed when Vercel rewrites /api/* via API_SERVICE_URL.
# CORS_ALLOWED_ORIGIN_PATTERNS=https://codestream.npsolver.io,https://*.vercel.app
```

Adjust the CORS origins to match your actual Vercel production domain. If using an EC2 IAM role, omit AWS access keys. Do **not** set `AWS_ENDPOINT_URL` in production.

```bash
sudo systemctl restart codestream-api codestream-worker
```

---

## Step 4 — Start services

```bash
sudo systemctl enable --now codestream-api
sudo systemctl enable --now codestream-worker
```

Verify:

```bash
sudo systemctl status codestream-api codestream-worker
curl -s http://127.0.0.1:8082/result/test   # 404 or JSON, not connection refused
sudo -u codestream docker run --rm codestream-python-runner:latest python3 -c "print('ok')"
```

Logs: `journalctl -u codestream-api -f` / `journalctl -u codestream-worker -f`

---

## Step 5 — nginx + HTTPS

### Install certbot

**Amazon Linux 2023:** `sudo dnf install -y certbot python3-certbot-nginx`

**Ubuntu:** `sudo apt-get install -y certbot python3-certbot-nginx`

### Configure nginx

Install the **HTTP-only** config first (SSL cert paths are added by certbot):

```bash
sudo sed 's/YOUR_DOMAIN/api.codestream.npsolver.io/g' \
  /opt/codestream-src/deploy/nginx/codestream.conf \
  | sudo tee /etc/nginx/conf.d/codestream.conf

sudo nginx -t
sudo systemctl enable --now nginx
sudo certbot --nginx -d api.codestream.npsolver.io
```

certbot obtains the certificate and updates the nginx config for HTTPS + HTTP redirect.
Do **not** add `ssl_certificate` paths manually before running certbot — nginx will fail to start if those files do not exist yet.

### Confirm HTTPS

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://api.codestream.npsolver.io/result/test
```

Open your Vercel frontend, submit Python code, and confirm end-to-end execution.

---

## Step 6 — Vercel frontend

In the Vercel project → **Settings → Environment Variables**:

| Variable | Value |
|----------|-------|
| `API_SERVICE_URL` | `https://api.codestream.npsolver.io` |

**Redeploy** after setting or changing this variable — Next.js reads it at build time for the `/api/*` rewrite rule.

The browser fetches `/api/execute`, `/api/result/{id}`, etc. on your Vercel domain. Vercel proxies those requests to the EC2 API, so you do not need CORS on api-service for normal use.

---

## Step 7 — Deploy updates

```bash
cd /opt/codestream-src
git pull
sudo REPO_DIR=/opt/codestream-src ./deploy/scripts/bootstrap-ec2.sh --redeploy
```

Or restart manually after a full bootstrap:

```bash
sudo systemctl restart codestream-api codestream-worker
```

**Automatic deploy:** pushes to `master`/`main` run [.github/workflows/ci-deploy.yml](../.github/workflows/ci-deploy.yml) (tests on GitHub, then deploy via a self-hosted runner on EC2). See [GitHub Actions setup](#github-actions-setup) below.

Frontend updates deploy via Vercel (git push or Vercel dashboard).

---

## GitHub Actions setup

CI/CD runs on every push/PR to `master` or `main`. Tests run on GitHub-hosted runners; merging to the default branch deploys the backend via a **self-hosted runner** on EC2 (no inbound SSH from GitHub required).

### 1. First-time bootstrap

Run the full bootstrap once before relying on CI:

```bash
sudo REPO_DIR=/opt/codestream-src ./deploy/scripts/bootstrap-ec2.sh
sudo systemctl enable --now codestream-api codestream-worker
```

### 2. EC2: git pull access

The deploy job runs `git pull` in `/opt/codestream-src`. For a **private** repo, add a [deploy key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/managing-deploy-keys) so the server can pull from GitHub. Public repos work without extra setup.

### 3. Install the self-hosted runner (one time)

On EC2, get a registration token: **GitHub repo → Settings → Actions → Runners → New self-hosted runner → Linux**.

```bash
cd /opt/codestream-src
git pull   # ensure install-github-runner.sh is present
sudo GITHUB_REPO=YOUR_ORG/CodeStream RUNNER_TOKEN=PASTE_TOKEN_HERE \
  ./deploy/scripts/install-github-runner.sh
```

The script creates a `github-runner` user, installs the runner under `/opt/actions-runner`, labels it `codestream`, and starts a systemd service. The deploy workflow uses `runs-on: [self-hosted, codestream]`.

Confirm the runner shows **Idle** in **Settings → Actions → Runners**.

Optional: create a **production** environment under **Settings → Environments** to require manual approval before deploy.

### 4. What happens on push

1. **Test job** (GitHub-hosted): `mvn test`, build JARs, build frontend.
2. **Deploy job** (EC2 runner): `git pull` → `bootstrap-ec2.sh --redeploy` → wait for systemd services.

No `EC2_HOST` / SSH secrets are needed. Keep SSH (port 22) restricted to your IP for manual admin only.

### 5. Runner maintenance

| Task | Command |
|------|---------|
| Runner status | `sudo systemctl status actions.runner.*` |
| Runner logs | `journalctl -u actions.runner.* -f` |
| Re-register runner | Re-run `install-github-runner.sh` with a fresh token |

---

## File reference

| Path | Purpose |
|------|---------|
| [deploy/env.production.example](./env.production.example) | EC2 environment template |
| [deploy/env.vercel.example](./env.vercel.example) | Vercel environment template |
| [deploy/nginx/codestream.conf](./nginx/codestream.conf) | nginx → api-service:8082 |
| [deploy/systemd/codestream-api.service](./systemd/codestream-api.service) | API systemd unit |
| [deploy/systemd/codestream-worker.service](./systemd/codestream-worker.service) | Worker systemd unit |
| [deploy/scripts/bootstrap-ec2.sh](./scripts/bootstrap-ec2.sh) | EC2 setup script |
| [deploy/scripts/install-github-runner.sh](./scripts/install-github-runner.sh) | Self-hosted GitHub Actions runner |

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| CORS error in browser | Only if using `NEXT_PUBLIC_API_BASE_URL`; with `API_SERVICE_URL` rewrites, CORS should not apply |
| Worker can't run code | `journalctl -u codestream-worker`; `codestream` user in `docker` group; image exists |
| API can't reach DB | `DATABASE_URL`; DB firewall allows EC2 IP |
| SQS errors | IAM role; queue URLs and region |
| 502 from nginx | `systemctl status codestream-api`; port 8082 listening on localhost |
| Frontend hits wrong host | `API_SERVICE_URL` on Vercel; **redeploy** after changing (build-time rewrite) |
