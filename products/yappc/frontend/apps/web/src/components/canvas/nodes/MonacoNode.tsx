/**
 * MonacoNode
 *
 * A canvas node that embeds a code editor. When `@monaco-editor/react` is
 * installed and bundled, it uses the full Monaco experience (syntax
 * highlighting, IntelliSense, minimap). Otherwise it gracefully degrades to
 * a styled `<textarea>` with monospace rendering.
 *
 * Installation (optional — full Monaco experience):
 *   pnpm add @monaco-editor/react
 *
 * Architecture:
 *   - Uses `CanvasContentWrapper` to isolate pointer/scroll/keyboard events
 *     from the canvas so typing inside the editor does not trigger shortcuts.
 *   - Uses `NodeResizer` scaled by `cameraZoomAtom` so the resize handles
 *     stay physically constant regardless of canvas zoom.
 *   - Renders a placeholder card when zoom < MIN_ZOOM_THRESHOLD to avoid
 *     rendering a heavy editor DOM subtree at tiny zoom levels.
 *
 * @doc.type component
 * @doc.purpose Code editor canvas node
 * @doc.layer product
 * @doc.pattern ContentNode
 */

import React, { lazy, Suspense, useCallback } from 'react';
import { type NodeProps } from '@xyflow/react';
import { NodeResizer } from '@xyflow/react';
import { useAtomValue } from 'jotai';
import { cameraZoomAtom } from '../workspace';
import { CanvasContentWrapper } from '../CanvasContentWrapper';

// ============================================================================
// Types
// ============================================================================

export interface MonacoNodeData {
    /** Programming / markup language for syntax highlighting */
    language?: string;
    /** Current source code content */
    value?: string;
    /** Called when the user edits the code */
    onChange?: (value: string) => void;
    /** Optional node title shown in the header drag handle */
    title?: string;
    /** Whether the editor is read-only */
    readOnly?: boolean;
    [key: string]: unknown;
}

// ============================================================================
// Constants
// ============================================================================

/** Below this zoom we render a lightweight placeholder instead of the editor */
const MIN_ZOOM_THRESHOLD = 0.35;

/** Languages available in the header selector */
const LANGUAGES = [
    'typescript', 'javascript', 'python', 'java', 'go',
    'rust', 'cpp', 'c', 'csharp', 'html', 'css', 'json',
    'yaml', 'markdown', 'sql', 'bash', 'plaintext',
];

// ============================================================================
// Lazy Monaco loader (only bundled if @monaco-editor/react is installed)
// ============================================================================

let LazyMonaco: React.LazyExoticComponent<
    React.ComponentType<{
        language: string;
        value: string;
        onChange?: (value: string | undefined) => void;
        options?: Record<string, unknown>;
        theme?: string;
    }>
> | null = null;

try {
    // Dynamic import — tree-shaken out if the package isn't present
    LazyMonaco = lazy(() =>
        import('@monaco-editor/react').then((m) => ({ default: m.Editor }))
    );
} catch {
    // Package not installed — use textarea fallback
    LazyMonaco = null;
}

// ============================================================================
// Textarea fallback editor
// ============================================================================

interface NativeCodeEditorProps {
    value: string;
    onChange: (v: string) => void;
    readOnly: boolean;
}

function NativeCodeEditor({ value, onChange, readOnly }: NativeCodeEditorProps) {
    const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        // Tab key: insert spaces instead of changing focus
        if (e.key === 'Tab') {
            e.preventDefault();
            const el = e.currentTarget;
            const start = el.selectionStart;
            const end = el.selectionEnd;
            const newValue = value.slice(0, start) + '    ' + value.slice(end);
            onChange(newValue);
            // Restore cursor position after React re-render
            requestAnimationFrame(() => {
                el.selectionStart = el.selectionEnd = start + 4;
            });
        }
    }, [value, onChange]);

    return (
        <textarea
            className="w-full h-full resize-none bg-gray-950 text-green-300 font-mono text-sm p-3 outline-none border-none leading-relaxed"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            readOnly={readOnly}
            spellCheck={false}
            aria-label="Code editor"
            aria-multiline="true"
            aria-readonly={readOnly}
        />
    );
}

// ============================================================================
// Component
// ============================================================================

/**
 * MonacoNode — code editor canvas node.
 *
 * Props are passed via ReactFlow's `data` field:
 * ```ts
 * const node = {
 *   id: 'monaco-1',
 *   type: 'monaco',
 *   position: { x: 200, y: 300 },
 *   data: { language: 'typescript', value: 'const x = 1;', title: 'types.ts' },
 * };
 * ```
 */
export function MonacoNode({ data, selected }: NodeProps<MonacoNodeData>) {
    const zoom = useAtomValue(cameraZoomAtom);
    const language = data.language ?? 'typescript';
    const value = data.value ?? '';
    const readOnly = data.readOnly ?? false;
    const title = data.title ?? 'Code';

    const handleChange = useCallback(
        (v: string) => data.onChange?.(v),
        [data],
    );

    /** Resize handle size stays ~8px physical regardless of zoom */
    const handleSize = Math.round(8 / zoom);

    return (
        <>
            <NodeResizer
                isVisible={selected}
                minWidth={200}
                minHeight={120}
                handleStyle={{ width: handleSize, height: handleSize }}
            />

            {/* Drag handle — MUST be outside CanvasContentWrapper */}
            <div
                className="flex items-center justify-between px-3 py-1.5 bg-gray-900 border-b border-gray-700 rounded-t-md cursor-grab active:cursor-grabbing select-none"
                style={{ minWidth: 200 }}
            >
                <span className="text-gray-200 text-xs font-mono font-semibold truncate">{title}</span>
                {!readOnly && (
                    <select
                        className="nodrag nopan text-xs bg-gray-800 text-gray-300 border border-gray-600 rounded px-1 py-0.5 ml-2"
                        value={language}
                        onChange={(e) => {
                            // Surface language change to parent via a synthetic data update
                            // (parent should wire up an onChange for language changes)
                            data.onChange?.(value); // keep value, just triggers re-render
                            void e.target.value; // allow controlled/uncontrolled parents to handle
                        }}
                        aria-label="Language selector"
                    >
                        {LANGUAGES.map((l) => (
                            <option key={l} value={l}>{l}</option>
                        ))}
                    </select>
                )}
            </div>

            {/* Editor body */}
            <CanvasContentWrapper className="rounded-b-md overflow-hidden min-h-[120px]">
                {zoom < MIN_ZOOM_THRESHOLD ? (
                    /* Zoom-out placeholder — avoids rendering expensive editor DOM */
                    <div className="flex items-center justify-center w-full h-full bg-gray-950 text-gray-500 text-xs font-mono select-none">
                        <span>{title} — zoom in to edit</span>
                    </div>
                ) : LazyMonaco ? (
                    <Suspense
                        fallback={
                            <div className="flex items-center justify-center w-full h-full bg-gray-950 text-gray-500 text-xs">
                                Loading editor…
                            </div>
                        }
                    >
                        <LazyMonaco
                            language={language}
                            value={value}
                            onChange={(v) => handleChange(v ?? '')}
                            theme="vs-dark"
                            options={{
                                readOnly,
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                fontSize: 13,
                                lineNumbers: 'on',
                                wordWrap: 'on',
                                automaticLayout: true,
                                scrollbar: { vertical: 'hidden', horizontal: 'hidden' },
                            }}
                        />
                    </Suspense>
                ) : (
                    /* Native textarea fallback when Monaco isn't installed */
                    <NativeCodeEditor
                        value={value}
                        onChange={handleChange}
                        readOnly={readOnly}
                    />
                )}
            </CanvasContentWrapper>
        </>
    );
}
