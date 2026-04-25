import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { resolve } from "path";

const workspaceAliases = {
  "@ghatana/design-system": resolve(
    __dirname,
    "../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/canvas": resolve(
    __dirname,
    "../../../platform/typescript/canvas/src/index.ts",
  ),
  "@ghatana/canvas/flow": resolve(
    __dirname,
    "../../../platform/typescript/canvas/src/flow/index.ts",
  ),
  "@ghatana/theme": resolve(
    __dirname,
    "../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": resolve(
    __dirname,
    "../../../platform/typescript/tokens/src/index.ts",
  ),
  "@ghatana/platform-utils": resolve(
    __dirname,
    "../../../platform/typescript/platform-utils/src/index.ts",
  ),
  "@ghatana/domain-components": resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/index.ts",
  ),
  "@ghatana/domain-components/privacy": resolve(
    __dirname,
    "../../../platform/typescript/domain-components/src/privacy/index.ts",
  ),
  "@ghatana/realtime": resolve(
    __dirname,
    "../../../platform/typescript/realtime/src/index.ts",
  ),
  "@ghatana/platform-events": resolve(
    __dirname,
    "../../../platform/typescript/platform-events/src/index.ts",
  ),
  "@ghatana/security-ui": resolve(
    __dirname,
    "../../../platform/typescript/security-ui/src/index.ts",
  ),
  "@ghatana/voice-ui": resolve(
    __dirname,
    "../../../platform/typescript/voice-ui/src/index.ts",
  ),
  "@ghatana/nlp-ui": resolve(
    __dirname,
    "../../../platform/typescript/nlp-ui/src/index.ts",
  ),
  "@ghatana/audit-ui": resolve(
    __dirname,
    "../../../platform/typescript/audit-ui/src/index.ts",
  ),
  "@ghatana/selection-ui": resolve(
    __dirname,
    "../../../platform/typescript/selection-ui/src/index.ts",
  ),
  "@audio-video/ui": resolve(
    __dirname,
    "../../../products/audio-video/libs/audio-video-ui/src/index.tsx",
  ),
  clsx: resolve(__dirname, "node_modules/clsx"),
  "tailwind-merge": resolve(__dirname, "node_modules/tailwind-merge"),
};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      ...workspaceAliases,
      "@": resolve(__dirname, "src"),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Heavy third-party libraries split into separate chunks (P7-3d)
          "vendor-react": ["react", "react-dom", "react-router"],
          "vendor-editor": ["@monaco-editor/react"],
          "vendor-flow": ["@xyflow/react"],
          "vendor-charts": ["recharts"],
          "vendor-query": ["@tanstack/react-query", "jotai"],
        },
      },
    },
  },
  server: {
    port: 3001,
    proxy: {
      "/api": "http://localhost:8090",
      "/admin": "http://localhost:8090",
      "/events": "http://localhost:8090",
    },
  },
});
