# @yappc/config-compiler

Compiles declarative configs into React code and canvas scenes.

## Installation

```bash
pnpm add @yappc/config-compiler
```

## Usage

```typescript
import { ConfigCompiler, mergeCompilerOptions } from '@yappc/config-compiler';

const compiler = new ConfigCompiler();
const options = mergeCompilerOptions({
  typescript: true,
  includeComments: true,
});

const result = await compiler.compile(pageConfig, options);
```

## Architecture

- **ConfigCompiler**: Main compiler that orchestrates code generation
- **Types**: Compiler context, options, and result types
- **Generators**: Code and canvas generators (TODO)

## Development

```bash
pnpm build
pnpm test
pnpm type-check
```
