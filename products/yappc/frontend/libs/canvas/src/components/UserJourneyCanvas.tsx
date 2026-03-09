/**
 * UserJourneyCanvas Component
 * 
 * User journey mapping canvas for UX research (Journey 19.1)
 * 
 * Features:
 * - Journey stages with touchpoints
 * - Pain points and emotions tracking
 * - User quotes and insights
 * - Transcript analysis with AI
 * - Heatmap visualization
 * - Export journey maps
 * 
 * @doc.type component
 * @doc.purpose User journey mapping for UX research
 * @doc.layer product
 * @doc.pattern Canvas
 */

import React, { useState } from 'react';
import { Box, Surface as Paper, Typography, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Select, MenuItem, FormControl, InputLabel, IconButton, InteractiveList as List, ListItem, ListItemText, ListItemButton, Chip, Alert, Divider, Collapse, Tooltip, Stack } from '@ghatana/ui';
import { Plus as AddIcon, Settings as SettingsIcon, Trash2 as DeleteIcon, Upload as UploadIcon, Download as DownloadIcon, Share2 as ShareIcon, TrendingDown as PainIcon, TrendingUp as DelightIcon, Annoyed as SentimentVeryDissatisfied, Frown as SentimentDissatisfied, Meh as SentimentNeutral, Smile as SentimentSatisfied, Laugh as SentimentVerySatisfied, Pointer as TouchpointIcon, Brain as InsightIcon, Quote as QuoteIcon, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import type { Node } from '@xyflow/react';
import { useUserJourney } from '../hooks/useUserJourney';
import type {
    JourneyStage,
    JourneyTouchpoint,
    JourneyPainPoint,
    JourneyEmotion,
    UserQuote,
    EmotionType,
} from '../hooks/useUserJourney';

/**
 * Props for UserJourneyCanvas
 */
export interface UserJourneyCanvasProps {
    /**
     * Initial journey name
     */
    initialJourneyName?: string;

    /**
     * Initial persona
     */
    initialPersona?: string;

    /**
     * Callback when journey is exported
     */
    onExport?: (journey: string) => void;

    /**
     * Callback when journey is shared
     */
    onShare?: (journey: string) => void;
}

// Emotion type definitions
const EMOTION_TYPES: Array<{ value: EmotionType; label: string; icon: React.ReactElement; color: string }> = [
    { value: 'very-negative', label: 'Very Frustrated', icon: <SentimentVeryDissatisfied />, color: '#d32f2f' },
    { value: 'negative', label: 'Frustrated', icon: <SentimentDissatisfied />, color: '#f57c00' },
    { value: 'neutral', label: 'Neutral', icon: <SentimentNeutral />, color: '#fbc02d' },
    { value: 'positive', label: 'Satisfied', icon: <SentimentSatisfied />, color: '#7cb342' },
    { value: 'very-positive', label: 'Delighted', icon: <SentimentVerySatisfied />, color: '#388e3c' },
];

/**
 * UserJourneyCanvas Component
 */
export const UserJourneyCanvas: React.FC<UserJourneyCanvasProps> = ({
    initialJourneyName = 'Customer Journey',
    initialPersona = 'End User',
    onExport,
    onShare,
}) => {
    const journey = useUserJourney({
        initialJourneyName,
        initialPersona,
    });

    // UI State
    const [configDialogOpen, setConfigDialogOpen] = useState(false);
    const [touchpointDialogOpen, setTouchpointDialogOpen] = useState(false);
    const [painPointDialogOpen, setPainPointDialogOpen] = useState(false);
    const [quoteDialogOpen, setQuoteDialogOpen] = useState(false);
    const [transcriptDialogOpen, setTranscriptDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [libraryExpanded, setLibraryExpanded] = useState(true);

    // Config Dialog State
    const [stageName, setStageName] = useState('');
    const [stageDescription, setStageDescription] = useState('');

    // Touchpoint Dialog State
    const [touchpointStage, setTouchpointStage] = useState('');
    const [touchpointName, setTouchpointName] = useState('');
    const [touchpointType, setTouchpointType] = useState<'digital' | 'physical' | 'human'>('digital');
    const [touchpointChannel, setTouchpointChannel] = useState('');

    // Pain Point Dialog State
    const [painPointStage, setPainPointStage] = useState('');
    const [painPointDescription, setPainPointDescription] = useState('');
    const [painPointSeverity, setPainPointSeverity] = useState<1 | 2 | 3>(2);
    const [painPointCategory, setPainPointCategory] = useState('');

    // Quote Dialog State
    const [quoteStage, setQuoteStage] = useState('');
    const [quoteText, setQuoteText] = useState('');
    const [quoteSource, setQuoteSource] = useState('');

    // Transcript Analysis State
    const [transcript, setTranscript] = useState('');
    const [analyzing, setAnalyzing] = useState(false);

    // Handlers
    const handleAddStage = () => {
        if (stageName.trim()) {
            journey.addStage(stageName.trim(), stageDescription.trim());
            setStageName('');
            setStageDescription('');
            setConfigDialogOpen(false);
        }
    };

    const handleAddTouchpoint = () => {
        if (touchpointStage && touchpointName.trim()) {
            journey.addTouchpoint(touchpointStage, {
                name: touchpointName.trim(),
                type: touchpointType,
                channel: touchpointChannel.trim() || undefined,
            });
            setTouchpointName('');
            setTouchpointChannel('');
            setTouchpointDialogOpen(false);
        }
    };

    const handleAddPainPoint = () => {
        if (painPointStage && painPointDescription.trim()) {
            journey.addPainPoint(painPointStage, {
                description: painPointDescription.trim(),
                severity: painPointSeverity,
                category: painPointCategory.trim() || undefined,
            });
            setPainPointDescription('');
            setPainPointCategory('');
            setPainPointDialogOpen(false);
        }
    };

    const handleAddQuote = () => {
        if (quoteStage && quoteText.trim()) {
            journey.addUserQuote(quoteStage, {
                text: quoteText.trim(),
                source: quoteSource.trim() || undefined,
            });
            setQuoteText('');
            setQuoteSource('');
            setQuoteDialogOpen(false);
        }
    };

    const handleAnalyzeTranscript = async () => {
        if (!transcript.trim()) return;

        setAnalyzing(true);
        try {
            await journey.analyzeTranscript(transcript);
            setTranscript('');
            setTranscriptDialogOpen(false);
        } finally {
            setAnalyzing(false);
        }
    };

    const handleExport = () => {
        const exported = journey.exportJourney();
        if (onExport) {
            onExport(exported);
        }
        // Copy to clipboard
        navigator.clipboard.writeText(exported);
        setExportDialogOpen(false);
    };

    const handleShare = () => {
        const exported = journey.exportJourney();
        if (onShare) {
            onShare(exported);
        }
    };

    const getEmotionConfig = (emotion: EmotionType) => {
        return EMOTION_TYPES.find(e => e.value === emotion) || EMOTION_TYPES[2];
    };

    const calculateEmotionAverage = (stage: JourneyStage): number => {
        if (!stage.emotions || stage.emotions.length === 0) return 0;
        const emotionValues = { 'very-negative': 1, 'negative': 2, 'neutral': 3, 'positive': 4, 'very-positive': 5 };
        const sum = stage.emotions.reduce((acc, e) => acc + emotionValues[e.type], 0);
        return sum / stage.emotions.length;
    };

    return (
        <Box className="flex h-screen bg-gray-50 dark:bg-gray-950">
            {/* Left Sidebar - Component Library */}
            <Paper
                elevation={2}
                className="flex flex-col w-[280px] rounded-none border-r border-gray-200 dark:border-gray-700"
            >
                <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex items-center justify-between">
                        <Typography as="h6">Journey Elements</Typography>
                        <IconButton size="sm" onClick={() => setLibraryExpanded(!libraryExpanded)}>
                            {libraryExpanded ? <ExpandLess /> : <ExpandMore />}
                        </IconButton>
                    </Box>
                </Box>

                <Collapse in={libraryExpanded}>
                    <List className="p-2">
                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setConfigDialogOpen(true)}>
                                <AddIcon className="mr-2 text-blue-600" />
                                <ListItemText primary="Add Stage" secondary="Journey phase" />
                            </ListItemButton>
                        </ListItem>

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setTouchpointDialogOpen(true)}>
                                <TouchpointIcon className="mr-2 text-sky-600" />
                                <ListItemText primary="Add Touchpoint" secondary="Interaction point" />
                            </ListItemButton>
                        </ListItem>

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setPainPointDialogOpen(true)}>
                                <PainIcon className="mr-2 text-red-600" />
                                <ListItemText primary="Add Pain Point" secondary="User frustration" />
                            </ListItemButton>
                        </ListItem>

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setQuoteDialogOpen(true)}>
                                <QuoteIcon className="mr-2 text-green-600" />
                                <ListItemText primary="Add Quote" secondary="User feedback" />
                            </ListItemButton>
                        </ListItem>

                        <Divider className="my-2" />

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setTranscriptDialogOpen(true)}>
                                <InsightIcon className="mr-2 text-indigo-600" />
                                <ListItemText primary="Analyze Transcript" secondary="AI insights" />
                            </ListItemButton>
                        </ListItem>
                    </List>
                </Collapse>
            </Paper>

            {/* Main Canvas Area */}
            <Box className="flex-1 flex flex-col">
                {/* Toolbar */}
                <Paper variant="raised" className="p-4 rounded-none border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex items-center gap-4 flex-wrap">
                        <TextField
                            size="sm"
                            label="Journey Name"
                            value={journey.journeyName}
                            onChange={(e) => journey.setJourneyName(e.target.value)}
                            className="w-[300px]"
                        />

                        <TextField
                            size="sm"
                            label="Persona"
                            value={journey.persona}
                            onChange={(e) => journey.setPersona(e.target.value)}
                            className="w-[200px]"
                        />

                        <Box className="flex items-center">
                            <Chip label={`${journey.stages.length} Stages`} size="sm" className="mr-2" />
                            <Chip label={`${journey.getTouchpointCount()} Touchpoints`} size="sm" className="mr-2" />
                            <Chip label={`${journey.getPainPointCount()} Pain Points`} size="sm" tone="danger" />
                        </Box>

                        <Box className="flex items-center ml-auto">
                            <Tooltip title="Export Journey">
                                <IconButton onClick={() => setExportDialogOpen(true)}>
                                    <DownloadIcon />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Share Journey">
                                <IconButton onClick={handleShare}>
                                    <ShareIcon />
                                </IconButton>
                            </Tooltip>
                        </Box>
                    </Box>
                </Paper>

                {/* Journey Visualization */}
                <Box className="flex-1 overflow-auto p-6">
                    {journey.stages.length === 0 ? (
                        <Alert severity="info" className="mt-8 max-w-[600px] mx-auto">
                            <Typography as="p" gutterBottom>
                                <strong>Start mapping your user journey</strong>
                            </Typography>
                            <Typography as="p" className="text-sm">
                                Click "Add Stage" in the left sidebar to create journey phases like Awareness, Consideration, Purchase, etc.
                            </Typography>
                        </Alert>
                    ) : (
                        <Box>
                            {journey.stages.map((stage, index) => {
                                const emotionAvg = calculateEmotionAverage(stage);
                                const emotionColor = emotionAvg === 0 ? '#9e9e9e' :
                                    emotionAvg < 2 ? '#d32f2f' :
                                        emotionAvg < 3 ? '#f57c00' :
                                            emotionAvg < 4 ? '#fbc02d' :
                                                emotionAvg < 4.5 ? '#7cb342' : '#388e3c';

                                return (
                                    <Paper
                                        key={stage.id}
                                        elevation={2}
                                        className="mb-6 p-4 relative" style={{ borderLeft: '4px solid', borderColor: 'emotionColor' }} >
                                        {/* Stage Header */}
                                        <Box className="flex items-center justify-between mb-4">
                                            <Box>
                                                <Typography as="h6">
                                                    {index + 1}. {stage.name}
                                                </Typography>
                                                {stage.description && (
                                                    <Typography as="p" className="text-sm" color="text.secondary">
                                                        {stage.description}
                                                    </Typography>
                                                )}
                                            </Box>
                                            <Box>
                                                <IconButton
                                                    size="sm"
                                                    onClick={() => journey.deleteStage(stage.id)}
                                                    tone="danger"
                                                >
                                                    <DeleteIcon />
                                                </IconButton>
                                            </Box>
                                        </Box>

                                        {/* Touchpoints */}
                                        {stage.touchpoints && stage.touchpoints.length > 0 && (
                                            <Box className="mb-4">
                                                <Typography as="p" className="text-sm font-medium" color="info.main" gutterBottom>
                                                    <TouchpointIcon size={16} className="mr-1 align-middle" />
                                                    Touchpoints ({stage.touchpoints.length})
                                                </Typography>
                                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                                    {stage.touchpoints.map((tp) => (
                                                        <Chip
                                                            key={tp.id}
                                                            label={`${tp.name} ${tp.channel ? `(${tp.channel})` : ''}`}
                                                            size="sm"
                                                            tone="info"
                                                            variant="outlined"
                                                            onDelete={() => journey.deleteTouchpoint(stage.id, tp.id)}
                                                        />
                                                    ))}
                                                </Stack>
                                            </Box>
                                        )}

                                        {/* Pain Points */}
                                        {stage.painPoints && stage.painPoints.length > 0 && (
                                            <Box className="mb-4">
                                                <Typography as="p" className="text-sm font-medium" color="error.main" gutterBottom>
                                                    <PainIcon size={16} className="mr-1 align-middle" />
                                                    Pain Points ({stage.painPoints.length})
                                                </Typography>
                                                <List dense>
                                                    {stage.painPoints.map((pp) => (
                                                        <ListItem
                                                            key={pp.id}
                                                            className="mb-1 rounded bg-red-100 dark:bg-red-900/30 opacity-[0.9]"
                                                            secondaryAction={
                                                                <IconButton
                                                                    edge="end"
                                                                    size="sm"
                                                                    onClick={() => journey.deletePainPoint(stage.id, pp.id)}
                                                                >
                                                                    <DeleteIcon size={16} />
                                                                </IconButton>
                                                            }
                                                        >
                                                            <ListItemText
                                                                primary={pp.description}
                                                                secondary={`Severity: ${'⭐'.repeat(pp.severity)}${pp.category ? ` | ${pp.category}` : ''}`}
                                                            />
                                                        </ListItem>
                                                    ))}
                                                </List>
                                            </Box>
                                        )}

                                        {/* Emotions */}
                                        {stage.emotions && stage.emotions.length > 0 && (
                                            <Box className="mb-4">
                                                <Typography as="p" className="text-sm font-medium" gutterBottom>
                                                    Emotions ({stage.emotions.length})
                                                </Typography>
                                                <Stack direction="row" spacing={1}>
                                                    {stage.emotions.map((emotion) => {
                                                        const config = getEmotionConfig(emotion.type);
                                                        return (
                                                            <Chip
                                                                key={emotion.id}
                                                                icon={config.icon}
                                                                label={config.label}
                                                                size="sm"
                                                                className="text-white [&_.MuiChip-icon]:text-white" style={{ backgroundColor: config.color }}
                                                                onDelete={() => journey.deleteEmotion(stage.id, emotion.id)}
                                                            />
                                                        );
                                                    })}
                                                </Stack>
                                            </Box>
                                        )}

                                        {/* User Quotes */}
                                        {stage.userQuotes && stage.userQuotes.length > 0 && (
                                            <Box>
                                                <Typography as="p" className="text-sm font-medium" color="success.main" gutterBottom>
                                                    <QuoteIcon size={16} className="mr-1 align-middle" />
                                                    User Quotes ({stage.userQuotes.length})
                                                </Typography>
                                                <List dense>
                                                    {stage.userQuotes.map((quote) => (
                                                        <ListItem
                                                            key={quote.id}
                                                            className="mb-1 rounded bg-green-100 dark:bg-green-900/30 opacity-[0.9]"
                                                            secondaryAction={
                                                                <IconButton
                                                                    edge="end"
                                                                    size="sm"
                                                                    onClick={() => journey.deleteUserQuote(stage.id, quote.id)}
                                                                >
                                                                    <DeleteIcon size={16} />
                                                                </IconButton>
                                                            }
                                                        >
                                                            <ListItemText
                                                                primary={`"${quote.text}"`}
                                                                secondary={quote.source ? `— ${quote.source}` : undefined}
                                                            />
                                                        </ListItem>
                                                    ))}
                                                </List>
                                            </Box>
                                        )}
                                    </Paper>
                                );
                            })}
                        </Box>
                    )}
                </Box>
            </Box>

            {/* Add Stage Dialog */}
            <Dialog open={configDialogOpen} onClose={() => setConfigDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Journey Stage</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        margin="dense"
                        label="Stage Name"
                        fullWidth
                        value={stageName}
                        onChange={(e) => setStageName(e.target.value)}
                        placeholder="e.g., Awareness, Consideration, Purchase"
                    />
                    <TextField
                        margin="dense"
                        label="Description"
                        fullWidth
                        multiline
                        rows={2}
                        value={stageDescription}
                        onChange={(e) => setStageDescription(e.target.value)}
                        placeholder="Optional description of this stage"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConfigDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddStage} variant="solid" disabled={!stageName.trim()}>
                        Add Stage
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Touchpoint Dialog */}
            <Dialog open={touchpointDialogOpen} onClose={() => setTouchpointDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Touchpoint</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Stage</InputLabel>
                        <Select
                            value={touchpointStage}
                            onChange={(e) => setTouchpointStage(e.target.value)}
                            label="Stage"
                        >
                            {journey.stages.map((stage) => (
                                <MenuItem key={stage.id} value={stage.id}>
                                    {stage.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Touchpoint Name"
                        fullWidth
                        value={touchpointName}
                        onChange={(e) => setTouchpointName(e.target.value)}
                        placeholder="e.g., Homepage, Email, Customer Service"
                    />
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Type</InputLabel>
                        <Select
                            value={touchpointType}
                            onChange={(e) => setTouchpointType(e.target.value as unknown)}
                            label="Type"
                        >
                            <MenuItem value="digital">Digital</MenuItem>
                            <MenuItem value="physical">Physical</MenuItem>
                            <MenuItem value="human">Human</MenuItem>
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Channel"
                        fullWidth
                        value={touchpointChannel}
                        onChange={(e) => setTouchpointChannel(e.target.value)}
                        placeholder="e.g., Website, Store, Phone"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setTouchpointDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAddTouchpoint}
                        variant="solid"
                        disabled={!touchpointStage || !touchpointName.trim()}
                    >
                        Add Touchpoint
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Pain Point Dialog */}
            <Dialog open={painPointDialogOpen} onClose={() => setPainPointDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Pain Point</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Stage</InputLabel>
                        <Select
                            value={painPointStage}
                            onChange={(e) => setPainPointStage(e.target.value)}
                            label="Stage"
                        >
                            {journey.stages.map((stage) => (
                                <MenuItem key={stage.id} value={stage.id}>
                                    {stage.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Description"
                        fullWidth
                        multiline
                        rows={3}
                        value={painPointDescription}
                        onChange={(e) => setPainPointDescription(e.target.value)}
                        placeholder="Describe the pain point or frustration"
                    />
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Severity</InputLabel>
                        <Select
                            value={painPointSeverity}
                            onChange={(e) => setPainPointSeverity(e.target.value as unknown)}
                            label="Severity"
                        >
                            <MenuItem value={1}>Low (⭐)</MenuItem>
                            <MenuItem value={2}>Medium (⭐⭐)</MenuItem>
                            <MenuItem value={3}>High (⭐⭐⭐)</MenuItem>
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Category"
                        fullWidth
                        value={painPointCategory}
                        onChange={(e) => setPainPointCategory(e.target.value)}
                        placeholder="e.g., Usability, Performance, Trust"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setPainPointDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAddPainPoint}
                        variant="solid"
                        disabled={!painPointStage || !painPointDescription.trim()}
                    >
                        Add Pain Point
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Quote Dialog */}
            <Dialog open={quoteDialogOpen} onClose={() => setQuoteDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add User Quote</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Stage</InputLabel>
                        <Select
                            value={quoteStage}
                            onChange={(e) => setQuoteStage(e.target.value)}
                            label="Stage"
                        >
                            {journey.stages.map((stage) => (
                                <MenuItem key={stage.id} value={stage.id}>
                                    {stage.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Quote"
                        fullWidth
                        multiline
                        rows={3}
                        value={quoteText}
                        onChange={(e) => setQuoteText(e.target.value)}
                        placeholder="Enter user feedback or quote"
                    />
                    <TextField
                        margin="dense"
                        label="Source"
                        fullWidth
                        value={quoteSource}
                        onChange={(e) => setQuoteSource(e.target.value)}
                        placeholder="e.g., User Interview #3, Survey Response"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setQuoteDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAddQuote}
                        variant="solid"
                        disabled={!quoteStage || !quoteText.trim()}
                    >
                        Add Quote
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Transcript Analysis Dialog */}
            <Dialog open={transcriptDialogOpen} onClose={() => setTranscriptDialogOpen(false)} size="md" fullWidth>
                <DialogTitle>Analyze Interview Transcript</DialogTitle>
                <DialogContent>
                    <Alert severity="info" className="mb-4">
                        Paste user interview transcript. AI will extract pain points, emotions, and quotes automatically.
                    </Alert>
                    <TextField
                        margin="dense"
                        label="Transcript"
                        fullWidth
                        multiline
                        rows={12}
                        value={transcript}
                        onChange={(e) => setTranscript(e.target.value)}
                        placeholder="Paste interview transcript here..."
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setTranscriptDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAnalyzeTranscript}
                        variant="solid"
                        disabled={!transcript.trim() || analyzing}
                    >
                        {analyzing ? 'Analyzing...' : 'Analyze with AI'}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="md" fullWidth>
                <DialogTitle>Export Journey Map</DialogTitle>
                <DialogContent>
                    <Alert severity="success" className="mb-4">
                        Journey map copied to clipboard! You can also download as JSON.
                    </Alert>
                    <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                        Export includes all stages, touchpoints, pain points, emotions, and user quotes.
                    </Typography>
                    <Box className="mt-4">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Summary:
                        </Typography>
                        <Chip label={`${journey.stages.length} Stages`} size="sm" className="mr-2" />
                        <Chip label={`${journey.getTouchpointCount()} Touchpoints`} size="sm" className="mr-2" />
                        <Chip label={`${journey.getPainPointCount()} Pain Points`} size="sm" tone="danger" className="mr-2" />
                        <Chip label={`${journey.getQuoteCount()} Quotes`} size="sm" tone="success" />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)}>Close</Button>
                    <Button onClick={handleExport} variant="solid" startIcon={<DownloadIcon />}>
                        Copy to Clipboard
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default UserJourneyCanvas;

// Type exports
export type {
    JourneyStage,
    JourneyTouchpoint,
    JourneyPainPoint,
    JourneyEmotion,
    UserQuote,
    EmotionType,
};
