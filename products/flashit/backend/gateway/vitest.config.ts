import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
    test: {
        globals: true,
        environment: 'node',
        setupFiles: ['./src/__tests__/setup.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'json', 'html', 'lcov'],
            exclude: [
                'node_modules/',
                'dist/',
                '**/*.test.ts',
                '**/*.spec.ts',
                'prisma/',
                'src/__tests__/setup.ts',
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
            '@ghatana/data-access-context': path.resolve(
                __dirname,
                '../../../../platform/typescript/data-access-context/src/index.ts',
            ),
            '@flashit/shared': path.resolve(__dirname, '../../libs/ts/shared/src/index.ts'),
        },
    },
});
