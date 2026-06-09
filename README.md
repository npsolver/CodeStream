# CodeStream
A real-time code execution pipeline built with Apache Kafka and Java.

## Frontend

Next.js app in `frontend/`. It posts Python code to the api-service, polls for results, and shows stdout or errors.

```bash
# Terminal 1 — infrastructure (Kafka, PostgreSQL, schema registry)
docker compose up -d
# PostgreSQL: localhost:5432, db/user/password codestream
# Flyway migrations run automatically when api-service starts

# start api-service and worker-service per your usual workflow

# Terminal 2 — UI (rewrites /api → http://localhost:8082)
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). Override the API target with `API_SERVICE_URL` (server rewrites) or `NEXT_PUBLIC_API_BASE_URL` (direct browser calls, e.g. `http://localhost:8082`).
