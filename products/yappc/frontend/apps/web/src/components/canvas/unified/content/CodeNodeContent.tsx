/**
 * Code Node Content
 * 
 * Renders code content within a canvas node.
 * Provides inline code editing with syntax highlighting without mode switching.
 * 
 * @doc.type component
 * @doc.purpose Inline code content for canvas nodes
 * @doc.layer product
 * @doc.pattern Content Renderer
 */

import React, { useState, useCallback, useRef } from 'react';
import { Box, IconButton, Tooltip, Select, MenuItem, FormControl, Typography } from '@ghatana/ui';
import { Pencil as Edit, Play as PlayArrow, Copy as ContentCopy } from 'lucide-react';

interface CodeNodeContentProps {
    data?: {
        language: string;
        content: string;
    };
    onChange?: (newData: unknown) => void;
    readonly?: boolean;
}

const DEFAULT_CODE = {
    language: 'javascript',
    content: '// Write your code here\nfunction example() {\n  console.log("Hello World");\n}',
};

const SUPPORTED_LANGUAGES = [
    'javascript', 'typescript', 'python', 'java', 'cpp', 'c',
    'html', 'css', 'json', 'markdown', 'sql', 'bash'
];

export const CodeNodeContent: React.FC<CodeNodeContentProps> = ({
    data,
    onChange,
    readonly = false
}) => {
    const [isEditing, setIsEditing] = useState(false);
    const [editContent, setEditContent] = useState(data?.content || DEFAULT_CODE.content);
    const [language, setLanguage] = useState(data?.language || DEFAULT_CODE.language);
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Sync with parent data
    React.useEffect(() => {
        if (data) {
            setEditContent(data.content);
            setLanguage(data.language);
        }
    }, [data]);

    const notifyChange = useCallback((newContent: string, newLanguage: string) => {
        const newData = {
            language: newLanguage,
            content: newContent,
        };
        onChange?.(newData);
    }, [onChange]);

    const handleEdit = useCallback(() => {
        setIsEditing(true);
        // Focus textarea after state update
        setTimeout(() => {
            textareaRef.current?.focus();
        }, 0);
    }, []);

    const handleSave = useCallback(() => {
        setIsEditing(false);
        notifyChange(editContent, language);
    }, [editContent, language, notifyChange]);

    const handleCancel = useCallback(() => {
        setIsEditing(false);
        // Revert to original content
        if (data) {
            setEditContent(data.content);
            setLanguage(data.language);
        }
    }, [data]);

    const handleCopy = useCallback(() => {
        navigator.clipboard.writeText(editContent);
    }, [editContent]);

    const handleRun = useCallback(() => {
        // This would integrate with a code execution service
        console.log('Run code:', language, editContent);
    }, [language, editContent]);

    const handleLanguageChange = useCallback((event: unknown) => {
        const newLanguage = event.target.value;
        setLanguage(newLanguage);

        // Provide default content for different languages
        const defaultContent =
            newLanguage === 'javascript' || newLanguage === 'typescript'
                ? `// ${newLanguage} code\nfunction example() {\n  console.log("Hello World");\n}`
                : newLanguage === 'python'
                    ? `# Python code\ndef example():\n    print("Hello World")`
                    : newLanguage === 'html'
                        ? `<!-- HTML code -->\n<!DOCTYPE html>\n<html>\n<head>\n  <title>Example</title>\n</head>\n<body>\n  <h1>Hello World</h1>\n</body>\n</html>`
                        : `// ${newLanguage} code\n// Write your code here`;

        setEditContent(defaultContent);
    }, []);

    if (isEditing && !readonly) {
        return (
            <Box className="flex flex-col gap-2 p-2 min-w-[400px] min-h-[250px]">
                {/* Language Selector */}
                <Box className="flex gap-2 items-center justify-between">
                    <FormControl size="sm" className="min-w-[120px]">
                        <Select
                            value={language}
                            onChange={handleLanguageChange}
                            size="sm"
                        >
                            {SUPPORTED_LANGUAGES.map(lang => (
                                <MenuItem key={lang} value={lang}>
                                    {lang.charAt(0).toUpperCase() + lang.slice(1)}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    {/* Action Buttons */}
                    <Box className="flex gap-2">
                        <Tooltip title="Cancel">
                            <IconButton size="sm" onClick={handleCancel}>
                                ×
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Save">
                            <IconButton size="sm" onClick={handleSave} tone="primary">
                                ✓
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Box>

                {/* Code Editor */}
                <Box className="rounded overflow-hidden flex-1 border border-solid border-[#ccc]">
                    <textarea
                        ref={textareaRef}
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        placeholder="Write your code here..."
                        style={{
                            width: '100%',
                            height: '100%',
                            minHeight: 180,
                            padding: 8,
                            border: 'none',
                            outline: 'none',
                            fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                            fontSize: '13px',
                            lineHeight: '1.4',
                            resize: 'none',
                            backgroundColor: 'background.paper',
                        }}
                    />
                </Box>
            </Box>
        );
    }

    // Read-only view
    return (
        <Box className="relative w-full h-[200px]">
            {/* Code Preview */}
            <Box className="w-full h-full rounded overflow-hidden border border-solid border-[#ccc] bg-white dark:bg-gray-900">
                {/* Language Header */}
                <Box className="px-4 py-2 flex items-center justify-between border-b border-solid border-b-[#ccc] bg-gray-100" >
                    <Typography as="span" className="text-xs text-gray-500" className="font-bold font-mono">
                        {language}
                    </Typography>
                    <Box className="flex gap-1">
                        <Tooltip title="Copy">
                            <IconButton size="sm" onClick={handleCopy}>
                                <ContentCopy size={16} />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Run">
                            <IconButton size="sm" onClick={handleRun}>
                                <PlayArrow size={16} />
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Box>

                {/* Code Content */}
                <Box className="p-4 overflow-auto h-[calc(100% - 40px)]">
                    <pre style={{
                        margin: 0,
                        fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                        fontSize: '12px',
                        lineHeight: '1.4',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                    }}>
                        <code>{editContent}</code>
                    </pre>
                </Box>
            </Box>

            {/* Edit Button */}
            {!readonly && (
                <Box className="absolute rounded p-1 top-[8px] right-[8px] bg-white dark:bg-gray-900 shadow-sm">
                    <Tooltip title="Edit">
                        <IconButton size="sm" onClick={handleEdit}>
                            <Edit size={16} />
                        </IconButton>
                    </Tooltip>
                </Box>
            )}
        </Box>
    );
};
