/**
 * Annotations Canvas Content
 * 
 * Annotations system for Brainstorm × Component level.
 * Collaborative annotations and comments on components.
 * 
 * @doc.type component
 * @doc.purpose Component annotation and commenting system
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface Annotation {
    id: string;
    componentName: string;
    type: 'comment' | 'suggestion' | 'question' | 'issue';
    author: string;
    timestamp: Date;
    content: string;
    resolved: boolean;
    replies: number;
    position: { line?: number; column?: number };
    tags?: string[];
}

// Mock annotations data
const MOCK_ANNOTATIONS: Annotation[] = [
    {
        id: '1',
        componentName: 'UserProfile.tsx',
        type: 'comment',
        author: 'Alice Chen',
        timestamp: new Date('2026-01-08T10:30:00'),
        content: 'This component should handle loading states better. Consider adding a skeleton loader.',
        resolved: false,
        replies: 3,
        position: { line: 45, column: 12 },
        tags: ['ux', 'improvement'],
    },
    {
        id: '2',
        componentName: 'AuthService.ts',
        type: 'issue',
        author: 'Bob Martinez',
        timestamp: new Date('2026-01-08T14:15:00'),
        content: 'Token refresh logic has a race condition when multiple requests fail simultaneously.',
        resolved: false,
        replies: 5,
        position: { line: 78, column: 5 },
        tags: ['security', 'critical', 'bug'],
    },
    {
        id: '3',
        componentName: 'ProductCard.tsx',
        type: 'suggestion',
        author: 'Carol Wu',
        timestamp: new Date('2026-01-07T16:45:00'),
        content: 'We could memoize the price calculation to improve performance on large product lists.',
        resolved: true,
        replies: 2,
        position: { line: 32, column: 8 },
        tags: ['performance', 'optimization'],
    },
    {
        id: '4',
        componentName: 'Dashboard.tsx',
        type: 'question',
        author: 'David Kim',
        timestamp: new Date('2026-01-09T09:00:00'),
        content: 'Should we keep the old metrics dashboard or fully migrate to the new one?',
        resolved: false,
        replies: 7,
        position: { line: 120 },
        tags: ['architecture', 'decision'],
    },
    {
        id: '5',
        componentName: 'ApiClient.ts',
        type: 'comment',
        author: 'Eve Johnson',
        timestamp: new Date('2026-01-08T11:20:00'),
        content: 'Great refactoring! The error handling is much cleaner now.',
        resolved: true,
        replies: 1,
        position: { line: 56, column: 15 },
        tags: ['praise'],
    },
    {
        id: '6',
        componentName: 'OrderForm.tsx',
        type: 'issue',
        author: 'Frank Lee',
        timestamp: new Date('2026-01-09T08:30:00'),
        content: 'Form validation is not working correctly for international phone numbers.',
        resolved: false,
        replies: 2,
        position: { line: 89, column: 20 },
        tags: ['validation', 'i18n', 'bug'],
    },
    {
        id: '7',
        componentName: 'SearchBar.tsx',
        type: 'suggestion',
        author: 'Alice Chen',
        timestamp: new Date('2026-01-06T13:00:00'),
        content: 'Add debouncing to the search input to reduce API calls.',
        resolved: true,
        replies: 4,
        position: { line: 23, column: 10 },
        tags: ['performance', 'api'],
    },
    {
        id: '8',
        componentName: 'PaymentGateway.ts',
        type: 'question',
        author: 'Bob Martinez',
        timestamp: new Date('2026-01-08T15:45:00'),
        content: 'Are we planning to add support for Apple Pay and Google Pay?',
        resolved: false,
        replies: 6,
        position: { line: 145 },
        tags: ['feature-request', 'payments'],
    },
];

const getTypeColor = (type: Annotation['type']) => {
    switch (type) {
        case 'comment':
            return '#2196F3';
        case 'suggestion':
            return '#9C27B0';
        case 'question':
            return '#FF9800';
        case 'issue':
            return '#F44336';
    }
};

const getTypeIcon = (type: Annotation['type']) => {
    switch (type) {
        case 'comment':
            return '💬';
        case 'suggestion':
            return '💡';
        case 'question':
            return '❓';
        case 'issue':
            return '🐛';
    }
};

const AnnotationCard = ({
    annotation,
    onClick,
}: {
    annotation: Annotation;
    onClick: (id: string) => void;
}) => {
    const typeColor = getTypeColor(annotation.type);

    return (
        <Paper
            elevation={2}
            onClick={() => onClick(annotation.id)}
            className="p-4 mb-3 cursor-pointer" style={{ border: `2px solid ${annotation.resolved ? '#E0E0E0' : typeColor, backgroundColor: 'typeColor', alignItems: 'start' }}
        >
            <Boing sx: alignItems: 'start' */>
                <Typography className="text-2xl">{getTypeIcon(annotation.type)}</Typography>
                <Box className="flex-1">
                    <Box className="flex items-center gap-2 mb-1">
                        <Chip
                            label={annotation.type}
                            size="small"
                            className="font-semibold text-white h-[20px] text-[0.7rem]" />
                        <Typography variant="body2" className="font-medium text-[0.85rem] font-mono">
                            {annotation.componentName}
                        </Typography>
                        {annotation.position.line && (
                            <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                :L{annotation.position.line}
                            </Typography>
                        )}
                    </Box>

                    <Typography variant="body2" className="mb-2 text-[0.9rem]">
                        {annotation.content}
                    </Typography>

                    <Box className="flex items-center gap-3 flex-wrap">
                        <Typography variant="caption" color="text.secondary" className="text-xs">
                            {annotation.author}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" className="text-xs">
                            {annotation.timestamp.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                        </Typography>
                        {annotation.replies > 0 && (
                            <Chip
                                label={`${annotation.replies} ${annotation.replies === 1 ? 'reply' : 'replies'}`}
                                size="small"
                                variant="outlined"
                                className="h-[18px] text-[0.65rem]"
                            />
                        )}
                        {annotation.resolved && (
                            <Chip label="✓ Resolved" size="small" color="success" className="h-[18px] text-[0.65rem]" />
                        )}
                    </Box>

                    {annotation.tags && annotation.tags.length > 0 && (
                        <Box className="flex gap-1 flex-wrap mt-2">
                            {annotation.tags.map(tag => (
                                <Chip
                                    key={tag}
                                    label={tag}
                                    size="small"
                                    variant="outlined"
                                    className="h-[16px] text-[0.6rem]"
                                />
                            ))}
                        </Box>
                    )}
                </Box>
            </Box>
        </Paper>
    );
};

export const AnnotationsCanvas = () => {
    const [annotations] = useState<Annotation[]>(MOCK_ANNOTATIONS);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterType, setFilterType] = useState<Annotation['type'] | 'all'>('all');
    const [showResolved, setShowResolved] = useState(true);

    const filteredAnnotations = useMemo(() => {
        return annotations.filter(ann => {
            const matchesSearch =
                searchQuery === '' ||
                ann.content.toLowerCase().includes(searchQuery.toLowerCase()) ||
                ann.componentName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                ann.author.toLowerCase().includes(searchQuery.toLowerCase()) ||
                ann.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));

            const matchesType = filterType === 'all' || ann.type === filterType;
            const matchesResolved = showResolved || !ann.resolved;

            return matchesSearch && matchesType && matchesResolved;
        });
    }, [annotations, searchQuery, filterType, showResolved]);

    const stats = useMemo(() => {
        return {
            total: annotations.length,
            unresolved: annotations.filter(a => !a.resolved).length,
            byType: {
                comment: annotations.filter(a => a.type === 'comment').length,
                suggestion: annotations.filter(a => a.type === 'suggestion').length,
                question: annotations.filter(a => a.type === 'question').length,
                issue: annotations.filter(a => a.type === 'issue').length,
            },
            totalReplies: annotations.reduce((acc, a) => acc + a.replies, 0),
        };
    }, [annotations]);

    const hasContent = annotations.length > 0;

    const groupedAnnotations = useMemo(() => {
        const groups: Record<string, Annotation[]> = {};
        filteredAnnotations.forEach(ann => {
            if (!groups[ann.componentName]) {
                groups[ann.componentName] = [];
            }
            groups[ann.componentName].push(ann);
        });
        return groups;
    }, [filteredAnnotations]);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Add Annotation',
                    onClick: () => {
                        console.log('Add Annotation');
                    },
                },
                secondaryAction: {
                    label: 'Import Comments',
                    onClick: () => {
                        console.log('Import Comments');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full flex flex-col bg-[#fafafa]"
            >
                {/* Top toolbar */}
                <Box
                    className="z-[10] p-4 bg-white" style={{ borderBottom: '1px solid rgba(0 }} >
                    <Box className="flex gap-4 items-center mb-2">
                        <TextField
                            size="small"
                            placeholder="Search annotations..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Add Annotation
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap items-center">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterType('all')}
                            color={filterType === 'all' ? 'primary' : 'default'}
                        />
                        {(['comment', 'suggestion', 'question', 'issue'] as const).map(type => (
                            <Chip
                                key={type}
                                label={`${getTypeIcon(type)} ${type}`}
                                size="small"
                                onClick={() => setFilterType(type)}
                                style={{ backgroundColor: filterType === type ? getTypeColor(type) : undefined, color: filterType === type ? 'white' : undefined, borderBottom: '2px solid rgba(0 }}
                            />
                        ))}
                        <Chip
                            label={showResolved ? 'Hide Resolved' : 'Show Resolved'}
                            size="small"
                            onClick={() => setShowResolved(!showResolved)}
                            variant="outlined"
                            className="ml-auto"
                        />
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 overflow-y-auto p-4"
                >
                    {Object.entries(groupedAnnotations).length === 0 && (
                        <Box className="flex justify-center items-center h-full">
                            <Typography color="text.secondary">No annotations match your filters</Typography>
                        </Box>
                    )}

                    {Object.entries(groupedAnnotations).map(([componentName, anns]) => (
                        <Box key={componentName} className="mb-6">
                            <Box
                                className="flex items-center gap-2 mb-3 pb-1" >
                                <Typography variant="subtitle2" className="font-semibold font-mono">
                                    {componentName}
                                </Typography>
                                <Chip label={anns.length} size="small" color="primary" className="h-[20px] text-[0.7rem]" />
                            </Box>
                            {anns.map(ann => (
                                <AnnotationCard key={ann.id} annotation={ann} onClick={() => { }} />
                            ))}
                        </Box>
                    ))}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Annotation Stats
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Unresolved: {stats.unresolved}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total Replies: {stats.totalReplies}
                    </Typography>
                    <Box className="mt-2">
                        <Typography variant="caption" display="block" style={{ color: getTypeColor('comment') }}>
                            💬 Comments: {stats.byType.comment}
                        </Typography>
                        <Typography variant="caption" display="block" style={{ color: getTypeColor('suggestion') }}>
                            💡 Suggestions: {stats.byType.suggestion}
                        </Typography>
                        <Typography variant="caption" display="block" style={{ color: getTypeColor('question') }}>
                            ❓ Questions: {stats.byType.question}
                        </Typography>
                        <Typography variant="caption" display="block" style={{ color: getTypeColor('issue') }}>
                            🐛 Issues: {stats.byType.issue}
                        </Typography>
                    </Box>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default AnnotationsCanvas;
