import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import path from "path";

export default defineConfig({
  plugins: [react()],
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
      "@ghatana/design-system": path.resolve(__dirname, "../../../../platform/typescript/design-system/src/index.ts"),
      "@ghatana/theme": path.resolve(__dirname, "../../../../platform/typescript/theme/dist/index.js"),
      "@ghatana/tokens": path.resolve(__dirname, "../../../../platform/typescript/tokens/dist/index.js"),
      "@ghatana/platform-utils": path.resolve(__dirname, "../../../../platform/typescript/foundation/platform-utils/dist/index.js"),
    },
    preserveSymlinks: true
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    rollupOptions: {
      external: [
        '@tanstack/query-core',
        '@tanstack/query-devtools',
        'react-router',
        'react-router/dom',
        '@mui/utils',
        '@mui/material',
        '@mui/system',
        '@emotion/react',
        '@emotion/styled'
      ],
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


