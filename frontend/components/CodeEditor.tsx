"use client";

import CodeMirror from "@uiw/react-codemirror";
import { python } from "@codemirror/lang-python";
import { EditorView } from "@codemirror/view";

const editorTheme = EditorView.theme({
  "&": {
    backgroundColor: "transparent",
    height: "100%",
  },
  ".cm-gutters": {
    backgroundColor: "#121820",
    borderRight: "1px solid #2a3544",
    color: "#64748b",
  },
  ".cm-activeLineGutter": {
    backgroundColor: "#1a2330",
  },
  ".cm-activeLine": {
    backgroundColor: "rgba(61, 214, 140, 0.06)",
  },
  ".cm-cursor": {
    borderLeftColor: "#3dd68c",
  },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground": {
    backgroundColor: "rgba(61, 214, 140, 0.2) !important",
  },
});

interface CodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}

export function CodeEditor({ value, onChange, readOnly }: CodeEditorProps) {
  return (
    <CodeMirror
      value={value}
      height="100%"
      theme="dark"
      extensions={[python(), editorTheme, EditorView.lineWrapping]}
      onChange={onChange}
      editable={!readOnly}
      basicSetup={{
        lineNumbers: true,
        foldGutter: true,
        highlightActiveLine: true,
        bracketMatching: true,
        autocompletion: false,
      }}
    />
  );
}
