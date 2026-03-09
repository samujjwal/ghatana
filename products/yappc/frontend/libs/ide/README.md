# @yappc/ide

Collaborative Polyglot IDE core package for YAPPC App-Creator.

## Overview

This package provides the foundational infrastructure for a collaborative, polyglot IDE that integrates seamlessly with the YAPPC canvas system. It supports real-time collaboration, multiple programming languages, and can be embedded as a canvas node.

## Features

- **Collaborative Editing**: Real-time collaboration using CRDT-based state synchronization
- **Polyglot Support**: Multi-language support with LSP integration
- **Canvas Integration**: Can be embedded as a node in the YAPPC canvas
- **File Management**: Complete file and folder operations with UUID-based stable identity
- **Tab System**: Multi-file tab management with dirty state tracking
- **State Management**: Jotai-based reactive state management
- **Monaco Editor**: Integrated Monaco editor for code editing
- **Presence Awareness**: Real-time user presence and cursor tracking

## Installation

```bash
pnpm add @yappc/ide
```

## Usage

### Basic Setup

```typescript
import { IDEShell } from '@yappc/ide';
import { Provider } from 'jotai';

function App() {
  return (
    <Provider>
      <IDEShell />
    </Provider>
  );
}
```

### File Operations

```typescript
import { useIDEFileOperations } from '@yappc/ide';

function MyComponent() {
  const {
    createNewFile,
    openFile,
    saveFile,
    deleteFile,
  } = useIDEFileOperations();

  const handleCreateFile = async () => {
    const file = createNewFile('/src/index.ts', '', 'typescript');
    openFile(file.id);
  };

  return <button onClick={handleCreateFile}>New File</button>;
}
```

### State Management

```typescript
import { useAtom } from 'jotai';
import { ideActiveFileAtom, ideOpenTabsAtom } from '@yappc/ide';

function EditorComponent() {
  const [activeFile] = useAtom(ideActiveFileAtom);
  const [openTabs] = useAtom(ideOpenTabsAtom);

  return (
    <div>
      <p>Active: {activeFile?.name}</p>
      <p>Open tabs: {openTabs.length}</p>
    </div>
  );
}
```

## Architecture

### Package Structure

```
libs/ide/
├── src/
│   ├── types/           # TypeScript type definitions
│   ├── state/           # Jotai atoms for state management
│   ├── hooks/           # React hooks for IDE operations
│   ├── utils/           # Utility functions
│   ├── components/      # React components
│   └── index.ts         # Main entry point
├── package.json
├── tsconfig.json
└── README.md
```

### Core Concepts

#### Stable Identity

All files and folders use UUID-based stable identity to support safe rename/move operations in collaborative environments.

#### CRDT Integration

IDE state extends the existing YAPPC canvas CRDT schema for seamless collaboration.

#### Canvas Embedding

The IDE can be embedded as a canvas node, allowing visual and code editing in the same workspace.

## API Reference

### Types

- `IDEFile`: File metadata with content and language
- `IDEFolder`: Folder structure with children
- `IDETab`: Editor tab representation
- `IDEState`: Complete IDE state for CRDT sync
- `IDEPresence`: User presence information

### Atoms

- `ideStateAtom`: Root IDE state
- `ideFilesAtom`: All files in workspace
- `ideFoldersAtom`: All folders in workspace
- `ideActiveFileAtom`: Currently active file
- `ideOpenTabsAtom`: Open editor tabs
- `ideSettingsAtom`: IDE settings (persisted)

### Hooks

- `useIDEFileOperations()`: File and folder CRUD operations

### Components

- `IDEShell`: Main IDE container
- `FileExplorer`: File tree navigation
- `TabBar`: Editor tab management
- `EditorPanel`: Code editor panel
- `StatusBar`: IDE status information

## Development

### Build

```bash
pnpm build
```

### Test

```bash
pnpm test
```

### Lint

```bash
pnpm lint
```

## Integration with YAPPC

This package integrates with:

- `@yappc/canvas`: Canvas system for visual editing
- `@yappc/code-editor`: Monaco editor integration
- `@yappc/crdt-core`: CRDT collaboration infrastructure
- `@yappc/state`: State management patterns
- `@yappc/ui`: UI component library

## Roadmap

### Phase 0 (Current)

- ✅ Package structure
- ✅ Type definitions
- ✅ State management
- ✅ File operations
- 🔄 React components
- ⏳ Canvas integration

### Phase 1

- CRDT synchronization
- Real-time collaboration
- Presence tracking

### Phase 2

- IDE Shell UX
- File tree operations
- Collaborative editing

### Phase 3

- Monaco editor integration
- LSP client foundation
- Performance optimization

### Phase 4

- UI Builder synchronization
- Bidirectional code generation
- Drag-and-drop components

## Contributing

Follow YAPPC coding standards:

- Use Jotai for app state
- Add `@doc.*` tags on all public functions
- No `any` types
- Tailwind CSS for styling
- > 90% test coverage

## License

MIT

## Version

0.1.0
