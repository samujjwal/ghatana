/**
 * Code Generation Step Component
 *
 * Fourth step in the AI-powered workflow wizard.
 * Handles AI code generation and user review.
 *
 * Features:
 * - AI code generation
 * - Diff view
 * - Code editing
 * - Accept/reject changes
 *
 * @doc.type component
 * @doc.purpose Code generation workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { Box, Surface as Paper, Typography, Button, IconButton, Tabs, Tab, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Chip, LinearProgress, Alert, Collapse, Tooltip } from '@ghatana/ui';
import { Code as CodeIcon, File as FileIcon, Plus as AddIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Check as CheckIcon, X as RejectIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Sparkles as AIIcon, RefreshCw as RegenerateIcon, Copy as CopyIcon } from 'lucide-react';

export interface CodeStepProps {
    /** Plan data from previous step */
    planData: {
        steps: { id: string; name: string }[];
    };
    /** Current code generation value */
    value: CodeStepData;
    /** Callback when code changes */
    onChange: (code: CodeStepData) => void;
    /** Callback when step is complete */
    onComplete: (data: CodeStepData) => void;
    /** Callback to go back */
    onBack?: () => void;
    /** Whether step is loading */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
}

export interface CodeStepData {
    files: GeneratedFile[];
    currentFileIndex: number;
    status: 'generating' | 'review' | 'approved' | 'regenerating';
}

export interface GeneratedFile {
    path: string;
    language: string;
    operation: 'create' | 'modify' | 'delete';
    originalContent?: string;
    generatedContent: string;
    userModifiedContent?: string;
    status: 'pending' | 'accepted' | 'rejected' | 'modified';
    aiExplanation?: string;
}

const LANGUAGE_COLORS: Record<string, string> = {
    typescript: '#3178c6',
    javascript: '#f7df1e',
    java: '#b07219',
    python: '#3572A5',
    css: '#563d7c',
    html: '#e34c26',
};

/**
 * CodeStep Component
 */
export const CodeStep: React.FC<CodeStepProps> = ({
    planData: _planData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    void _planData; // Used for code generation context
    const [isGenerating, setIsGenerating] = useState(false);
    const [expandedFile, setExpandedFile] = useState<string | null>(null);

    // Simulate code generation on mount
    useEffect(() => {
        if (value.status === 'generating' && value.files.length === 0) {
            generateCode();
        }
    }, []);

    const generateCode = useCallback(async () => {
        setIsGenerating(true);
        try {
            // Simulate AI code generation
            await new Promise((resolve) => setTimeout(resolve, 2000));

            const mockFiles: GeneratedFile[] = [
                {
                    path: 'src/components/Auth/Login.tsx',
                    language: 'typescript',
                    operation: 'create',
                    generatedContent: `import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';

interface LoginProps {
    onSuccess?: () => void;
}

export const Login: React.FC<LoginProps> = ({ onSuccess }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const { login, isLoading, error } = useAuth();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const result = await login(email, password);
        if (result.success && onSuccess) {
            onSuccess();
        }
    };

    return (
        <form onSubmit={handleSubmit}>
            <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Email"
            />
            <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
            />
            {error && <div className="error">{error}</div>}
            <button type="submit" disabled={isLoading}>
                {isLoading ? 'Logging in...' : 'Login'}
            </button>
        </form>
    );
};

export default Login;`,
                    status: 'pending',
                    aiExplanation: 'Created a Login component with email/password form, error handling, and loading state.',
                },
                {
                    path: 'src/hooks/useAuth.ts',
                    language: 'typescript',
                    operation: 'create',
                    generatedContent: `import { useState, useCallback } from 'react';
import { authService } from '../services/authService';

interface AuthState {
    user: User | null;
    isLoading: boolean;
    error: string | null;
}

export const useAuth = () => {
    const [state, setState] = useState<AuthState>({
        user: null,
        isLoading: false,
        error: null,
    });

    const login = useCallback(async (email: string, password: string) => {
        setState(s => ({ ...s, isLoading: true, error: null }));
        try {
            const user = await authService.login(email, password);
            setState({ user, isLoading: false, error: null });
            return { success: true };
        } catch (err) {
            setState(s => ({ ...s, isLoading: false, error: err.message }));
            return { success: false, error: err.message };
        }
    }, []);

    const logout = useCallback(async () => {
        await authService.logout();
        setState({ user: null, isLoading: false, error: null });
    }, []);

    return {
        ...state,
        login,
        logout,
    };
};`,
                    status: 'pending',
                    aiExplanation: 'Created useAuth hook with login/logout functions and state management.',
                },
            ];

            onChange({
                ...value,
                files: mockFiles,
                status: 'review',
            });
        } finally {
            setIsGenerating(false);
        }
    }, [value, onChange]);

    const toggleFileExpand = useCallback((index: number) => {
        setExpandedFiles((prev) => {
            const next = new Set(prev);
            if (next.has(index)) {
                next.delete(index);
            } else {
                next.add(index);
            }
            return next;
        });
    }, []);

    const handleAcceptFile = useCallback((index: number) => {
        const newFiles = [...value.files];
        newFiles[index] = { ...newFiles[index], status: 'accepted' };
        onChange({ ...value, files: newFiles });
    }, [value, onChange]);

    const handleRejectFile = useCallback((index: number) => {
        const newFiles = [...value.files];
        newFiles[index] = { ...newFiles[index], status: 'rejected' };
        onChange({ ...value, files: newFiles });
    }, [value, onChange]);

    const handleAcceptAll = useCallback(() => {
        const newFiles = value.files.map((f) => ({
            ...f,
            status: 'accepted' as const,
        }));
        onChange({ ...value, files: newFiles });
    }, [value, onChange]);

    const handleContinue = useCallback(() => {
        const acceptedFiles = value.files.filter((f) => f.status === 'accepted');
        if (acceptedFiles.length > 0) {
            onComplete({ ...value, status: 'approved' });
        }
    }, [value, onComplete]);

    const handleCopyCode = useCallback((content: string) => {
        navigator.clipboard.writeText(content);
    }, []);

    const acceptedCount = value.files.filter((f) => f.status === 'accepted').length;
    const pendingCount = value.files.filter((f) => f.status === 'pending').length;

    const getOperationIcon = (operation: string) => {
        switch (operation) {
            case 'create':
                return <AddIcon size={16} tone="success" />;
            case 'modify':
                return <EditIcon size={16} tone="warning" />;
            case 'delete':
                return <DeleteIcon size={16} tone="danger" />;
            default:
                return <FileIcon size={16} />;
        }
    };

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <CodeIcon tone="primary" />
                Generated Code
            </Typography>

            <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                Review the AI-generated code changes before applying them.
            </Typography>

            {error && (
                <Alert severity="error" className="mb-4">
                    {error}
                </Alert>
            )}

            {(isLoading || isGenerating) && (
                <Box className="mb-6">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                        <AIIcon size={16} className="mr-1 align-middle" />
                        Generating code...
                    </Typography>
                    <LinearProgress />
                </Box>
            )}

            {/* Summary */}
            <Paper variant="outlined" className="p-4 mb-4">
                <Box className="flex flex-wrap gap-4 items-center">
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Files</Typography>
                        <Typography as="h6">{value.files.length}</Typography>
                    </Box>
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Accepted</Typography>
                        <Typography as="h6" color="success.main">{acceptedCount}</Typography>
                    </Box>
                    <Box>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Pending</Typography>
                        <Typography as="h6" color="warning.main">{pendingCount}</Typography>
                    </Box>
                    <Box className="grow" />
                    <Button
                        size="sm"
                        startIcon={<CheckIcon />}
                        onClick={handleAcceptAll}
                        disabled={pendingCount === 0}
                    >
                        Accept All
                    </Button>
                </Box>
            </Paper>

            {/* File List */}
            <Paper variant="outlined" className="mb-4">
                <List disablePadding>
                    {value.files.map((file, index) => (
                        <React.Fragment key={file.path}>
                            <ListItem
                                className="border-gray-200 dark:border-gray-700" style={{ borderBottom: expandedFiles.has(index) ? 1 : 0, backgroundColor: file.status === 'accepted'
                                        ? 'success.lighter'
                                        : file.status === 'rejected'
                                            ? 'error.lighter'
                                            : undefined }}
                            >
                                <ListItemIcon className="min-w-[36px]">
                                    {getOperationIcon(file.operation)}
                                </ListItemIcon>
                                <ListItemText
                                    primary={
                                        <Box className="flex items-center gap-2">
                                            <Typography as="p" className="text-sm font-mono">
                                                {file.path}
                                            </Typography>
                                            <Chip
                                                size="sm"
                                                label={file.language}
                                                className="h-[20px] text-white" style={{ backgroundColor: LANGUAGE_COLORS[file.language] || undefined }}
                                            />
                                            <Chip
                                                size="sm"
                                                label={file.status}
                                                color={
                                                    file.status === 'accepted'
                                                        ? 'success'
                                                        : file.status === 'rejected'
                                                            ? 'error'
                                                            : 'default'
                                                }
                                                className="h-[20px]"
                                            />
                                        </Box>
                                    }
                                />
                                <Box className="flex gap-1">
                                    <Tooltip title="Accept">
                                        <IconButton
                                            size="sm"
                                            tone="success"
                                            onClick={() => handleAcceptFile(index)}
                                            disabled={file.status === 'accepted'}
                                        >
                                            <CheckIcon size={16} />
                                        </IconButton>
                                    </Tooltip>
                                    <Tooltip title="Reject">
                                        <IconButton
                                            size="sm"
                                            tone="danger"
                                            onClick={() => handleRejectFile(index)}
                                            disabled={file.status === 'rejected'}
                                        >
                                            <RejectIcon size={16} />
                                        </IconButton>
                                    </Tooltip>
                                    <IconButton
                                        size="sm"
                                        onClick={() => toggleFileExpand(index)}
                                    >
                                        {expandedFiles.has(index) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                    </IconButton>
                                </Box>
                            </ListItem>
                            <Collapse in={expandedFiles.has(index)}>
                                <Box className="p-4 bg-gray-50" >
                                    {file.aiExplanation && (
                                        <Alert severity="info" icon={<AIIcon />} className="mb-4">
                                            {file.aiExplanation}
                                        </Alert>
                                    )}
                                    <Box className="relative">
                                        <IconButton
                                            size="sm"
                                            className="absolute top-[8px] right-[8px]"
                                            onClick={() => handleCopyCode(file.generatedContent)}
                                        >
                                            <CopyIcon size={16} />
                                        </IconButton>
                                        <Box
                                            component="pre"
                                            className="p-4 rounded overflow-auto text-sm bg-[#1e1e1e] text-[#d4d4d4] max-h-[400px] font-mono"
                                        >
                                            {file.generatedContent}
                                        </Box>
                                    </Box>
                                </Box>
                            </Collapse>
                        </React.Fragment>
                    ))}
                </List>
            </Paper>

            {/* Navigation */}
            <Box className="flex justify-between gap-4">
                {onBack && (
                    <Button onClick={onBack} disabled={isLoading || isGenerating}>
                        Back
                    </Button>
                )}
                <Box className="grow" />
                <Button
                    startIcon={<RegenerateIcon />}
                    onClick={generateCode}
                    disabled={isLoading || isGenerating}
                >
                    Regenerate
                </Button>
                <Button
                    variant="solid"
                    onClick={handleContinue}
                    disabled={isLoading || isGenerating || acceptedCount === 0}
                    endIcon={<CheckIcon />}
                >
                    Continue ({acceptedCount} files)
                </Button>
            </Box>
        </Box>
    );
};

export default CodeStep;
