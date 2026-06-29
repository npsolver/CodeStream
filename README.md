# CodeStream
A real-time code execution pipeline built with Amazon SQS and Java.

## Architecture

```text
POST /execute → api-service → SQS (code-submissions)
                                    ↓
                              worker-service → Docker (python-runner)
                                    ↓
                              SQS (execution-results) → api-service → PostgreSQL
GET /result/{id} ← api-service ← PostgreSQL
```

## Prerequisites

- Java 17, Maven, Docker
- Node.js 18+ (frontend)
- AWS CLI (for local queue setup or AWS queue creation)

---

## Local development

### 1. Start infrastructure

**Local PostgreSQL (default):**

```bash
docker compose up -d postgres python-runner
./scripts/setup-sqs-local.sh
```

**Managed PostgreSQL (e.g. Neon):** skip the `postgres` container and set `DATABASE_URL` before starting api-service (see [Database](#database) below).

Copy the `export` lines printed by the script into your shell.

### 2. Build (required after pulling changes — installs the updated `common` module)

```bash
mvn clean install -DskipTests
```

### 3. Start api-service

Use the `export` lines from step 1 in the **same terminal** (SQS credentials + queue URLs), then:

```bash
export GEMINI_API_KEY="your-google-ai-studio-key"   # optional, for AI suggestions
mvn -pl api-service spring-boot:run
```

Or build and run in one step: `mvn -pl api-service -am spring-boot:run`

### 4. Start worker-service (same machine as Docker, same SQS env vars as step 3)

```bash
mvn -pl worker-service spring-boot:run
```

### 5. Start frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

---

## Amazon SQS setup (AWS production)

### Step 1 — Create queues

In the [AWS SQS console](https://console.aws.amazon.com/sqs/) (same region as your app, e.g. `us-east-1`):

1. **Create queue** → name: `code-submissions` → Standard queue → Create
2. **Create queue** → name: `execution-results` → Standard queue → Create

Copy each **Queue URL** (looks like `https://sqs.us-east-1.amazonaws.com/123456789012/code-submissions`).

### Step 2 — IAM permissions

Create an IAM user or role for api-service and worker-service with:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ],
      "Resource": [
        "arn:aws:sqs:REGION:ACCOUNT_ID:code-submissions",
        "arn:aws:sqs:REGION:ACCOUNT_ID:execution-results"
      ]
    }
  ]
}
```

Attach to an EC2 instance role, ECS task role, or use access keys on a VPS.

### Step 3 — Environment variables

On the server running **both** Java services:

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export SQS_SUBMISSION_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/ACCOUNT/code-submissions
export SQS_RESULT_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/ACCOUNT/execution-results

# Do NOT set AWS_ENDPOINT_URL in production (real AWS only)
unset AWS_ENDPOINT_URL

export GEMINI_API_KEY=...
export POSTGRES_PASSWORD=...   # if you changed the default
```

Optional tuning:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SQS_POLL_INTERVAL_MS` | `1000` | Delay between poll cycles |
| `SQS_MAX_MESSAGES` | `10` | Messages per receive call |
| `SQS_VISIBILITY_TIMEOUT_SECONDS` | `60` | Hide message while processing |

### Step 4 — Deploy services

Run api-service, worker-service, and frontend. Use a managed Postgres URL via `DATABASE_URL`, or start local Postgres with `docker compose up -d postgres python-runner`.

For production: frontend on **Vercel**, api + worker on **EC2** behind nginx at `api.codestream.npsolver.io` — see **[deploy/EC2-DEPLOY.md](deploy/EC2-DEPLOY.md)** and **[deploy/env.vercel.example](deploy/env.vercel.example)**.

---

## Database

api-service connects to PostgreSQL using either a **`DATABASE_URL`** (managed/hosted DB) or **local Docker defaults**.

### Managed PostgreSQL (Neon, RDS, etc.)

Set a standard Postgres connection URL. When `DATABASE_URL` is set, it overrides the local Docker settings:

```bash
export DATABASE_URL="postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require"
mvn -pl api-service spring-boot:run
```

Example (Neon pooler):

```bash
export DATABASE_URL="postgresql://neondb_owner:YOUR_PASSWORD@ep-example-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require"
```

Flyway runs migrations on startup. For managed databases (Neon), the app automatically baselines when needed and skips `V1` if `execution_results` already exists.

### Local Docker PostgreSQL (default)

When `DATABASE_URL` is **not** set, api-service uses:

| Variable | Default |
|----------|---------|
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `codestream` |
| `POSTGRES_USER` | `codestream` |
| `POSTGRES_PASSWORD` | `codestream` |

Start the container:

```bash
docker compose up -d postgres
```

Optional JDBC query string for local overrides: `POSTGRES_JDBC_PARAMS` (e.g. `?sslmode=disable`).

---

## Configuration reference

| Variable | Service | Purpose |
|----------|---------|---------|
| `SQS_SUBMISSION_QUEUE_URL` | api, worker | Jobs from API to worker |
| `SQS_RESULT_QUEUE_URL` | api, worker | Results from worker to API |
| `AWS_REGION` | api, worker | AWS region |
| `AWS_ENDPOINT_URL` | api, worker | LocalStack only (`http://localhost:4566`) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | api, worker | AWS credentials |
| `GEMINI_API_KEY` | api | AI fix suggestions |
| `DATABASE_URL` | api | Managed Postgres URL (overrides local Docker DB) |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` | api | Local Docker Postgres (when `DATABASE_URL` unset) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | api | Local Docker Postgres credentials |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | api | Comma-separated CORS origins (production domain) |
| `EXECUTION_DOCKER_*` | worker | Docker sandbox limits and image name |
| `RESULT_RETENTION_DAYS` | api | Postgres result retention |

---

## Frontend

Next.js app in `frontend/`. It posts Python code to the api-service, polls for results, and shows stdout or errors.

**Local dev:** uses Next.js rewrites (`/api/*` → `http://localhost:8082`). Override with `API_SERVICE_URL`.

**Production (Vercel):** set `API_SERVICE_URL=https://api.codestream.npsolver.io`. Browser calls `/api/*`; Vercel rewrites proxy to the API. Redeploy after changing. See [deploy/env.vercel.example](deploy/env.vercel.example).

## AI error suggestions (Google AI Studio / Gemini)

When Python execution fails, the UI asks Gemini for a fix and shows a summary, explanation, and suggested code.

1. Open [Google AI Studio](https://aistudio.google.com/) and sign in.
2. Go to **Get API key** → **Create API key**.
3. Set `GEMINI_API_KEY` when starting api-service.

Optional: `GEMINI_MODEL=gemini-2.5-flash` (default).

## Editor persistence & result retention

- **Editor code** is saved in the browser (`localStorage`) and restored after refresh.
- **Execution results** in PostgreSQL are deleted automatically after **1 day** (configurable via `RESULT_RETENTION_DAYS`).
