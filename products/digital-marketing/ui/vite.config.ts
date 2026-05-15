import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { resolve } from "path";

const workspaceAliases = {
  "@ghatana/design-system": resolve(
    __dirname,
    "../../../platform/typescript/design-system/src/index.ts",
  ),
  "@ghatana/platform-utils": resolve(
    __dirname,
    "../../../platform/typescript/platform-utils/src/index.ts",
  ),
  "@ghatana/product-shell": resolve(
    __dirname,
    "../../../platform/typescript/product-shell/src/index.ts",
  ),
  "@ghatana/theme": resolve(
    __dirname,
    "../../../platform/typescript/theme/src/index.ts",
  ),
  "@ghatana/tokens": resolve(
    __dirname,
    "../../../platform/typescript/tokens/src/index.ts",
  ),
};

const apiProxyTarget =
  process.env.DMOS_API_PROXY_TARGET ??
  process.env.VITE_API_BASE_URL ??
  "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    chunkSizeWarningLimit: 250,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules/react") || id.includes("node_modules/react-dom") || id.includes("node_modules/react-router-dom")) {
            return "react";
          }
          if (id.includes("node_modules/@tanstack/react-query")) {
            return "query";
          }
          return undefined;
        },
      },
    },
  },
  resolve: {
    alias: {
      ...workspaceAliases,
      "@": resolve(__dirname, "src"),
    },
  },
  server: {
    port: 5174,
    proxy: {
      "/v1": {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
});
