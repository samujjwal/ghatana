/**
 * CodeScaffoldDialog Component
 * 
 * Modal dialog for code scaffolding from service nodes.
 * Follows Journey 3.1 (Developer - Code Scaffolding) from YAPPC_USER_JOURNEYS.md.
 * 
 * Features:
 * - Language/framework selection
 * - Code generation options (tests, docs)
 * - File structure preview
 * - Integration with AICodeGenerationService
 * 
 * @doc.type component
 * @doc.purpose Code scaffolding dialog
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography, Select, MenuItem, FormControl, InputLabel, Checkbox, FormControlLabel, FormGroup, Spinner as CircularProgress, Alert, Surface as Paper, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Divider, Chip } from '@ghatana/ui';
import { Code as CodeIcon, FileText as FileIcon, Folder as FolderIcon, Check as CheckIcon, X as CloseIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';
import type { GeneratedFile, CodeGenerationResult } from '../integration/types';

/**
 * Language options
 */
const LANGUAGES = [
    { value: 'typescript', label: 'TypeScript', icon: '🔷' },
    { value: 'javascript', label: 'JavaScript', icon: '🟨' },
    { value: 'java', label: 'Java', icon: '☕' },
    { value: 'python', label: 'Python', icon: '🐍' },
    { value: 'go', label: 'Go', icon: '🔷' },
] as const;

/**
 * Framework options per language
 */
const FRAMEWORKS: Record<string, Array<{ value: string; label: string }>> = {
    typescript: [
        { value: 'react', label: 'React' },
        { value: 'nextjs', label: 'Next.js' },
        { value: 'express', label: 'Express' },
        { value: 'fastify', label: 'Fastify' },
        { value: 'nestjs', label: 'NestJS' },
    ],
    javascript: [
        { value: 'react', label: 'React' },
        { value: 'vue', label: 'Vue' },
        { value: 'express', label: 'Express' },
        { value: 'koa', label: 'Koa' },
    ],
    java: [
        { value: 'spring-boot', label: 'Spring Boot' },
        { value: 'activej', label: 'ActiveJ' },
        { value: 'quarkus', label: 'Quarkus' },
    ],
    python: [
        { value: 'fastapi', label: 'FastAPI' },
        { value: 'django', label: 'Django' },
        { value: 'flask', label: 'Flask' },
    ],
    go: [
        { value: 'gin', label: 'Gin' },
        { value: 'echo', label: 'Echo' },
        { value: 'fiber', label: 'Fiber' },
    ],
};

/**
 * CodeScaffoldDialog props
 */
export interface CodeScaffoldDialogProps {
    /** Dialog open state */
    open: boolean;
    /** Node to scaffold code from */
    node: Node | null;
    /** On close callback */
    onClose: () => void;
    /** On generate callback */
    onGenerate?: (result: CodeGenerationResult) => void;
    /** Code generation function */
    generateCode?: (options: ScaffoldOptions) => Promise<CodeGenerationResult>;
}

/**
 * Scaffold options
 */
export interface ScaffoldOptions {
    nodeId: string;
    nodeLabel: string;
    language: string;
    framework: string;
    includeTests: boolean;
    includeDocumentation: boolean;
    includeValidation: boolean;
}

/**
 * CodeScaffoldDialog component
 */
export function CodeScaffoldDialog({
    open,
    node,
    onClose,
    onGenerate,
    generateCode,
}: CodeScaffoldDialogProps) {
    const theme = useTheme();
    const [language, setLanguage] = useState('typescript');
    const [framework, setFramework] = useState('fastify');
    const [includeTests, setIncludeTests] = useState(true);
    const [includeDocumentation, setIncludeDocumentation] = useState(true);
    const [includeValidation, setIncludeValidation] = useState(false);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState<CodeGenerationResult | null>(null);
    const [error, setError] = useState<Error | null>(null);

    // Available frameworks for selected language
    const availableFrameworks = useMemo(() => {
        return FRAMEWORKS[language] || [];
    }, [language]);

    // Update framework when language changes
    const handleLanguageChange = useCallback((newLanguage: string) => {
        setLanguage(newLanguage);
        const frameworks = FRAMEWORKS[newLanguage] || [];
        if (frameworks.length > 0) {
            setFramework(frameworks[0].value);
        }
    }, []);

    // Handle generate
    const handleGenerate = useCallback(async () => {
        if (!node) return;

        setLoading(true);
        setError(null);
        setResult(null);

        try {
            const options: ScaffoldOptions = {
                nodeId: node.id,
                nodeLabel: node.data.label || 'Unnamed',
                language,
                framework,
                includeTests,
                includeDocumentation,
                includeValidation,
            };

            let generationResult: CodeGenerationResult;

            if (generateCode) {
                // Use provided generation function
                generationResult = await generateCode(options);
            } else {
                // Mock generation for development
                await new Promise((resolve) => setTimeout(resolve, 2000));
                generationResult = {
                    success: true,
                    files: [
                        {
                            path: `src/services/${node.data.label.toLowerCase().replace(/\s+/g, '-')}.service.ts`,
                            content: `// ${node.data.label} Service\nexport class ${node.data.label}Service {\n  // NOTE: Implement\n}`,
                            language: 'typescript',
                            type: 'source',
                        },
                        {
                            path: `src/services/__tests__/${node.data.label.toLowerCase().replace(/\s+/g, '-')}.service.test.ts`,
                            content: `// ${node.data.label} Service Tests\ndescribe('${node.data.label}Service', () => {\n  // NOTE: Write tests\n});`,
                            language: 'typescript',
                            type: 'test',
                        },
                    ],
                    summary: `Generated ${node.data.label} service with TypeScript/Fastify`,
                };
            }

            setResult(generationResult);
            setLoading(false);

            if (generationResult.success) {
                onGenerate?.(generationResult);
            }
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Code generation failed');
            setError(error);
            setLoading(false);
        }
    }, [node, language, framework, includeTests, includeDocumentation, includeValidation, generateCode, onGenerate]);

    // Handle close
    const handleClose = useCallback(() => {
        setResult(null);
        setError(null);
        onClose();
    }, [onClose]);

    // Render file tree
    const renderFileTree = (files: GeneratedFile[]) => {
        return (
            <List dense>
                {files.map((file, index) => (
                    <ListItem key={index}>
                        <ListItemIcon>
                            <FileIcon size={16} color={file.type === 'test' ? 'warning' : 'primary'} />
                        </ListItemIcon>
                        <ListItemText
                            primary={file.path}
                            secondary={`${file.language} • ${file.type}`}
                        />
                        <Chip
                            label={`${(file.content.length / 1024).toFixed(1)} KB`}
                            size="sm"
                            variant="outlined"
                        />
                    </ListItem>
                ))}
            </List>
        );
    };

    if (!node) return null;

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            size="md"
            fullWidth
            PaperProps={{
                sx: {
                    minHeight: '500px',
                },
            }}
        >
            <DialogTitle>
                <Box display="flex" alignItems="center" gap={1}>
                    <CodeIcon />
                    <Typography as="h6">Scaffold Code</Typography>
                </Box>
                <Typography as="p" className="text-sm mt-1" color="text.secondary">
                    Generate code for: <strong>{node.data.label}</strong>
                </Typography>
            </DialogTitle>

            <DialogContent dividers>
                {!result && !error && (
                    <Box className="flex flex-col gap-6">
                        {/* Language Selection */}
                        <FormControl fullWidth>
                            <InputLabel>Language</InputLabel>
                            <Select
                                value={language}
                                label="Language"
                                onChange={(e) => handleLanguageChange(e.target.value)}
                                disabled={loading}
                            >
                                {LANGUAGES.map((lang) => (
                                    <MenuItem key={lang.value} value={lang.value}>
                                        <Box display="flex" alignItems="center" gap={1}>
                                            <span>{lang.icon}</span>
                                            <span>{lang.label}</span>
                                        </Box>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {/* Framework Selection */}
                        <FormControl fullWidth>
                            <InputLabel>Framework</InputLabel>
                            <Select
                                value={framework}
                                label="Framework"
                                onChange={(e) => setFramework(e.target.value)}
                                disabled={loading || availableFrameworks.length === 0}
                            >
                                {availableFrameworks.map((fw) => (
                                    <MenuItem key={fw.value} value={fw.value}>
                                        {fw.label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <Divider />

                        {/* Options */}
                        <Box>
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Generation Options
                            </Typography>
                            <FormGroup>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={includeTests}
                                            onChange={(e) => setIncludeTests(e.target.checked)}
                                            disabled={loading}
                                        />
                                    }
                                    label="Include unit tests"
                                />
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={includeDocumentation}
                                            onChange={(e) => setIncludeDocumentation(e.target.checked)}
                                            disabled={loading}
                                        />
                                    }
                                    label="Include documentation (JSDoc/JavaDoc)"
                                />
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={includeValidation}
                                            onChange={(e) => setIncludeValidation(e.target.checked)}
                                            disabled={loading}
                                        />
                                    }
                                    label="Include input validation"
                                />
                            </FormGroup>
                        </Box>
                    </Box>
                )}

                {/* Loading State */}
                {loading && (
                    <Box
                        className="flex flex-col items-center justify-center py-12 gap-4"
                    >
                        <CircularProgress size={48} />
                        <Typography as="p" color="text.secondary">
                            Generating code with AI...
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            This may take a few seconds
                        </Typography>
                    </Box>
                )}

                {/* Error State */}
                {error && (
                    <Alert severity="error" className="mb-4">
                        {error.message}
                    </Alert>
                )}

                {/* Success State */}
                {result && result.success && (
                    <Box>
                        <Alert severity="success" className="mb-4">
                            {result.summary}
                        </Alert>

                        <Paper variant="outlined" className="p-4 bg-gray-50 dark:bg-gray-800">
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Generated Files ({result.files.length})
                            </Typography>
                            {renderFileTree(result.files)}
                        </Paper>
                    </Box>
                )}

                {/* Failure State */}
                {result && !result.success && (
                    <Box>
                        <Alert severity="error" className="mb-4">
                            {result.summary}
                        </Alert>
                        {result.errors && result.errors.length > 0 && (
                            <Paper variant="outlined" className="p-4">
                                <Typography as="p" className="text-sm font-medium" tone="danger" gutterBottom>
                                    Errors:
                                </Typography>
                                <List dense>
                                    {result.errors.map((err: string, index: number) => (
                                        <ListItem key={index}>
                                            <ListItemIcon>
                                                <CloseIcon tone="danger" size={16} />
                                            </ListItemIcon>
                                            <ListItemText primary={err} />
                                        </ListItem>
                                    ))}
                                </List>
                            </Paper>
                        )}
                    </Box>
                )}
            </DialogContent>

            <DialogActions>
                <Button onClick={handleClose} disabled={loading}>
                    {result ? 'Close' : 'Cancel'}
                </Button>
                {!result && (
                    <Button
                        variant="solid"
                        onClick={handleGenerate}
                        disabled={loading}
                        startIcon={loading ? <CircularProgress size={16} /> : <CodeIcon />}
                    >
                        {loading ? 'Generating...' : 'Generate Code'}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    );
}
