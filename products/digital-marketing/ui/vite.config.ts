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

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      ...workspaceAliases,
      "@": resolve(__dirname, "src"),
    },
  },
  server: {
    port: 5174,
  },
});
