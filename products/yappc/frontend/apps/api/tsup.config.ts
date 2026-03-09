import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm'],
  target: 'node20',
  clean: true,
  sourcemap: true,
  minify: false,
  splitting: false,
  bundle: true,
  noExternal: [/(^(?!@prisma|pg|@pgbouncer).*)/, /(^\.\.?\/)/],
  external: ['@prisma/client', '@prisma/adapter-pg', 'pg'],
  platform: 'node',
  esbuildOptions(options) {
    options.resolveExtensions = ['.ts', '.js', '.mjs', '.json'];
    options.banner = {
      js: "import { createRequire } from 'module'; const require = createRequire(import.meta.url);",
    };
  },
});
