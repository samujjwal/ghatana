"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_1 = require("vitest/config");
const plugin_react_1 = require("@vitejs/plugin-react");
const path_1 = require("path");
exports.default = (0, config_1.defineConfig)({
    plugins: [(0, plugin_react_1.default)()],
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: ['../../libs/test/setupTests.ts'],
        include: [
            '**/__tests__/**/*.{test,spec}.{ts,tsx}',
            '**/*.{test,spec}.{ts,tsx}',
        ],
        exclude: [
            '**/node_modules/**',
            '**/dist/**',
            '**/build/**',
            '**/.{idea,git,cache,output,temp}/**',
            'e2e/**',
        ],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html', 'lcov'],
            exclude: ['**/*.d.ts', '**/dist/**', '**/build/**', '**/__tests__/**'],
        },
        testTimeout: 10000,
        hookTimeout: 10000,
        teardownTimeout: 10000,
        isolate: true,
        reporters: ['default', 'json', 'junit'],
        outputFile: {
            json: './test-results/results.json',
            junit: './test-results/junit.xml',
        },
    },
    resolve: {
        alias: {
            '@ghatana/yappc-types': (0, path_1.resolve)(__dirname, '../../libs/types/src'),
            '@ghatana/yappc-graphql': (0, path_1.resolve)(__dirname, '../../libs/graphql/src'),
            '@ghatana/yappc-mocks': (0, path_1.resolve)(__dirname, '../../libs/mocks/src'),
            '@ghatana/yappc-diagram': (0, path_1.resolve)(__dirname, '../../libs/diagram/src'),
            '@ghatana/yappc-test-helpers': (0, path_1.resolve)(__dirname, '../../libs/test-helpers/src'),
            '@ghatana/ui': (0, path_1.resolve)(__dirname, '../../../../../libs/typescript/ui/src'),
        },
    },
});
