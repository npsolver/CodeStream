"use client";

import { useEffect, useState } from "react";
import type { SuggestFixResponse } from "@/lib/api";

type AiState = "idle" | "loading" | "ready" | "failed";

interface AiSuggestionPanelProps {
  state: AiState;
  suggestion: SuggestFixResponse | null;
  errorMessage: string | null;
  onApplyFix: (code: string) => void;
  onDismiss: () => void;
}

export function AiSuggestionPanel({
  state,
  suggestion,
  errorMessage,
  onApplyFix,
  onDismiss,
}: AiSuggestionPanelProps) {
  const [applied, setApplied] = useState(false);

  useEffect(() => {
    setApplied(false);
  }, [suggestion?.correctedCode]);

  if (state === "idle") {
    return null;
  }

  return (
    <section className="flex max-h-[min(420px,45vh)] min-h-[180px] flex-col overflow-hidden rounded-xl border border-violet-500/30 bg-gradient-to-b from-violet-950/40 to-surface-overlay shadow-xl ring-1 ring-violet-500/20">
      <div className="flex items-center justify-between border-b border-violet-500/20 px-4 py-3">
        <div className="flex items-center gap-2">
          <span
            className="flex h-6 w-6 items-center justify-center rounded-md bg-violet-500/20 text-xs text-violet-200"
            aria-hidden
          >
            ✦
          </span>
          <h2 className="text-sm font-semibold text-violet-100">AI fix suggestion</h2>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="text-xs text-slate-500 transition hover:text-slate-300"
        >
          Dismiss
        </button>
      </div>

      <div className="flex-1 overflow-auto p-4 text-sm leading-relaxed">
        {state === "loading" && (
          <div className="flex items-center gap-3 text-violet-200/80">
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-violet-400/30 border-t-violet-300" />
            <p className="animate-pulse">Analyzing the error with Gemini…</p>
          </div>
        )}

        {state === "failed" && (
          <div className="space-y-2">
            <p className="font-medium text-rose-300">Could not get an AI suggestion</p>
            <p className="whitespace-pre-wrap text-slate-400">{errorMessage}</p>
          </div>
        )}

        {state === "ready" && suggestion && (
          <div className="space-y-4">
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-violet-300/80">
                Summary
              </p>
              <p className="text-slate-200">{suggestion.summary}</p>
            </div>

            {suggestion.explanation && (
              <div>
                <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-violet-300/80">
                  Explanation
                </p>
                <p className="whitespace-pre-wrap text-slate-300">
                  {suggestion.explanation}
                </p>
              </div>
            )}

            {suggestion.correctedCode && (
              <div>
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="text-xs font-semibold uppercase tracking-wider text-violet-300/80">
                    Suggested code
                  </p>
                  <button
                    type="button"
                    disabled={applied}
                    onClick={() => {
                      onApplyFix(suggestion.correctedCode);
                      setApplied(true);
                    }}
                    className={`rounded-md px-3 py-1 text-xs font-semibold text-white transition ${
                      applied
                        ? "cursor-default bg-violet-900"
                        : "bg-violet-600 hover:bg-violet-500"
                    }`}
                  >
                    {applied ? "Applied!" : "Apply fix"}
                  </button>
                </div>
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg border border-violet-500/20 bg-black/40 p-3 font-mono text-xs text-violet-50">
                  {suggestion.correctedCode}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
