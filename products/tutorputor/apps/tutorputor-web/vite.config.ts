import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import path from "path";

export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    include: ["scheduler", "react-dom", "react-dom/client"],
  },
  server: {
    host: '127.0.0.1',
    port: 3201,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:3200",
        changeOrigin: true,
        secure: false
      }
    }
  },
  resolve: {
    dedupe: ["react", "react-dom", "react-router", "react-router-dom"],
    alias: {
      "@": path.resolve(__dirname, "./src"),
      "@ghatana/charts": path.resolve(__dirname, "../../../../platform/typescript/charts/dist/index.js"),
      "@ghatana/design-system": path.resolve(__dirname, "../../../../platform/typescript/design-system/src/index.ts"),
      "@ghatana/theme": path.resolve(__dirname, "../../../../platform/typescript/theme/src/index.ts"),
      "@ghatana/tokens": path.resolve(__dirname, "../../../../platform/typescript/tokens/dist/index.js"),
      "@ghatana/platform-utils": path.resolve(__dirname, "../../../../platform/typescript/foundation/platform-utils/dist/index.js"),
      "@tutorputor/core": path.resolve(__dirname, "../../libs/tutorputor-core/src/index.ts"),
      "@tutorputor/simulation/renderer/easing": path.resolve(__dirname, "../../libs/tutorputor-simulation/src/renderer/easing.ts"),
      "@tutorputor/simulation": path.resolve(__dirname, "../../libs/tutorputor-simulation/src/index.ts"),
      "@tutorputor/ui": path.resolve(__dirname, "../../libs/tutorputor-ui/src/index.ts"),
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, "index.html"),
        sw: path.resolve(__dirname, "src/sw.ts"),
      },
      output: {
        entryFileNames: (chunkInfo) => {
          return chunkInfo.name === 'sw' ? '[name].js' : 'assets/[name]-[hash].js';
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./vitest.setup.ts"
  }
});


