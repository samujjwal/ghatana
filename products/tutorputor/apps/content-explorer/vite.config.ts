import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 3205,
    proxy: {
      "/api": {
        target: "http://localhost:3200",
        changeOrigin: true,
        onError: (_err, _req, res) => {
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "API Gateway unavailable" }));
        },
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: process.env.NODE_ENV === "development",
    target: "es2020",
    minify: "esbuild",
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes("node_modules")) {
            if (id.includes("react")) return "react-vendor";
            if (id.includes("@tanstack/react-query")) return "react-query";
            if (id.includes("recharts")) return "recharts";
            if (id.includes("@ghatana")) return "ghatana-ui";
            if (id.includes("jotai")) return "jotai-state";
            return "vendor";
          }
          if (id.includes("pages/")) {
            const match = id.match(/pages\/([^/]+)/);
            if (match) return `page-${match[1]}`;
          }
        },
        chunkFileNames: "js/[name]-[hash].js",
        entryFileNames: "js/[name]-[hash].js",
        assetFileNames: (info) => {
          const ext = info.name?.split(".").pop() ?? "";
          if (/png|jpe?g|gif|svg/.test(ext)) return "images/[name]-[hash][extname]";
          if (/woff2?|eot|ttf|otf/.test(ext)) return "fonts/[name]-[hash][extname]";
          if (ext === "css") return "css/[name]-[hash][extname]";
          return "assets/[name]-[hash][extname]";
        },
      },
    },
    cssCodeSplit: true,
    chunkSizeWarningLimit: 800,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./vitest.setup.ts",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
});
