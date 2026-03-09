/**
 * @ghatana/yappc-code-editor - Production-Grade Code Editor Library
 *
 * A comprehensive code editor library with Monaco integration for
 * syntax highlighting, IntelliSense, and code generation.
 *
 * Features:
 * - 🎨 Monaco Editor integration
 * - 📝 Multi-language support (TypeScript, JavaScript, HTML, CSS, etc.)
 * - 🔍 IntelliSense and auto-completion
 * - 📊 Code diff viewer
 * - 🧱 Visual block-based editor
 * - 🔄 Code generation from visual blocks
 * - 🎯 Custom themes and configurations
 * - ♿ Full accessibility support
 *
 * @doc.type module
 * @doc.purpose Code editor library
 * @doc.layer product
 * @doc.pattern Library
 *
 * @example
 * ```tsx
 * import { CodeEditor, CodeDiffViewer } from '@ghatana/yappc-code-editor';
 *
 * function MyEditor() {
 *   const [code, setCode] = useState('');
 *   return (
 *     <CodeEditor
 *       value={code}
 *       onChange={setCode}
 *       config={{ language: 'typescript', theme: 'vs-dark' }}
 *     />
 *   );
 * }
 * ```
 */

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

// Version
export const CODE_EDITOR_VERSION = '1.0.0';

// Components
export { CodeEditor } from './components/CodeEditor';
export { EnhancedCodeEditor } from './components/EnhancedCodeEditor';
export type { EnhancedCodeEditorProps, CollaborativeCursor, EditorInstance, EditorMetrics } from './components/EnhancedCodeEditor';
export { MultiFileCodeEditor } from './components/MultiFileCodeEditor';
export type { FileTab, FileEditorState, MultiFileEditorConfig } from './components/MultiFileCodeEditor';

// Managers
export { EditorLifecycleManager, getEditorLifecycleManager, disposeEditorManager } from './managers/EditorLifecycleManager';
export type { EditorPoolConfig, EditorFactory } from './managers/EditorLifecycleManager';
export { 
  CollaborativeEditingManager, 
  createCollaborativeEditingManager 
} from './managers/CollaborativeEditingManager';
export type { 
  CollaborativeEditingConfig, 
  UserPresence, 
  EditConflict, 
  CollaborativeEditingEvents 
} from './managers/CollaborativeEditingManager';

// Bindings
export { YjsMonacoBinding, createYjsMonacoBinding } from './bindings/YjsMonacoBinding';
export type { YjsMonacoBindingConfig, TextChangeEvent, BindingMetrics } from './bindings/YjsMonacoBinding';

// Hooks
export { 
  useCollaborativeEditor, 
  useEnhancedCollaborativeEditor 
} from './hooks/useCollaborativeEditor';
export type { 
  UseCollaborativeEditorConfig, 
  CollaborativeEditorState, 
  CollaborativeEditorActions, 
  UseCollaborativeEditorReturn 
} from './hooks/useCollaborativeEditor';
