/**
 * EmbedNode - Embedded content block (web pages, iframes, videos)
 * 
 * Supports embedding:
 * - Web pages (iframe)
 * - YouTube videos
 * - Figma designs
 * - Google Docs/Sheets
 * - Custom URLs
 * 
 * @doc.type component
 * @doc.purpose Embedded content for external resources
 * @doc.layer canvas/nodes
 * @doc.pattern ReactFlowNode
 */

import React, { memo, useState, useCallback, useMemo } from 'react';
import { Handle, Position, type NodeProps, NodeResizer } from '@xyflow/react';
import {
  Box,
  Typography,
  IconButton,
  Button,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

export type EmbedType = 'url' | 'youtube' | 'figma' | 'google-docs' | 'google-sheets' | 'image' | 'video';

export interface EmbedNodeData {
    url?: string;
    embedType?: EmbedType;
    title?: string;
    description?: string;
    // Display options
    showControls?: boolean;
    aspectRatio?: '16:9' | '4:3' | '1:1' | 'auto';
    // State
    loading?: boolean;
    error?: string;
    // Preview (for image)
    previewUrl?: string;
}

interface EmbedNodeProps extends NodeProps {
    data: EmbedNodeData;
}

// Embed type detection from URL
function detectEmbedType(url: string): EmbedType {
    if (!url) return 'url';

    const urlLower = url.toLowerCase();

    if (urlLower.includes('youtube.com') || urlLower.includes('youtu.be')) {
        return 'youtube';
    }
    if (urlLower.includes('figma.com')) {
        return 'figma';
    }
    if (urlLower.includes('docs.google.com')) {
        return 'google-docs';
    }
    if (urlLower.includes('sheets.google.com')) {
        return 'google-sheets';
    }
    if (/\.(jpg|jpeg|png|gif|webp|svg)(\?|$)/i.test(url)) {
        return 'image';
    }
    if (/\.(mp4|webm|ogg)(\?|$)/i.test(url)) {
        return 'video';
    }

    return 'url';
}

// Convert URL to embeddable format
function getEmbedUrl(url: string, type: EmbedType): string {
    if (!url) return '';

    switch (type) {
        case 'youtube': {
            // Convert youtube.com/watch?v=ID to youtube.com/embed/ID
            const match = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([^&\s]+)/);
            if (match) {
                return `https://www.youtube.com/embed/${match[1]}`;
            }
            return url;
        }
        case 'figma': {
            // Figma embed URL
            const match = url.match(/figma\.com\/(file|proto)\/([^/]+)/);
            if (match) {
                return `https://www.figma.com/embed?embed_host=ghatana&url=${encodeURIComponent(url)}`;
            }
            return url;
        }
        case 'google-docs':
        case 'google-sheets': {
            // Add /embed if not present
            if (!url.includes('/embed')) {
                return url.replace('/edit', '/preview').replace('/view', '/preview');
            }
            return url;
        }
        default:
            return url;
    }
}

// Get icon for embed type
function getEmbedIcon(type: EmbedType): string {
    switch (type) {
        case 'youtube': return '▶️';
        case 'figma': return '🎨';
        case 'google-docs': return '📄';
        case 'google-sheets': return '📊';
        case 'image': return '🖼️';
        case 'video': return '🎬';
        default: return '🌐';
    }
}

function EmbedNodeComponent({ data, selected, id }: EmbedNodeProps) {
    const {
        url = '',
        embedType: providedType,
        title,
        description,
        showControls = true,
        aspectRatio = '16:9',
        loading = false,
        error,
        previewUrl
    } = data;

    const [inputUrl, setInputUrl] = useState(url);
    const [isEditing, setIsEditing] = useState(!url);
    const [isLoading, setIsLoading] = useState(loading);

    const embedType = providedType || detectEmbedType(url);
    const embedUrl = useMemo(() => getEmbedUrl(url, embedType), [url, embedType]);
    const embedIcon = getEmbedIcon(embedType);

    // Calculate aspect ratio padding
    const aspectPadding = useMemo(() => {
        switch (aspectRatio) {
            case '16:9': return '56.25%';
            case '4:3': return '75%';
            case '1:1': return '100%';
            default: return '56.25%';
        }
    }, [aspectRatio]);

    const handleUrlSubmit = useCallback(() => {
        if (inputUrl.trim()) {
            setIsEditing(false);
            setIsLoading(true);
            // Would dispatch update action here
            setTimeout(() => setIsLoading(false), 1000);
        }
    }, [inputUrl]);

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            handleUrlSubmit();
        } else if (e.key === 'Escape') {
            setInputUrl(url);
            if (url) setIsEditing(false);
        }
    }, [url, handleUrlSubmit]);

    return (
        <>
            <NodeResizer
                minWidth={200}
                minHeight={150}
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
                className="w-full h-full min-w-[300px] min-h-[200px] flex flex-col rounded-lg border-[2px] bg-white dark:bg-gray-900 overflow-hidden" style={{ borderColor: selected ? 'primary.main' : 'divider', boxShadow: selected ? '0 0 0 2px rgba(25, 118, 210, 0.2)' : 1 }}
            >
                {/* Header */}
                {showControls && (
                    <Box
                        className="flex items-center gap-2 px-3 py-2 border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 border-b" >
                        <Box className="text-xl">{embedIcon}</Box>
                        <Typography variant="subtitle2" className="flex-1" noWrap>
                            {title || (url ? new URL(url).hostname : 'Embed Content')}
                        </Typography>
                        {url && (
                            <>
                                <IconButton
                                    size="small"
                                    onClick={() => setIsEditing(true)}
                                    title="Edit URL"
                                >
                                    ✏️
                                </IconButton>
                                <IconButton
                                    size="small"
                                    onClick={() => window.open(url, '_blank')}
                                    title="Open in new tab"
                                >
                                    ↗️
                                </IconButton>
                            </>
                        )}
                    </Box>
                )}

                {/* Content Area */}
                <Box className="flex-1 relative overflow-hidden">
                    {/* URL Input (editing mode) */}
                    {isEditing && (
                        <Box
                            className="absolute flex flex-col items-center justify-center gap-4 p-6 bg-gray-50 dark:bg-gray-800 inset-0" >
                            <Typography variant="body2" color="text.secondary">
                                Paste a URL to embed
                            </Typography>
                            <TextField
                                value={inputUrl}
                                onChange={(e) => setInputUrl(e.target.value)}
                                onKeyDown={handleKeyDown}
                                placeholder="https://..."
                                fullWidth
                                size="small"
                                autoFocus
                            />
                            <Box className="flex gap-2">
                                <Button
                                    variant="contained"
                                    size="small"
                                    onClick={handleUrlSubmit}
                                    disabled={!inputUrl.trim()}
                                >
                                    Embed
                                </Button>
                                {url && (
                                    <Button
                                        variant="outlined"
                                        size="small"
                                        onClick={() => {
                                            setInputUrl(url);
                                            setIsEditing(false);
                                        }}
                                    >
                                        Cancel
                                    </Button>
                                )}
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                                Supports: YouTube, Figma, Google Docs, images, and more
                            </Typography>
                        </Box>
                    )}

                    {/* Loading state */}
                    {isLoading && !isEditing && (
                        <Box
                            className="absolute flex items-center justify-center bg-gray-50 dark:bg-gray-800 inset-0" >
                            <CircularProgress size={32} />
                        </Box>
                    )}

                    {/* Error state */}
                    {error && !isEditing && (
                        <Box
                            className="absolute flex flex-col items-center justify-center gap-2 p-4 inset-0" style={{ backgroundColor: 'error.lighter' }} >
                            <Typography color="error">⚠️ Failed to load</Typography>
                            <Typography variant="caption" color="error">
                                {error}
                            </Typography>
                            <Button size="small" onClick={() => setIsEditing(true)}>
                                Edit URL
                            </Button>
                        </Box>
                    )}

                    {/* Embedded content */}
                    {url && !isEditing && !isLoading && !error && (
                        <>
                            {/* Image */}
                            {embedType === 'image' && (
                                <img
                                    src={url}
                                    alt={title || 'Embedded image'}
                                    style={{
                                        width: '100%',
                                        height: '100%',
                                        objectFit: 'contain'
                                    }}
                                    onError={() => {
                                        // Would set error state
                                    }}
                                />
                            )}

                            {/* Video */}
                            {embedType === 'video' && (
                                <video
                                    src={url}
                                    controls
                                    style={{
                                        width: '100%',
                                        height: '100%'
                                    }}
                                />
                            )}

                            {/* iframe for everything else */}
                            {!['image', 'video'].includes(embedType) && (
                                <iframe
                                    src={embedUrl}
                                    title={title || 'Embedded content'}
                                    style={{
                                        width: '100%',
                                        height: '100%',
                                        border: 'none'
                                    }}
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                    allowFullScreen
                                />
                            )}
                        </>
                    )}
                </Box>

                {/* Description */}
                {description && !isEditing && (
                    <Box className="px-3 py-2 border-gray-200 dark:border-gray-700 border-t" >
                        <Typography variant="caption" color="text.secondary">
                            {description}
                        </Typography>
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

export const EmbedNode = memo(EmbedNodeComponent);
export default EmbedNode;
