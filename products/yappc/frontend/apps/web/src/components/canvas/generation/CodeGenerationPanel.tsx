/**
 * Code Generation Panel Component
 * 
 * UI for generating and managing code artifacts from canvas designs.
 * 
 * @doc.type component
 * @doc.purpose UI for code generation
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useMemo } from 'react';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Chip,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  ListItem,
  ListItemIcon,
  ListItemText,
  Select,
  Stack,
  Switch,
  Tab,
  Tabs,
  Tooltip,
  Typography,
  Spinner as CircularProgress,
  InteractiveList as List,
  Surface as Paper,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { Code as CodeIcon, Download as DownloadIcon, Copy as CopyIcon, Eye as PreviewIcon, CheckCircle as CheckCircleIcon, AlertCircle as ErrorIcon, AlertTriangle as WarningIcon, FileText as FileIcon, Bug as TestIcon, Settings as ConfigIcon, Book as DocsIcon, HardDrive as SchemaIcon, Plug as ApiIcon, Cloud as InfraIcon } from 'lucide-react';
import type { CodeGenerationResult, GeneratedArtifact, ArtifactType } from '../services/canvas/agents/GenerationAgent';
import type { GenerationOptions } from '../services/canvas/agents/GenerationAgent';

// ============================================================================
// Props
// ============================================================================

export interface CodeGenerationPanelProps {
    /** Generation result */
    generationResult: CodeGenerationResult | null;

    /** Is generating */
    isGenerating: boolean;

    /** Can generate */
    canGenerate: boolean;

    /** Errors */
    errors: string[];

    /** Warnings */
    warnings: string[];

    /** Callbacks */
    onGenerate: (options: Partial<GenerationOptions>) => void;
    onDownloadArtifact: (artifactId: string) => void;
    onDownloadAsZip: () => void;
    onPreviewArtifact: (artifactId: string) => void;
    onCopyArtifact: (artifactId: string) => void;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Code Generation Panel
 */
export const CodeGenerationPanel: React.FC<CodeGenerationPanelProps> = ({
    generationResult,
    isGenerating,
    canGenerate,
    errors,
    warnings,
    onGenerate,
    onDownloadArtifact,
    onDownloadAsZip,
    onPreviewArtifact,
    onCopyArtifact,
}) => {
    const [selectedTab, setSelectedTab] = useState(0);
    const [language, setLanguage] = useState<'typescript' | 'java' | 'python' | 'go'>('typescript');
    const [includeTests, setIncludeTests] = useState(true);
    const [includeDocs, setIncludeDocs] = useState(true);
    const [includeConfig, setIncludeConfig] = useState(true);

    /**
     * Handle generate button click
     */
    const handleGenerate = () => {
        onGenerate({
            language,
            includeTests,
            includeDocumentation: includeDocs,
            includeConfiguration: includeConfig,
        });
    };

    /**
     * Group artifacts by type
     */
    const artifactsByType = useMemo(() => {
        if (!generationResult) return {};

        const grouped: Record<ArtifactType, GeneratedArtifact[]> = {
            source: [],
            test: [],
            config: [],
            documentation: [],
            schema: [],
            api: [],
            infrastructure: [],
        };

        for (const artifact of generationResult.artifacts) {
            grouped[artifact.type].push(artifact);
        }

        return grouped;
    }, [generationResult]);

    /**
     * Get icon for artifact type
     */
    const getArtifactIcon = (type: ArtifactType) => {
        switch (type) {
            case 'source': return <CodeIcon />;
            case 'test': return <TestIcon />;
            case 'config': return <ConfigIcon />;
            case 'documentation': return <DocsIcon />;
            case 'schema': return <SchemaIcon />;
            case 'api': return <ApiIcon />;
            case 'infrastructure': return <InfraIcon />;
            default: return <FileIcon />;
        }
    };

    /**
     * Render configuration form
     */
    const renderConfiguration = () => (
        <Box>
            <Typography variant="h6" gutterBottom>
                Generation Options
            </Typography>

            <Stack spacing={2} className="mb-6">
                <FormControl fullWidth>
                    <InputLabel>Language</InputLabel>
                    <Select
                        value={language}
                        label="Language"
                        onChange={(e) => setLanguage(e.target.value as unknown)}
                    >
                        <MenuItem value="typescript">TypeScript</MenuItem>
                        <MenuItem value="java">Java</MenuItem>
                        <MenuItem value="python">Python</MenuItem>
                        <MenuItem value="go">Go</MenuItem>
                    </Select>
                </FormControl>

                <FormControlLabel
                    control={<Switch checked={includeTests} onChange={(e) => setIncludeTests(e.target.checked)} />}
                    label="Include Tests"
                />

                <FormControlLabel
                    control={<Switch checked={includeDocs} onChange={(e) => setIncludeDocs(e.target.checked)} />}
                    label="Include Documentation"
                />

                <FormControlLabel
                    control={<Switch checked={includeConfig} onChange={(e) => setIncludeConfig(e.target.checked)} />}
                    label="Include Configuration"
                />
            </Stack>

            {errors.length > 0 && (
                <Alert severity="error" className="mb-4">
                    <AlertTitle>Cannot Generate</AlertTitle>
                    {errors.map((error, index) => (
                        <Typography key={index} variant="body2">
                            • {error}
                        </Typography>
                    ))}
                </Alert>
            )}

            {warnings.length > 0 && (
                <Alert severity="warning" className="mb-4">
                    {warnings.map((warning, index) => (
                        <Typography key={index} variant="body2">
                            • {warning}
                        </Typography>
                    ))}
                </Alert>
            )}

            <Button
                variant="contained"
                size="large"
                fullWidth
                startIcon={isGenerating ? <CircularProgress size={20} /> : <CodeIcon />}
                onClick={handleGenerate}
                disabled={!canGenerate || isGenerating}
            >
                {isGenerating ? 'Generating Code...' : 'Generate Code'}
            </Button>
        </Box>
    );

    /**
     * Render artifact item
     */
    const renderArtifact = (artifact: GeneratedArtifact) => (
        <ListItem
            key={artifact.id}
            secondaryAction={
                <Stack direction="row" spacing={1}>
                    <Tooltip title="Preview">
                        <IconButton size="small" onClick={() => onPreviewArtifact(artifact.id)}>
                            <PreviewIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Copy to Clipboard">
                        <IconButton size="small" onClick={() => onCopyArtifact(artifact.id)}>
                            <CopyIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Download">
                        <IconButton size="small" onClick={() => onDownloadArtifact(artifact.id)}>
                            <DownloadIcon />
                        </IconButton>
                    </Tooltip>
                </Stack>
            }
            className="mb-2 border-blue-600 border-l-[3px]" >
            <ListItemIcon>{getArtifactIcon(artifact.type)}</ListItemIcon>
            <ListItemText
                primary={artifact.path}
                secondary={
                    <Stack direction="row" spacing={1} alignItems="center">
                        <Chip label={artifact.language} size="small" />
                        {artifact.framework && <Chip label={artifact.framework} size="small" variant="outlined" />}
                        <Typography variant="caption" color="text.secondary">
                            {artifact.content.split('\n').length} lines
                        </Typography>
                    </Stack>
                }
            />
        </ListItem>
    );

    /**
     * Render artifacts by type
     */
    const renderArtifactsByType = (type: ArtifactType) => {
        const artifacts = artifactsByType[type] || [];
        if (artifacts.length === 0) return null;

        return (
            <Box key={type} className="mb-6">
                <Stack direction="row" spacing={1} alignItems="center" className="mb-2">
                    {getArtifactIcon(type)}
                    <Typography variant="subtitle2" textTransform="capitalize">
                        {type} Files ({artifacts.length})
                    </Typography>
                </Stack>
                <List dense>
                    {artifacts.map(renderArtifact)}
                </List>
            </Box>
        );
    };

    /**
     * Render results
     */
    const renderResults = () => {
        if (!generationResult) {
            return (
                <Box textAlign="center" py={4}>
                    <CodeIcon className="mb-4 text-[64px] text-gray-500 dark:text-gray-400" />
                    <Typography variant="body1" color="text.secondary">
                        Configure options and click "Generate Code" to get started
                    </Typography>
                </Box>
            );
        }

        if (!generationResult.success) {
            return (
                <Box>
                    <Alert severity="error">
                        <AlertTitle>Generation Failed</AlertTitle>
                        {generationResult.errors.map((error, index) => (
                            <Typography key={index} variant="body2">
                                • {error}
                            </Typography>
                        ))}
                    </Alert>
                </Box>
            );
        }

        return (
            <Box>
                {/* Summary */}
                <Paper variant="outlined" className="p-4 mb-6">
                    <Stack direction="row" spacing={2} alignItems="center" mb={2}>
                        <CheckCircleIcon color="success" className="text-[32px]" />
                        <Box flex={1}>
                            <Typography variant="h6">Generation Complete</Typography>
                            <Typography variant="body2" color="text.secondary">
                                {generationResult.summary}
                            </Typography>
                        </Box>
                    </Stack>

                    <Stack direction="row" spacing={2} mb={2}>
                        <Chip
                            icon={<FileIcon />}
                            label={`${generationResult.statistics.totalFiles} Files`}
                            color="primary"
                        />
                        <Chip
                            icon={<CodeIcon />}
                            label={`${generationResult.statistics.totalLines} Lines`}
                            color="primary"
                            variant="outlined"
                        />
                    </Stack>

                    <Button
                        variant="contained"
                        startIcon={<DownloadIcon />}
                        onClick={onDownloadAsZip}
                        fullWidth
                    >
                        Download All as ZIP
                    </Button>
                </Paper>

                {/* Warnings */}
                {generationResult.warnings.length > 0 && (
                    <Alert severity="warning" className="mb-4">
                        {generationResult.warnings.map((warning, index) => (
                            <Typography key={index} variant="body2">
                                • {warning}
                            </Typography>
                        ))}
                    </Alert>
                )}

                {/* Artifacts by type */}
                {renderArtifactsByType('source')}
                {renderArtifactsByType('test')}
                {renderArtifactsByType('config')}
                {renderArtifactsByType('schema')}
                {renderArtifactsByType('api')}
                {renderArtifactsByType('documentation')}
                {renderArtifactsByType('infrastructure')}

                {generationResult.artifacts.length === 0 && (
                    <Box textAlign="center" py={4}>
                        <WarningIcon className="mb-4 text-5xl text-amber-600" />
                        <Typography color="text.secondary">
                            No artifacts generated
                        </Typography>
                    </Box>
                )}
            </Box>
        );
    };

    /**
     * Main render
     */
    return (
        <Paper className="h-full flex flex-col">
            {/* Header */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                <Typography variant="h6">Code Generation</Typography>
                <Typography variant="caption" color="text.secondary">
                    Generate production-ready code from your canvas design
                </Typography>
            </Box>

            {/* Tabs */}
            <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)} className="px-4">
                <Tab label="Configure" />
                <Tab label="Results" disabled={!generationResult} />
            </Tabs>

            <Divider />

            {/* Content */}
            <Box className="flex-1 overflow-auto p-4">
                {selectedTab === 0 && renderConfiguration()}
                {selectedTab === 1 && renderResults()}
            </Box>
        </Paper>
    );
};
