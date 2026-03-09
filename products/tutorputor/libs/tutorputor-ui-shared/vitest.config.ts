import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        environment: 'node',
        include: ['tests/**/*.test.ts', 'tests/**/*.test.tsx'],
        coverage: {
            provider: 'v8',
            include: ['src/**/*.ts', 'src/**/*.tsx'],
            reporter: ['text', 'lcov'],
            thresholds: {
                lines: 90,
                branches: 80,
                functions: 90,
                statements: 90,
            },
        },
    },
});
