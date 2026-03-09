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

import { useState, useEffect, useCallback } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import { Box, Button, Typography } from '@ghatana/ui';
import { useCanvasPersistence } from '../../../utils/canvasPersistence';

interface CodeEditorState {
    code: string;
    language: string;
    fileName: string;
}

export const CodeEditorCanvas = () => {
    const [editorState, setEditorState] = useState<CodeEditorState>({
        code: '',
        language: 'typescript',
        fileName: 'untitled.ts',
    });
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
                            variant="outlined"
                            onClick={() => handleCodeChange('')}
                            className="text-white" style={{ borderColor: 'white', borderBottom: '1px solid rgba(0 }} >
                            Clear
                        </Button>
                    </Box>
                </Box>

                {/* Code Editor - Professional textarea with syntax-aware features */}
                <Box className="flex-1 bg-[#1E1E1E] text-[#D4D4D4]">
                    <textarea
                        value={editorState.code}
                        onChange={(e) => handleCodeChange(e.target.value)}
                        placeholder="// Start typing your code here..."
                        spellCheck={false}
                        style={{
                            width: '100%',
                            height: '100%',
                            fontFamily: '"Fira Code", "Cascadia Code", "JetBrains Mono", Consolas, monospace',
                            fontSize: '14px',
                            lineHeight: '1.6',
                            backgroundColor: '#1E1E1E',
                            color: '#D4D4D4',
                            border: 'none',
                            padding: '16px',
                            resize: 'none',
                            outline: 'none',
                            tabSize: 2,
                            whiteSpace: 'pre',
                            overflowWrap: 'normal',
                            overflowX: 'auto',
                        }}
                        onKeyDown={(e) => {
                            // Tab key inserts 2 spaces
                            if (e.key === 'Tab') {
                                e.preventDefault();
                                const start = e.currentTarget.selectionStart;
                                const end = e.currentTarget.selectionEnd;
                                const newValue = editorState.code.substring(0, start) + '  ' + editorState.code.substring(end);
                                handleCodeChange(newValue);
                                // Set cursor position after inserted spaces
                                setTimeout(() => {
                                    e.currentTarget.selectionStart = e.currentTarget.selectionEnd = start + 2;
                                }, 0);
                            }
                        }}
                    />
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
