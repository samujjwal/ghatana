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
      "@ghatana/charts": path.resolve(__dirname, "../../../../platform/typescript/charts/src/index.ts"),
      "@ghatana/design-system": path.resolve(__dirname, "../../../../platform/typescript/design-system/src/index.ts"),
      "@ghatana/product-shell": path.resolve(__dirname, "../../../../platform/typescript/product-shell/src/index.ts"),
      "@ghatana/theme": path.resolve(__dirname, "../../../../platform/typescript/theme/src/index.ts"),
      "@ghatana/tokens": path.resolve(__dirname, "../../../../platform/typescript/tokens/src/index.ts"),
      "@ghatana/platform-utils": path.resolve(__dirname, "../../../../platform/typescript/platform-utils/src/index.ts"),
      "@tutorputor/core": path.resolve(__dirname, "../../libs/tutorputor-core/src/index.ts"),
      "@tutorputor/simulation/renderer/easing": path.resolve(__dirname, "../../libs/tutorputor-simulation/src/renderer/easing.ts"),
      "@tutorputor/simulation": path.resolve(__dirname, "../../libs/tutorputor-simulation/src/index.ts"),
      "@tutorputor/ui": path.resolve(__dirname, "../../libs/tutorputor-ui/src/index.ts"),
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    // F-027: Bundle size budget — report compressed size for CI comparison
    reportCompressedSize: true,
    target: "es2020",
    minify: "esbuild",
    rollupOptions: {
      input: {
        main: path.resolve(__dirname, "index.html"),
        sw: path.resolve(__dirname, "src/sw.ts"),
      },
      output: {
        entryFileNames: (chunkInfo) => {
          return chunkInfo.name === 'sw' ? '[name].js' : 'assets/[name]-[hash].js';
        },
        // F-027: Manual chunk splitting to keep feature modules < 50KB gzipped
        // and core vendor libs < 100KB gzipped.
        manualChunks: (id) => {
          if (!id.includes("node_modules")) {
            // Route-level code splitting for lazy-loaded pages
            if (id.includes("/pages/")) {
              const match = id.match(/\/pages\/([^/]+)\//);
              if (match) return `page-${match[1]}`;
            }
            return undefined;
          }
          // Stable, cacheable vendor chunks
          if (id.includes("react-dom") || id.includes("react/")) return "react-vendor";
          if (id.includes("@tanstack/react-query")) return "react-query";
          if (id.includes("react-router")) return "react-router";
          if (id.includes("@ghatana/design-system") || id.includes("@ghatana/tokens") || id.includes("@ghatana/theme")) return "ghatana-ui";
          if (id.includes("@ghatana/charts")) return "ghatana-charts";
          if (id.includes("jotai")) return "jotai-state";
          if (id.includes("lucide-react")) return "lucide-icons";
          if (id.includes("zod")) return "zod";
          return "vendor-other";
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
