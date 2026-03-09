/**
 * Code Node - Syntax-highlighted code content
 * 
 * Renders editable code content with monospace font
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface CodeNodeData {
    code: string;
    language?: string;
    fontSize?: number;
    onCodeChange?: (code: string) => void;
}

/**
 * Code Node Component
 */
export const CodeNode = React.memo(({ data, selected, id }: NodeProps<CodeNodeData>) => {
    const {
        code = '// Your code here',
        language = 'javascript',
        fontSize = 12,
        onCodeChange
    } = data;

    const [isEditing, setIsEditing] = useState(false);
    const [localCode, setLocalCode] = useState(code);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Auto-focus when editing
    useEffect(() => {
        if (isEditing && textareaRef.current) {
            textareaRef.current.focus();
            textareaRef.current.select();
        }
    }, [isEditing]);

    // Handle double-click to edit
    const handleDoubleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        setIsEditing(true);
    };

    // Handle blur - save changes
    const handleBlur = () => {
        setIsEditing(false);
        if (localCode !== code && onCodeChange) {
            onCodeChange(localCode);
        }
    };

    // Handle code change
    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        setLocalCode(e.target.value);
    };

    // Handle key press
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Escape') {
            setLocalCode(code);
            setIsEditing(false);
        } else if (e.key === 'Enter' && e.metaKey) {
            handleBlur();
        }
    };

    return (
        <Box
            onDoubleClick={handleDoubleClick}
            className="w-full h-full bg-[#1e1e1e] border-[2px] rounded flex flex-col overflow-hidden relative pointer-events-auto p-2" style={{ borderColor: selected ? 'primary.main' : '#333', boxShadow: selected ? 3 : 1, cursor: isEditing ? 'text' : 'move' }}
        >
            <Box
                className="flex items-center pb-1 border-[#333] border-b" >
                <Typography
                    className="text-[11px] font-semibold text-[#666] font-mono"
                >
                    {language}
                </Typography>
            </Box>

            {isEditing ? (
                <textarea
                    ref={textareaRef}
                    value={localCode}
                    onChange={handleChange}
                    onBlur={handleBlur}
                    onKeyDown={handleKeyDown}
                    style={{
                        flex: 1,
                        border: 'none',
                        outline: 'none',
                        background: 'transparent',
                        resize: 'none',
                        fontFamily: 'monospace',
                        fontSize: `${fontSize}px`,
                        color: '#d4d4d4',
                        lineHeight: 1.6,
                        padding: '8px',
                        paddingTop: '8px'
                    }}
                    spellCheck="false"
                />
            ) : (
                <Box
                    className="flex-1 overflow-auto p-2 font-mono" style={{ fontSize: `${fontSize }}
                >
                    {localCode || '// Your code here'}
                </Box>
            )}

            {/* Connection Handles */}
            <Handle
                type="source"
                position={Position.Bottom}
                style={{
                    width: 8,
                    height: 8,
                    background: '#f57c00',
                    border: 'none',
                    opacity: selected ? 1 : 0
                }}
            />
            <Handle
                type="target"
                position={Position.Top}
                style={{
                    width: 8,
                    height: 8,
                    background: '#f57c00',
                    border: 'none',
                    opacity: selected ? 1 : 0
                }}
            />
        </Box>
    );
});

CodeNode.displayName = 'CodeNode';
