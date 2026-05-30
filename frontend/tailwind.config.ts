import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
      },
      colors: {
        surface: {
          DEFAULT: "#0f1419",
          raised: "#161d27",
          overlay: "#1c2533",
        },
        accent: {
          DEFAULT: "#3dd68c",
          muted: "#2a9d6a",
        },
      },
      boxShadow: {
        glow: "0 0 40px -12px rgba(61, 214, 140, 0.35)",
      },
    },
  },
  plugins: [],
};

export default config;
