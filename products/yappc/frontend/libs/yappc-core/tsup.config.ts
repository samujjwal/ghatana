import { defineConfig } from 'tsup'

export default defineConfig({
  entry: {
    index: 'src/index.ts',
    'utils/index': 'src/utils/index.ts',
    'types/index': 'src/types/index.ts',
    'constants/index': 'src/constants/index.ts'
  },
  format: ['cjs', 'esm'],
  dts: true,
  clean: true,
  external: ['react', 'react-dom'],
  treeshake: true,
  splitting: false,
  minify: process.env.NODE_ENV === 'production',
  sourcemap: true,
})
