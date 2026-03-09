/**
 * Simple Text Node
 * Basic text element for canvas with inline editing
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface SimpleTextData {
    text: string;
    fontSize?: number;
    color?: string;
}

export const SimpleTextNode = React.memo(({ data, selected, id }: NodeProps<SimpleTextData>) => {
    const { text = 'Text', fontSize = 16, color = '#333' } = data as SimpleTextData;
    const [isEditing, setIsEditing] = useState(false);
    const [editText, setEditText] = useState(text);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        setEditText(text);
    }, [text]);

    useEffect(() => {
        if (isEditing && inputRef.current) {
            inputRef.current.focus();
            inputRef.current.select();
        }
    }, [isEditing]);

    const handleDoubleClick = () => {
        setIsEditing(true);
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleSave();
        } else if (e.key === 'Escape') {
            setIsEditing(false);
            setEditText(text);
        }
    };

    const handleSave = () => {
        // Update the node data through ReactFlow
        const nodeElement = document.querySelector(`[data-node-id="${id}"]`);
        if (nodeElement) {
            const event = new CustomEvent('nodeTextChange', {
                detail: { nodeId: id, text: editText }
            });
            nodeElement.dispatchEvent(event);
        }
        setIsEditing(false);
    };

    const handleBlur = () => {
        handleSave();
    };

    return (
        <Box
            data-node-id={id}
            className="bg-transparent rounded-lg p-2 min-w-[100px] min-h-[40px]" style={{ border: selected ? '2px solid #2196f3' : 'none', cursor: isEditing ? 'text' : 'move' }}
            onDoubleClick={handleDoubleClick}
        >
            <Handle type="target" position={Position.Top} />
            {isEditing ? (
                <TextField
                    inputRef={inputRef}
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    onBlur={handleBlur}
                    variant="standard"
                    inputProps={{
                        style: {
                            fontSize: `${fontSize}px`,
                            color: color,
                            backgroundColor: 'transparent',
                            border: 'none',
                            padding: 0,
                        },
                    }}
                />
            ) : (
                <Typography
                    variant="body1"
                    style={{ fontSize: `${fontSize}px`, color }}
                >
                    {text}
                </Typography>
            )}
            <Handle type="source" position={Position.Bottom} />
        </Box>
    );
});

SimpleTextNode.displayName = 'SimpleTextNode';
