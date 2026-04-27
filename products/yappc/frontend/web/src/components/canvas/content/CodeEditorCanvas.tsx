/**
 * Code Editor Canvas Content
 * 
 * Monaco-based code editor for Code × Code level.
 * Implements the base canvas content pattern with persistence and history.
 * 
 * @doc.type component
 * @doc.purpose Code editor canvas implementation
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import { Box, Button, Typography } from '@ghatana/design-system';
import { useCanvasPersistence } from '../../../utils/canvasPersistence';
import {
    CodeDiffViewer,
    CodeEditor,
    VisualBlockEditor,
    type VisualCodeBlock,
} from '@ghatana/code-editor';

interface CodeEditorState {
    code: string;
    language: string;
    fileName: string;
}

type EditorViewMode = 'editor' | 'diff' | 'visual';

const INITIAL_VISUAL_BLOCKS: VisualCodeBlock[] = [
    {
        id: 'visual-imports',
        type: 'import',
        label: 'Import React utilities',
        code: "import { useMemo } from 'react';",
    },
    {
        id: 'visual-function',
        type: 'function',
        label: 'Generate component output',
        code: 'export function renderFeatureFlag() {\n  return true;\n}',
    },
];

export const CodeEditorCanvas = () => {
    const [editorState, setEditorState] = useState<CodeEditorState>({
        code: '',
        language: 'typescript',
        fileName: 'untitled.ts',
    });
    const [viewMode, setViewMode] = useState<EditorViewMode>('editor');
    const [visualBlocks, setVisualBlocks] = useState<VisualCodeBlock[]>(
        INITIAL_VISUAL_BLOCKS
    );
    const [isLoading] = useState(false);

    const { save, load } = useCanvasPersistence<CodeEditorState>(
        'code',
        'code'
    );

    // Load persisted state on mount
    useEffect(() => {
        const savedState = load();
        if (savedState) {
            setEditorState(savedState);
        }
    }, [load]);

    // Auto-save on changes
    useEffect(() => {
        if (editorState.code) {
            const timeout = setTimeout(() => {
                save(editorState);
            }, 1000); // Debounce 1 second

            return () => clearTimeout(timeout);
        }
    }, [editorState, save]);

    const handleCodeChange = useCallback((newCode: string) => {
        setEditorState(prev => ({ ...prev, code: newCode }));
    }, []);

    const hasContent = Boolean(editorState.code);

    const generatedPreviewCode = useMemo(() => {
        const lines: string[] = ['// Generated from visual blocks', ''];
        visualBlocks.forEach((block) => {
            if (block.code && block.code.trim().length > 0) {
                lines.push(block.code);
                return;
            }
            lines.push(`// ${block.type}: ${block.label}`);
        });
        return lines.join('\n');
    }, [visualBlocks]);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            isLoading={isLoading}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Start Coding',
                    onClick: () => {
                        setEditorState(prev => ({
                            ...prev,
                            code: '// Start typing...\n'
                        }));
                    },
                },
            }}
        >
            <Box className="h-full w-full flex flex-col">
                {/* Editor Header */}
                <Box className="flex justify-between items-center p-4 bg-[#1E1E1E] text-white" >
                    <Typography variant="body2" className="font-mono">
                        {editorState.fileName}
                    </Typography>
                    <Box className="flex gap-2">
                        <Button
                            size="small"
                            variant={viewMode === 'editor' ? 'contained' : 'outlined'}
                            onClick={() => setViewMode('editor')}
                            className="text-white"
                            style={{ borderColor: 'white' }}
                            data-testid="code-editor-mode-editor"
                        >
                            Editor
                        </Button>
                        <Button
                            size="small"
                            variant={viewMode === 'diff' ? 'contained' : 'outlined'}
                            onClick={() => setViewMode('diff')}
                            className="text-white"
                            style={{ borderColor: 'white' }}
                            data-testid="code-editor-mode-diff"
                        >
                            Diff
                        </Button>
                        <Button
                            size="small"
                            variant={viewMode === 'visual' ? 'contained' : 'outlined'}
                            onClick={() => setViewMode('visual')}
                            className="text-white"
                            style={{ borderColor: 'white' }}
                            data-testid="code-editor-mode-visual"
                        >
                            Visual
                        </Button>
                        <Button
                            size="small"
                            variant="outlined"
                            onClick={() => handleCodeChange('')}
                            className="text-white"
                            style={{ borderColor: 'white' }}
                            data-testid="code-editor-clear"
                        >
                            Clear
                        </Button>
                    </Box>
                </Box>

                <Box className="flex-1 bg-[#1E1E1E] text-[#D4D4D4]" data-testid="code-editor-canvas-content">
                    {viewMode === 'editor' ? (
                        <CodeEditor
                            value={editorState.code}
                            onChange={handleCodeChange}
                            config={{
                                language: 'typescript',
                                theme: 'vs-dark',
                                options: {
                                    minimap: { enabled: false },
                                    wordWrap: 'on',
                                    automaticLayout: true,
                                },
                            }}
                            height="100%"
                        />
                    ) : null}

                    {viewMode === 'diff' ? (
                        <CodeDiffViewer
                            original={generatedPreviewCode}
                            modified={editorState.code}
                            language="typescript"
                            height="100%"
                        />
                    ) : null}

                    {viewMode === 'visual' ? (
                        <VisualBlockEditor
                            blocks={visualBlocks}
                            onBlocksChange={setVisualBlocks}
                            targetLanguage="typescript"
                            height="100%"
                            showCodePreview
                        />
                    ) : null}
                </Box>

                {/* Status Bar */}
                <Box className="flex justify-between text-xs p-2 bg-[#007ACC] text-white">
                    <Typography variant="caption">
                        {editorState.language.toUpperCase()}
                    </Typography>
                    <Typography variant="caption">
                        Lines: {editorState.code.split('\n').length} |
                        Chars: {editorState.code.length}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default CodeEditorCanvas;
