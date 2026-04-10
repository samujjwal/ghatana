/**
 * @doc.type module
 * @doc.purpose Public API exports for Code Editor (Monaco, AST, LSP, collaborative)
 * @doc.layer platform
 */

export type { LazyMonacoEditorProps } from './LazyMonacoEditor';
export {
  LazyMonacoEditor,
  preloadMonacoEditor,
  useMonacoLoader,
  MonacoBundleInfo,
} from './LazyMonacoEditor';

// Default export for convenience
export { default } from './LazyMonacoEditor';

// Types
export type {
  CodeLanguage,
  EditorTheme,
  CodeEditorConfig,
  CodeEditorProps,
  CodeDiffViewerProps,
  VisualBlockType,
  VisualCodeBlock,
  VisualBlockInput,
  VisualBlockOutput,
  VisualCodeEditorProps,
  CodeGenerationOptions,
  CodeGenerationResult,
  EditorAction,
  EditorDecoration,
} from './types';
export { DEFAULT_EDITOR_CONFIG } from './types';

// Components
export * from './components';

// Managers
export * from './managers';

// Bindings
export * from './bindings';

// Hooks
export * from './hooks';

// AST
export * from './ast';

// LSP
export * from './lsp';

// Debugging
export * from './debugging';

// Refactoring
export * from './refactoring';
