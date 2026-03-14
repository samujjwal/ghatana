import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    MenuItem,
    Chip,
    Grid,
    FormControl,
    InputLabel,
    Divider,
    Tab,
    Tabs,
    LinearProgress,
} from '@ghatana/design-system';

/**
 * Time entry data
 */
export interface TimeEntry {
    id: string;
    date: string;
    taskId?: string;
    taskTitle: string;
    category: 'development' | 'meetings' | 'code-review' | 'planning' | 'learning' | 'other';
    hours: number;
    description?: string;
    tags?: string[];
}

/**
 * Daily summary
 */
export interface DailySummary {
    date: string;
    totalHours: number;
    entries: TimeEntry[];
    categoryBreakdown: Record<string, number>;
}

/**
 * Weekly summary
 */
export interface WeeklySummary {
    weekStart: string;
    weekEnd: string;
    totalHours: number;
    dailySummaries: DailySummary[];
    topCategories: Array<{ category: string; hours: number; percentage: number }>;
    topTasks: Array<{ taskTitle: string; hours: number }>;
}

/**
 * Props for TimeLoggingPanel
 */
interface TimeLoggingPanelProps {
    onAddEntry?: (entry: Omit<TimeEntry, 'id'>) => void;
    onUpdateEntry?: (entry: TimeEntry) => void;
    onDeleteEntry?: (entryId: string) => void;
    existingEntries?: TimeEntry[];
    availableTasks?: Array<{ id: string; title: string }>;
    targetHoursPerWeek?: number;
}

/**
 * Time Logging Panel
 *
 * Component for tracking daily work time:
 * - Quick time entry form
 * - Daily time summary
 * - Weekly overview
 * - Category breakdown
 * - Task-based time allocation
 * - Progress toward weekly goals
 */
export const TimeLoggingPanel: React.FC<TimeLoggingPanelProps> = ({
    onAddEntry,
    onUpdateEntry,
    onDeleteEntry,
    existingEntries: initialEntries,
    availableTasks = [],
    targetHoursPerWeek = 40,
}) => {
    // Mock data if none provided
    const mockEntries: TimeEntry[] = [
        {
            id: 'e1',
            date: new Date().toISOString().split('T')[0],
            taskTitle: 'Implement user authentication',
            category: 'development',
            hours: 4.5,
            description: 'Built JWT-based auth system with refresh tokens',
            tags: ['backend', 'security'],
        },
        {
            id: 'e2',
            date: new Date().toISOString().split('T')[0],
            taskTitle: 'Team standup',
            category: 'meetings',
            hours: 0.5,
        },
        {
            id: 'e3',
            date: new Date().toISOString().split('T')[0],
            taskTitle: 'Review PRs',
            category: 'code-review',
            hours: 1.5,
            description: 'Reviewed 3 pull requests from team members',
        },
        {
            id: 'e4',
            date: new Date(Date.now() - 86400000).toISOString().split('T')[0], // Yesterday
            taskTitle: 'Sprint planning',
            category: 'planning',
            hours: 2,
        },
        {
            id: 'e5',
            date: new Date(Date.now() - 86400000).toISOString().split('T')[0],
            taskTitle: 'Database optimization',
            category: 'development',
            hours: 5,
            tags: ['performance', 'database'],
        },
    ];

    const mockTasks = availableTasks.length > 0 ? availableTasks : [
        { id: 'task-1', title: 'Implement user authentication' },
        { id: 'task-2', title: 'Database optimization' },
        { id: 'task-3', title: 'Build API endpoints' },
        { id: 'task-4', title: 'Write unit tests' },
    ];

    const [entries, setEntries] = useState<TimeEntry[]>(initialEntries || mockEntries);
    const [activeTab, setActiveTab] = useState<'today' | 'week'>('today');

    // New entry form state
    const [newEntry, setNewEntry] = useState<Omit<TimeEntry, 'id'>>({
        date: new Date().toISOString().split('T')[0],
        taskTitle: '',
        category: 'development',
        hours: 0,
        description: '',
        tags: [],
    });

    // Calculate summaries
    const getDailySummary = (date: string): DailySummary => {
        const dayEntries = entries.filter((e) => e.date === date);
        const totalHours = dayEntries.reduce((sum, e) => sum + e.hours, 0);
        const categoryBreakdown: Record<string, number> = {};

        dayEntries.forEach((entry) => {
            categoryBreakdown[entry.category] = (categoryBreakdown[entry.category] || 0) + entry.hours;
        });

        return {
            date,
            totalHours,
            entries: dayEntries,
            categoryBreakdown,
        };
    };

    const getWeeklySummary = (): WeeklySummary => {
        const now = new Date();
        const weekStart = new Date(now);
        weekStart.setDate(now.getDate() - now.getDay()); // Start of week (Sunday)
        const weekEnd = new Date(weekStart);
        weekEnd.setDate(weekStart.getDate() + 6); // End of week (Saturday)

        const weekEntries = entries.filter((e) => {
            const entryDate = new Date(e.date);
            return entryDate >= weekStart && entryDate <= weekEnd;
        });

        const totalHours = weekEntries.reduce((sum, e) => sum + e.hours, 0);

        // Category breakdown
        const categoryTotals: Record<string, number> = {};
        weekEntries.forEach((entry) => {
            categoryTotals[entry.category] = (categoryTotals[entry.category] || 0) + entry.hours;
        });

        const topCategories = Object.entries(categoryTotals)
            .map(([category, hours]) => ({
                category,
                hours,
                percentage: (hours / totalHours) * 100,
            }))
            .sort((a, b) => b.hours - a.hours)
            .slice(0, 5);

        // Task breakdown
        const taskTotals: Record<string, number> = {};
        weekEntries.forEach((entry) => {
            taskTotals[entry.taskTitle] = (taskTotals[entry.taskTitle] || 0) + entry.hours;
        });

        const topTasks = Object.entries(taskTotals)
            .map(([taskTitle, hours]) => ({ taskTitle, hours }))
            .sort((a, b) => b.hours - a.hours)
            .slice(0, 5);

        // Daily summaries
        const dailySummaries: DailySummary[] = [];
        for (let i = 0; i < 7; i++) {
            const date = new Date(weekStart);
            date.setDate(weekStart.getDate() + i);
            const dateStr = date.toISOString().split('T')[0];
            dailySummaries.push(getDailySummary(dateStr));
        }

        return {
            weekStart: weekStart.toISOString().split('T')[0],
            weekEnd: weekEnd.toISOString().split('T')[0],
            totalHours,
            dailySummaries,
            topCategories,
            topTasks,
        };
    };

    const todaySummary = getDailySummary(new Date().toISOString().split('T')[0]);
    const weeklySummary = getWeeklySummary();

    // Category colors
    const getCategoryColor = (category: string): string => {
        switch (category) {
            case 'development':
                return 'primary';
            case 'meetings':
                return 'warning';
            case 'code-review':
                return 'info';
            case 'planning':
                return 'success';
            case 'learning':
                return 'secondary';
            case 'other':
                return 'default';
            default:
                return 'default';
        }
    };

    // Handlers
    const handleAddEntry = () => {
        if (newEntry.taskTitle.trim() && newEntry.hours > 0) {
            const entry: TimeEntry = {
                ...newEntry,
                id: `e${Date.now()}`,
            };
            setEntries([...entries, entry]);
            onAddEntry?.(newEntry);

            // Reset form
            setNewEntry({
                date: new Date().toISOString().split('T')[0],
                taskTitle: '',
                category: 'development',
                hours: 0,
                description: '',
                tags: [],
            });
        }
    };

    const handleDeleteEntry = (entryId: string) => {
        setEntries(entries.filter((e) => e.id !== entryId));
        onDeleteEntry?.(entryId);
    };

    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    };

    const formatDayOfWeek = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { weekday: 'short' });
    };

    return (
        <Box>
            <Typography variant="h5" gutterBottom>
                Time Logging
            </Typography>

            {/* Quick Entry Form */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="subtitle1" gutterBottom>
                    Log Time
                </Typography>
                <Grid container spacing={2}>
                    <Grid item xs={12} sm={6} md={3}>
                        <TextField
                            fullWidth
                            label="Date"
                            type="date"
                            value={newEntry.date}
                            onChange={(e) => setNewEntry({ ...newEntry, date: e.target.value })}
                            InputLabelProps={{ shrink: true }}
                            size="small"
                        />
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                        <FormControl fullWidth size="small">
                            <InputLabel>Task</InputLabel>
                            <Select
                                value={newEntry.taskTitle}
                                label="Task"
                                onChange={(e) => setNewEntry({ ...newEntry, taskTitle: e.target.value })}
                            >
                                {mockTasks.map((task) => (
                                    <MenuItem key={task.id} value={task.title}>
                                        {task.title}
                                    </MenuItem>
                                ))}
                                <MenuItem value="custom">Custom task...</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={6} md={2}>
                        <FormControl fullWidth size="small">
                            <InputLabel>Category</InputLabel>
                            <Select
                                value={newEntry.category}
                                label="Category"
                                onChange={(e) =>
                                    setNewEntry({
                                        ...newEntry,
                                        category: e.target.value as TimeEntry['category'],
                                    })
                                }
                            >
                                <MenuItem value="development">Development</MenuItem>
                                <MenuItem value="meetings">Meetings</MenuItem>
                                <MenuItem value="code-review">Code Review</MenuItem>
                                <MenuItem value="planning">Planning</MenuItem>
                                <MenuItem value="learning">Learning</MenuItem>
                                <MenuItem value="other">Other</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid item xs={12} sm={6} md={2}>
                        <TextField
                            fullWidth
                            label="Hours"
                            type="number"
                            value={newEntry.hours || ''}
                            onChange={(e) =>
                                setNewEntry({ ...newEntry, hours: parseFloat(e.target.value) || 0 })
                            }
                            inputProps={{ min: 0, max: 24, step: 0.5 }}
                            size="small"
                        />
                    </Grid>
                    <Grid item xs={12} sm={12} md={2}>
                        <Button
                            fullWidth
                            variant="contained"
                            onClick={handleAddEntry}
                            disabled={!newEntry.taskTitle || newEntry.hours <= 0}
                        >
                            Add Entry
                        </Button>
                    </Grid>
                    {newEntry.taskTitle === 'custom' && (
                        <Grid item xs={12}>
                            <TextField
                                fullWidth
                                label="Custom Task Title"
                                placeholder="Describe what you worked on..."
                                value={newEntry.description || ''}
                                onChange={(e) => setNewEntry({ ...newEntry, description: e.target.value })}
                                size="small"
                            />
                        </Grid>
                    )}
                </Grid>
            </Card>

            {/* Tabs */}
            <Tabs value={activeTab} onChange={(_, val) => setActiveTab(val)} sx={{ mb: 2 }}>
                <Tab label="Today" value="today" />
                <Tab label="This Week" value="week" />
            </Tabs>

            {/* Today View */}
            {activeTab === 'today' && (
                <Box>
                    {/* Today's Stats */}
                    <Grid container spacing={2} sx={{ mb: 3 }}>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Total Hours Today
                                </Typography>
                                <Typography variant="h4">{todaySummary.totalHours.toFixed(1)}</Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Entries Logged
                                </Typography>
                                <Typography variant="h4">{todaySummary.entries.length}</Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Top Category
                                </Typography>
                                <Typography variant="h4">
                                    {Object.entries(todaySummary.categoryBreakdown).length > 0
                                        ? Object.entries(todaySummary.categoryBreakdown).sort(
                                            ([, a], [, b]) => b - a
                                        )[0][0]
                                        : '—'}
                                </Typography>
                            </Card>
                        </Grid>
                    </Grid>

                    {/* Today's Entries */}
                    <Card sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Today's Time Entries
                        </Typography>
                        <Stack spacing={2}>
                            {todaySummary.entries.length === 0 ? (
                                <Typography variant="body2" color="text.secondary">
                                    No time entries for today. Log your first entry above!
                                </Typography>
                            ) : (
                                todaySummary.entries.map((entry) => (
                                    <Box
                                        key={entry.id}
                                        sx={{
                                            p: 2,
                                            borderLeft: 4,
                                            borderColor: `${getCategoryColor(entry.category)}.main`,
                                            bgcolor: 'action.hover',
                                            borderRadius: 1,
                                        }}
                                    >
                                        <Stack direction="row" justifyContent="space-between" alignItems="start">
                                            <Box sx={{ flex: 1 }}>
                                                <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                                                    <Typography variant="subtitle2">{entry.taskTitle}</Typography>
                                                    <Chip
                                                        label={entry.category}
                                                        size="small"
                                                        color={getCategoryColor(entry.category) as any}
                                                    />
                                                </Stack>
                                                {entry.description && (
                                                    <Typography variant="body2" color="text.secondary">
                                                        {entry.description}
                                                    </Typography>
                                                )}
                                                {entry.tags && entry.tags.length > 0 && (
                                                    <Stack direction="row" spacing={0.5} sx={{ mt: 1 }}>
                                                        {entry.tags.map((tag) => (
                                                            <Chip key={tag} label={tag} size="small" variant="outlined" />
                                                        ))}
                                                    </Stack>
                                                )}
                                            </Box>
                                            <Stack direction="row" alignItems="center" spacing={1}>
                                                <Typography variant="h6">{entry.hours}h</Typography>
                                                <Button
                                                    size="small"
                                                    color="error"
                                                    onClick={() => handleDeleteEntry(entry.id)}
                                                >
                                                    ✕
                                                </Button>
                                            </Stack>
                                        </Stack>
                                    </Box>
                                ))
                            )}
                        </Stack>
                    </Card>
                </Box>
            )}

            {/* Week View */}
            {activeTab === 'week' && (
                <Box>
                    {/* Weekly Stats */}
                    <Grid container spacing={2} sx={{ mb: 3 }}>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Total Hours This Week
                                </Typography>
                                <Typography variant="h4">{weeklySummary.totalHours.toFixed(1)}</Typography>
                                <LinearProgress
                                    variant="determinate"
                                    value={(weeklySummary.totalHours / targetHoursPerWeek) * 100}
                                    sx={{ mt: 1 }}
                                />
                                <Typography variant="caption" color="text.secondary">
                                    {((weeklySummary.totalHours / targetHoursPerWeek) * 100).toFixed(0)}% of{' '}
                                    {targetHoursPerWeek}h target
                                </Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Daily Average
                                </Typography>
                                <Typography variant="h4">
                                    {(weeklySummary.totalHours / 7).toFixed(1)}h
                                </Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Card sx={{ p: 2 }}>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Total Entries
                                </Typography>
                                <Typography variant="h4">
                                    {weeklySummary.dailySummaries.reduce(
                                        (sum, d) => sum + d.entries.length,
                                        0
                                    )}
                                </Typography>
                            </Card>
                        </Grid>
                    </Grid>

                    {/* Category Breakdown */}
                    <Card sx={{ p: 3, mb: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Time by Category
                        </Typography>
                        <Stack spacing={2}>
                            {weeklySummary.topCategories.map((cat) => (
                                <Box key={cat.category}>
                                    <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            <Chip
                                                label={cat.category}
                                                size="small"
                                                color={getCategoryColor(cat.category) as any}
                                            />
                                        </Stack>
                                        <Typography variant="body2">
                                            {cat.hours.toFixed(1)}h ({cat.percentage.toFixed(0)}%)
                                        </Typography>
                                    </Stack>
                                    <LinearProgress
                                        variant="determinate"
                                        value={cat.percentage}
                                        sx={{ height: 8, borderRadius: 1 }}
                                    />
                                </Box>
                            ))}
                        </Stack>
                    </Card>

                    {/* Top Tasks */}
                    <Card sx={{ p: 3, mb: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Top Tasks This Week
                        </Typography>
                        <Stack spacing={1}>
                            {weeklySummary.topTasks.map((task, index) => (
                                <Stack key={index} direction="row" justifyContent="space-between">
                                    <Typography variant="body2">{task.taskTitle}</Typography>
                                    <Typography variant="body2" fontWeight="medium">
                                        {task.hours.toFixed(1)}h
                                    </Typography>
                                </Stack>
                            ))}
                        </Stack>
                    </Card>

                    {/* Daily Breakdown */}
                    <Card sx={{ p: 3 }}>
                        <Typography variant="subtitle1" gutterBottom>
                            Daily Breakdown
                        </Typography>
                        <Grid container spacing={2}>
                            {weeklySummary.dailySummaries.map((day) => (
                                <Grid item xs={12} sm={6} md key={day.date}>
                                    <Box
                                        sx={{
                                            p: 2,
                                            border: 1,
                                            borderColor: 'divider',
                                            borderRadius: 1,
                                            textAlign: 'center',
                                        }}
                                    >
                                        <Typography variant="caption" color="text.secondary">
                                            {formatDayOfWeek(day.date)}
                                        </Typography>
                                        <Typography variant="subtitle2">{formatDate(day.date)}</Typography>
                                        <Typography variant="h5" sx={{ my: 1 }}>
                                            {day.totalHours.toFixed(1)}h
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            {day.entries.length} entries
                                        </Typography>
                                    </Box>
                                </Grid>
                            ))}
                        </Grid>
                    </Card>
                </Box>
            )}
        </Box>
    );
};
