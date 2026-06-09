"use client";

import dynamic from "next/dynamic";
import { useCallback, useRef, useState } from "react";
import {
  submitCode,
  suggestFix,
  waitForResult,
  type ExecutionResult,
  type SuggestFixResponse,
} from "@/lib/api";
import { AiSuggestionPanel } from "@/components/AiSuggestionPanel";
import { OutputPanel } from "@/components/OutputPanel";

const CodeEditor = dynamic(
  () => import("@/components/CodeEditor").then((m) => m.CodeEditor),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-full items-center justify-center p-6 text-sm text-slate-500">
        Loading editor…
      </div>
    ),
  },
);

const DEFAULT_CODE = `def greet(name: str) -> str:
    return f"Hello, {name}!"

print(greet("CodeStream"))
for i in range(3):
    print(f"  step {i + 1}")
`;

type PanelState = "idle" | "waiting" | "done" | "failed";
type AiState = "idle" | "loading" | "ready" | "failed";

export function Playground() {
  const [code, setCode] = useState(DEFAULT_CODE);
  const [panelState, setPanelState] = useState<PanelState>("idle");
  const [result, setResult] = useState<ExecutionResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [aiState, setAiState] = useState<AiState>("idle");
  const [aiSuggestion, setAiSuggestion] = useState<SuggestFixResponse | null>(
    null,
  );
  const [aiErrorMessage, setAiErrorMessage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const isRunning = panelState === "waiting";

  const handleRun = useCallback(async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setPanelState("waiting");
    setResult(null);
    setErrorMessage(null);
    setAiState("idle");
    setAiSuggestion(null);
    setAiErrorMessage(null);

    try {
      const jobId = await submitCode(code);
      setResult({ status: "PROCESSING", jobId });

      const execution = await waitForResult(jobId, controller.signal);
      setResult(execution);
      setPanelState("done");

      const errorText = execution.error?.trim();
      if (
        errorText &&
        (execution.status === "ERROR" || execution.status === "TIMEOUT")
      ) {
        setAiState("loading");
        try {
          const suggestion = await suggestFix(code, errorText);
          setAiSuggestion(suggestion);
          setAiState("ready");
        } catch (aiErr) {
          setAiErrorMessage(
            aiErr instanceof Error
              ? aiErr.message
              : "Failed to fetch AI suggestion",
          );
          setAiState("failed");
        }
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      setErrorMessage(
        err instanceof Error ? err.message : "Something went wrong",
      );
      setPanelState("failed");
    }
  }, [code]);

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-800/80 bg-surface-raised/80 backdrop-blur-sm">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-4 px-6 py-4">
          <div className="flex items-center gap-3">
            <div
              className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-accent to-emerald-700 text-lg font-bold text-surface shadow-glow"
              aria-hidden
            >
              ▶
            </div>
            <div>
              <h1 className="text-lg font-semibold tracking-tight text-white">
                CodeStream
              </h1>
              <p className="text-xs text-slate-500">Python execution playground</p>
            </div>
          </div>

          <button
            type="button"
            onClick={handleRun}
            disabled={isRunning || !code.trim()}
            className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2.5 text-sm font-semibold text-surface shadow-glow transition hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isRunning ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-surface/30 border-t-surface" />
                Running…
              </>
            ) : (
              <>Run code</>
            )}
          </button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-[1600px] flex-1 flex-col gap-4 p-4 lg:flex-row lg:p-6">
        <section className="flex min-h-[320px] flex-1 flex-col overflow-hidden rounded-xl border border-slate-700/60 bg-surface-overlay shadow-xl lg:min-h-0">
          <div className="flex items-center justify-between border-b border-slate-700/60 px-4 py-3">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
              Python
            </h2>
            <span className="rounded bg-slate-800 px-2 py-0.5 font-mono text-xs text-slate-500">
              main.py
            </span>
          </div>
          <div className="min-h-[280px] flex-1 p-1 lg:min-h-0">
            <CodeEditor value={code} onChange={setCode} readOnly={isRunning} />
          </div>
        </section>

        <div className="flex w-full min-w-0 flex-col gap-4 lg:w-[min(480px,42%)]">
          <section className="flex min-h-[240px] flex-1 flex-col overflow-hidden rounded-xl border border-slate-700/60 bg-surface-overlay shadow-xl lg:min-h-[280px]">
            <OutputPanel
              state={panelState}
              result={result}
              errorMessage={errorMessage}
            />
          </section>

          <AiSuggestionPanel
            state={aiState}
            suggestion={aiSuggestion}
            errorMessage={aiErrorMessage}
            onApplyFix={setCode}
            onDismiss={() => {
              setAiState("idle");
              setAiSuggestion(null);
              setAiErrorMessage(null);
            }}
          />
        </div>
      </main>
    </div>
  );
}
