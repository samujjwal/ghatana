/**
 * Inspector Panel Component
 * 
 * Side panel showing detailed information for selected artifacts.
 * 
 * @doc.type component
 * @doc.purpose Artifact detail inspector
 * @doc.layer product
 * @doc.pattern Panel
 */

import * as React from 'react';
import { Drawer, Box, Typography, IconButton, Divider, TextField, Stack, Chip, Button, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Avatar, Tab, Tabs, Surface as Paper } from '@ghatana/ui';
import { X as CloseIcon, Pencil as EditIcon, Save as SaveIcon, Link as LinkIcon, AlertTriangle as WarningIcon, History as HistoryIcon, MessageSquare as CommentIcon, User as PersonIcon } from 'lucide-react';
import { PersonaBadge, StatusBadge } from './PersonaBadge';
import { type ArtifactType } from './ArtifactPalette';

export interface InspectorArtifact {
    id: string;
    type: ArtifactType;
    title: string;
    description?: string;
    status: 'pending' | 'in-progress' | 'review' | 'complete' | 'blocked';
    persona?: string;
    phase: string;
    linkedArtifacts?: string[];
    blockers?: Blocker[];
    comments?: Comment[];
    history?: HistoryEntry[];
    createdAt: Date;
    updatedAt: Date;
    createdBy?: string;
}

interface Blocker {
    id: string;
    description: string;
    blockedBy?: string;
    severity: 'high' | 'medium' | 'low';
    createdAt: Date;
}

interface Comment {
    id: string;
    author: string;
    content: string;
    mentions?: string[];
    createdAt: Date;
}

interface HistoryEntry {
    id: string;
    action: string;
    user: string;
    timestamp: Date;
}

// Parse @mentions from text
const parseMentions = (text: string): string[] => {
    const mentionRegex = /@([a-zA-Z0-9_-]+)/g;
    const mentions: string[] = [];
    let match;
    while ((match = mentionRegex.exec(text)) !== null) {
        mentions.push(match[1]);
    }
    return mentions;
};

// Render text with highlighted @mentions
const renderWithMentions = (text: string) => {
    const parts = text.split(/(@[a-zA-Z0-9_-]+)/);
    return parts.map((part, index) => {
        if (part.startsWith('@')) {
            return (
                <Box
                    component="span"
                    key={index}
                    className="px-1 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-white rounded-sm font-medium" >
                    {part}
                </Box>
            );
        }
        return <span key={index}>{part}</span>;
    });
};

export interface InspectorPanelProps {
    open: boolean;
    artifact: InspectorArtifact | null;
    onClose: () => void;
    onUpdate: (id: string, updates: Partial<InspectorArtifact>) => void;
    onAddBlocker: (artifactId: string, blocker: Omit<Blocker, 'id' | 'createdAt'>) => void;
    onAddComment: (artifactId: string, content: string) => void;
    onLinkArtifact: (artifactId: string, targetId: string) => void;
}

const ARTIFACT_ICONS: Record<ArtifactType, string> = {
    brief: 'BR',
    'user-story': 'US',
    requirement: 'REQ',
    design: 'DES',
    mockup: 'MK',
    'api-spec': 'API',
    code: 'CODE',
    test: 'TEST',
    deployment: 'DEP',
    metric: 'MET',
};

export const InspectorPanel: React.FC<InspectorPanelProps> = ({
    open,
    artifact,
    onClose,
    onUpdate,
    onAddBlocker,
    onAddComment,
    onLinkArtifact,
}) => {
    const [isEditing, setIsEditing] = React.useState(false);
    const [editedTitle, setEditedTitle] = React.useState('');
    const [editedDescription, setEditedDescription] = React.useState('');
    const [activeTab, setActiveTab] = React.useState(0);
    const [newComment, setNewComment] = React.useState('');
    const [newBlocker, setNewBlocker] = React.useState('');
    const [mentionQuery, setMentionQuery] = React.useState<string | null>(null);
    const [mentionPosition, setMentionPosition] = React.useState(0);
    const commentInputRef = React.useRef<HTMLTextAreaElement>(null);

    // Available personas/users for mentions
    const availableMentions = [
        { id: 'product-manager', label: 'Product Manager', icon: 'PM' },
        { id: 'developer', label: 'Developer', icon: 'DEV' },
        { id: 'designer', label: 'Designer', icon: 'DS' },
        { id: 'qa', label: 'QA Engineer', icon: 'QA' },
        { id: 'devops', label: 'DevOps', icon: 'OPS' },
        { id: 'security', label: 'Security', icon: 'SEC' },
    ];

    React.useEffect(() => {
        if (artifact) {
            setEditedTitle(artifact.title);
            setEditedDescription(artifact.description || '');
        }
    }, [artifact]);

    const handleSave = () => {
        if (artifact) {
            onUpdate(artifact.id, {
                title: editedTitle,
                description: editedDescription,
            });
            setIsEditing(false);
        }
    };

    const handleAddComment = () => {
        if (artifact && newComment.trim()) {
            const mentions = parseMentions(newComment);
            onAddComment(artifact.id, newComment);
            console.log('Mentions detected:', mentions);
            setNewComment('');
            setMentionQuery(null);
        }
    };

    const handleCommentChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const value = e.target.value;
        setNewComment(value);

        // Detect @ symbol for mention dropdown
        const cursorPosition = e.target.selectionStart || 0;
        const textBeforeCursor = value.substring(0, cursorPosition);
        const lastAtSymbol = textBeforeCursor.lastIndexOf('@');

        if (lastAtSymbol !== -1 && lastAtSymbol === textBeforeCursor.length - 1) {
            // @ just typed
            setMentionQuery('');
            setMentionPosition(cursorPosition);
        } else if (lastAtSymbol !== -1) {
            const textAfterAt = textBeforeCursor.substring(lastAtSymbol + 1);
            if (textAfterAt.match(/^[a-zA-Z0-9_-]*$/) && textAfterAt.length <= 20) {
                setMentionQuery(textAfterAt);
                setMentionPosition(cursorPosition);
            } else {
                setMentionQuery(null);
            }
        } else {
            setMentionQuery(null);
        }
    };

    const handleSelectMention = (mentionId: string) => {
        if (commentInputRef.current && mentionPosition) {
            const textBeforeCursor = newComment.substring(0, mentionPosition);
            const textAfterCursor = newComment.substring(mentionPosition);
            const lastAtSymbol = textBeforeCursor.lastIndexOf('@');
            const newText =
                textBeforeCursor.substring(0, lastAtSymbol) + `@${mentionId} ` + textAfterCursor;
            setNewComment(newText);
            setMentionQuery(null);
            commentInputRef.current.focus();
        }
    };

    const handleAddBlocker = () => {
        if (artifact && newBlocker.trim()) {
            onAddBlocker(artifact.id, {
                description: newBlocker,
                severity: 'high',
            });
            setNewBlocker('');
        }
    };

    if (!artifact) return null;

    const icon = ARTIFACT_ICONS[artifact.type] || 'ART';

    return (
        <Drawer
            anchor="right"
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    width: 420,
                    maxWidth: '100%',
                },
            }}
        >
            <Box className="h-full flex flex-col">
                {/* Header */}
                <Box
                    className="p-4 flex items-center justify-between border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex items-center gap-2">
                        <Typography className="text-2xl">{icon}</Typography>
                        <Typography as="h6" noWrap className="max-w-[250px]">
                            {artifact.title}
                        </Typography>
                    </Box>
                    <IconButton onClick={onClose} size="sm">
                        <CloseIcon />
                    </IconButton>
                </Box>

                {/* Metadata */}
                <Box className="p-4 bg-gray-100 dark:bg-gray-800">
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                        <StatusBadge status={artifact.status} size="sm" />
                        {artifact.persona && <PersonaBadge persona={artifact.persona} size="sm" />}
                        <Chip label={artifact.phase} size="sm" variant="outlined" />
                        {artifact.linkedArtifacts && artifact.linkedArtifacts.length > 0 && (
                            <Chip
                                icon={<LinkIcon />}
                                label={`${artifact.linkedArtifacts.length} linked`}
                                size="sm"
                                tone="primary"
                                variant="outlined"
                            />
                        )}
                        {artifact.blockers && artifact.blockers.length > 0 && (
                            <Chip
                                icon={<WarningIcon />}
                                label={`${artifact.blockers.length} blockers`}
                                size="sm"
                                tone="danger"
                                variant="outlined"
                            />
                        )}
                    </Stack>
                </Box>

                {/* Tabs */}
                <Box className="border-gray-200 dark:border-gray-700 border-b" >
                    <Tabs value={activeTab} onChange={(_, newValue) => setActiveTab(newValue)}>
                        <Tab label="Details" icon={<EditIcon size={16} />} iconPosition="start" />
                        <Tab
                            label={`Comments (${artifact.comments?.length || 0})`}
                            icon={<CommentIcon size={16} />}
                            iconPosition="start"
                        />
                        <Tab label="History" icon={<HistoryIcon size={16} />} iconPosition="start" />
                    </Tabs>
                </Box>

                {/* Content */}
                <Box className="flex-1 overflow-auto p-4">
                    {/* Details Tab */}
                    {activeTab === 0 && (
                        <Stack spacing={3}>
                            {/* Title & Description */}
                            <Box>
                                <Box className="flex justify-between mb-2">
                                    <Typography as="p" className="text-sm font-medium" color="text.secondary">
                                        Title
                                    </Typography>
                                    {!isEditing && (
                                        <IconButton size="sm" onClick={() => setIsEditing(true)}>
                                            <EditIcon size={16} />
                                        </IconButton>
                                    )}
                                </Box>
                                {isEditing ? (
                                    <TextField
                                        fullWidth
                                        size="sm"
                                        value={editedTitle}
                                        onChange={(e) => setEditedTitle(e.target.value)}
                                    />
                                ) : (
                                    <Typography as="p">{artifact.title}</Typography>
                                )}
                            </Box>

                            <Box>
                                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                                    Description
                                </Typography>
                                {isEditing ? (
                                    <TextField
                                        fullWidth
                                        multiline
                                        rows={4}
                                        size="sm"
                                        value={editedDescription}
                                        onChange={(e) => setEditedDescription(e.target.value)}
                                        placeholder="Add a description..."
                                    />
                                ) : (
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        {artifact.description || 'No description'}
                                    </Typography>
                                )}
                            </Box>

                            {isEditing && (
                                <Stack direction="row" spacing={1}>
                                    <Button variant="solid" size="sm" startIcon={<SaveIcon />} onClick={handleSave}>
                                        Save
                                    </Button>
                                    <Button size="sm" onClick={() => setIsEditing(false)}>
                                        Cancel
                                    </Button>
                                </Stack>
                            )}

                            <Divider />

                            {/* Blockers */}
                            <Box>
                                <Typography as="p" className="text-sm font-medium" gutterBottom>
                                    Blockers
                                </Typography>
                                {artifact.blockers && artifact.blockers.length > 0 ? (
                                    <List dense>
                                        {artifact.blockers.map((blocker) => (
                                            <ListItem
                                                key={blocker.id}
                                                className="rounded mb-2 bg-red-100 dark:bg-red-900/30 text-white"
                                            >
                                                <ListItemIcon>
                                                    <WarningIcon className="text-white" />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={blocker.description}
                                                    secondary={
                                                        <Typography as="span" className="text-xs text-gray-500" className="text-white">
                                                            {blocker.severity.toUpperCase()}
                                                        </Typography>
                                                    }
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                ) : (
                                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                                        No blockers
                                    </Typography>
                                )}

                                <TextField
                                    fullWidth
                                    size="sm"
                                    placeholder="Add a blocker..."
                                    value={newBlocker}
                                    onChange={(e) => setNewBlocker(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleAddBlocker()}
                                    InputProps={{
                                        endAdornment: (
                                            <Button size="sm" onClick={handleAddBlocker} disabled={!newBlocker.trim()}>
                                                Add
                                            </Button>
                                        ),
                                    }}
                                />
                            </Box>

                            {/* Linked Artifacts */}
                            <Box>
                                <Typography as="p" className="text-sm font-medium" gutterBottom>
                                    Linked Artifacts
                                </Typography>
                                {artifact.linkedArtifacts && artifact.linkedArtifacts.length > 0 ? (
                                    <Stack spacing={0.5}>
                                        {artifact.linkedArtifacts.map((linkedId) => (
                                            <Chip
                                                key={linkedId}
                                                label={`Artifact ${linkedId.slice(0, 8)}`}
                                                size="sm"
                                                icon={<LinkIcon />}
                                                onClick={() => console.log('Navigate to:', linkedId)}
                                            />
                                        ))}
                                    </Stack>
                                ) : (
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        No linked artifacts
                                    </Typography>
                                )}
                            </Box>

                            {/* Metadata */}
                            <Box>
                                <Typography as="p" className="text-sm font-medium" gutterBottom>
                                    Metadata
                                </Typography>
                                <Stack spacing={1}>
                                    <Box className="flex justify-between">
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            Created
                                        </Typography>
                                        <Typography as="span" className="text-xs text-gray-500">
                                            {artifact.createdAt.toLocaleDateString()} by {artifact.createdBy || 'Unknown'}
                                        </Typography>
                                    </Box>
                                    <Box className="flex justify-between">
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            Updated
                                        </Typography>
                                        <Typography as="span" className="text-xs text-gray-500">
                                            {artifact.updatedAt.toLocaleDateString()}
                                        </Typography>
                                    </Box>
                                </Stack>
                            </Box>
                        </Stack>
                    )}

                    {/* Comments Tab */}
                    {activeTab === 1 && (
                        <Stack spacing={2}>
                            {artifact.comments && artifact.comments.length > 0 ? (
                                artifact.comments.map((comment) => (
                                    <Paper key={comment.id} variant="flat" className="p-4 border border-gray-200 dark:border-gray-700">
                                        <Box className="flex gap-2 mb-2">
                                            <Avatar className="w-[32px] h-[32px]">
                                                {comment.author.charAt(0).toUpperCase()}
                                            </Avatar>
                                            <Box className="flex-1">
                                                <Typography as="p" className="text-sm font-medium">{comment.author}</Typography>
                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                    {comment.createdAt.toLocaleString()}
                                                </Typography>
                                            </Box>
                                        </Box>
                                        <Typography as="p" className="text-sm">{renderWithMentions(comment.content)}</Typography>
                                    </Paper>
                                ))
                            ) : (
                                <Typography as="p" className="text-sm" color="text.secondary" className="text-center py-8">
                                    No comments yet
                                </Typography>
                            )}

                            <TextField
                                inputRef={commentInputRef}
                                fullWidth
                                multiline
                                rows={3}
                                size="sm"
                                placeholder="Add a comment... (use @mention to tag someone)"
                                value={newComment}
                                onChange={handleCommentChange}
                            />

                            {/* Mention Dropdown */}
                            {mentionQuery !== null && (
                                <Paper
                                    elevation={3}
                                    className="absolute mb-2 overflow-auto bottom-[100%] left-[0px] right-[0px] max-h-[200px] z-[1000]"
                                >
                                    <List dense>
                                        {availableMentions
                                            .filter((m) => m.id.includes(mentionQuery.toLowerCase()))
                                            .map((mention) => (
                                                <ListItemButton
                                                    key={mention.id}
                                                    onClick={() => handleSelectMention(mention.id)}
                                                >
                                                    <ListItemIcon className="min-w-[32px]">
                                                        <Typography>{mention.icon}</Typography>
                                                    </ListItemIcon>
                                                    <ListItemText
                                                        primary={mention.label}
                                                        secondary={`@${mention.id}`}
                                                    />
                                                </ListItemButton>
                                            ))}
                                    </List>
                                </Paper>
                            )}

                            <Button
                                variant="solid"
                                size="sm"
                                onClick={handleAddComment}
                                disabled={!newComment.trim()}
                            >
                                Add Comment
                            </Button>
                        </Stack>
                    )}

                    {/* History Tab */}
                    {activeTab === 2 && (
                        <List>
                            {artifact.history && artifact.history.length > 0 ? (
                                artifact.history.map((entry) => (
                                    <ListItem key={entry.id} alignItems="flex-start">
                                        <ListItemIcon>
                                            <PersonIcon />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={entry.action}
                                            secondary={
                                                <>
                                                    <Typography component="span" as="p" className="text-sm" color="text.primary">
                                                        {entry.user}
                                                    </Typography>
                                                    {` — ${entry.timestamp.toLocaleString()}`}
                                                </>
                                            }
                                        />
                                    </ListItem>
                                ))
                            ) : (
                                <Typography as="p" className="text-sm" color="text.secondary" className="text-center py-8">
                                    No history available
                                </Typography>
                            )}
                        </List>
                    )}
                </Box>
            </Box>
        </Drawer>
    );
};
