import type { ExecutionResult, ExecutionStatus } from "@/lib/api";

type PanelState = "idle" | "waiting" | "done" | "failed";

interface OutputPanelProps {
  state: PanelState;
  result: ExecutionResult | null;
  errorMessage: string | null;
}

function statusLabel(status: ExecutionStatus | null): string {
  switch (status) {
    case "PROCESSING":
      return "Running…";
    case "SUCCESS":
      return "Completed";
    case "ERROR":
      return "Failed";
    case "TIMEOUT":
      return "Timed out";
    default:
      return "Ready";
  }
}

function statusColor(status: ExecutionStatus | null): string {
  switch (status) {
    case "SUCCESS":
      return "bg-emerald-500/20 text-emerald-300 ring-emerald-500/30";
    case "ERROR":
    case "TIMEOUT":
      return "bg-rose-500/20 text-rose-300 ring-rose-500/30";
    case "PROCESSING":
      return "bg-amber-500/20 text-amber-200 ring-amber-500/30";
    default:
      return "bg-slate-500/20 text-slate-300 ring-slate-500/30";
  }
}

export function OutputPanel({ state, result, errorMessage }: OutputPanelProps) {
  const status = state === "waiting" ? "PROCESSING" : (result?.status ?? null);
  const showSpinner = state === "waiting";

  const stdout = result?.output?.trim() ?? "";
  const stderr = result?.error?.trim() ?? "";
  const hasStdout = stdout.length > 0;
  const hasStderr = stderr.length > 0;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center justify-between border-b border-slate-700/60 px-4 py-3">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
          Output
        </h2>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${statusColor(status)}`}
        >
          {showSpinner && (
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
          )}
          {statusLabel(status)}
        </span>
      </div>

      <div className="flex-1 overflow-auto p-4 font-mono text-sm leading-relaxed">
        {state === "idle" && (
          <p className="text-slate-500">
            Run your code to see stdout, stderr, and execution status here.
          </p>
        )}

        {state === "waiting" && (
          <div className="space-y-3 text-slate-400">
            <p className="animate-pulse">Waiting for the worker to finish…</p>
            {result?.jobId && (
              <p className="text-xs text-slate-600">Job {result.jobId}</p>
            )}
          </div>
        )}

        {state === "failed" && errorMessage && (
          <pre className="whitespace-pre-wrap text-rose-300">{errorMessage}</pre>
        )}

        {state === "done" && result && (
          <div className="space-y-4">
            {result.status === "SUCCESS" && !hasStdout && !hasStderr && (
              <p className="text-slate-500">Program finished with no output.</p>
            )}

            {hasStdout && (
              <section>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-emerald-400/80">
                  stdout
                </p>
                <pre className="whitespace-pre-wrap rounded-lg border border-slate-700/50 bg-black/30 p-3 text-emerald-100">
                  {stdout}
                </pre>
              </section>
            )}

            {hasStderr && (
              <section>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-rose-400/80">
                  {result.status === "ERROR" ? "error" : "stderr"}
                </p>
                <pre className="whitespace-pre-wrap rounded-lg border border-rose-900/40 bg-rose-950/30 p-3 text-rose-200">
                  {stderr}
                </pre>
              </section>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
