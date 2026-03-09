import { defineConfig, loadEnv, type ConfigEnv, type UserConfig } from "vite";
import { crx } from "@crxjs/vite-plugin";
import react from "@vitejs/plugin-react";
import { resolve, dirname } from "path";
import { excludeNodeModules } from "./vite-plugin-exclude-node-modules";

// Get __dirname equivalent in ES modules
const __dirname = dirname(new URL(import.meta.url).pathname);

import type { ManifestV3Export } from "@crxjs/vite-plugin";
import manifestJson from "./manifest.config";

const manifest = manifestJson as ManifestV3Export;

interface EnvVariables {
  BROWSER?: string;
  [key: string]: string | undefined;
}

const getBrowserConfig = (env: EnvVariables): typeof manifest => {
  const browser = env["BROWSER"] || "chrome";
  const isFirefox = browser === "firefox";

  const browserManifest = JSON.parse(JSON.stringify(manifest));

  if (isFirefox) {
    browserManifest.browser_specific_settings = {
      gecko: {
        id: "{guardian-extension@example.com}",
        strict_min_version: "109.0",
      },
    };

    if (browserManifest.background?.service_worker) {
      const { service_worker, ...rest } = browserManifest.background;
      browserManifest.background = {
        ...rest,
        type: "module",
        scripts: [service_worker],
      };
    }
  }

  return browserManifest;
};

export default defineConfig(({ mode }: ConfigEnv): UserConfig => {
  const env = loadEnv(mode, process.cwd(), "") as EnvVariables;
  const browser = env["BROWSER"] || "chrome";
  const browserConfig = getBrowserConfig(env);

  return {
    base: "/",
    define: {
      __BROWSER__: JSON.stringify(browser),
      "process.env.NODE_ENV": JSON.stringify(mode),
    },
    resolve: {
      conditions: ['browser'], // Force browser export conditions
      dedupe: ["react", "react-dom"],
      alias: [
        { find: "@", replacement: resolve(__dirname, "./src") },
        {
          find: "@components",
          replacement: resolve(__dirname, "./src/components"),
        },
        {
          find: "@services",
          replacement: resolve(__dirname, "./src/services"),
        },
        { find: "@utils", replacement: resolve(__dirname, "./src/utils") },
        { find: "@types", replacement: resolve(__dirname, "./src/types") },
        {
          find: "@ghatana/dcmaar-browser-extension-core",
          replacement: resolve(
            __dirname,
            "../../../../libs/typescript/browser-extension-core/src"
          ),
        },
        {
          find: "@ghatana/dcmaar-guardian-plugins",
          replacement: resolve(
            __dirname,
            "../../packages/guardian-plugins/src"
          ),
        },
        {
          find: "@ghatana/dcmaar-connectors",
          replacement: resolve(
            __dirname,
            "../../../../libs/typescript/connectors/src/index.browser"
          ),
        },
        {
          find: "@ghatana/dcmaar-shared-ui-charts",
          replacement: resolve(
            __dirname,
            "../../../../libs/typescript/shared-ui-charts/src"
          ),
        },
        {
          find: "@ghatana/dcmaar-shared-ui-tailwind",
          replacement: resolve(
            __dirname,
            "../../../../libs/typescript/shared-ui-tailwind/src"
          ),
        },
      ],
    },
    build: {
      outDir: `dist/${browser}`,
      emptyOutDir: true,
      rollupOptions: {
        input: {
          popup: resolve(__dirname, "src/popup/index.html"),
          dashboard: resolve(__dirname, "src/dashboard/index.html"),
          options: resolve(__dirname, "src/options/index.html"),
          "content-script": resolve(__dirname, "src/content/index.ts"),
        },
        output: {
          dir: `dist/${browser}`,
          entryFileNames: "[name].js",
          chunkFileNames: "assets/[name]-[hash].js",
          assetFileNames: "assets/[name]-[hash].[ext]",
          format: "es",
          inlineDynamicImports: false,
          manualChunks(id, { getModuleInfo }) {
            // Prevent code splitting for content script
            if (id.includes("src/content/")) {
              return "content-script";
            }
            // Keep UI code separate from content script
            if (
              id.includes("node_modules/react/") ||
              id.includes("node_modules/react-dom/")
            ) {
              return "react-vendor";
            }
            if (id.includes("node_modules/scheduler/")) {
              return "react-vendor";
            }
          },
        },
        preserveEntrySignatures: "strict",
      },
      target: "es2022",
      minify: "esbuild",
      sourcemap: true,
      modulePreload: { polyfill: false },
      chunkSizeWarningLimit: 2000,
    },
    plugins: [
      excludeNodeModules(), // Exclude Node.js-only modules from browser bundle
      react({
        jsxRuntime: "classic",
        jsxImportSource: "react",
      }),
      crx({
        manifest: browserConfig as unknown as chrome.runtime.ManifestV3,
      } as any),
    ],
    server: {
      port: 3000,
      strictPort: true,
      hmr: {
        port: 3000,
      },
    },
    optimizeDeps: {
      include: ["react", "react-dom"],
    },
  };
});
