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

## AI error suggestions (Google AI Studio / Gemini)

When Python execution fails, the UI asks Gemini for a fix and shows a summary, explanation, and suggested code.

1. Open [Google AI Studio](https://aistudio.google.com/) and sign in.
2. Go to **Get API key** → **Create API key** (use an existing Google Cloud project or create one).
3. Start api-service with the key:

```bash
export GEMINI_API_KEY="your-api-key-here"
mvn -pl api-service spring-boot:run
```

Optional: `GEMINI_MODEL=gemini-2.5-flash` (default). Older models like `gemini-2.0-flash` were shut down in June 2026. The key stays on the server only — never put it in the frontend.
