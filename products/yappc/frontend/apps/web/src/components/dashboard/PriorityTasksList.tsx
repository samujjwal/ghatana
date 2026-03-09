import { useNavigate } from 'react-router';
import { ChevronRight, Code as CodeIcon, ClipboardList as AssignmentIcon, CloudUpload as DeployIcon, ArrowRight as ArrowForwardIcon, CheckCircle as CheckCircleOutline, Clock as AccessTime, Ban as Block } from 'lucide-react';
import { Typography, Button, Chip, Box, Surface as Paper, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Stack } from '@ghatana/ui';

/**
 * Priority Task interface 
 */
export interface PriorityTask {
    id: string;
    title: string;
    project: string;
    projectId: string;
    type: 'Design' | 'Code' | 'Deploy';
    priority: 'High' | 'Urgent' | 'Medium' | 'Low';
    persona: string;
    dueDate?: string;
    isBlocked?: boolean;
}

interface PriorityTasksListProps {
    tasks: PriorityTask[];
    onTaskClick: (task: PriorityTask) => void;
    onViewAll: () => void;
}

/**
 * PriorityTasksList component
 * 
 * Displays a list of actionable high-priority tasks.
 */
export function PriorityTasksList({ tasks, onTaskClick, onViewAll }: PriorityTasksListProps) {
    return (
        <div className="mb-10">
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography as="h6" fontWeight="bold" className="flex items-center gap-2">
                    My Priority Tasks
                    <Chip label={tasks.length} size="sm" tone="primary" />
                </Typography>
                <Button endIcon={<ArrowForwardIcon />} size="sm" onClick={onViewAll}>View all</Button>
            </Box>
            <Paper variant="outlined" className="rounded-lg overflow-hidden">
                {tasks.length === 0 ? (
                    <Box className="p-8 text-center text-gray-500 dark:text-gray-400">
                        <CheckCircleOutline className="mb-2 text-5xl opacity-[0.5]" />
                        <Typography as="p">
                            No priority tasks. You're all caught up!
                        </Typography>
                    </Box>
                ) : (
                    <>
                        <List disablePadding>
                            {tasks.map((task, index) => (
                                <div key={task.id}>
                                    <ListItemButton onClick={() => onTaskClick(task)} className="py-4">
                                        <ListItemIcon>
                                            {task.type === 'Code' && <CodeIcon tone="primary" />}
                                            {task.type === 'Design' && <AssignmentIcon tone="secondary" />}
                                            {task.type === 'Deploy' && <DeployIcon tone="success" />}
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={
                                                <Typography as="p" className="text-lg font-medium" fontWeight="medium">
                                                    {task.title}
                                                </Typography>
                                            }
                                            secondaryTypographyProps={{ component: 'div' }}
                                            secondary={
                                                <Box className="mt-1">
                                                    <Typography as="p" className="text-sm" color="text.secondary">
                                                        {task.project}
                                                    </Typography>
                                                    <Stack direction="row" spacing={2} className="mt-1">
                                                        <Box
                                                            component="span"
                                                            style={{ color: task.priority === 'Urgent' ? 'error.main' :
                                                                    task.priority === 'High' ? 'warning.main' : 'text.secondary' }} >
                                                            {task.priority}
                                                        </Box>
                                                        {task.dueDate && (
                                                            <Stack direction="row" spacing={0.5} alignItems="center" className="text-gray-500 dark:text-gray-400">
                                                                <AccessTime className="text-sm" />
                                                                <Typography as="span" className="text-xs text-gray-500">{new Date(task.dueDate).toLocaleDateString()}</Typography>
                                                            </Stack>
                                                        )}
                                                        {task.isBlocked && (
                                                            <Stack direction="row" spacing={0.5} alignItems="center" className="text-red-600">
                                                                <Block className="text-sm" />
                                                                <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">Blocked</Typography>
                                                            </Stack>
                                                        )}
                                                    </Stack>
                                                </Box>
                                            }
                                        />
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            <Chip label={task.type} size="sm" variant="outlined" />
                                            <Chip
                                                label={task.persona}
                                                size="sm"
                                                className="h-[20px] text-[0.7rem] bg-gray-100 dark:bg-gray-800"
                                            />
                                            <ChevronRight color="action" />
                                        </Stack>
                                    </ListItemButton>
                                    {index < tasks.length - 1 && <Box className="border-gray-200 dark:border-gray-700 border-b" />}
                                </div>
                            ))}
                        </List>
                        <Box className="p-2 text-center bg-gray-100 dark:bg-gray-800">
                            <Button size="sm" endIcon={<ArrowForwardIcon />} onClick={onViewAll}>Go to Inbox</Button>
                        </Box>
                    </>
                )}
            </Paper>
        </div>
    );
}
