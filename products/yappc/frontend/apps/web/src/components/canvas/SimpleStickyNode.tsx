/**
 * Simple Sticky Note Node
 * Basic sticky note for canvas with inline editing
 */

import React, { useState, useRef, useEffect } from 'react';
import { Box, Typography } from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Handle, Position, type NodeProps } from '@xyflow/react';

export interface SimpleStickyData {
    text: string;
    color?: string;
}

export const SimpleStickyNode = React.memo(({ data, selected, id }: NodeProps<SimpleStickyData>) => {
    const { text = 'Sticky Note', color = '#fff9c4' } = data as SimpleStickyData;
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
            className="rounded-lg p-4 min-w-[150px] min-h-[100px]" style={{ backgroundColor: color, border: selected ? '2px solid #2196f3' : '1px solid #fbc02d', boxShadow: selected ? '0 4px 8px rgba(0,0,0,0.2)' : '0 2px 4px rgba(0,0,0,0.1)', cursor: isEditing ? 'text' : 'move' }}
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
                    multiline
                    rows={3}
                    variant="standard"
                    inputProps={{
                        style: {
                            fontSize: '14px',
                            color: '#333',
                            backgroundColor: 'transparent',
                            border: 'none',
                            padding: 8,
                            minHeight: '60px',
                        },
                    }}
                />
            ) : (
                <Typography
                    variant="body2"
                    className="text-sm whitespace-pre-wrap break-words text-[#333] min-h-[60px] p-2"
                >
                    {text}
                </Typography>
            )}
            <Handle type="source" position={Position.Bottom} />
        </Box>
    );
});

SimpleStickyNode.displayName = 'SimpleStickyNode';
