import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./vitest.setup.ts'],
        exclude: [
            '**/node_modules/**',
            '**/dist/**',
            '**/e2e/**',
            '**/*.spec.ts',
        ],
        // Single-threaded for stability (prevents memory issues)
        // Can be parallelized in CI with more resources
        pool: 'threads',
        poolOptions: {
            threads: {
                singleThread: true,
            },
        },
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
        alias: {
            '@': path.resolve(__dirname, './src'),
            '@/app': path.resolve(__dirname, './src/app'),
            '@/features': path.resolve(__dirname, './src/features'),
            '@/shared': path.resolve(__dirname, './src/shared'),
            '@/hooks': path.resolve(__dirname, './src/hooks'),
            '@/state': path.resolve(__dirname, './src/state'),
            '@/services': path.resolve(__dirname, './src/services'),
            '@/styles': path.resolve(__dirname, './src/styles'),
        },
    },
});
