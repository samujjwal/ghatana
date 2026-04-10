# `@ghatana/code-editor`

Monaco-based code editor component with lazy loading for all Ghatana products.

## Overview

Provides a lazily-loaded Monaco editor wrapper (`LazyMonacoEditor`) that defers the full Monaco bundle until the component mounts, keeping initial page load times fast.

## Usage

```tsx
import LazyMonacoEditor from '@ghatana/code-editor';
// or named import:
import { LazyMonacoEditor } from '@ghatana/code-editor';
import type { LazyMonacoEditorProps } from '@ghatana/code-editor';

<LazyMonacoEditor
  language="typescript"
  value={code}
  onChange={(value) => setCode(value ?? '')}
  height="400px"
/>
```

## API

| Export | Description |
|--------|-------------|
| `LazyMonacoEditor` | Lazily-loaded Monaco editor React component |
| `LazyMonacoEditorProps` | Props interface for `LazyMonacoEditor` |

## Installation

```jsonc
// package.json
"dependencies": {
  "@ghatana/code-editor": "workspace:*"
}
```
