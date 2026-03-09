import React, { useState } from 'react';
import {
    Card,
    CardContent,
    Chip,
    Avatar,
    IconButton,
    Tabs,
    Tab,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    LinearProgress,
    Alert,
    Divider,
    List,
    ListItem,
    ListItemText,
    ListItemAvatar,
    ListItemSecondaryAction,
    AvatarGroup,
} from '@ghatana/ui';
import {
    Folder,
    Add,
    MoreVert,
    DragIndicator,
    Comment,
    AttachFile,
    People,
    Timeline,
    Assignment,
    CheckCircle,
    Schedule,
    Flag,
    Edit,
    Delete,
    Visibility,
} from '@ghatana/ui/icons';

// ============================================================================
// Type Definitions
// ============================================================================

interface Project {
    id: string;
    name: string;
    description: string;
    status: 'planning' | 'active' | 'on_hold' | 'completed';
    priority: 'low' | 'medium' | 'high' | 'critical';
    startDate: Date;
    dueDate: Date;
    progress: number; // 0-100
    owner: ProjectMember;
    team: ProjectMember[];
}

interface ProjectMember {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    role: string;
}

interface Task {
    id: string;
    title: string;
    description?: string;
    status: 'todo' | 'in_progress' | 'review' | 'done';
    priority: 'low' | 'medium' | 'high';
    assignee?: ProjectMember;
    dueDate?: Date;
    tags?: string[];
    comments: Comment[];
    attachments: Attachment[];
}

interface Comment {
    id: string;
    author: ProjectMember;
    content: string;
    timestamp: Date;
}

interface Attachment {
    id: string;
    name: string;
    url: string;
    uploadedBy: ProjectMember;
    uploadedAt: Date;
    size: number; // bytes
}

interface Activity {
    id: string;
    type: 'task_created' | 'task_updated' | 'comment_added' | 'file_uploaded' | 'member_added';
    user: ProjectMember;
    description: string;
    timestamp: Date;
    relatedTaskId?: string;
}

export interface ProjectWorkspaceProps {
    project?: Project;
    tasks?: Task[];
    activities?: Activity[];
    onUpdateProject?: (updates: Partial<Project>) => void;
    onCreateTask?: (task: Omit<Task, 'id' | 'comments' | 'attachments'>) => void;
    onUpdateTask?: (taskId: string, updates: Partial<Task>) => void;
    onDeleteTask?: (taskId: string) => void;
    onAddComment?: (taskId: string, content: string) => void;
    onUploadFile?: (taskId: string, file: File) => void;
    onAddMember?: (member: ProjectMember) => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockProject: Project = {
    id: 'proj-1',
    name: 'User Authentication Redesign',
    description: 'Modernize the authentication system with OAuth2, SSO, and improved security',
    status: 'active',
    priority: 'high',
    startDate: new Date(2024, 10, 1),
    dueDate: new Date(2025, 0, 31),
    progress: 65,
    owner: {
        id: 'u1',
        name: 'Sarah Johnson',
        email: 'sarah@company.com',
        role: 'Tech Lead',
    },
    team: [
        { id: 'u1', name: 'Sarah Johnson', email: 'sarah@company.com', role: 'Tech Lead' },
        { id: 'u2', name: 'Michael Chen', email: 'michael@company.com', role: 'Backend Engineer' },
        { id: 'u3', name: 'Emily Rodriguez', email: 'emily@company.com', role: 'Frontend Engineer' },
        { id: 'u4', name: 'David Kim', email: 'david@company.com', role: 'QA Engineer' },
    ],
};

const mockTasks: Task[] = [
    {
        id: 't1',
        title: 'Design OAuth2 flow',
        description: 'Create detailed flow diagrams for OAuth2 implementation',
        status: 'done',
        priority: 'high',
        assignee: mockProject.team[0],
        dueDate: new Date(2024, 11, 15),
        tags: ['design', 'backend'],
        comments: [
            {
                id: 'c1',
                author: mockProject.team[1],
                content: 'Flow diagram looks good, approved!',
                timestamp: new Date(2024, 11, 14),
            },
        ],
        attachments: [
            {
                id: 'a1',
                name: 'oauth2-flow.pdf',
                url: '/files/oauth2-flow.pdf',
                uploadedBy: mockProject.team[0],
                uploadedAt: new Date(2024, 11, 13),
                size: 245000,
            },
        ],
    },
    {
        id: 't2',
        title: 'Implement backend API endpoints',
        description: 'Create /auth/login, /auth/callback, /auth/refresh endpoints',
        status: 'in_progress',
        priority: 'high',
        assignee: mockProject.team[1],
        dueDate: new Date(2024, 11, 20),
        tags: ['backend', 'api'],
        comments: [],
        attachments: [],
    },
    {
        id: 't3',
        title: 'Build login UI components',
        description: 'Design and implement new login and registration forms',
        status: 'in_progress',
        priority: 'medium',
        assignee: mockProject.team[2],
        dueDate: new Date(2024, 11, 22),
        tags: ['frontend', 'ui'],
        comments: [
            {
                id: 'c2',
                author: mockProject.team[0],
                content: 'Please use the new design system components',
                timestamp: new Date(2024, 11, 10),
            },
        ],
        attachments: [],
    },
    {
        id: 't4',
        title: 'Write integration tests',
        description: 'Test OAuth flow end-to-end',
        status: 'todo',
        priority: 'medium',
        assignee: mockProject.team[3],
        dueDate: new Date(2024, 11, 25),
        tags: ['testing', 'qa'],
        comments: [],
        attachments: [],
    },
    {
        id: 't5',
        title: 'Security audit',
        description: 'Review implementation for security vulnerabilities',
        status: 'todo',
        priority: 'high',
        tags: ['security'],
        comments: [],
        attachments: [],
    },
];

const mockActivities: Activity[] = [
    {
        id: 'act1',
        type: 'task_updated',
        user: mockProject.team[1],
        description: 'moved "Implement backend API endpoints" to In Progress',
        timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000),
        relatedTaskId: 't2',
    },
    {
        id: 'act2',
        type: 'comment_added',
        user: mockProject.team[0],
        description: 'commented on "Build login UI components"',
        timestamp: new Date(Date.now() - 4 * 60 * 60 * 1000),
        relatedTaskId: 't3',
    },
    {
        id: 'act3',
        type: 'file_uploaded',
        user: mockProject.team[0],
        description: 'uploaded oauth2-flow.pdf to "Design OAuth2 flow"',
        timestamp: new Date(Date.now() - 6 * 60 * 60 * 1000),
        relatedTaskId: 't1',
    },
    {
        id: 'act4',
        type: 'task_created',
        user: mockProject.team[0],
        description: 'created task "Security audit"',
        timestamp: new Date(Date.now() - 8 * 60 * 60 * 1000),
        relatedTaskId: 't5',
    },
];

// ============================================================================
// Component
// ============================================================================

export const ProjectWorkspace: React.FC<ProjectWorkspaceProps> = ({
    project = mockProject,
    tasks = mockTasks,
    activities = mockActivities,
    onUpdateProject,
    onCreateTask,
    onUpdateTask,
    onDeleteTask,
    onAddComment,
    onUploadFile,
    onAddMember,
}) => {
    const [selectedTab, setSelectedTab] = useState<number>(0);
    const [selectedTask, setSelectedTask] = useState<Task | null>(null);
    const [taskDialogOpen, setTaskDialogOpen] = useState(false);
    const [createTaskDialogOpen, setCreateTaskDialogOpen] = useState(false);

    // Form state
    const [newTaskTitle, setNewTaskTitle] = useState('');
    const [newTaskDescription, setNewTaskDescription] = useState('');
    const [newTaskStatus, setNewTaskStatus] = useState<Task['status']>('todo');
    const [newTaskPriority, setNewTaskPriority] = useState<Task['priority']>('medium');
    const [newTaskAssignee, setNewTaskAssignee] = useState<string>('');
    const [newComment, setNewComment] = useState('');

    const tasksByStatus = {
        todo: tasks.filter((t) => t.status === 'todo'),
        in_progress: tasks.filter((t) => t.status === 'in_progress'),
        review: tasks.filter((t) => t.status === 'review'),
        done: tasks.filter((t) => t.status === 'done'),
    };

    const handleCreateTask = () => {
        if (!newTaskTitle) return;

        const newTask: Omit<Task, 'id' | 'comments' | 'attachments'> = {
            title: newTaskTitle,
            description: newTaskDescription || undefined,
            status: newTaskStatus,
            priority: newTaskPriority,
            assignee: newTaskAssignee ? project.team.find((m) => m.id === newTaskAssignee) : undefined,
            tags: [],
        };

        onCreateTask?.(newTask);
        setCreateTaskDialogOpen(false);
        resetCreateForm();
    };

    const resetCreateForm = () => {
        setNewTaskTitle('');
        setNewTaskDescription('');
        setNewTaskStatus('todo');
        setNewTaskPriority('medium');
        setNewTaskAssignee('');
    };

    const handleAddComment = () => {
        if (!selectedTask || !newComment.trim()) return;
        onAddComment?.(selectedTask.id, newComment);
        setNewComment('');
    };

    const handleTaskClick = (task: Task) => {
        setSelectedTask(task);
        setTaskDialogOpen(true);
    };

    const getStatusColor = (status: Task['status']): 'default' | 'info' | 'warning' | 'success' => {
        switch (status) {
            case 'todo':
                return 'default';
            case 'in_progress':
                return 'info';
            case 'review':
                return 'warning';
            case 'done':
                return 'success';
            default:
                return 'default';
        }
    };

    const getPriorityColor = (
        priority: Task['priority'] | Project['priority']
    ): 'default' | 'info' | 'warning' | 'error' => {
        switch (priority) {
            case 'low':
                return 'default';
            case 'medium':
                return 'info';
            case 'high':
                return 'warning';
            case 'critical':
                return 'error';
            default:
                return 'default';
        }
    };

    const getProjectStatusColor = (status: Project['status']): 'default' | 'info' | 'warning' | 'success' => {
        switch (status) {
            case 'planning':
                return 'default';
            case 'active':
                return 'info';
            case 'on_hold':
                return 'warning';
            case 'completed':
                return 'success';
            default:
                return 'default';
        }
    };

    const formatDate = (date: Date): string => {
        return date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const formatTimestamp = (date: Date): string => {
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / (1000 * 60));
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return formatDate(date);
    };

    const formatFileSize = (bytes: number): string => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    };

    return (
        <div className="space-y-6">
            {/* Project Header */}
            <Card>
                <CardContent className="p-6">
                    <div className="flex items-start justify-between mb-4">
                        <div className="flex items-center gap-3">
                            <Folder fontSize="large" className="text-primary" />
                            <div>
                                <h1 className="text-3xl font-bold dark:text-white">{project.name}</h1>
                                <p className="text-gray-600 dark:text-gray-400 mt-1">{project.description}</p>
                            </div>
                        </div>
                        <div className="flex items-center gap-2">
                            <Chip label={project.status.replace('_', ' ').toUpperCase()} color={getProjectStatusColor(project.status)} />
                            <Chip label={project.priority.toUpperCase()} color={getPriorityColor(project.priority)} />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Owner</p>
                            <div className="flex items-center gap-2">
                                <Avatar src={project.owner.avatar} size="small">
                                    {project.owner.name.split(' ').map((n) => n[0]).join('')}
                                </Avatar>
                                <span className="dark:text-white">{project.owner.name}</span>
                            </div>
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Timeline</p>
                            <p className="dark:text-white">
                                {formatDate(project.startDate)} - {formatDate(project.dueDate)}
                            </p>
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Team</p>
                            <AvatarGroup max={4}>
                                {project.team.map((member) => (
                                    <Avatar key={member.id} src={member.avatar} size="small" title={member.name}>
                                        {member.name.split(' ').map((n) => n[0]).join('')}
                                    </Avatar>
                                ))}
                            </AvatarGroup>
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Progress</p>
                            <div className="flex items-center gap-2">
                                <LinearProgress variant="determinate" value={project.progress} className="flex-1" />
                                <span className="dark:text-white">{project.progress}%</span>
                            </div>
                        </div>
                    </div>

                    <div className="flex items-center gap-4">
                        <div className="flex items-center gap-2 text-sm">
                            <Assignment className="text-gray-400" fontSize="small" />
                            <span className="dark:text-white">{tasks.length} tasks</span>
                        </div>
                        <div className="flex items-center gap-2 text-sm">
                            <CheckCircle className="text-success" fontSize="small" />
                            <span className="dark:text-white">{tasksByStatus.done.length} completed</span>
                        </div>
                        <div className="flex items-center gap-2 text-sm">
                            <Schedule className="text-info" fontSize="small" />
                            <span className="dark:text-white">{tasksByStatus.in_progress.length} in progress</span>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)}>
                    <Tab label="Board" />
                    <Tab label="List" />
                    <Tab label="Activity" />
                    <Tab label="Files" />
                </Tabs>

                <CardContent className="p-6">
                    {/* Board View */}
                    {selectedTab === 0 && (
                        <div>
                            <div className="flex items-center justify-between mb-4">
                                <h2 className="text-xl font-bold dark:text-white">Task Board</h2>
                                <Button variant="contained" color="primary" startIcon={<Add />} onClick={() => setCreateTaskDialogOpen(true)}>
                                    New Task
                                </Button>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                                {/* To Do Column */}
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold dark:text-white">To Do</h3>
                                        <Chip label={tasksByStatus.todo.length} size="small" />
                                    </div>
                                    {tasksByStatus.todo.map((task) => (
                                        <Card
                                            key={task.id}
                                            className="cursor-pointer hover:shadow-lg transition-shadow"
                                            onClick={() => handleTaskClick(task)}
                                        >
                                            <CardContent className="p-3">
                                                <div className="flex items-start justify-between mb-2">
                                                    <p className="font-semibold dark:text-white text-sm">{task.title}</p>
                                                    <Chip label={task.priority.toUpperCase()} size="small" color={getPriorityColor(task.priority)} />
                                                </div>
                                                {task.assignee && (
                                                    <div className="flex items-center gap-2 mt-2">
                                                        <Avatar src={task.assignee.avatar} size="small">
                                                            {task.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                        </Avatar>
                                                        <span className="text-xs dark:text-gray-400">{task.assignee.name}</span>
                                                    </div>
                                                )}
                                                {task.tags && task.tags.length > 0 && (
                                                    <div className="flex flex-wrap gap-1 mt-2">
                                                        {task.tags.map((tag, idx) => (
                                                            <Chip key={idx} label={tag} size="small" variant="outlined" />
                                                        ))}
                                                    </div>
                                                )}
                                                <div className="flex items-center gap-3 mt-2 text-xs text-gray-600 dark:text-gray-400">
                                                    {task.comments.length > 0 && (
                                                        <span className="flex items-center gap-1">
                                                            <Comment fontSize="inherit" />
                                                            {task.comments.length}
                                                        </span>
                                                    )}
                                                    {task.attachments.length > 0 && (
                                                        <span className="flex items-center gap-1">
                                                            <AttachFile fontSize="inherit" />
                                                            {task.attachments.length}
                                                        </span>
                                                    )}
                                                </div>
                                            </CardContent>
                                        </Card>
                                    ))}
                                </div>

                                {/* In Progress Column */}
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold dark:text-white">In Progress</h3>
                                        <Chip label={tasksByStatus.in_progress.length} size="small" color="info" />
                                    </div>
                                    {tasksByStatus.in_progress.map((task) => (
                                        <Card
                                            key={task.id}
                                            className="cursor-pointer hover:shadow-lg transition-shadow"
                                            onClick={() => handleTaskClick(task)}
                                        >
                                            <CardContent className="p-3">
                                                <div className="flex items-start justify-between mb-2">
                                                    <p className="font-semibold dark:text-white text-sm">{task.title}</p>
                                                    <Chip label={task.priority.toUpperCase()} size="small" color={getPriorityColor(task.priority)} />
                                                </div>
                                                {task.assignee && (
                                                    <div className="flex items-center gap-2 mt-2">
                                                        <Avatar src={task.assignee.avatar} size="small">
                                                            {task.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                        </Avatar>
                                                        <span className="text-xs dark:text-gray-400">{task.assignee.name}</span>
                                                    </div>
                                                )}
                                                {task.tags && task.tags.length > 0 && (
                                                    <div className="flex flex-wrap gap-1 mt-2">
                                                        {task.tags.map((tag, idx) => (
                                                            <Chip key={idx} label={tag} size="small" variant="outlined" />
                                                        ))}
                                                    </div>
                                                )}
                                                <div className="flex items-center gap-3 mt-2 text-xs text-gray-600 dark:text-gray-400">
                                                    {task.comments.length > 0 && (
                                                        <span className="flex items-center gap-1">
                                                            <Comment fontSize="inherit" />
                                                            {task.comments.length}
                                                        </span>
                                                    )}
                                                    {task.attachments.length > 0 && (
                                                        <span className="flex items-center gap-1">
                                                            <AttachFile fontSize="inherit" />
                                                            {task.attachments.length}
                                                        </span>
                                                    )}
                                                </div>
                                            </CardContent>
                                        </Card>
                                    ))}
                                </div>

                                {/* Review Column */}
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold dark:text-white">Review</h3>
                                        <Chip label={tasksByStatus.review.length} size="small" color="warning" />
                                    </div>
                                    {tasksByStatus.review.map((task) => (
                                        <Card
                                            key={task.id}
                                            className="cursor-pointer hover:shadow-lg transition-shadow"
                                            onClick={() => handleTaskClick(task)}
                                        >
                                            <CardContent className="p-3">
                                                <div className="flex items-start justify-between mb-2">
                                                    <p className="font-semibold dark:text-white text-sm">{task.title}</p>
                                                    <Chip label={task.priority.toUpperCase()} size="small" color={getPriorityColor(task.priority)} />
                                                </div>
                                                {task.assignee && (
                                                    <div className="flex items-center gap-2 mt-2">
                                                        <Avatar src={task.assignee.avatar} size="small">
                                                            {task.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                        </Avatar>
                                                        <span className="text-xs dark:text-gray-400">{task.assignee.name}</span>
                                                    </div>
                                                )}
                                            </CardContent>
                                        </Card>
                                    ))}
                                </div>

                                {/* Done Column */}
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold dark:text-white">Done</h3>
                                        <Chip label={tasksByStatus.done.length} size="small" color="success" />
                                    </div>
                                    {tasksByStatus.done.map((task) => (
                                        <Card
                                            key={task.id}
                                            className="cursor-pointer hover:shadow-lg transition-shadow opacity-80"
                                            onClick={() => handleTaskClick(task)}
                                        >
                                            <CardContent className="p-3">
                                                <div className="flex items-start justify-between mb-2">
                                                    <p className="font-semibold dark:text-white text-sm line-through">{task.title}</p>
                                                    <CheckCircle className="text-success" fontSize="small" />
                                                </div>
                                                {task.assignee && (
                                                    <div className="flex items-center gap-2 mt-2">
                                                        <Avatar src={task.assignee.avatar} size="small">
                                                            {task.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                        </Avatar>
                                                        <span className="text-xs dark:text-gray-400">{task.assignee.name}</span>
                                                    </div>
                                                )}
                                            </CardContent>
                                        </Card>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    {/* List View */}
                    {selectedTab === 1 && (
                        <div>
                            <div className="flex items-center justify-between mb-4">
                                <h2 className="text-xl font-bold dark:text-white">All Tasks</h2>
                                <Button variant="contained" color="primary" startIcon={<Add />} onClick={() => setCreateTaskDialogOpen(true)}>
                                    New Task
                                </Button>
                            </div>

                            <List>
                                {tasks.map((task) => (
                                    <React.Fragment key={task.id}>
                                        <ListItem className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800" onClick={() => handleTaskClick(task)}>
                                            <ListItemAvatar>
                                                <DragIndicator className="text-gray-400" />
                                            </ListItemAvatar>
                                            <ListItemText
                                                primary={
                                                    <div className="flex items-center gap-2">
                                                        <span className={`dark:text-white ${task.status === 'done' ? 'line-through' : ''}`}>
                                                            {task.title}
                                                        </span>
                                                        <Chip label={task.status.replace('_', ' ').toUpperCase()} size="small" color={getStatusColor(task.status)} />
                                                        <Chip label={task.priority.toUpperCase()} size="small" color={getPriorityColor(task.priority)} />
                                                    </div>
                                                }
                                                secondary={
                                                    <span className="dark:text-gray-400">
                                                        {task.assignee ? `Assigned to ${task.assignee.name}` : 'Unassigned'}
                                                        {task.dueDate && ` · Due ${formatDate(task.dueDate)}`}
                                                    </span>
                                                }
                                            />
                                            <ListItemSecondaryAction>
                                                <div className="flex items-center gap-2">
                                                    {task.comments.length > 0 && (
                                                        <Chip label={task.comments.length} size="small" icon={<Comment fontSize="small" />} />
                                                    )}
                                                    {task.assignee && (
                                                        <Avatar src={task.assignee.avatar} size="small">
                                                            {task.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                        </Avatar>
                                                    )}
                                                </div>
                                            </ListItemSecondaryAction>
                                        </ListItem>
                                        <Divider />
                                    </React.Fragment>
                                ))}
                            </List>
                        </div>
                    )}

                    {/* Activity Feed */}
                    {selectedTab === 2 && (
                        <div>
                            <h2 className="text-xl font-bold dark:text-white mb-4">Recent Activity</h2>
                            <List>
                                {activities.map((activity) => (
                                    <React.Fragment key={activity.id}>
                                        <ListItem>
                                            <ListItemAvatar>
                                                <Avatar src={activity.user.avatar}>
                                                    {activity.user.name.split(' ').map((n) => n[0]).join('')}
                                                </Avatar>
                                            </ListItemAvatar>
                                            <ListItemText
                                                primary={
                                                    <span className="dark:text-white">
                                                        <strong>{activity.user.name}</strong> {activity.description}
                                                    </span>
                                                }
                                                secondary={<span className="dark:text-gray-400">{formatTimestamp(activity.timestamp)}</span>}
                                            />
                                        </ListItem>
                                        <Divider />
                                    </React.Fragment>
                                ))}
                            </List>
                        </div>
                    )}

                    {/* Files Tab */}
                    {selectedTab === 3 && (
                        <div>
                            <h2 className="text-xl font-bold dark:text-white mb-4">Files</h2>
                            {tasks.flatMap((t) => t.attachments).length === 0 ? (
                                <Alert severity="info">No files uploaded yet.</Alert>
                            ) : (
                                <List>
                                    {tasks.flatMap((task) =>
                                        task.attachments.map((attachment) => (
                                            <React.Fragment key={attachment.id}>
                                                <ListItem>
                                                    <ListItemAvatar>
                                                        <AttachFile className="text-gray-400" />
                                                    </ListItemAvatar>
                                                    <ListItemText
                                                        primary={<span className="dark:text-white">{attachment.name}</span>}
                                                        secondary={
                                                            <span className="dark:text-gray-400">
                                                                Uploaded by {attachment.uploadedBy.name} · {formatFileSize(attachment.size)} ·{' '}
                                                                {formatTimestamp(attachment.uploadedAt)}
                                                            </span>
                                                        }
                                                    />
                                                    <ListItemSecondaryAction>
                                                        <IconButton size="small">
                                                            <Visibility fontSize="small" />
                                                        </IconButton>
                                                    </ListItemSecondaryAction>
                                                </ListItem>
                                                <Divider />
                                            </React.Fragment>
                                        ))
                                    )}
                                </List>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Task Detail Dialog */}
            <Dialog open={taskDialogOpen} onClose={() => setTaskDialogOpen(false)} maxWidth="md" fullWidth>
                {selectedTask && (
                    <>
                        <DialogTitle>
                            <div className="flex items-center justify-between">
                                <h2 className="text-xl font-bold dark:text-white">{selectedTask.title}</h2>
                                <div className="flex items-center gap-2">
                                    <Chip label={selectedTask.status.replace('_', ' ').toUpperCase()} color={getStatusColor(selectedTask.status)} />
                                    <Chip label={selectedTask.priority.toUpperCase()} color={getPriorityColor(selectedTask.priority)} />
                                </div>
                            </div>
                        </DialogTitle>
                        <DialogContent>
                            <div className="space-y-4">
                                {selectedTask.description && (
                                    <div>
                                        <h3 className="font-semibold dark:text-white mb-2">Description</h3>
                                        <p className="text-gray-700 dark:text-gray-300">{selectedTask.description}</p>
                                    </div>
                                )}

                                <div className="grid grid-cols-2 gap-4">
                                    {selectedTask.assignee && (
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Assignee</p>
                                            <div className="flex items-center gap-2">
                                                <Avatar src={selectedTask.assignee.avatar} size="small">
                                                    {selectedTask.assignee.name.split(' ').map((n) => n[0]).join('')}
                                                </Avatar>
                                                <span className="dark:text-white">{selectedTask.assignee.name}</span>
                                            </div>
                                        </div>
                                    )}
                                    {selectedTask.dueDate && (
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">Due Date</p>
                                            <p className="dark:text-white">{formatDate(selectedTask.dueDate)}</p>
                                        </div>
                                    )}
                                </div>

                                {selectedTask.tags && selectedTask.tags.length > 0 && (
                                    <div>
                                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Tags</p>
                                        <div className="flex flex-wrap gap-1">
                                            {selectedTask.tags.map((tag, idx) => (
                                                <Chip key={idx} label={tag} size="small" variant="outlined" />
                                            ))}
                                        </div>
                                    </div>
                                )}

                                <Divider />

                                {/* Comments */}
                                <div>
                                    <h3 className="font-semibold dark:text-white mb-3">Comments ({selectedTask.comments.length})</h3>
                                    {selectedTask.comments.length === 0 ? (
                                        <Alert severity="info">No comments yet.</Alert>
                                    ) : (
                                        <List>
                                            {selectedTask.comments.map((comment) => (
                                                <React.Fragment key={comment.id}>
                                                    <ListItem>
                                                        <ListItemAvatar>
                                                            <Avatar src={comment.author.avatar}>
                                                                {comment.author.name.split(' ').map((n) => n[0]).join('')}
                                                            </Avatar>
                                                        </ListItemAvatar>
                                                        <ListItemText
                                                            primary={
                                                                <div className="flex items-center justify-between">
                                                                    <span className="font-semibold dark:text-white">{comment.author.name}</span>
                                                                    <span className="text-xs text-gray-600 dark:text-gray-400">
                                                                        {formatTimestamp(comment.timestamp)}
                                                                    </span>
                                                                </div>
                                                            }
                                                            secondary={<span className="dark:text-gray-300">{comment.content}</span>}
                                                        />
                                                    </ListItem>
                                                    <Divider />
                                                </React.Fragment>
                                            ))}
                                        </List>
                                    )}

                                    <div className="flex gap-2 mt-3">
                                        <TextField
                                            fullWidth
                                            size="small"
                                            placeholder="Add a comment..."
                                            value={newComment}
                                            onChange={(e) => setNewComment(e.target.value)}
                                            onKeyPress={(e) => e.key === 'Enter' && handleAddComment()}
                                        />
                                        <Button onClick={handleAddComment} variant="contained" size="small">
                                            Send
                                        </Button>
                                    </div>
                                </div>

                                {/* Attachments */}
                                {selectedTask.attachments.length > 0 && (
                                    <div>
                                        <h3 className="font-semibold dark:text-white mb-3">
                                            Attachments ({selectedTask.attachments.length})
                                        </h3>
                                        <List>
                                            {selectedTask.attachments.map((attachment) => (
                                                <React.Fragment key={attachment.id}>
                                                    <ListItem>
                                                        <ListItemAvatar>
                                                            <AttachFile className="text-gray-400" />
                                                        </ListItemAvatar>
                                                        <ListItemText
                                                            primary={<span className="dark:text-white">{attachment.name}</span>}
                                                            secondary={
                                                                <span className="dark:text-gray-400">
                                                                    {formatFileSize(attachment.size)} · {attachment.uploadedBy.name}
                                                                </span>
                                                            }
                                                        />
                                                    </ListItem>
                                                    <Divider />
                                                </React.Fragment>
                                            ))}
                                        </List>
                                    </div>
                                )}
                            </div>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setTaskDialogOpen(false)}>Close</Button>
                            <Button onClick={() => onDeleteTask?.(selectedTask.id)} color="error" variant="outlined">
                                Delete
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Create Task Dialog */}
            <Dialog open={createTaskDialogOpen} onClose={() => setCreateTaskDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Create New Task</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 pt-2">
                        <TextField
                            fullWidth
                            label="Task Title"
                            value={newTaskTitle}
                            onChange={(e) => setNewTaskTitle(e.target.value)}
                            required
                        />

                        <TextField
                            fullWidth
                            multiline
                            rows={3}
                            label="Description"
                            value={newTaskDescription}
                            onChange={(e) => setNewTaskDescription(e.target.value)}
                        />

                        <FormControl fullWidth>
                            <InputLabel>Status</InputLabel>
                            <Select value={newTaskStatus} onChange={(e) => setNewTaskStatus(e.target.value as Task['status'])} label="Status">
                                <MenuItem value="todo">To Do</MenuItem>
                                <MenuItem value="in_progress">In Progress</MenuItem>
                                <MenuItem value="review">Review</MenuItem>
                                <MenuItem value="done">Done</MenuItem>
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Priority</InputLabel>
                            <Select
                                value={newTaskPriority}
                                onChange={(e) => setNewTaskPriority(e.target.value as Task['priority'])}
                                label="Priority"
                            >
                                <MenuItem value="low">Low</MenuItem>
                                <MenuItem value="medium">Medium</MenuItem>
                                <MenuItem value="high">High</MenuItem>
                                <MenuItem value="critical">Critical</MenuItem>
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Assignee</InputLabel>
                            <Select value={newTaskAssignee} onChange={(e) => setNewTaskAssignee(e.target.value as string)} label="Assignee">
                                <MenuItem value="">
                                    <em>Unassigned</em>
                                </MenuItem>
                                {project.team.map((member) => (
                                    <MenuItem key={member.id} value={member.id}>
                                        {member.name} - {member.role}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button
                        onClick={() => {
                            setCreateTaskDialogOpen(false);
                            resetCreateForm();
                        }}
                    >
                        Cancel
                    </Button>
                    <Button onClick={handleCreateTask} variant="contained" color="primary" disabled={!newTaskTitle}>
                        Create Task
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default ProjectWorkspace;
