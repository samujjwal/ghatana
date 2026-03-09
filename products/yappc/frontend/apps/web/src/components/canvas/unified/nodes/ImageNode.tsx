/**
 * ImageNode - Image upload and display
 * 
 * Supports:
 * - Drag-and-drop upload
 * - URL input
 * - Clipboard paste
 * - Image cropping and filters
 * 
 * @doc.type component
 * @doc.purpose Image display for visual content
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useState, useCallback, useRef } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import {
  Box,
  Typography,
  IconButton,
  Button,
  Spinner as CircularProgress,
} from '@ghatana/ui';

export interface ImageNodeData {
    src?: string;
    alt?: string;
    caption?: string;
    // Display options
    objectFit?: 'contain' | 'cover' | 'fill' | 'none';
    borderRadius?: number;
    opacity?: number;
    // Filters
    filter?: string;  // CSS filter string
    // State
    loading?: boolean;
    error?: string;
}

interface ImageNodeProps extends NodeProps {
    data: ImageNodeData;
}

function ImageNodeComponent({ data, selected, id }: ImageNodeProps) {
    const {
        src = '',
        alt = 'Image',
        caption,
        objectFit = 'contain',
        borderRadius = 8,
        opacity = 1,
        filter,
        loading: initialLoading = false,
        error: initialError
    } = data;

    const [isDragging, setIsDragging] = useState(false);
    const [isLoading, setIsLoading] = useState(initialLoading);
    const [error, setError] = useState<string | undefined>(initialError);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleDragOver = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(true);
    }, []);

    const handleDragLeave = useCallback(() => {
        setIsDragging(false);
    }, []);

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setIsDragging(false);

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            const file = files[0];
            if (file.type.startsWith('image/')) {
                handleFile(file);
            }
        }
    }, []);

    const handleFile = useCallback((file: File) => {
        setIsLoading(true);
        setError(undefined);

        const reader = new FileReader();
        reader.onload = (e) => {
            // Would dispatch update action with e.target?.result
            setIsLoading(false);
        };
        reader.onerror = () => {
            setError('Failed to read file');
            setIsLoading(false);
        };
        reader.readAsDataURL(file);
    }, []);

    const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            handleFile(file);
        }
    }, [handleFile]);

    const handleClick = useCallback(() => {
        if (!src) {
            fileInputRef.current?.click();
        }
    }, [src]);

    return (
        <>
            <NodeResizer
                minWidth={100}
                minHeight={100}
                isVisible={selected}
                lineStyle={{ borderColor: '#1976d2', borderWidth: 2 }}
                handleStyle={{
                    backgroundColor: '#1976d2',
                    width: 10,
                    height: 10,
                    borderRadius: 2
                }}
            />

            <Box
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={handleClick}
                className="w-full h-full min-w-[150px] min-h-[150px] flex flex-col" style={{ borderRadius: `${borderRadius, backgroundColor: 'error.lighter' }}
            >
                {/* Hidden file input */}
                <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileSelect}
                    style={{ display: 'none' }}
                />

                {/* Content */}
                <Box className="flex-1 relative overflow-hidden">
                    {/* Loading state */}
                    {isLoading && (
                        <Box
                            className="absolute flex items-center justify-cenmigrate remaining sx: inset: 0 */
                        >
                            <CircularProgress size={32} />
                        </Box>
                    )}

                    {/* Error state */}
                    {error && (
                        <Box
                            className="absolute flex flex-col items-center justify-center gap-2 p-4 inset-0" >
                            <Typography color="error">⚠️ {error}</Typography>
                            <Button size="small" onClick={() => fileInputRef.current?.click()}>
                                Try Again
                            </Button>
                        </Box>
                    )}

                    {/* Empty state (drop zone) */}
                    {!src && !isLoading && !error && (
                        <Box
                            className={`absolute flex flex-col items-center justify-center gap-2 p-4 inset-0 ${isDragging ? 'bg-blue-100 text-blue-600' : 'bg-gray-50 text-gray-500'}`}>
                            <Box className="text-[2.5rem]">🖼️</Box>
                            <Typography variant="body2" textAlign="center">
                                {isDragging ? 'Drop image here' : 'Click or drag image'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                                PNG, JPG, GIF, SVG
                            </Typography>
                        </Box>
                    )}

                    {/* Image */}
                    {src && !isLoading && !error && (
                        <img
                            src={src}
                            alt={alt}
                            style={{ width: '100%',
                                height: '100%',
                                objectFit,
                                opacity,
                                filter: filter || 'none', backgroundColor: 'rgba(0' }}
                            onError={() => setError('Failed to load image')}
                        />
                    )}
                </Box>

                {/* Caption */}
                {caption && src && (
                    <Box
                        className="px-3 py-2 border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 border-t"
                    >
                        <Typography variant="caption" color="text.secondary">
                            {caption}
                        </Typography>
                    </Box>
                )}

                {/* Toolbar (visible on hover/selection) */}
                {src && selected && (
                    <Box
                        className="absolute flex gap-1 rounded p-1 top-[8px] right-[8px]" >
                        <IconButton
                            size="small"
                            className="text-white"
                            onClick={(e) => {
                                e.stopPropagation();
                                fileInputRef.current?.click();
                            }}
                            title="Replace image"
                        >
                            🔄
                        </IconButton>
                        <IconButton
                            size="small"
                            className="text-white"
                            onClick={(e) => {
                                e.stopPropagation();
                                window.open(src, '_blank');
                            }}
                            title="Open full size"
                        >
                            ↗️
                        </IconButton>
                    </Box>
                )}
            </Box>

            {/* Connection handles */}
            <Handle
                type="source"
                position={Position.Right}
                style={{
                    opacity: selected ? 1 : 0,
                    background: '#1976d2',
                    width: 8,
                    height: 8,
                    border: '2px solid white'
                }}
            />
            <Handle
                type="target"
                position={Position.Left}
                style={{
                    opacity: selected ? 1 : 0,
                    background: '#1976d2',
                    width: 8,
                    height: 8,
                    border: '2px solid white'
                }}
            />
        </>
    );
}

export const ImageNode = memo(ImageNodeComponent);
export default ImageNode;
