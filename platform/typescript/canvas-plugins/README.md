# @ghatana/canvas-plugins

Plugin registry, plugin manager, and extension APIs for the Ghatana canvas.

## Usage

```ts
import { PluginManager, getPluginManager } from '@ghatana/canvas-plugins';
import type { CanvasPlugin, PluginManifest } from '@ghatana/canvas-plugins';
```

## API Surface

- `PluginManager` — lifecycle management (install, activate, deactivate, uninstall)
- `getPluginManager()` — singleton accessor
- Full registry suite: element, node, tool, panel, shortcut, context-menu
- Plugin types: `CanvasPlugin`, `PluginManifest`, `PluginContext`, `PluginCanvasAPI`
