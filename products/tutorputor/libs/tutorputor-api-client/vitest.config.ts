import { defineConfig } from "vitest/config";
import { resolve } from "path";

const authClientSrc = resolve(__dirname, "../tutorputor-auth-client/src");

export default defineConfig({
  resolve: {
    // Subpath aliases must appear before the package root alias
    alias: [
      {
        find: "@tutorputor/auth-client/headers",
        replacement: resolve(authClientSrc, "headers.ts"),
      },
      {
        find: "@tutorputor/auth-client/token",
        replacement: resolve(authClientSrc, "token.ts"),
      },
      {
        find: "@tutorputor/auth-client/storage",
        replacement: resolve(authClientSrc, "storage.ts"),
      },
      {
        find: "@tutorputor/auth-client",
        replacement: resolve(authClientSrc, "index.ts"),
      },
    ],
  },
  test: {
    environment: "jsdom",
    globals: false,
  },
});
