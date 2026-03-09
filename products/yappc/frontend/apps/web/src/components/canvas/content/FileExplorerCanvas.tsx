/**
 * File Explorer Canvas Content
 * 
 * File tree browser for Code × File level.
 * Displays project file structure with search and navigation.
 * 
 * @doc.type component
 * @doc.purpose File explorer for navigating project structure
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import { Box, Typography, IconButton } from '@ghatana/ui';
import { TextField, Collapse } from '@ghatana/ui';
import { Folder, FolderOpen, File as InsertDriveFile, Search, ChevronDown as ExpandMore, ChevronRight, Code, FileText as Description, Image as ImageIcon } from 'lucide-react';

interface FileNode {
    name: string;
    path: string;
    type: 'file' | 'folder';
    children?: FileNode[];
    extension?: string;
}

// Mock file tree data
const MOCK_FILE_TREE: FileNode[] = [
    {
        name: 'src',
        path: '/src',
        type: 'folder',
        children: [
            {
                name: 'components',
                path: '/src/components',
                type: 'folder',
                children: [
                    { name: 'Button.tsx', path: '/src/components/Button.tsx', type: 'file', extension: 'tsx' },
                    { name: 'Input.tsx', path: '/src/components/Input.tsx', type: 'file', extension: 'tsx' },
                    { name: 'Modal.tsx', path: '/src/components/Modal.tsx', type: 'file', extension: 'tsx' },
                ],
            },
            {
                name: 'utils',
                path: '/src/utils',
                type: 'folder',
                children: [
                    { name: 'helpers.ts', path: '/src/utils/helpers.ts', type: 'file', extension: 'ts' },
                    { name: 'validators.ts', path: '/src/utils/validators.ts', type: 'file', extension: 'ts' },
                ],
            },
            { name: 'index.ts', path: '/src/index.ts', type: 'file', extension: 'ts' },
            { name: 'App.tsx', path: '/src/App.tsx', type: 'file', extension: 'tsx' },
        ],
    },
    {
        name: 'public',
        path: '/public',
        type: 'folder',
        children: [
            { name: 'index.html', path: '/public/index.html', type: 'file', extension: 'html' },
            { name: 'favicon.ico', path: '/public/favicon.ico', type: 'file', extension: 'ico' },
        ],
    },
    { name: 'package.json', path: '/package.json', type: 'file', extension: 'json' },
    { name: 'tsconfig.json', path: '/tsconfig.json', type: 'file', extension: 'json' },
    { name: 'README.md', path: '/README.md', type: 'file', extension: 'md' },
];

const getFileIcon = (extension?: string) => {
    switch (extension) {
        case 'ts':
        case 'tsx':
        case 'js':
        case 'jsx':
            return <Code size={16} />;
        case 'md':
        case 'txt':
            return <Description size={16} />;
        case 'png':
        case 'jpg':
        case 'svg':
        case 'ico':
            return <ImageIcon size={16} />;
        default:
            return <InsertDriveFile size={16} />;
    }
};

const FileTreeItem = ({
    node,
    level = 0,
    onFileClick,
    searchQuery,
}: {
    node: FileNode;
    level?: number;
    onFileClick: (path: string) => void;
    searchQuery: string;
}) => {
    const [expanded, setExpanded] = useState(level < 2); // Auto-expand first 2 levels

    const matchesSearch = useMemo(() => {
        if (!searchQuery) return true;
        return node.name.toLowerCase().includes(searchQuery.toLowerCase());
    }, [node.name, searchQuery]);

    if (!matchesSearch && node.type === 'file') return null;

    const hasMatchingChildren = useMemo(() => {
        if (node.type === 'file') return false;
        if (!searchQuery) return true;

        const checkChildren = (nodes: FileNode[]): boolean => {
            return nodes.some(child => {
                if (child.name.toLowerCase().includes(searchQuery.toLowerCase())) return true;
                if (child.children) return checkChildren(child.children);
                return false;
            });
        };

        return node.children ? checkChildren(node.children) : false;
    }, [node, searchQuery]);

    if (node.type === 'folder' && !hasMatchingChildren) return null;

    return (
        <Box>
            <Box
                onClick={() => {
                    if (node.type === 'folder') {
                        setExpanded(!expanded);
                    } else {
                        onFileClick(node.path);
                    }
                }}
                className="flex items-center p-[4px 8px]" style={{ paddingLeft: `${level * 20 + 8 }}
            >
                {node.type === 'folder' && (
                    <IconButton size="small" className="p-0 mr-1">
                        {expanded ? <ExpandMore size={16} /> : <ChevronRight size={16} />}
                    </IconButton>
                )}
                {node.type === 'folder' ? (
                    expanded ? <FolderOpen size={16} className="mr-2 text-[#FFA726]" /> : <Folder size={16} className="mr-2 text-[#FFA726]" />
                ) : (
                    <Box className="flex items-center mr-2 ml-6 text-gray-500 dark:text-gray-400">
                        {getFileIcon(node.extension)}
                    </Box>
                )}
                <Typography
                    variant="body2"
                    className="text-sm" style={{ fontFamily: node.type === 'file' ? 'monospace' : 'inherit', fontWeight: matchesSearch && searchQuery ? 600 : 400 }}
                >
                    {node.name}
                </Typography>
            </Box>

            {node.type === 'folder' && node.children && (
                <Collapse in={expanded}>
                    {node.children.map((child) => (
                        <FileTreeItem
                            key={child.path}
                            node={child}
                            level={level + 1}
                            onFileClick={onFileClick}
                            searchQuery={searchQuery}
                        />
                    ))}
                </Collapse>
            )}
        </Box>
    );
};

export const FileExplorerCanvas = () => {
    const [fileTree] = useState<FileNode[]>(MOCK_FILE_TREE);
    const [selectedFile, setSelectedFile] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const hasContent = fileTree.length > 0;

    const handleFileClick = (path: string) => {
        setSelectedFile(path);
        console.log('Open file:', path);
        // NOTE: Integrate with code editor or file viewer
    };

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Import Project',
                    onClick: () => {
                        console.log('Import project');
                    },
                },
                secondaryAction: {
                    label: 'Create New File',
                    onClick: () => {
                        console.log('Create file');
                    },
                },
            }}
        >
            <Box
                className="h-full w-full flex flex-col bg-white dark:bg-gray-900"
            >
                {/* Search bar */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)]">
                    <TextField
                        fullWidth
                        size="small"
                        placeholder="Search files..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        InputProps={{
                            startAdornment: <Search className="text-gray-500 dark:text-gray-400 mr-2" />,
                        }}
                    />
                </Box>

                {/* File tree */}
                <Box className="flex-1 overflow-auto p-2">
                    {fileTree.map((node) => (
                        <FileTreeItem
                            key={node.path}
                            node={node}
                            onFileClick={handleFileClick}
                            searchQuery={searchQuery}
                        />
                    ))}
                </Box>

                {/* Status bar */}
                {selectedFile && (
                    <Box
                        className="p-2 bg-[rgba(0,_0,_0,_0.02)]" style={{ borderTop: '1px solid rgba(0, 0, 0, 0.12)' }} >
                        <Typography variant="caption" className="font-mono">
                            Selected: {selectedFile}
                        </Typography>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default FileExplorerCanvas;
