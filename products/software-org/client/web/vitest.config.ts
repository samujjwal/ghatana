import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

const workspaceAliases = [
    {
        find: /^@ghatana\/design-system$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/design-system/src/index.ts'),
    },
    {
        find: /^@ghatana\/design-system\/icons$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/design-system/src/icons/index.tsx'),
    },
    {
        find: /^@ghatana\/ui-integration$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/ui-integration/src/index.ts'),
    },
    {
        find: /^@ghatana\/tokens$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/tokens/src/index.ts'),
    },
    {
        find: /^@ghatana\/api$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/api/src/index.ts'),
    },
    {
        find: /^@ghatana\/realtime$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/realtime/src/index.ts'),
    },
    {
        find: /^@ghatana\/theme$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/theme/src/index.ts'),
    },
    {
        find: /^@ghatana\/utils$/,
        replacement: path.resolve(__dirname, '../../../../platform/typescript/utils/src/index.ts'),
    },
    {
        find: /^react-router-dom$/,
        replacement: path.resolve(__dirname, './node_modules/react-router-dom'),
    },
    {
        find: /^react-router$/,
        replacement: path.resolve(__dirname, './node_modules/react-router'),
    },
];

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./vitest.setup.ts'],
        alias: workspaceAliases,
        exclude: [
            '**/node_modules/**',
            '**/dist/**',
            '**/e2e/**',
            '**/*.spec.ts',
        ],
        // Keep file execution serial for stability while shared packages are source-linked.
        fileParallelism: false,
        // Timeout configurations
        testTimeout: 15000,
        hookTimeout: 15000,
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html', 'lcov'],
            include: [
                'src/lib/persona/**/*.{ts,tsx}',
                'src/schemas/persona.schema.ts',
                'src/hooks/usePersonaComposition.ts',
                'src/components/persona/**/*.{ts,tsx}',
            ],
            exclude: [
                '**/__tests__/**',
                '**/*.test.{ts,tsx}',
                '**/*.spec.{ts,tsx}',
                '**/types.ts',
                '**/index.ts',
            ],
            thresholds: {
                lines: 80,
                functions: 80,
                branches: 80,
                statements: 80,
            },
        },
    },
    resolve: {
        alias: [
            ...workspaceAliases,
            { find: '@', replacement: path.resolve(__dirname, './src') },
            { find: '@/app', replacement: path.resolve(__dirname, './src/app') },
            { find: '@/features', replacement: path.resolve(__dirname, './src/features') },
            { find: '@/shared', replacement: path.resolve(__dirname, './src/shared') },
            { find: '@/hooks', replacement: path.resolve(__dirname, './src/hooks') },
            { find: '@/state', replacement: path.resolve(__dirname, './src/state') },
            { find: '@/services', replacement: path.resolve(__dirname, './src/services') },
            { find: '@/styles', replacement: path.resolve(__dirname, './src/styles') },
        ],
    },
});
