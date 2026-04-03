/**
 * @doc.type module
 * @doc.purpose Public API exports for Code Editor (Monaco lazy-loading) component
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
