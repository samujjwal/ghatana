import { defineConfig, loadEnv } from "vite";
import { reactRouter } from "@react-router/dev/vite";
import path from "path";

// Load environment variables - use 'development' mode by default for dev server
const mode = process.env.NODE_ENV || 'development';
const env = loadEnv(mode, process.cwd());
const useMocks = env.VITE_USE_MOCKS === 'true';
console.log('[Vite Config] NODE_ENV:', mode, 'VITE_USE_MOCKS:', env.VITE_USE_MOCKS, '=> useMocks:', useMocks);

const workspaceAliases = [
    {
        find: /^@ghatana\/design-system$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/design-system/src/index.ts"),
    },
    {
        find: /^@ghatana\/design-system\/icons$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/design-system/src/icons/index.tsx"),
    },
    {
        find: /^@ghatana\/ui-integration$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/ui-integration/src/index.ts"),
    },
    {
        find: /^@ghatana\/tokens$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/tokens/src/index.ts"),
    },
    {
        find: /^@ghatana\/api$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/api/src/index.ts"),
    },
    {
        find: /^@ghatana\/realtime$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/realtime/src/index.ts"),
    },
    {
        find: /^@ghatana\/theme$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/theme/src/index.ts"),
    },
    {
        find: /^@ghatana\/utils$/,
        replacement: path.resolve(__dirname, "../../../../platform/typescript/utils/src/index.ts"),
    },
    {
        find: /^react-router-dom$/,
        replacement: path.resolve(__dirname, "./node_modules/react-router-dom"),
    },
    {
        find: /^react-router$/,
        replacement: path.resolve(__dirname, "./node_modules/react-router"),
    },
];

export default defineConfig({
    // Provide a compile-time `process.env` shim for packages that reference
    // Node-style env variables so they don't blow up in browser runtime.
    // We expose at least NODE_ENV (falling back to 'development').
    define: {
        'process.env': JSON.stringify({ NODE_ENV: process.env.NODE_ENV || 'development' }),
        'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development'),
    },
    plugins: [
        // React Router v7 Framework Mode plugin - supports both SSR and SPA
        reactRouter(),
    ],
    resolve: {
        alias: [
            ...workspaceAliases,
            { find: "@", replacement: path.resolve(__dirname, "./src") },
            { find: "@/app", replacement: path.resolve(__dirname, "./src/app") },
            { find: "@/features", replacement: path.resolve(__dirname, "./src/features") },
            { find: "@/shared", replacement: path.resolve(__dirname, "./src/shared") },
            { find: "@/hooks", replacement: path.resolve(__dirname, "./src/hooks") },
            { find: "@/state", replacement: path.resolve(__dirname, "./src/state") },
            { find: "@/services", replacement: path.resolve(__dirname, "./src/services") },
            { find: "@/styles", replacement: path.resolve(__dirname, "./src/styles") },
            { find: "@/pages", replacement: path.resolve(__dirname, "./src/pages") },
            { find: "@/components", replacement: path.resolve(__dirname, "./src/components") },
            // Local workspace aliases for Yappc DevSecOps store and types so
            // DevSecOpsBoardPage and related adapters can import them directly.
            {
                find: "@ghatana/yappc-store/devsecops",
                replacement: path.resolve(__dirname, "../../../yappc/app-creator/libs/store/src/devsecops"),
            },
            {
                find: "@ghatana/yappc-types/devsecops",
                replacement: path.resolve(__dirname, "../../../yappc/app-creator/libs/types/src/devsecops"),
            },
        ],
    },
    server: {
        // Bind to 0.0.0.0 to ensure both IPv4 and IPv6 interfaces are available
        // and browser clients can connect regardless of DNS resolution of "localhost".
        host: '0.0.0.0',
        port: Number(env.PORT || process.env.PORT) || 4000,
        // If the port is already in use fail fast instead of picking another
        strictPort: true,
        // Enable proxy even when using MSW mocks (VITE_USE_MOCKS=true)
        // This allows MSW to passthrough specific requests (like /api/v1/org/*) to the real backend
        proxy: {
            "/api": {
                target: "http://localhost:3101",
                changeOrigin: true,
                // Don't rewrite - backend already expects /api/v1
                // Frontend calls /api/v1/... which proxies directly to backend
            },
            "/socket.io": {
                target: "http://localhost:3101",
                changeOrigin: true,
                ws: true, // Enable WebSocket proxying
            },
        },
        // Use explicit HMR host/protocol so WS connections don't try the wrong
        // origin when the dev server picks a different port at startup.
        // We prefer localhost with ws protocol and rely on the browser to connect
        // to the actual port the dev server uses.
        hmr: {
            protocol: (env.VITE_HMR_PROTOCOL as 'ws' | 'wss') || 'ws',
            host: env.VITE_HMR_HOST || 'localhost',
            port: Number(env.VITE_HMR_PORT || env.PORT || process.env.PORT) || 4000,
            // Ensure HMR works properly in Docker and network environments
            clientPort: 4000,
            overlay: true,
        },
        // Enable watch for file changes with better performance
        watch: {
            usePolling: true,
            interval: 500,
            // Reduce watched files to prevent EMFILE errors
            ignored: ['**/node_modules/**', '**/dist/**', '**/coverage/**', '**/.git/**'],
        },
    },
    build: {
        outDir: "dist",
        sourcemap: true,
        rollupOptions: {
            output: {
                manualChunks(id: string) {
                    // Split major libraries into dedicated chunks to avoid a single large vendor bundle
                    if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) return 'react-vendor';
                    if (id.includes('node_modules/@tanstack/react-query')) return 'react-query';
                    if (id.includes('node_modules/jotai')) return 'jotai';
                    if (id.includes('node_modules/axios')) return 'axios';
                    if (id.includes('node_modules')) return 'vendor';
                    if (id.includes('/src/services/api/kpisApi')) return 'kpisApi';
                    if (id.includes('/src/services/api/mlApi')) return 'mlApi';
                    if (id.includes('/src/services/api/automationApi')) return 'automationApi';
                    if (id.includes('/src/services/api/monitoringApi')) return 'monitoringApi';
                    if (id.includes('/src/features/ml-observatory') || id.includes('MLObservatory')) return 'ml-observatory';
                    if (id.includes('/src/features/automation-engine') || id.includes('AutomationEngine')) return 'automation-engine';
                    if (id.includes('/src/features/dashboard') || id.includes('Dashboard')) return 'dashboard';
                    // default: let Rollup decide
                    return null;
                },
            },
        },
    },
});
