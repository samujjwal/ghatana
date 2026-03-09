/**
 * LearnStep - UI for the Learn step of the workflow
 *
 * @doc.type component
 * @doc.purpose Learn step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, TextField, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, ListItemText as ListItemSecondaryAction, Divider, Chip, Alert, FormControl, InputLabel, Select, MenuItem, ToggleButtonGroup, ToggleButton } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, ThumbsUp as WorkedIcon, ThumbsDown as DidntWorkIcon, Lightbulb as ImprovementIcon, Brain as RootCauseIcon, User as PeopleIcon, Code as TechIcon, Settings as ProcessIcon, Globe as ExternalIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { LearnStepData, Lesson, RootCause } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

const LESSON_CATEGORIES: { value: Lesson['category']; label: string; icon: React.ElementType; color: 'success' | 'error' | 'primary' }[] = [
    { value: 'WHAT_WORKED', label: 'What Worked', icon: WorkedIcon, color: 'success' },
    { value: 'WHAT_DIDNT', label: "What Didn't Work", icon: DidntWorkIcon, color: 'error' },
    { value: 'IMPROVEMENT', label: 'Improvement', icon: ImprovementIcon, color: 'primary' },
];

const ROOT_CAUSE_CATEGORIES: { value: RootCause['category']; label: string; icon: React.ElementType }[] = [
    { value: 'PROCESS', label: 'Process', icon: ProcessIcon },
    { value: 'TECHNOLOGY', label: 'Technology', icon: TechIcon },
    { value: 'PEOPLE', label: 'People', icon: PeopleIcon },
    { value: 'EXTERNAL', label: 'External', icon: ExternalIcon },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function LearnStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as LearnStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newLessonCategory, setNewLessonCategory] = React.useState<Lesson['category']>('WHAT_WORKED');
    const [newLessonDesc, setNewLessonDesc] = React.useState('');
    const [newLessonActionable, setNewLessonActionable] = React.useState(false);

    const [newRootCauseCategory, setNewRootCauseCategory] = React.useState<RootCause['category']>('PROCESS');
    const [newRootCauseDesc, setNewRootCauseDesc] = React.useState('');
    const [newFactor, setNewFactor] = React.useState('');
    const [tempFactors, setTempFactors] = React.useState<string[]>([]);

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.learn.data;
    const currentData: LearnStepData = baseData ?? {
        lessons: [],
        rootCauses: [],
    };
    const lessons = currentData.lessons ?? [];
    const rootCauses = currentData.rootCauses ?? [];

    const handleChange = (field: keyof LearnStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    // Lessons
    const handleAddLesson = () => {
        if (newLessonDesc.trim()) {
            const lesson: Lesson = {
                id: `lesson-${Date.now()}`,
                category: newLessonCategory,
                description: newLessonDesc.trim(),
                actionable: newLessonActionable,
            };
            handleChange('lessons', [...lessons, lesson]);
            setNewLessonDesc('');
            setNewLessonActionable(false);
        }
    };

    const handleRemoveLesson = (id: string) => {
        handleChange(
            'lessons',
            lessons.filter((l) => l.id !== id)
        );
    };

    // Root causes
    const handleAddFactor = () => {
        if (newFactor.trim()) {
            setTempFactors([...tempFactors, newFactor.trim()]);
            setNewFactor('');
        }
    };

    const handleRemoveTempFactor = (index: number) => {
        setTempFactors(tempFactors.filter((_, i) => i !== index));
    };

    const handleAddRootCause = () => {
        if (newRootCauseDesc.trim()) {
            const rootCause: RootCause = {
                id: `root-${Date.now()}`,
                category: newRootCauseCategory,
                description: newRootCauseDesc.trim(),
                contributingFactors: tempFactors,
            };
            handleChange('rootCauses', [...rootCauses, rootCause]);
            setNewRootCauseDesc('');
            setTempFactors([]);
        }
    };

    const handleRemoveRootCause = (id: string) => {
        handleChange(
            'rootCauses',
            rootCauses.filter((r) => r.id !== id)
        );
    };

    // Group lessons by category
    const lessonsByCategory = LESSON_CATEGORIES.map((cat) => ({
        ...cat,
        lessons: lessons.filter((l) => l.category === cat.value),
    }));

    return (
        <Box className="max-w-[800px] mx-auto">

            {/* Lessons Learned */}
            <Card className="mb-6">
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        Lessons Learned
                    </Typography>

                    <Box className="flex gap-2 mb-4 items-start flex-wrap">
                        <ToggleButtonGroup
                            value={newLessonCategory}
                            exclusive
                            onChange={(_, v) => v && setNewLessonCategory(v)}
                            size="sm"
                        >
                            {LESSON_CATEGORIES.map((cat) => (
                                <ToggleButton key={cat.value} value={cat.value} color={cat.color}>
                                    <cat.icon size={16} className="mr-1" />
                                    {cat.label}
                                </ToggleButton>
                            ))}
                        </ToggleButtonGroup>
                    </Box>

                    <Box className="flex gap-2 mb-4">
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Describe the lesson..."
                            value={newLessonDesc}
                            onChange={(e) => setNewLessonDesc(e.target.value)}
                        />
                        <Button
                            variant={newLessonActionable ? 'contained' : 'outlined'}
                            size="sm"
                            onClick={() => setNewLessonActionable(!newLessonActionable)}
                            className="whitespace-nowrap"
                        >
                            {newLessonActionable ? '✓ Actionable' : 'Actionable?'}
                        </Button>
                        <Button
                            variant="solid"
                            onClick={handleAddLesson}
                            disabled={!newLessonDesc.trim()}
                            startIcon={<AddIcon />}
                        >
                            Add
                        </Button>
                    </Box>

                    {lessons.length === 0 ? (
                        <Alert severity="info">No lessons captured yet. Reflect on what you learned.</Alert>
                    ) : (
                        <Box className="flex flex-col gap-4">
                            {lessonsByCategory.map(
                                (cat) =>
                                    cat.lessons.length > 0 && (
                                        <Box key={cat.value}>
                                            <Box className="flex items-center gap-2 mb-2">
                                                <cat.icon color={cat.color} size={16} />
                                                <Typography as="p" className="text-sm" fontWeight={500}>
                                                    {cat.label} ({cat.lessons.length})
                                                </Typography>
                                            </Box>
                                            <List className="rounded bg-gray-100 dark:bg-gray-800">
                                                {cat.lessons.map((lesson, index) => (
                                                    <React.Fragment key={lesson.id}>
                                                        <ListItem className="px-4">
                                                            <ListItemText primary={lesson.description} />
                                                            {lesson.actionable && (
                                                                <Chip label="Actionable" size="sm" tone="primary" className="mr-2" />
                                                            )}
                                                            <IconButton size="sm" onClick={() => handleRemoveLesson(lesson.id)}>
                                                                <DeleteIcon size={16} />
                                                            </IconButton>
                                                        </ListItem>
                                                        {index < cat.lessons.length - 1 && <Divider />}
                                                    </React.Fragment>
                                                ))}
                                            </List>
                                        </Box>
                                    )
                            )}
                        </Box>
                    )}
                </CardContent>
            </Card>

            {/* Root Causes */}
            <Card>
                <CardContent>
                    <Box className="flex items-center gap-2 mb-4">
                        <RootCauseIcon tone="warning" />
                        <Typography as="p" className="text-lg font-medium" fontWeight={600}>
                            Root Cause Analysis
                        </Typography>
                    </Box>

                    <Box className="flex gap-2 mb-4">
                        <FormControl size="sm" className="min-w-[140px]">
                            <InputLabel>Category</InputLabel>
                            <Select
                                value={newRootCauseCategory}
                                label="Category"
                                onChange={(e) => setNewRootCauseCategory(e.target.value as RootCause['category'])}
                            >
                                {ROOT_CAUSE_CATEGORIES.map((cat) => (
                                    <MenuItem key={cat.value} value={cat.value}>
                                        <Box className="flex items-center gap-2">
                                            <cat.icon size={16} />
                                            {cat.label}
                                        </Box>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            fullWidth
                            size="sm"
                            placeholder="Root cause description"
                            value={newRootCauseDesc}
                            onChange={(e) => setNewRootCauseDesc(e.target.value)}
                        />
                    </Box>

                    {/* Contributing factors */}
                    <Box className="mb-4">
                        <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                            Contributing Factors
                        </Typography>
                        <Box className="flex gap-2 mb-2">
                            <TextField
                                size="sm"
                                placeholder="Add factor..."
                                value={newFactor}
                                onChange={(e) => setNewFactor(e.target.value)}
                                onKeyPress={(e) => e.key === 'Enter' && handleAddFactor()}
                                className="grow"
                            />
                            <Button variant="outlined" size="sm" onClick={handleAddFactor} disabled={!newFactor.trim()}>
                                Add Factor
                            </Button>
                        </Box>
                        <Box className="flex flex-wrap gap-1">
                            {tempFactors.map((factor, index) => (
                                <Chip
                                    key={index}
                                    label={factor}
                                    size="sm"
                                    onDelete={() => handleRemoveTempFactor(index)}
                                />
                            ))}
                        </Box>
                    </Box>

                    <Button
                        variant="solid"
                        tone="warning"
                        onClick={handleAddRootCause}
                        disabled={!newRootCauseDesc.trim()}
                        startIcon={<AddIcon />}
                    >
                        Add Root Cause
                    </Button>

                    {rootCauses.length > 0 && (
                        <List className="mt-4">
                            {rootCauses.map((rootCause, index) => {
                                const cat = ROOT_CAUSE_CATEGORIES.find((c) => c.value === rootCause.category);
                                const CatIcon = cat?.icon || ProcessIcon;

                                return (
                                    <React.Fragment key={rootCause.id}>
                                        <ListItem className="px-0 items-start">
                                            <ListItemIcon className="mt-1 min-w-[40px]">
                                                <CatIcon tone="warning" />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={
                                                    <Box className="flex items-center gap-2">
                                                        <Typography as="p" className="text-sm" fontWeight={500}>
                                                            {rootCause.description}
                                                        </Typography>
                                                        <Chip label={cat?.label} size="sm" variant="outlined" className="h-[18px] text-[10px]" />
                                                    </Box>
                                                }
                                                secondary={
                                                    rootCause.contributingFactors.length > 0 && (
                                                        <>
                                                            {rootCause.contributingFactors.map((factor, i) => (
                                                                <Chip key={i} label={factor} size="sm" variant="outlined" className="h-[20px] text-[10px]" />
                                                            ))}
                                                        </>
                                                    )
                                                }
                                            />
                                            <ListItemSecondaryAction>
                                                <IconButton size="sm" onClick={() => handleRemoveRootCause(rootCause.id)}>
                                                    <DeleteIcon size={16} />
                                                </IconButton>
                                            </ListItemSecondaryAction>
                                        </ListItem>
                                        {index < rootCauses.length - 1 && <Divider />}
                                    </React.Fragment>
                                );
                            })}
                        </List>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
}

export default LearnStep;
