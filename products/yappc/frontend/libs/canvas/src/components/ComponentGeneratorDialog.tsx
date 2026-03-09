/**
 * @doc.type component
 * @doc.purpose Component generator dialog for Journey 7.1 (Frontend Engineer - Component Development)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, MenuItem, FormControlLabel, Checkbox, Box, Typography, Tabs, Tab, IconButton, InteractiveList as List, ListItem, ListItemText, ListItemText as ListItemSecondaryAction, Chip, Alert, Spinner as CircularProgress, Surface as Paper, Divider } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, Code as CodeIcon, FileText as FileIcon, Download as DownloadIcon, Copy as CopyIcon } from 'lucide-react';
import {
    useComponentGeneration,
    type UIFramework,
    type StylingApproach,
    type ComponentGenerationOptions,
} from '../hooks/useComponentGeneration';
import type { ComponentProp } from '../services/StorybookService';
import type { Node } from '@xyflow/react';

/**
 * Component props
 */
export interface ComponentGeneratorDialogProps {
    /**
     * Dialog open state
     */
    open: boolean;

    /**
     * Close handler
     */
    onClose: () => void;

    /**
     * Selected wireframe node
     */
    node: Node | null;

    /**
     * Generation complete callback
     */
    onGenerated?: (node: Node, files: { component: string; test?: string; story?: string }) => void;
}

/**
 * Tab panel component
 */
interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
    <div role="tabpanel" hidden={value !== index} style={{ height: '100%' }}>
        {value === index && <Box className="p-6 h-full">{children}</Box>}
    </div>
);

/**
 * Component Generator Dialog
 * 
 * Modal for converting wireframe nodes to React components with TypeScript,
 * Tailwind CSS, tests, and Storybook stories.
 * 
 * @example
 * ```tsx
 * <ComponentGeneratorDialog
 *   open={open}
 *   onClose={handleClose}
 *   node={selectedNode}
 *   onGenerated={(node, files) => console.log('Generated:', files)}
 * />
 * ```
 */
export const ComponentGeneratorDialog: React.FC<ComponentGeneratorDialogProps> = ({
    open,
    onClose,
    node,
    onGenerated,
}) => {
    const {
        generateComponent,
        isGenerating,
        error,
        lastGenerated,
        downloadFiles,
        copyToClipboard,
    } = useComponentGeneration();

    const [activeTab, setActiveTab] = useState(0);
    const [framework, setFramework] = useState<UIFramework>('react');
    const [styling, setStyling] = useState<StylingApproach>('tailwind');
    const [typescript, setTypescript] = useState(true);
    const [includeTests, setIncludeTests] = useState(true);
    const [includeStorybook, setIncludeStorybook] = useState(true);
    const [accessible, setAccessible] = useState(true);
    const [responsive, setResponsive] = useState(true);
    const [props, setProps] = useState<ComponentProp[]>([]);
    const [newPropName, setNewPropName] = useState('');
    const [newPropType, setNewPropType] = useState('string');
    const [newPropRequired, setNewPropRequired] = useState(true);
    const [copySuccess, setCopySuccess] = useState(false);

    // Component name from node
    const componentName = useMemo(() => {
        return node?.data.label || node?.data.name || 'Component';
    }, [node]);

    // Reset state when dialog closes
    React.useEffect(() => {
        if (!open) {
            setActiveTab(0);
            setCopySuccess(false);
        }
    }, [open]);

    // Load props from node data
    React.useEffect(() => {
        if (node?.data.props) {
            setProps(node.data.props);
        } else {
            setProps([]);
        }
    }, [node]);

    // Add prop
    const handleAddProp = useCallback(() => {
        if (!newPropName.trim()) return;

        setProps(prev => [
            ...prev,
            {
                name: newPropName,
                type: newPropType,
                required: newPropRequired,
            },
        ]);

        setNewPropName('');
        setNewPropType('string');
        setNewPropRequired(true);
    }, [newPropName, newPropType, newPropRequired]);

    // Remove prop
    const handleRemoveProp = useCallback((index: number) => {
        setProps(prev => prev.filter((_, i) => i !== index));
    }, []);

    // Generate component
    const handleGenerate = useCallback(async () => {
        if (!node) return;

        const options: ComponentGenerationOptions = {
            framework,
            styling,
            typescript,
            props,
            includeTests,
            includeStorybook,
            accessible,
            responsive,
        };

        try {
            const result = await generateComponent(node, options);

            if (onGenerated) {
                onGenerated(node, {
                    component: result.componentFile.content,
                    test: result.testFile?.content,
                    story: result.storyFile?.content,
                });
            }

            setActiveTab(1); // Switch to preview tab
        } catch (err) {
            console.error('Generation failed:', err);
        }
    }, [node, framework, styling, typescript, props, includeTests, includeStorybook, accessible, responsive, generateComponent, onGenerated]);

    // Download all files
    const handleDownload = useCallback(() => {
        if (lastGenerated) {
            downloadFiles(lastGenerated);
        }
    }, [lastGenerated, downloadFiles]);

    // Copy to clipboard
    const handleCopy = useCallback(async (content: string) => {
        try {
            await copyToClipboard(content);
            setCopySuccess(true);
            setTimeout(() => setCopySuccess(false), 2000);
        } catch (err) {
            console.error('Copy failed:', err);
        }
    }, [copyToClipboard]);

    return (
        <Dialog open={open} onClose={onClose} size="lg" fullWidth>
            <DialogTitle>
                <Box className="flex justify-between items-center">
                    <Typography as="h6">Convert to React Component</Typography>
                    <Chip label={componentName} tone="primary" />
                </Box>
            </DialogTitle>

            <DialogContent className="h-[600px]">
                <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
                    <Tab label="Configuration" icon={<CodeIcon />} iconPosition="start" />
                    <Tab label="Preview" icon={<FileIcon />} iconPosition="start" disabled={!lastGenerated} />
                    <Tab label="Files" icon={<DownloadIcon />} iconPosition="start" disabled={!lastGenerated} />
                </Tabs>

                {/* Configuration Tab */}
                <TabPanel value={activeTab} index={0}>
                    <Box className="grid gap-4">
                        {/* Framework & Styling */}
                        <Box className="grid gap-4 grid-cols-2">
                            <TextField
                                select
                                label="UI Framework"
                                value={framework}
                                onChange={e => setFramework(e.target.value as UIFramework)}
                                fullWidth
                            >
                                <MenuItem value="react">React</MenuItem>
                                <MenuItem value="vue" disabled>Vue (Coming Soon)</MenuItem>
                                <MenuItem value="angular" disabled>Angular (Coming Soon)</MenuItem>
                                <MenuItem value="svelte" disabled>Svelte (Coming Soon)</MenuItem>
                            </TextField>

                            <TextField
                                select
                                label="Styling Approach"
                                value={styling}
                                onChange={e => setStyling(e.target.value as StylingApproach)}
                                fullWidth
                            >
                                <MenuItem value="tailwind">Tailwind CSS</MenuItem>
                                <MenuItem value="css-modules">CSS Modules</MenuItem>
                                <MenuItem value="styled-components">Styled Components</MenuItem>
                                <MenuItem value="emotion">Emotion</MenuItem>
                                <MenuItem value="sass">Sass</MenuItem>
                            </TextField>
                        </Box>

                        {/* Options */}
                        <Box>
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Generation Options
                            </Typography>
                            <Box className="grid gap-2 grid-cols-3">
                                <FormControlLabel
                                    control={<Checkbox checked={typescript} onChange={e => setTypescript(e.target.checked)} />}
                                    label="TypeScript"
                                />
                                <FormControlLabel
                                    control={<Checkbox checked={includeTests} onChange={e => setIncludeTests(e.target.checked)} />}
                                    label="Generate Tests"
                                />
                                <FormControlLabel
                                    control={<Checkbox checked={includeStorybook} onChange={e => setIncludeStorybook(e.target.checked)} />}
                                    label="Storybook Stories"
                                />
                                <FormControlLabel
                                    control={<Checkbox checked={accessible} onChange={e => setAccessible(e.target.checked)} />}
                                    label="Accessible (WCAG)"
                                />
                                <FormControlLabel
                                    control={<Checkbox checked={responsive} onChange={e => setResponsive(e.target.checked)} />}
                                    label="Responsive"
                                />
                            </Box>
                        </Box>

                        <Divider />

                        {/* Props Editor */}
                        <Box>
                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                Component Props
                            </Typography>

                            {/* Add Prop */}
                            <Box className="flex gap-2 mb-4">
                                <TextField
                                    label="Prop Name"
                                    value={newPropName}
                                    onChange={e => setNewPropName(e.target.value)}
                                    size="sm"
                                    style={{ flex: 2 }}
                                    placeholder="e.g., userName"
                                />
                                <TextField
                                    select
                                    label="Type"
                                    value={newPropType}
                                    onChange={e => setNewPropType(e.target.value)}
                                    size="sm"
                                    className="flex-1"
                                >
                                    <MenuItem value="string">string</MenuItem>
                                    <MenuItem value="number">number</MenuItem>
                                    <MenuItem value="boolean">boolean</MenuItem>
                                    <MenuItem value="User">User</MenuItem>
                                    <MenuItem value="string[]">string[]</MenuItem>
                                    <MenuItem value="() => void">() =&gt; void</MenuItem>
                                    <MenuItem value="'primary' | 'secondary'">Union Type</MenuItem>
                                </TextField>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={newPropRequired}
                                            onChange={e => setNewPropRequired(e.target.checked)}
                                            size="sm"
                                        />
                                    }
                                    label="Required"
                                />
                                <IconButton onClick={handleAddProp} tone="primary">
                                    <AddIcon />
                                </IconButton>
                            </Box>

                            {/* Props List */}
                            <Paper variant="outlined" className="overflow-auto max-h-[200px]">
                                {props.length === 0 ? (
                                    <Box className="p-4 text-center text-gray-500 dark:text-gray-400">
                                        No props defined. Add props above.
                                    </Box>
                                ) : (
                                    <List dense>
                                        {props.map((prop, index) => (
                                            <ListItem key={index}>
                                                <ListItemText
                                                    primary={
                                                        <Box className="flex gap-2 items-center">
                                                            <Typography as="p" className="text-sm" fontWeight="bold">
                                                                {prop.name}
                                                            </Typography>
                                                            <Chip label={prop.type} size="sm" />
                                                            {prop.required && <Chip label="required" size="sm" tone="danger" />}
                                                        </Box>
                                                    }
                                                    secondary={prop.description}
                                                />
                                                <ListItemSecondaryAction>
                                                    <IconButton edge="end" onClick={() => handleRemoveProp(index)} size="sm">
                                                        <DeleteIcon />
                                                    </IconButton>
                                                </ListItemSecondaryAction>
                                            </ListItem>
                                        ))}
                                    </List>
                                )}
                            </Paper>
                        </Box>

                        {/* Error Display */}
                        {error && (
                            <Alert severity="error" onClose={() => { }}>
                                {error}
                            </Alert>
                        )}
                    </Box>
                </TabPanel>

                {/* Preview Tab */}
                <TabPanel value={activeTab} index={1}>
                    {lastGenerated && (
                        <Box className="h-full flex flex-col">
                            <Box className="flex justify-between mb-4">
                                <Typography as="p" className="text-sm font-medium">
                                    {lastGenerated.componentFile.filename}
                                </Typography>
                                <Button
                                    startIcon={<CopyIcon />}
                                    onClick={() => handleCopy(lastGenerated.componentFile.content)}
                                    size="sm"
                                >
                                    {copySuccess ? 'Copied!' : 'Copy'}
                                </Button>
                            </Box>
                            <Paper
                                variant="outlined"
                                className="flex-1 overflow-auto p-4 text-sm bg-[#f6f8fa] font-mono leading-relaxed"
                            >
                                <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                                    <code>{lastGenerated.componentFile.content}</code>
                                </pre>
                            </Paper>
                        </Box>
                    )}
                </TabPanel>

                {/* Files Tab */}
                <TabPanel value={activeTab} index={2}>
                    {lastGenerated && (
                        <Box>
                            <Box className="flex justify-between mb-4">
                                <Typography as="p" className="text-sm font-medium">Generated Files</Typography>
                                <Button
                                    startIcon={<DownloadIcon />}
                                    onClick={handleDownload}
                                    variant="solid"
                                >
                                    Download All
                                </Button>
                            </Box>

                            <List>
                                <ListItem>
                                    <ListItemText
                                        primary={lastGenerated.componentFile.filename}
                                        secondary="Main component file"
                                    />
                                    <ListItemSecondaryAction>
                                        <IconButton
                                            edge="end"
                                            onClick={() => handleCopy(lastGenerated.componentFile.content)}
                                        >
                                            <CopyIcon />
                                        </IconButton>
                                    </ListItemSecondaryAction>
                                </ListItem>

                                {lastGenerated.testFile && (
                                    <ListItem>
                                        <ListItemText
                                            primary={lastGenerated.testFile.filename}
                                            secondary="Unit tests"
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                onClick={() => handleCopy(lastGenerated.testFile!.content)}
                                            >
                                                <CopyIcon />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                )}

                                {lastGenerated.storyFile && (
                                    <ListItem>
                                        <ListItemText
                                            primary={lastGenerated.storyFile.filename}
                                            secondary="Storybook stories"
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                onClick={() => handleCopy(lastGenerated.storyFile!.content)}
                                            >
                                                <CopyIcon />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                )}

                                {lastGenerated.styleFile && (
                                    <ListItem>
                                        <ListItemText
                                            primary={lastGenerated.styleFile.filename}
                                            secondary="Style file"
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                onClick={() => handleCopy(lastGenerated.styleFile!.content)}
                                            >
                                                <CopyIcon />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                )}
                            </List>
                        </Box>
                    )}
                </TabPanel>
            </DialogContent>

            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant="solid"
                    onClick={handleGenerate}
                    disabled={isGenerating || props.length === 0}
                    startIcon={isGenerating ? <CircularProgress size={20} /> : <CodeIcon />}
                >
                    {isGenerating ? 'Generating...' : 'Generate Component'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
