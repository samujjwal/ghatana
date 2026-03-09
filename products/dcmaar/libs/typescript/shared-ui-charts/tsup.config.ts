import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm', 'cjs'],
  dts: true,
  sourcemap: true,
  clean: true,
  external: ['react', 'react-dom', 'recharts'],
  esbuildOptions: (options) => {
    // Append "use client" to the top of the entry point
    options.banner = {
      js: '"use client";',
    };
  },
});
