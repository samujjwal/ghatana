/**
 * @ghatana/yappc-code-editor - Type Definitions
 *
 * Production-grade type system for code editor functionality.
 *
 * @doc.type module
 * @doc.purpose Type definitions for code editor library
 * @doc.layer shared
 * @doc.pattern Type System
 */

import type { editor } from 'monaco-editor';

/**
 * Supported programming languages
 */
export type CodeLanguage =
  | 'typescript'
  | 'javascript'
  | 'html'
  | 'css'
  | 'scss'
  | 'json'
  | 'yaml'
  | 'markdown'
  | 'python'
  | 'java'
  | 'go'
  | 'rust'
  | 'sql'
  | 'graphql'
  | 'shell'
  | 'plaintext';

/**
 * Editor theme options
 */
export type EditorTheme = 'vs' | 'vs-dark' | 'hc-black' | 'hc-light';

/**
 * Code editor configuration
 */
export interface CodeEditorConfig {
  /** Programming language */
  language: CodeLanguage;
  /** Editor theme */
  theme?: EditorTheme;
  /** Read-only mode */
  readOnly?: boolean;
  /** Show line numbers */
  lineNumbers?: 'on' | 'off' | 'relative';
  /** Show minimap */
  minimap?: boolean;
  /** Word wrap mode */
  wordWrap?: 'on' | 'off' | 'wordWrapColumn' | 'bounded';
  /** Tab size */
  tabSize?: number;
  /** Insert spaces instead of tabs */
  insertSpaces?: boolean;
  /** Font size in pixels */
  fontSize?: number;
  /** Font family */
  fontFamily?: string;
  /** Auto-format on paste */
  formatOnPaste?: boolean;
  /** Auto-format on type */
  formatOnType?: boolean;
  /** Enable code folding */
  folding?: boolean;
  /** Enable bracket pair colorization */
  bracketPairColorization?: boolean;
  /** Enable auto-closing brackets */
  autoClosingBrackets?: 'always' | 'languageDefined' | 'beforeWhitespace' | 'never';
  /** Enable auto-closing quotes */
  autoClosingQuotes?: 'always' | 'languageDefined' | 'beforeWhitespace' | 'never';
  /** Enable suggestions */
  suggestOnTriggerCharacters?: boolean;
  /** Enable quick suggestions */
  quickSuggestions?: boolean;
  /** Custom Monaco options */
  monacoOptions?: editor.IStandaloneEditorConstructionOptions;
}

/**
 * Default editor configuration
 */
export const DEFAULT_EDITOR_CONFIG: CodeEditorConfig = {
  language: 'typescript',
  theme: 'vs-dark',
  readOnly: false,
  lineNumbers: 'on',
  minimap: true,
  wordWrap: 'on',
  tabSize: 2,
  insertSpaces: true,
  fontSize: 14,
  fontFamily: "'Fira Code', 'Consolas', monospace",
  formatOnPaste: true,
  formatOnType: true,
  folding: true,
  bracketPairColorization: true,
  autoClosingBrackets: 'languageDefined',
  autoClosingQuotes: 'languageDefined',
  suggestOnTriggerCharacters: true,
  quickSuggestions: true,
};

/**
 * Code editor props
 */
export interface CodeEditorProps {
  /** Code content */
  value: string;
  /** Change handler */
  onChange?: (value: string) => void;
  /** Programming language */
  language?: CodeLanguage;
  /** Editor configuration */
  config?: Partial<CodeEditorConfig> & {
    options?: editor.IStandaloneEditorConstructionOptions;
  };
  /** Editor height */
  height?: string | number;
  /** Editor width */
  width?: string | number;
  /** Custom class name */
  className?: string;
  /** Editor mounted callback */
  onMount?: (editor: editor.IStandaloneCodeEditor) => void;
  /** Validation errors */
  markers?: editor.IMarkerData[];
  /** Path for model (enables multi-file support) */
  path?: string;
  /** Read-only mode */
  readOnly?: boolean;
  /** Execute callback (Cmd/Ctrl + Enter) */
  onExecute?: () => void;
  /** Format callback (Cmd/Ctrl + Shift + F) */
  onFormat?: () => void;
  /** Schema for SQL autocomplete (table -> columns mapping) */
  schema?: Record<string, string[]>;
}

/**
 * Code diff viewer props
 */
export interface CodeDiffViewerProps {
  /** Original code */
  original: string;
  /** Modified code */
  modified: string;
  /** Programming language */
  language?: CodeLanguage;
  /** Editor theme */
  theme?: EditorTheme;
  /** Viewer height */
  height?: string | number;
  /** Render side by side */
  renderSideBySide?: boolean;
  /** Enable inline diff */
  enableSplitViewResizing?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * Visual code editor block types
 */
export type VisualBlockType =
  | 'function'
  | 'variable'
  | 'condition'
  | 'loop'
  | 'event'
  | 'action'
  | 'comment'
  | 'import'
  | 'export'
  | 'class'
  | 'interface';

/**
 * Visual code block
 */
export interface VisualCodeBlock {
  id: string;
  type: VisualBlockType;
  label: string;
  inputs?: VisualBlockInput[];
  outputs?: VisualBlockOutput[];
  children?: VisualCodeBlock[];
  code?: string;
  position?: { x: number; y: number };
}

/**
 * Visual block input
 */
export interface VisualBlockInput {
  id: string;
  name: string;
  type: string;
  required?: boolean;
  defaultValue?: unknown;
}

/**
 * Visual block output
 */
export interface VisualBlockOutput {
  id: string;
  name: string;
  type: string;
}

/**
 * Visual code editor props
 */
export interface VisualCodeEditorProps {
  /** Visual blocks */
  blocks: VisualCodeBlock[];
  /** Block change handler */
  onBlocksChange?: (blocks: VisualCodeBlock[]) => void;
  /** Generated code preview */
  showCodePreview?: boolean;
  /** Target language for code generation */
  targetLanguage?: CodeLanguage;
  /** Editor height */
  height?: string | number;
  /** Custom class name */
  className?: string;
}

/**
 * Code generation options
 */
export interface CodeGenerationOptions {
  /** Target language */
  language: CodeLanguage;
  /** Include comments */
  includeComments?: boolean;
  /** Format output */
  format?: boolean;
  /** Include type annotations */
  includeTypes?: boolean;
  /** Module format */
  moduleFormat?: 'esm' | 'commonjs' | 'umd';
}

/**
 * Code generation result
 */
export interface CodeGenerationResult {
  /** Generated code */
  code: string;
  /** Source map (if available) */
  sourceMap?: string;
  /** Warnings during generation */
  warnings?: string[];
  /** Errors during generation */
  errors?: string[];
}

/**
 * Editor action
 */
export interface EditorAction {
  id: string;
  label: string;
  keybinding?: number;
  contextMenuGroupId?: string;
  run: (editor: editor.IStandaloneCodeEditor) => void;
}

/**
 * Editor decoration
 */
export interface EditorDecoration {
  range: {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
  };
  options: editor.IModelDecorationOptions;
}
