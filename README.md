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

```bash
docker compose up -d
./scripts/setup-sqs-local.sh
```

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

Same as local: Postgres via `docker compose up -d postgres python-runner`, then run api-service, worker-service, and frontend. Point `codestream.npsolver.io` at the frontend with a reverse proxy (see deployment notes in prior docs).

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
| `RESULT_RETENTION_DAYS` | api | Postgres result retention |

---

## Frontend

Next.js app in `frontend/`. It posts Python code to the api-service, polls for results, and shows stdout or errors.

Override the API target with `API_SERVICE_URL` (server rewrites) or `NEXT_PUBLIC_API_BASE_URL` (direct browser calls).

## AI error suggestions (Google AI Studio / Gemini)

When Python execution fails, the UI asks Gemini for a fix and shows a summary, explanation, and suggested code.

1. Open [Google AI Studio](https://aistudio.google.com/) and sign in.
2. Go to **Get API key** → **Create API key**.
3. Set `GEMINI_API_KEY` when starting api-service.

Optional: `GEMINI_MODEL=gemini-2.5-flash` (default).

## Editor persistence & result retention

- **Editor code** is saved in the browser (`localStorage`) and restored after refresh.
- **Execution results** in PostgreSQL are deleted automatically after **1 day** (configurable via `RESULT_RETENTION_DAYS`).
