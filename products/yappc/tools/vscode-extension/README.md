# YAPPC Canvas Sync - VS Code Extension

Comprehensive VS Code integration for YAPPC canvas with bidirectional synchronization, code scaffolding, and embedded canvas viewing.

## Features

### 🎨 Canvas Webview
- **Embedded Canvas**: View YAPPC canvas directly in VS Code webview panel
- **Real-time Integration**: Two-way communication between VS Code and canvas
- **Connection Monitoring**: Automatic timeout detection and error handling

### 🌲 Canvas Tree View
- **Hierarchical Navigation**: Browse all canvas nodes in the Explorer sidebar
- **Icon Mapping**: Visual icons for different node types (component, service, API, database, etc.)
- **Click to Open**: Jump to associated files with a single click

### 🔄 Bidirectional File Sync
- **WebSocket Integration**: Real-time sync between VS Code and canvas
- **Auto-Sync**: Optional automatic file synchronization on save
- **File Watcher**: Monitors TypeScript/JavaScript file changes
- **Sync All Command**: Bulk sync all workspace files to canvas

### 🏗️ Code Scaffolding
- **Template-Based Generation**: Generate code from canvas nodes
- **Multiple Languages**: Support for React components, services, APIs, and database schemas
- **Smart Templates**: Context-aware code generation based on node type
- **Auto-Directory Creation**: Automatically creates parent directories

### 🔗 Open in Canvas
- **File-to-Canvas Navigation**: Open current file in canvas with node highlighting
- **Context Menu Integration**: Right-click in editor to navigate to canvas
- **Node ID Tracking**: Maintains mapping between files and canvas nodes

## Installation

### From Marketplace (Coming Soon)
1. Open VS Code
2. Press `Ctrl+P` (or `Cmd+P` on Mac)
3. Type `ext install yappc.yappc-canvas-sync`

### From Source
```bash
cd products/yappc/vscode-extension
npm install
npm run compile
```

Press `F5` to launch extension development host.

## Configuration

Open VS Code settings (`Ctrl+,` or `Cmd+,`) and configure:

```json
{
  "yappc.canvasUrl": "http://localhost:3000",
  "yappc.enableAutoSync": false,
  "yappc.syncInterval": 5000
}
```

### Settings Reference

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `yappc.canvasUrl` | string | `http://localhost:3000` | URL of the YAPPC canvas application |
| `yappc.enableAutoSync` | boolean | `false` | Enable automatic file sync to canvas on save |
| `yappc.syncInterval` | number | `5000` | Auto-sync interval in milliseconds |

## Commands

All commands are accessible via Command Palette (`Ctrl+Shift+P` or `Cmd+Shift+P`):

| Command | Description | Shortcut |
|---------|-------------|----------|
| `YAPPC: Open Canvas Webview` | Opens YAPPC canvas in embedded webview panel | - |
| `YAPPC: Open in Canvas` | Opens current file in canvas browser with node highlighting | - |
| `YAPPC: Scaffold Code from Canvas` | Generate code from selected canvas node | - |
| `YAPPC: Refresh Canvas Tree` | Refresh the canvas tree view | - |
| `YAPPC: Sync to Canvas` | Sync all workspace files to canvas | - |

## Usage

### 1. View Canvas in VS Code
1. Open Command Palette
2. Run `YAPPC: Open Canvas Webview`
3. Canvas opens in side panel with full interactivity

### 2. Navigate Code from Canvas
1. Open the **YAPPC Canvas** view in Explorer sidebar
2. Browse the canvas node hierarchy
3. Click any node to open its associated file

### 3. Scaffold Code from Canvas
1. In Canvas Tree View, select a node
2. Click the scaffold icon or run `YAPPC: Scaffold Code from Canvas`
3. Choose destination path
4. Code is generated with appropriate template

### 4. Open File in Canvas
1. Open any TypeScript/JavaScript file
2. Right-click in editor
3. Select `YAPPC: Open in Canvas`
4. Canvas opens with the node highlighted

### 5. Enable Auto-Sync
1. Set `yappc.enableAutoSync` to `true`
2. Save any file in workspace
3. Changes automatically sync to canvas

## Architecture

```
vscode-extension/
├── src/
│   ├── extension.ts              # Extension entry point
│   ├── commands/
│   │   ├── scaffoldCode.ts       # Code scaffolding logic
│   │   └── openInCanvas.ts       # Navigate to canvas
│   ├── services/
│   │   └── FileSyncService.ts    # WebSocket file sync
│   ├── views/
│   │   └── CanvasTreeView.ts     # Tree view provider
│   └── providers/
│       └── webviewProvider.ts    # Canvas webview panel
├── package.json                   # Extension manifest
└── tsconfig.json                  # TypeScript config
```

## Development

### Prerequisites
- Node.js 20+
- TypeScript 5.3+
- VS Code 1.85.0+
- YAPPC dev server running on `localhost:5173`

### Build
```bash
npm install
npm run compile
```

### Watch Mode
```bash
npm run watch
```

### Testing
1. Start YAPPC dev server:
   ```bash
   cd ../../app-creator
   pnpm dev
   ```

2. Press `F5` in VS Code to launch extension host

3. Test all commands in the extension development host

### Package Extension
```bash
npm run package
```

Generates `yappc-canvas-sync-1.0.0.vsix` for distribution.

## Troubleshooting

### Canvas Won't Load
- Ensure YAPPC dev server is running on `http://localhost:5173`
- Check that port 5173 is not blocked by firewall
- Look for error messages in VS Code Output panel

### Sync Not Working
- Verify `yappc.canvasUrl` setting points to correct server
- Check WebSocket connection in browser console
- Ensure workspace has `.ts`, `.tsx`, `.js`, or `.jsx` files

### Scaffolding Fails
- Confirm you have write permissions in target directory
- Check that node type is supported (component/service/api/database)
- Review error message in VS Code notification

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

## License

MIT

## Support

- **Documentation**: [YAPPC Docs](../../docs/)
- **Issues**: [GitHub Issues](https://github.com/samujjwal/ghatana/issues)
- **Discussions**: [GitHub Discussions](https://github.com/samujjwal/ghatana/discussions)
