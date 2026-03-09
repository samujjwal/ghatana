/**
 * @doc.type component
 * @doc.purpose Code review mode component for Journey 18.1 (Engineering Lead - Code Review Mode)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';
import { Surface as Paper, Box, Typography, Button, IconButton, Dialog, DialogTitle, DialogContent, DialogActions, TextField, MenuItem, Chip, InteractiveList as List, ListItem, ListItemText, ListItemButton, Divider, Alert, Stack, FormControl, InputLabel, Select, Badge, Accordion, AccordionSummary, AccordionDetails, LinearProgress } from '@ghatana/ui';
import { Check, Check as ApproveIcon, X as RejectIcon, MessageSquare as CommentIcon, Plus as AddIcon, Trash2 as DeleteIcon, Code as CodeIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, Info as InfoIcon, Shield as SecurityIcon, Bug as BugIcon, ChevronDown as ExpandMoreIcon, BarChart3 as AnalysisIcon } from 'lucide-react';
import {
    useReviewMode,
    type ReviewFile,
    type Annotation,
    type AnnotationSeverity,
    type ReviewStatus,
} from '../hooks/useReviewMode';
import { StaticAnalysisClient } from '../services/StaticAnalysisClient';

/**
 * Component props
 */
export interface ReviewModeProps {
    /**
     * Reviewer name/email
     */
    reviewer: string;

    /**
     * Enable static analysis
     */
    enableStaticAnalysis?: boolean;

    /**
     * Callback when review is submitted
     */
    onReviewSubmit?: (status: ReviewStatus, comment: string) => void;

    /**
     * Initial files to review
     */
    initialFiles?: Omit<ReviewFile, 'id' | 'annotations'>[];
}

const SEVERITY_COLORS: Record<AnnotationSeverity, 'error' | 'warning' | 'info' | 'success'> = {
    error: 'error',
    warning: 'warning',
    info: 'info',
    suggestion: 'success',
};

const SEVERITY_ICONS: Record<AnnotationSeverity, React.ReactElement> = {
    error: <ErrorIcon />,
    warning: <WarningIcon />,
    info: <InfoIcon />,
    suggestion: <CommentIcon />,
};

/**
 * Review Mode Component
 * 
 * Provides comprehensive code review interface with annotation management,
 * approval workflow, and static analysis integration.
 */
export const ReviewMode: React.FC<ReviewModeProps> = ({
    reviewer,
    enableStaticAnalysis = true,
    onReviewSubmit,
    initialFiles = [],
}) => {
    const {
        files,
        selectedFile,
        selectFile,
        addFile,
        annotations,
        addAnnotation,
        resolveAnnotation,
        deleteAnnotation,
        addReply,
        getAnnotationsForFile,
        getUnresolvedAnnotations,
        decision,
        approve,
        requestChanges,
        comment: addComment,
        clearDecision,
        runStaticAnalysis,
        getAnalysisForFile,
        reviewStatus,
        canApprove,
        stats,
    } = useReviewMode({ reviewer, enableStaticAnalysis });

    const [annotationDialogOpen, setAnnotationDialogOpen] = useState(false);
    const [decisionDialogOpen, setDecisionDialogOpen] = useState(false);
    const [selectedDecision, setSelectedDecision] = useState<'approve' | 'request-changes' | 'comment'>('approve');
    const [decisionComment, setDecisionComment] = useState('');
    const [replyDialogOpen, setReplyDialogOpen] = useState(false);
    const [selectedAnnotation, setSelectedAnnotation] = useState<Annotation | null>(null);
    const [replyText, setReplyText] = useState('');
    const [analysisExpanded, setAnalysisExpanded] = useState(true);

    // New annotation form
    const [newAnnotation, setNewAnnotation] = useState<{
        lineNumber: number;
        severity: AnnotationSeverity;
        message: string;
    }>({
        lineNumber: 1,
        severity: 'info',
        message: '',
    });

    // Load initial files
    React.useEffect(() => {
        initialFiles.forEach(file => {
            addFile(file);
        });
    }, [initialFiles.length]);

    const handleAddAnnotation = useCallback(() => {
        if (!selectedFile || !newAnnotation.message.trim()) return;

        addAnnotation({
            fileId: selectedFile.id,
            filePath: selectedFile.path,
            lineNumber: newAnnotation.lineNumber,
            severity: newAnnotation.severity,
            message: newAnnotation.message,
            author: reviewer,
        });

        setNewAnnotation({
            lineNumber: 1,
            severity: 'info',
            message: '',
        });
        setAnnotationDialogOpen(false);
    }, [selectedFile, newAnnotation, addAnnotation, reviewer]);

    const handleSubmitDecision = useCallback(() => {
        if (!decisionComment.trim()) return;

        if (selectedDecision === 'approve') {
            approve(decisionComment);
        } else if (selectedDecision === 'request-changes') {
            requestChanges(decisionComment);
        } else {
            addComment(decisionComment);
        }

        if (onReviewSubmit) {
            onReviewSubmit(selectedDecision === 'approve' ? 'approved' : 'changes-requested', decisionComment);
        }

        setDecisionComment('');
        setDecisionDialogOpen(false);
    }, [selectedDecision, decisionComment, approve, requestChanges, addComment, onReviewSubmit]);

    const handleAddReply = useCallback(() => {
        if (!selectedAnnotation || !replyText.trim()) return;

        addReply(selectedAnnotation.id, replyText);
        setReplyText('');
        setReplyDialogOpen(false);
        setSelectedAnnotation(null);
    }, [selectedAnnotation, replyText, addReply]);

    const fileAnnotations = selectedFile ? getAnnotationsForFile(selectedFile.id) : [];
    const analysisResult = selectedFile ? getAnalysisForFile(selectedFile.id) : undefined;
    const unresolvedAnnotations = getUnresolvedAnnotations();

    return (
        <Box className="p-6 flex gap-4 h-[calc(100vh - 100px)]">
            {/* Left Panel - File List */}
            <Paper className="p-4 overflow-y-auto w-[300px]">
                <Typography as="h6" gutterBottom>
                    Files ({files.length})
                </Typography>

                <Stack spacing={1} className="mb-4">
                    <Chip
                        label={`${stats.filesReviewed}/${stats.totalFiles} Reviewed`}
                        size="sm"
                        tone="primary"
                        variant="outlined"
                    />
                    <Chip
                        label={`${stats.unresolvedAnnotations} Unresolved`}
                        size="sm"
                        color={stats.unresolvedAnnotations > 0 ? 'warning' : 'success'}
                    />
                    {stats.criticalIssues > 0 && (
                        <Chip
                            label={`${stats.criticalIssues} Critical`}
                            size="sm"
                            tone="danger"
                            icon={<ErrorIcon />}
                        />
                    )}
                </Stack>

                <Divider className="my-4" />

                <List dense>
                    {files.map(file => {
                        const fileAnns = getAnnotationsForFile(file.id);
                        const unresolved = fileAnns.filter(a => !a.resolved).length;

                        return (
                            <ListItemButton
                                key={file.id}
                                selected={selectedFile?.id === file.id}
                                onClick={() => selectFile(file.id)}
                            >
                                <ListItemText
                                    primary={
                                        <Box className="flex items-center gap-2">
                                            <Typography as="p" className="text-sm" noWrap>
                                                {file.path.split('/').pop()}
                                            </Typography>
                                            {unresolved > 0 && (
                                                <Badge badgeContent={unresolved} tone="danger" />
                                            )}
                                        </Box>
                                    }
                                    secondary={
                                        <Stack direction="row" spacing={0.5}>
                                            <Chip label={file.changeType} size="sm" />
                                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                +{file.additions} -{file.deletions}
                                            </Typography>
                                        </Stack>
                                    }
                                />
                            </ListItemButton>
                        );
                    })}
                </List>

                {files.length === 0 && (
                    <Alert severity="info">No files to review yet.</Alert>
                )}
            </Paper>

            {/* Center Panel - Code View */}
            <Paper className="flex-1 p-4 overflow-y-auto">
                {selectedFile ? (
                    <>
                        <Stack direction="row" justifyContent="space-between" alignItems="center" className="mb-4">
                            <Box>
                                <Typography as="h6">{selectedFile.path}</Typography>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {selectedFile.language} • {selectedFile.changeType}
                                </Typography>
                            </Box>
                            <Button
                                variant="outlined"
                                startIcon={<AddIcon />}
                                onClick={() => setAnnotationDialogOpen(true)}
                            >
                                Add Annotation
                            </Button>
                        </Stack>

                        {/* Static Analysis Results */}
                        {analysisResult && (
                            <Accordion expanded={analysisExpanded} onChange={() => setAnalysisExpanded(!analysisExpanded)}>
                                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                    <Stack direction="row" spacing={2} alignItems="center">
                                        <AnalysisIcon />
                                        <Typography>Static Analysis</Typography>
                                        {analysisResult.complexity.cyclomatic > 15 && (
                                            <Chip label="High Complexity" size="sm" tone="danger" />
                                        )}
                                    </Stack>
                                </AccordionSummary>
                                <AccordionDetails>
                                    <Stack spacing={2}>
                                        <Box>
                                            <Typography as="p" className="text-sm font-medium" gutterBottom>
                                                Complexity Metrics
                                            </Typography>
                                            <Stack spacing={1}>
                                                <Box>
                                                    <Typography as="p" className="text-sm">
                                                        Cyclomatic: {analysisResult.complexity.cyclomatic}
                                                    </Typography>
                                                    <LinearProgress
                                                        variant="determinate"
                                                        value={Math.min((analysisResult.complexity.cyclomatic / 20) * 100, 100)}
                                                        color={analysisResult.complexity.cyclomatic > 15 ? 'error' : 'success'}
                                                    />
                                                </Box>
                                                <Box>
                                                    <Typography as="p" className="text-sm">
                                                        Maintainability: {analysisResult.complexity.maintainabilityIndex}/100
                                                    </Typography>
                                                    <LinearProgress
                                                        variant="determinate"
                                                        value={analysisResult.complexity.maintainabilityIndex}
                                                        color={analysisResult.complexity.maintainabilityIndex < 50 ? 'error' : 'success'}
                                                    />
                                                </Box>
                                            </Stack>
                                        </Box>

                                        {analysisResult.hotspots.length > 0 && (
                                            <Box>
                                                <Stack direction="row" spacing={1} alignItems="center" className="mb-2">
                                                    <WarningIcon tone="warning" />
                                                    <Typography as="p" className="text-sm font-medium">
                                                        Hotspots ({analysisResult.hotspots.length})
                                                    </Typography>
                                                </Stack>
                                                <List dense>
                                                    {analysisResult.hotspots.map((hotspot, idx) => (
                                                        <ListItem key={idx}>
                                                            <ListItemText
                                                                primary={`Line ${hotspot.lineNumber}: ${hotspot.message}`}
                                                                secondary={`Type: ${hotspot.type}`}
                                                            />
                                                        </ListItem>
                                                    ))}
                                                </List>
                                            </Box>
                                        )}
                                    </Stack>
                                </AccordionDetails>
                            </Accordion>
                        )}

                        {/* Code Display */}
                        <Paper variant="outlined" className="mt-4 p-4 overflow-x-auto bg-[#f5f5f5]">
                            <pre style={{ margin: 0, fontFamily: 'monospace', fontSize: '13px' }}>
                                {selectedFile.content.split('\n').map((line, idx) => {
                                    const lineAnnotations = fileAnnotations.filter(a => a.lineNumber === idx + 1);

                                    return (
                                        <Box key={idx} className="flex gap-2">
                                            <Typography
                                                as="span" className="text-xs text-gray-500"
                                                className="text-right text-gray-500 dark:text-gray-400 min-w-[40px] select-none"
                                            >
                                                {idx + 1}
                                            </Typography>
                                            <Box className="flex-1">
                                                <Typography
                                                    as="p" className="text-sm"
                                                    component="code"
                                                    style={{
                                                        backgroundColor: lineAnnotations.length > 0 ? '#fff3e0' : 'transparent',
                                                        paddingLeft: lineAnnotations.length > 0 ? 4 : 0,
                                                        paddingRight: lineAnnotations.length > 0 ? 4 : 0,
                                                    }}
                                                >
                                                    {line || ' '}
                                                </Typography>
                                                {lineAnnotations.map(annotation => (
                                                    <Alert
                                                        key={annotation.id}
                                                        severity={SEVERITY_COLORS[annotation.severity]}
                                                        className="mt-1 py-0"
                                                        action={
                                                            <Stack direction="row" spacing={0.5}>
                                                                {!annotation.resolved && (
                                                                    <IconButton
                                                                        size="sm"
                                                                        onClick={() => resolveAnnotation(annotation.id)}
                                                                    >
                                                                        <Check size={16} />
                                                                    </IconButton>
                                                                )}
                                                                <IconButton
                                                                    size="sm"
                                                                    onClick={() => {
                                                                        setSelectedAnnotation(annotation);
                                                                        setReplyDialogOpen(true);
                                                                    }}
                                                                >
                                                                    <CommentIcon size={16} />
                                                                </IconButton>
                                                                <IconButton
                                                                    size="sm"
                                                                    onClick={() => deleteAnnotation(annotation.id)}
                                                                >
                                                                    <DeleteIcon size={16} />
                                                                </IconButton>
                                                            </Stack>
                                                        }
                                                    >
                                                        <Typography as="span" className="text-xs text-gray-500">
                                                            <strong>{annotation.author}:</strong> {annotation.message}
                                                            {annotation.replies && annotation.replies.length > 0 && (
                                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                                    {' '}({annotation.replies.length} {annotation.replies.length === 1 ? 'reply' : 'replies'})
                                                                </Typography>
                                                            )}
                                                        </Typography>
                                                    </Alert>
                                                ))}
                                            </Box>
                                        </Box>
                                    );
                                })}
                            </pre>
                        </Paper>
                    </>
                ) : (
                    <Box className="flex items-center justify-center h-full">
                        <Stack spacing={2} alignItems="center">
                            <CodeIcon className="text-gray-500 dark:text-gray-400 text-[64px]" />
                            <Typography as="h6" color="text.secondary">
                                Select a file to review
                            </Typography>
                        </Stack>
                    </Box>
                )}
            </Paper>

            {/* Right Panel - Actions */}
            <Paper className="p-4 overflow-y-auto w-[300px]">
                <Typography as="h6" gutterBottom>
                    Review Actions
                </Typography>

                <Stack spacing={2}>
                    {/* Review Status */}
                    {decision && (
                        <Alert
                            severity={decision.status === 'approved' ? 'success' : 'warning'}
                            action={
                                <IconButton size="sm" onClick={clearDecision}>
                                    <DeleteIcon size={16} />
                                </IconButton>
                            }
                        >
                            <Typography as="p" className="text-sm font-medium">
                                {decision.status === 'approved' ? 'Approved' : 'Changes Requested'}
                            </Typography>
                            <Typography as="span" className="text-xs text-gray-500">{decision.comment}</Typography>
                        </Alert>
                    )}

                    {/* Unresolved Annotations */}
                    {unresolvedAnnotations.length > 0 && (
                        <Alert severity="warning">
                            <Typography as="p" className="text-sm font-medium">
                                {unresolvedAnnotations.length} unresolved annotation{unresolvedAnnotations.length !== 1 && 's'}
                            </Typography>
                        </Alert>
                    )}

                    {/* Action Buttons */}
                    <Button
                        variant="solid"
                        tone="success"
                        startIcon={<ApproveIcon />}
                        fullWidth
                        disabled={!canApprove}
                        onClick={() => {
                            setSelectedDecision('approve');
                            setDecisionDialogOpen(true);
                        }}
                    >
                        Approve
                    </Button>

                    <Button
                        variant="solid"
                        tone="danger"
                        startIcon={<RejectIcon />}
                        fullWidth
                        onClick={() => {
                            setSelectedDecision('request-changes');
                            setDecisionDialogOpen(true);
                        }}
                    >
                        Request Changes
                    </Button>

                    <Button
                        variant="outlined"
                        startIcon={<CommentIcon />}
                        fullWidth
                        onClick={() => {
                            setSelectedDecision('comment');
                            setDecisionDialogOpen(true);
                        }}
                    >
                        Add Comment
                    </Button>

                    <Divider />

                    {/* Statistics */}
                    <Box>
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Review Statistics
                        </Typography>
                        <Stack spacing={1}>
                            <Stack direction="row" justifyContent="space-between">
                                <Typography as="span" className="text-xs text-gray-500">Total Annotations:</Typography>
                                <Typography as="span" className="text-xs text-gray-500">{stats.totalAnnotations}</Typography>
                            </Stack>
                            <Stack direction="row" justifyContent="space-between">
                                <Typography as="span" className="text-xs text-gray-500">Unresolved:</Typography>
                                <Typography as="span" className="text-xs text-gray-500" tone="danger">
                                    {stats.unresolvedAnnotations}
                                </Typography>
                            </Stack>
                            <Stack direction="row" justifyContent="space-between">
                                <Typography as="span" className="text-xs text-gray-500">Files Reviewed:</Typography>
                                <Typography as="span" className="text-xs text-gray-500">
                                    {stats.filesReviewed}/{stats.totalFiles}
                                </Typography>
                            </Stack>
                        </Stack>
                    </Box>
                </Stack>
            </Paper>

            {/* Add Annotation Dialog */}
            <Dialog open={annotationDialogOpen} onClose={() => setAnnotationDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Annotation</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Line Number"
                            type="number"
                            value={newAnnotation.lineNumber}
                            onChange={e => setNewAnnotation({ ...newAnnotation, lineNumber: parseInt(e.target.value) || 1 })}
                            fullWidth
                        />
                        <FormControl fullWidth>
                            <InputLabel>Severity</InputLabel>
                            <Select
                                value={newAnnotation.severity}
                                label="Severity"
                                onChange={e => setNewAnnotation({ ...newAnnotation, severity: e.target.value as AnnotationSeverity })}
                            >
                                <MenuItem value="error">Error</MenuItem>
                                <MenuItem value="warning">Warning</MenuItem>
                                <MenuItem value="info">Info</MenuItem>
                                <MenuItem value="suggestion">Suggestion</MenuItem>
                            </Select>
                        </FormControl>
                        <TextField
                            label="Message"
                            value={newAnnotation.message}
                            onChange={e => setNewAnnotation({ ...newAnnotation, message: e.target.value })}
                            fullWidth
                            multiline
                            rows={4}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAnnotationDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddAnnotation} variant="solid">
                        Add
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Decision Dialog */}
            <Dialog open={decisionDialogOpen} onClose={() => setDecisionDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>
                    {selectedDecision === 'approve' && 'Approve Review'}
                    {selectedDecision === 'request-changes' && 'Request Changes'}
                    {selectedDecision === 'comment' && 'Add Comment'}
                </DialogTitle>
                <DialogContent>
                    <TextField
                        label="Comment"
                        value={decisionComment}
                        onChange={e => setDecisionComment(e.target.value)}
                        fullWidth
                        multiline
                        rows={6}
                        className="mt-2"
                        placeholder="Provide your feedback..."
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDecisionDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleSubmitDecision} variant="solid">
                        Submit
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Reply Dialog */}
            <Dialog open={replyDialogOpen} onClose={() => setReplyDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Reply to Annotation</DialogTitle>
                <DialogContent>
                    {selectedAnnotation && (
                        <Box className="mb-4">
                            <Alert severity={SEVERITY_COLORS[selectedAnnotation.severity]}>
                                <Typography as="p" className="text-sm">
                                    <strong>{selectedAnnotation.author}:</strong> {selectedAnnotation.message}
                                </Typography>
                            </Alert>
                        </Box>
                    )}
                    <TextField
                        label="Reply"
                        value={replyText}
                        onChange={e => setReplyText(e.target.value)}
                        fullWidth
                        multiline
                        rows={4}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setReplyDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddReply} variant="solid">
                        Reply
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
