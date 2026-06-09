const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";

function parseApiError(body: string, status: number): string {
  if (!body) {
    return `AI suggestion failed (${status})`;
  }

  try {
    const parsed = JSON.parse(body) as { message?: string; error?: string };
    if (parsed.message) {
      return parsed.message;
    }
    if (parsed.error) {
      return parsed.error;
    }
  } catch {
    // not JSON
  }

  return body;
}

export type ExecutionStatus = "PROCESSING" | "SUCCESS" | "ERROR" | "TIMEOUT";

export interface ExecutionResult {
  status: ExecutionStatus;
  jobId?: string;
  output?: string | null;
  error?: string | null;
}

export async function submitCode(code: string): Promise<string> {
  const response = await fetch(`${API_BASE}/execute`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Submit failed (${response.status})`);
  }

  return response.text();
}

export async function fetchResult(jobId: string): Promise<ExecutionResult> {
  const response = await fetch(`${API_BASE}/result/${jobId}`);

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Result fetch failed (${response.status})`);
  }

  return response.json();
}

const POLL_INTERVAL_MS = 400;
const MAX_POLL_ATTEMPTS = 150;

export async function waitForResult(
  jobId: string,
  signal?: AbortSignal,
): Promise<ExecutionResult> {
  for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
    if (signal?.aborted) {
      throw new DOMException("Execution cancelled", "AbortError");
    }

    const result = await fetchResult(jobId);

    if (result.status !== "PROCESSING") {
      return result;
    }

    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  throw new Error("Timed out waiting for execution result");
}

export interface SuggestFixResponse {
  summary: string;
  explanation: string;
  correctedCode: string;
}

export async function suggestFix(
  code: string,
  error: string,
): Promise<SuggestFixResponse> {
  const response = await fetch(`${API_BASE}/ai/suggest-fix`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, error }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(parseApiError(text, response.status));
  }

  return response.json();
}
