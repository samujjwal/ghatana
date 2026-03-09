import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import compression from "vite-plugin-compression";
import path from "path";

export default defineConfig({
  plugins: [
    react(),
    // Enable gzip compression for production builds
    compression({
      verbose: true,
      disable: false,
      threshold: 10240, // Compress files larger than 10KB
      algorithm: "gzip",
      ext: ".gz",
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 3202,
    proxy: {
      "/api": {
        target: "http://localhost:3200",
        changeOrigin: true,
        onError: (err, req, res) => {
          console.error("[API Proxy Error]", err.message);
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "API Gateway unavailable" }));
        },
      },
      "/admin/api": {
        target: "http://localhost:3200",
        changeOrigin: true,
        onError: (err, req, res) => {
          console.error("[Admin API Proxy Error]", err.message);
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Admin API Gateway unavailable" }));
        },
      },
      "/auth": {
        target: "http://localhost:3200",
        changeOrigin: true,
        onError: (err, req, res) => {
          console.error("[Auth Proxy Error]", err.message);
          res.writeHead(503, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "Auth service unavailable" }));
        },
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: process.env.NODE_ENV === "development",
    // Performance optimization
    target: "es2020",
    minify: "esbuild",
    reportCompressedSize: true,

    // Chunk optimization for code splitting
    rollupOptions: {
      output: {
        // Optimize chunk sizes for better caching
        manualChunks: (id) => {
          // Vendor dependencies
          if (id.includes("node_modules")) {
            if (id.includes("react")) {
              return "react-vendor";
            }
            if (id.includes("@tanstack/react-query")) {
              return "react-query";
            }
            if (id.includes("@ghatana")) {
              return "ghatana-ui";
            }
            if (id.includes("lucide-react")) {
              return "lucide-icons";
            }
            if (id.includes("jotai")) {
              return "jotai-state";
            }
            if (id.includes("react-dnd")) {
              return "react-dnd";
            }
            return "vendor-other";
          }

          // Feature-based chunks
          if (id.includes("pages/")) {
            const match = id.match(/pages\/([^/]+)\//);
            if (match) {
              return `page-${match[1]}`;
            }
          }

          if (id.includes("components/")) {
            return "components";
          }

          if (id.includes("hooks/")) {
            return "hooks";
          }
        },
        // Optimize chunk names for caching
        chunkFileNames: "js/[name]-[hash].js",
        entryFileNames: "js/[name]-[hash].js",
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name.split(".");
          const ext = info[info.length - 1];
          if (/png|jpe?g|gif|svg/.test(ext)) {
            return `images/[name]-[hash][extname]`;
          } else if (/woff|woff2|eot|ttf|otf/.test(ext)) {
            return `fonts/[name]-[hash][extname]`;
          } else if (ext === "css") {
            return `css/[name]-[hash][extname]`;
          } else {
            return `assets/[name]-[hash][extname]`;
          }
        },
      },
    },

    // CSS optimization
    cssCodeSplit: true,
    cssMinify: true,

    // Asset optimization
    assetsInlineLimit: 4096,

    // Size warnings
    chunkSizeWarningLimit: 800,

    // Environment-specific settings
    commonjsOptions: {
      transformMixedEsModules: true,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./vitest.setup.ts",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
});
