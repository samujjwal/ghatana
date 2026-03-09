/**
 * Context Gathering Step Component
 *
 * Second step in the AI-powered workflow wizard.
 * Gathers context from the codebase and user input.
 *
 * Features:
 * - Automatic context detection
 * - File/folder selection
 * - Related code analysis
 * - Dependency mapping
 *
 * @doc.type component
 * @doc.purpose Context gathering workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { Box, Surface as Paper, Typography, TextField, Button, Chip, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Checkbox, Collapse, LinearProgress, Alert, IconButton, Divider } from '@ghatana/ui';
import { Folder as FolderIcon, File as FileIcon, Code as CodeIcon, Search as SearchIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Plus as AddIcon, Trash2 as DeleteIcon, Sparkles as AIIcon, Check as CheckIcon } from 'lucide-react';

export interface ContextStepProps {
    /** Intent data from previous step */
    intentData: {
        intent: string;
        parsedIntent: { type: string; target: string } | null;
    };
    /** Current context value */
    value: ContextStepData;
    /** Callback when context changes */
    onChange: (context: ContextStepData) => void;
    /** Callback when step is complete */
    onComplete: (data: ContextStepData) => void;
    /** Callback to go back */
    onBack?: () => void;
    /** Whether step is loading */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
}

export interface ContextStepData {
    selectedFiles: ContextFile[];
    additionalContext: string;
    detectedDependencies: string[];
    codebaseAnalysis: CodebaseAnalysis | null;
}

export interface ContextFile {
    path: string;
    type: 'file' | 'folder';
    language?: string;
    relevanceScore: number;
    isSelected: boolean;
}

export interface CodebaseAnalysis {
    languages: string[];
    frameworks: string[];
    patterns: string[];
    relatedComponents: string[];
}

/**
 * ContextStep Component
 */
export const ContextStep: React.FC<ContextStepProps> = ({
    intentData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [showFileSearch, setShowFileSearch] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedSections, setExpandedSections] = useState<Set<string>>(
        new Set(['suggested', 'analysis'])
    );

    // Simulate auto-detection on mount
    useEffect(() => {
        if (!value.codebaseAnalysis) {
            analyzeCodebase();
        }
    }, []);

    const analyzeCodebase = useCallback(async () => {
        setIsAnalyzing(true);
        try {
            // Simulate AI analysis
            await new Promise((resolve) => setTimeout(resolve, 1500));

            const mockAnalysis: CodebaseAnalysis = {
                languages: ['TypeScript', 'Java', 'Python'],
                frameworks: ['React', 'ActiveJ', 'FastAPI'],
                patterns: ['MVC', 'Repository Pattern', 'Service Layer'],
                relatedComponents: [
                    'src/components/Auth',
                    'src/services/UserService',
                    'src/hooks/useAuth',
                ],
            };

            const suggestedFiles: ContextFile[] = [
                { path: 'src/components/Auth/Login.tsx', type: 'file', language: 'TypeScript', relevanceScore: 0.95, isSelected: true },
                { path: 'src/services/UserService.ts', type: 'file', language: 'TypeScript', relevanceScore: 0.88, isSelected: true },
                { path: 'src/hooks/useAuth.ts', type: 'file', language: 'TypeScript', relevanceScore: 0.82, isSelected: false },
                { path: 'src/api/auth', type: 'folder', relevanceScore: 0.78, isSelected: false },
            ];

            onChange({
                ...value,
                selectedFiles: suggestedFiles,
                codebaseAnalysis: mockAnalysis,
                detectedDependencies: ['react', '@tanstack/react-query', 'axios'],
            });
        } finally {
            setIsAnalyzing(false);
        }
    }, [value, onChange]);

    const toggleSection = useCallback((section: string) => {
        setExpandedSections((prev) => {
            const next = new Set(prev);
            if (next.has(section)) {
                next.delete(section);
            } else {
                next.add(section);
            }
            return next;
        });
    }, []);

    const toggleFile = useCallback((index: number) => {
        const newFiles = [...value.selectedFiles];
        newFiles[index] = { ...newFiles[index], isSelected: !newFiles[index].isSelected };
        onChange({ ...value, selectedFiles: newFiles });
    }, [value, onChange]);

    const removeFile = useCallback((index: number) => {
        const newFiles = value.selectedFiles.filter((_, i) => i !== index);
        onChange({ ...value, selectedFiles: newFiles });
    }, [value, onChange]);

    const handleContextChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        onChange({ ...value, additionalContext: e.target.value });
    }, [value, onChange]);

    const handleContinue = useCallback(() => {
        onComplete(value);
    }, [value, onComplete]);

    const selectedCount = value.selectedFiles.filter((f) => f.isSelected).length;

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <CodeIcon tone="primary" />
                Gathering Context
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-6">
                AI is analyzing your codebase to understand the context for: <strong>{intentData.intent}</strong>
            </Typography>

            {error && (
                <Alert severity="error" className="mb-4">
                    {error}
                </Alert>
            )}

            {isAnalyzing && (
                <Box className="mb-6">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                        <AIIcon size={16} className="mr-1 align-middle" />
                        Analyzing codebase...
                    </Typography>
                    <LinearProgress />
                </Box>
            )}

            {/* Codebase Analysis */}
            {value.codebaseAnalysis && (
                <Paper variant="outlined" className="mb-4">
                    <Box
                        className="p-4 cursor-pointer flex items-center justify-between"
                        onClick={() => toggleSection('analysis')}
                    >
                        <Typography as="p" className="text-sm font-medium">
                            <AIIcon size={16} className="mr-1 align-middle" />
                            Codebase Analysis
                        </Typography>
                        {expandedSections.has('analysis') ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                    </Box>
                    <Collapse in={expandedSections.has('analysis')}>
                        <Divider />
                        <Box className="p-4">
                            <Box className="flex flex-wrap gap-2 mb-4">
                                {value.codebaseAnalysis.languages.map((lang) => (
                                    <Chip key={lang} size="sm" label={lang} tone="primary" variant="outlined" />
                                ))}
                                {value.codebaseAnalysis.frameworks.map((fw) => (
                                    <Chip key={fw} size="sm" label={fw} tone="secondary" variant="outlined" />
                                ))}
                            </Box>
                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                Detected patterns: {value.codebaseAnalysis.patterns.join(', ')}
                            </Typography>
                        </Box>
                    </Collapse>
                </Paper>
            )}

            {/* Suggested Files */}
            <Paper variant="outlined" className="mb-4">
                <Box
                    className="p-4 cursor-pointer flex items-center justify-between"
                    onClick={() => toggleSection('suggested')}
                >
                    <Typography as="p" className="text-sm font-medium">
                        Relevant Files ({selectedCount} selected)
                    </Typography>
                    {expandedSections.has('suggested') ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                </Box>
                <Collapse in={expandedSections.has('suggested')}>
                    <Divider />
                    <List dense>
                        {value.selectedFiles.map((file, index) => (
                            <ListItem key={file.path}>
                                <Checkbox
                                    checked={file.isSelected}
                                    onChange={() => toggleFile(index)}
                                    size="sm"
                                />
                                <ListItemIcon className="min-w-[36px]">
                                    {file.type === 'folder' ? <FolderIcon /> : <FileIcon />}
                                </ListItemIcon>
                                <ListItemText
                                    primary={file.path}
                                    secondary={
                                        <Box className="flex items-center gap-2">
                                            {file.language && (
                                                <Chip size="sm" label={file.language} className="h-[20px]" />
                                            )}
                                            <Typography as="span" className="text-xs text-gray-500">
                                                Relevance: {Math.round(file.relevanceScore * 100)}%
                                            </Typography>
                                        </Box>
                                    }
                                />
                                <ListItemSecondaryAction>
                                    <IconButton size="sm" onClick={() => removeFile(index)}>
                                        <DeleteIcon size={16} />
                                    </IconButton>
                                </ListItemSecondaryAction>
                            </ListItem>
                        ))}
                    </List>
                    <Box className="p-2">
                        <Button
                            size="sm"
                            startIcon={<AddIcon />}
                            onClick={() => setShowFileSearch(!showFileSearch)}
                        >
                            Add Files
                        </Button>
                    </Box>
                    {showFileSearch && (
                        <Box className="p-4 pt-0">
                            <TextField
                                fullWidth
                                size="sm"
                                placeholder="Search files..."
                                value={searchQuery}
                                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
                                InputProps={{
                                    startAdornment: <SearchIcon size={16} className="mr-2" />,
                                }}
                            />
                        </Box>
                    )}
                </Collapse>
            </Paper>

            {/* Additional Context */}
            <Paper variant="outlined" className="mb-4">
                <Box className="p-4">
                    <Typography as="p" className="text-sm font-medium" gutterBottom>
                        Additional Context (Optional)
                    </Typography>
                    <TextField
                        fullWidth
                        multiline
                        rows={3}
                        value={value.additionalContext}
                        onChange={handleContextChange}
                        placeholder="Add any additional information that might help the AI understand your requirements..."
                        variant="outlined"
                        size="sm"
                    />
                </Box>
            </Paper>

            {/* Navigation */}
            <Box className="mt-6 flex justify-between">
                {onBack && (
                    <Button onClick={onBack} disabled={isLoading}>
                        Back
                    </Button>
                )}
                <Box className="grow" />
                <Button
                    variant="solid"
                    onClick={handleContinue}
                    disabled={isLoading || isAnalyzing || selectedCount === 0}
                    endIcon={<CheckIcon />}
                >
                    Continue
                </Button>
            </Box>
        </Box>
    );
};

export default ContextStep;
