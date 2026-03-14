import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
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
    Checkbox,
    FormControlLabel,
    Alert,
    Divider,
    List,
    ListItem,
    ListItemText,
    ListItemAvatar,
    ListItemSecondaryAction,
} from '@ghatana/design-system';
import {
    CalendarToday,
    Add,
    Edit,
    Delete,
    CheckCircle,
    RadioButtonUnchecked,
    Notes,
    Assignment,
    EmojiEmotions,
    TrendingUp,
    MoreVert,
    Schedule,
    Person,
} from '@ghatana/design-system/icons';

// ============================================================================
// Type Definitions
// ============================================================================

interface OneOnOneMeeting {
    id: string;
    employeeId: string;
    employeeName: string;
    employeeAvatar?: string;
    employeeRole: string;
    date: Date;
    duration: number; // minutes
    status: 'scheduled' | 'completed' | 'cancelled';
    agenda: AgendaItem[];
    notes?: string;
    actionItems: ActionItem[];
    mood?: 'great' | 'good' | 'neutral' | 'concerned' | 'struggling';
    topics?: string[];
}

interface AgendaItem {
    id: string;
    title: string;
    description?: string;
    completed: boolean;
    addedBy: 'manager' | 'employee';
}

interface ActionItem {
    id: string;
    title: string;
    description?: string;
    assignee: 'manager' | 'employee';
    dueDate?: Date;
    completed: boolean;
    completedDate?: Date;
}

interface MeetingTemplate {
    id: string;
    name: string;
    agendaItems: string[];
}

export interface OneOnOneTrackerProps {
    meetings?: OneOnOneMeeting[];
    onScheduleMeeting?: (meeting: Partial<OneOnOneMeeting>) => void;
    onUpdateMeeting?: (meetingId: string, updates: Partial<OneOnOneMeeting>) => void;
    onCancelMeeting?: (meetingId: string) => void;
    onCompleteMeeting?: (meetingId: string) => void;
    onAddAgendaItem?: (meetingId: string, item: Omit<AgendaItem, 'id'>) => void;
    onCompleteAgendaItem?: (meetingId: string, itemId: string) => void;
    onAddActionItem?: (meetingId: string, item: Omit<ActionItem, 'id'>) => void;
    onCompleteActionItem?: (meetingId: string, itemId: string) => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockMeetings: OneOnOneMeeting[] = [
    {
        id: '1',
        employeeId: 'emp-1',
        employeeName: 'Sarah Johnson',
        employeeRole: 'Senior Software Engineer',
        date: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
        duration: 30,
        status: 'scheduled',
        agenda: [
            {
                id: 'a1',
                title: 'Q1 Goals Review',
                description: 'Review progress on quarterly objectives',
                completed: false,
                addedBy: 'manager',
            },
            {
                id: 'a2',
                title: 'Career Development Discussion',
                description: 'Discuss path to principal engineer role',
                completed: false,
                addedBy: 'employee',
            },
            {
                id: 'a3',
                title: 'Team Collaboration Feedback',
                completed: false,
                addedBy: 'manager',
            },
        ],
        actionItems: [
            {
                id: 'act1',
                title: 'Complete React certification',
                assignee: 'employee',
                dueDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
                completed: false,
            },
        ],
        topics: ['Goals', 'Career Development', 'Feedback'],
    },
    {
        id: '2',
        employeeId: 'emp-2',
        employeeName: 'Michael Chen',
        employeeRole: 'Software Engineer',
        date: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
        duration: 30,
        status: 'completed',
        agenda: [
            {
                id: 'a4',
                title: 'Sprint Retrospective',
                completed: true,
                addedBy: 'manager',
            },
            {
                id: 'a5',
                title: 'Workload Discussion',
                description: 'Address concerns about current capacity',
                completed: true,
                addedBy: 'employee',
            },
        ],
        notes:
            'Michael expressed concerns about workload. Agreed to redistribute some tasks. He is doing great on the authentication project.',
        actionItems: [
            {
                id: 'act2',
                title: 'Delegate 2 tasks to David',
                assignee: 'manager',
                dueDate: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000),
                completed: false,
            },
            {
                id: 'act3',
                title: 'Review task prioritization',
                assignee: 'employee',
                dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
                completed: true,
                completedDate: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000),
            },
        ],
        mood: 'good',
        topics: ['Sprint', 'Workload', 'Project Status'],
    },
    {
        id: '3',
        employeeId: 'emp-3',
        employeeName: 'David Kim',
        employeeRole: 'Junior Software Engineer',
        date: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000),
        duration: 45,
        status: 'scheduled',
        agenda: [
            {
                id: 'a6',
                title: 'Mentorship Check-in',
                description: 'Review progress with senior mentors',
                completed: false,
                addedBy: 'manager',
            },
            {
                id: 'a7',
                title: 'Training Needs',
                completed: false,
                addedBy: 'employee',
            },
        ],
        actionItems: [
            {
                id: 'act4',
                title: 'Enroll in TypeScript course',
                assignee: 'employee',
                dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000),
                completed: false,
            },
        ],
        topics: ['Mentorship', 'Training', 'Growth'],
    },
];

const meetingTemplates: MeetingTemplate[] = [
    {
        id: 'template-1',
        name: 'Weekly Check-in',
        agendaItems: [
            'Last week highlights',
            'This week priorities',
            'Blockers or concerns',
            'Any support needed?',
        ],
    },
    {
        id: 'template-2',
        name: 'Quarterly Review',
        agendaItems: [
            'Goals progress review',
            'Performance feedback',
            'Career development discussion',
            'Next quarter planning',
        ],
    },
    {
        id: 'template-3',
        name: 'Career Development',
        agendaItems: [
            'Long-term career goals',
            'Skills to develop',
            'Growth opportunities',
            'Action plan',
        ],
    },
];

// ============================================================================
// Component
// ============================================================================

export const OneOnOneTracker: React.FC<OneOnOneTrackerProps> = ({
    meetings = mockMeetings,
    onScheduleMeeting,
    onUpdateMeeting,
    onCancelMeeting,
    onCompleteMeeting,
    onAddAgendaItem,
    onCompleteAgendaItem,
    onAddActionItem,
    onCompleteActionItem,
}) => {
    const [selectedTab, setSelectedTab] = useState<number>(0);
    const [selectedMeeting, setSelectedMeeting] = useState<OneOnOneMeeting | null>(null);
    const [meetingDialogOpen, setMeetingDialogOpen] = useState(false);
    const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false);

    // Form state for scheduling
    const [newMeetingDate, setNewMeetingDate] = useState<string>('');
    const [newMeetingTime, setNewMeetingTime] = useState<string>('');
    const [newMeetingDuration, setNewMeetingDuration] = useState<number>(30);
    const [selectedEmployee, setSelectedEmployee] = useState<string>('');
    const [selectedTemplate, setSelectedTemplate] = useState<string>('');

    const upcomingMeetings = meetings
        .filter((m) => m.status === 'scheduled' && m.date > new Date())
        .sort((a, b) => a.date.getTime() - b.date.getTime());

    const pastMeetings = meetings
        .filter((m) => m.status === 'completed' || (m.status === 'scheduled' && m.date <= new Date()))
        .sort((a, b) => b.date.getTime() - a.date.getTime());

    const pendingActionItems = meetings.flatMap((m) =>
        m.actionItems.filter((a) => !a.completed).map((a) => ({ ...a, meetingId: m.id, employeeName: m.employeeName }))
    );

    const handleOpenMeetingDialog = (meeting: OneOnOneMeeting) => {
        setSelectedMeeting(meeting);
        setMeetingDialogOpen(true);
    };

    const handleScheduleNew = () => {
        if (!newMeetingDate || !newMeetingTime || !selectedEmployee) return;

        const dateTime = new Date(`${newMeetingDate}T${newMeetingTime}`);
        const employee = meetings.find((m) => m.employeeId === selectedEmployee);

        const newMeeting: Partial<OneOnOneMeeting> = {
            employeeId: selectedEmployee,
            employeeName: employee?.employeeName || '',
            employeeRole: employee?.employeeRole || '',
            date: dateTime,
            duration: newMeetingDuration,
            status: 'scheduled',
            agenda: selectedTemplate
                ? meetingTemplates
                    .find((t) => t.id === selectedTemplate)
                    ?.agendaItems.map((title, idx) => ({
                        id: `a-${idx}`,
                        title,
                        completed: false,
                        addedBy: 'manager' as const,
                    })) || []
                : [],
            actionItems: [],
        };

        onScheduleMeeting?.(newMeeting);
        setScheduleDialogOpen(false);
        resetScheduleForm();
    };

    const resetScheduleForm = () => {
        setNewMeetingDate('');
        setNewMeetingTime('');
        setNewMeetingDuration(30);
        setSelectedEmployee('');
        setSelectedTemplate('');
    };

    const formatDate = (date: Date): string => {
        return date.toLocaleDateString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const formatTime = (date: Date): string => {
        return date.toLocaleTimeString('en-US', {
            hour: 'numeric',
            minute: '2-digit',
            hour12: true,
        });
    };

    const getRelativeDate = (date: Date): string => {
        const now = new Date();
        const diffMs = date.getTime() - now.getTime();
        const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

        if (diffDays === 0) return 'Today';
        if (diffDays === 1) return 'Tomorrow';
        if (diffDays === -1) return 'Yesterday';
        if (diffDays > 0 && diffDays <= 7) return `In ${diffDays} days`;
        if (diffDays < 0 && diffDays >= -7) return `${Math.abs(diffDays)} days ago`;
        return formatDate(date);
    };

    const getMoodEmoji = (mood?: OneOnOneMeeting['mood']): React.ReactNode => {
        switch (mood) {
            case 'great':
                return <span className="text-2xl">😄</span>;
            case 'good':
                return <span className="text-2xl">🙂</span>;
            case 'neutral':
                return <span className="text-2xl">😐</span>;
            case 'concerned':
                return <span className="text-2xl">😟</span>;
            case 'struggling':
                return <span className="text-2xl">😰</span>;
            default:
                return null;
        }
    };

    const getMoodColor = (mood?: OneOnOneMeeting['mood']): 'success' | 'info' | 'warning' | 'error' | 'default' => {
        switch (mood) {
            case 'great':
                return 'success';
            case 'good':
                return 'info';
            case 'neutral':
                return 'default';
            case 'concerned':
                return 'warning';
            case 'struggling':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get unique employees from meetings
    const employees = Array.from(
        new Map(meetings.map((m) => [m.employeeId, { id: m.employeeId, name: m.employeeName, role: m.employeeRole }])).values()
    );

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <CalendarToday fontSize="large" className="text-primary" />
                    <div>
                        <h1 className="text-3xl font-bold dark:text-white">1:1 Meetings</h1>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            {upcomingMeetings.length} upcoming · {pendingActionItems.length} pending action items
                        </p>
                    </div>
                </div>
                <Button variant="contained" color="primary" startIcon={<Add />} onClick={() => setScheduleDialogOpen(true)}>
                    Schedule 1:1
                </Button>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <Schedule fontSize="large" className="text-info" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Upcoming</p>
                            <p className="text-2xl font-bold dark:text-white">{upcomingMeetings.length}</p>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <CheckCircle fontSize="large" className="text-success" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Completed</p>
                            <p className="text-2xl font-bold dark:text-white">
                                {meetings.filter((m) => m.status === 'completed').length}
                            </p>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <Assignment fontSize="large" className="text-warning" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Action Items</p>
                            <p className="text-2xl font-bold dark:text-white">{pendingActionItems.length}</p>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)}>
                    <Tab label={`Upcoming (${upcomingMeetings.length})`} />
                    <Tab label={`Past (${pastMeetings.length})`} />
                    <Tab label={`Action Items (${pendingActionItems.length})`} />
                </Tabs>

                <CardContent className="p-6">
                    {/* Upcoming Meetings Tab */}
                    {selectedTab === 0 && (
                        <div className="space-y-4">
                            {upcomingMeetings.length === 0 ? (
                                <Alert severity="info">No upcoming 1:1 meetings scheduled.</Alert>
                            ) : (
                                upcomingMeetings.map((meeting) => (
                                    <Card
                                        key={meeting.id}
                                        className="cursor-pointer hover:shadow-lg transition-shadow"
                                        onClick={() => handleOpenMeetingDialog(meeting)}
                                    >
                                        <CardContent className="p-4">
                                            <div className="flex items-start gap-4">
                                                <Avatar src={meeting.employeeAvatar}>
                                                    {meeting.employeeName
                                                        .split(' ')
                                                        .map((n) => n[0])
                                                        .join('')}
                                                </Avatar>
                                                <div className="flex-1">
                                                    <div className="flex items-center justify-between mb-2">
                                                        <div>
                                                            <p className="font-semibold text-lg dark:text-white">{meeting.employeeName}</p>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400">{meeting.employeeRole}</p>
                                                        </div>
                                                        <Chip label={getRelativeDate(meeting.date)} color="primary" />
                                                    </div>
                                                    <div className="flex items-center gap-4 text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                        <span>{formatDate(meeting.date)}</span>
                                                        <span>·</span>
                                                        <span>{formatTime(meeting.date)}</span>
                                                        <span>·</span>
                                                        <span>{meeting.duration} min</span>
                                                    </div>
                                                    <div className="flex flex-wrap gap-2">
                                                        {meeting.topics?.map((topic, idx) => (
                                                            <Chip key={idx} label={topic} size="small" variant="outlined" />
                                                        ))}
                                                    </div>
                                                    <div className="mt-3 flex items-center gap-4 text-sm">
                                                        <span className="text-gray-600 dark:text-gray-400">
                                                            {meeting.agenda.length} agenda items
                                                        </span>
                                                        <span className="text-gray-600 dark:text-gray-400">
                                                            {meeting.actionItems.filter((a) => !a.completed).length} pending actions
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                ))
                            )}
                        </div>
                    )}

                    {/* Past Meetings Tab */}
                    {selectedTab === 1 && (
                        <div className="space-y-4">
                            {pastMeetings.length === 0 ? (
                                <Alert severity="info">No past 1:1 meetings.</Alert>
                            ) : (
                                pastMeetings.map((meeting) => (
                                    <Card
                                        key={meeting.id}
                                        className="cursor-pointer hover:shadow-lg transition-shadow"
                                        onClick={() => handleOpenMeetingDialog(meeting)}
                                    >
                                        <CardContent className="p-4">
                                            <div className="flex items-start gap-4">
                                                <Avatar src={meeting.employeeAvatar}>
                                                    {meeting.employeeName
                                                        .split(' ')
                                                        .map((n) => n[0])
                                                        .join('')}
                                                </Avatar>
                                                <div className="flex-1">
                                                    <div className="flex items-center justify-between mb-2">
                                                        <div>
                                                            <p className="font-semibold text-lg dark:text-white">{meeting.employeeName}</p>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400">{meeting.employeeRole}</p>
                                                        </div>
                                                        <div className="flex items-center gap-2">
                                                            {meeting.mood && (
                                                                <Chip
                                                                    label={meeting.mood.toUpperCase()}
                                                                    size="small"
                                                                    color={getMoodColor(meeting.mood)}
                                                                    icon={getMoodEmoji(meeting.mood) as any}
                                                                />
                                                            )}
                                                            <Chip label={getRelativeDate(meeting.date)} size="small" />
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center gap-4 text-sm text-gray-600 dark:text-gray-400 mb-2">
                                                        <span>{formatDate(meeting.date)}</span>
                                                        <span>·</span>
                                                        <span>{meeting.duration} min</span>
                                                    </div>
                                                    {meeting.notes && (
                                                        <p className="text-sm text-gray-700 dark:text-gray-300 mb-3 line-clamp-2">
                                                            {meeting.notes}
                                                        </p>
                                                    )}
                                                    <div className="flex items-center gap-4 text-sm">
                                                        <span className="text-gray-600 dark:text-gray-400">
                                                            {meeting.agenda.filter((a) => a.completed).length}/{meeting.agenda.length} agenda
                                                            completed
                                                        </span>
                                                        <span className="text-gray-600 dark:text-gray-400">
                                                            {meeting.actionItems.filter((a) => a.completed).length}/{meeting.actionItems.length}{' '}
                                                            actions completed
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                ))
                            )}
                        </div>
                    )}

                    {/* Action Items Tab */}
                    {selectedTab === 2 && (
                        <div className="space-y-4">
                            {pendingActionItems.length === 0 ? (
                                <Alert severity="success">All action items completed! 🎉</Alert>
                            ) : (
                                <List>
                                    {pendingActionItems.map((item) => (
                                        <React.Fragment key={item.id}>
                                            <ListItem>
                                                <ListItemAvatar>
                                                    <Checkbox
                                                        checked={item.completed}
                                                        onChange={() => onCompleteActionItem?.(item.meetingId, item.id)}
                                                        icon={<RadioButtonUnchecked />}
                                                        checkedIcon={<CheckCircle />}
                                                    />
                                                </ListItemAvatar>
                                                <ListItemText
                                                    primary={<span className="dark:text-white">{item.title}</span>}
                                                    secondary={
                                                        <span className="dark:text-gray-400">
                                                            {item.assignee === 'manager' ? 'You' : item.employeeName} ·{' '}
                                                            {item.dueDate ? `Due ${getRelativeDate(item.dueDate)}` : 'No due date'}
                                                        </span>
                                                    }
                                                />
                                                <ListItemSecondaryAction>
                                                    <Chip
                                                        label={item.assignee === 'manager' ? 'You' : item.employeeName}
                                                        size="small"
                                                        color={item.assignee === 'manager' ? 'primary' : 'default'}
                                                    />
                                                </ListItemSecondaryAction>
                                            </ListItem>
                                            <Divider />
                                        </React.Fragment>
                                    ))}
                                </List>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Meeting Detail Dialog */}
            <Dialog open={meetingDialogOpen} onClose={() => setMeetingDialogOpen(false)} maxWidth="md" fullWidth>
                {selectedMeeting && (
                    <>
                        <DialogTitle>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <Avatar src={selectedMeeting.employeeAvatar}>
                                        {selectedMeeting.employeeName
                                            .split(' ')
                                            .map((n) => n[0])
                                            .join('')}
                                    </Avatar>
                                    <div>
                                        <h2 className="text-xl font-bold dark:text-white">{selectedMeeting.employeeName}</h2>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">{selectedMeeting.employeeRole}</p>
                                    </div>
                                </div>
                                {selectedMeeting.mood && (
                                    <Chip
                                        label={selectedMeeting.mood.toUpperCase()}
                                        color={getMoodColor(selectedMeeting.mood)}
                                        icon={getMoodEmoji(selectedMeeting.mood) as any}
                                    />
                                )}
                            </div>
                            <div className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                                {formatDate(selectedMeeting.date)} · {formatTime(selectedMeeting.date)} · {selectedMeeting.duration}{' '}
                                min
                            </div>
                        </DialogTitle>
                        <DialogContent>
                            <div className="space-y-6">
                                {/* Agenda */}
                                <div>
                                    <h3 className="text-lg font-semibold dark:text-white mb-3 flex items-center gap-2">
                                        <Notes />
                                        Agenda
                                    </h3>
                                    <List>
                                        {selectedMeeting.agenda.map((item) => (
                                            <React.Fragment key={item.id}>
                                                <ListItem>
                                                    <ListItemAvatar>
                                                        <Checkbox
                                                            checked={item.completed}
                                                            onChange={() => onCompleteAgendaItem?.(selectedMeeting.id, item.id)}
                                                            disabled={selectedMeeting.status === 'completed'}
                                                            icon={<RadioButtonUnchecked />}
                                                            checkedIcon={<CheckCircle />}
                                                        />
                                                    </ListItemAvatar>
                                                    <ListItemText
                                                        primary={
                                                            <span className={item.completed ? 'line-through dark:text-gray-500' : 'dark:text-white'}>
                                                                {item.title}
                                                            </span>
                                                        }
                                                        secondary={
                                                            item.description && <span className="dark:text-gray-400">{item.description}</span>
                                                        }
                                                    />
                                                    <ListItemSecondaryAction>
                                                        <Chip
                                                            label={item.addedBy === 'manager' ? 'You' : 'Employee'}
                                                            size="small"
                                                            variant="outlined"
                                                        />
                                                    </ListItemSecondaryAction>
                                                </ListItem>
                                                <Divider />
                                            </React.Fragment>
                                        ))}
                                    </List>
                                </div>

                                {/* Notes */}
                                {selectedMeeting.notes && (
                                    <div>
                                        <h3 className="text-lg font-semibold dark:text-white mb-2">Meeting Notes</h3>
                                        <Card>
                                            <CardContent>
                                                <p className="text-gray-700 dark:text-gray-300">{selectedMeeting.notes}</p>
                                            </CardContent>
                                        </Card>
                                    </div>
                                )}

                                {/* Action Items */}
                                <div>
                                    <h3 className="text-lg font-semibold dark:text-white mb-3 flex items-center gap-2">
                                        <Assignment />
                                        Action Items
                                    </h3>
                                    {selectedMeeting.actionItems.length === 0 ? (
                                        <Alert severity="info">No action items yet.</Alert>
                                    ) : (
                                        <List>
                                            {selectedMeeting.actionItems.map((item) => (
                                                <React.Fragment key={item.id}>
                                                    <ListItem>
                                                        <ListItemAvatar>
                                                            <Checkbox
                                                                checked={item.completed}
                                                                onChange={() => onCompleteActionItem?.(selectedMeeting.id, item.id)}
                                                                icon={<RadioButtonUnchecked />}
                                                                checkedIcon={<CheckCircle />}
                                                            />
                                                        </ListItemAvatar>
                                                        <ListItemText
                                                            primary={
                                                                <span
                                                                    className={item.completed ? 'line-through dark:text-gray-500' : 'dark:text-white'}
                                                                >
                                                                    {item.title}
                                                                </span>
                                                            }
                                                            secondary={
                                                                <span className="dark:text-gray-400">
                                                                    {item.assignee === 'manager' ? 'You' : selectedMeeting.employeeName}
                                                                    {item.dueDate && ` · Due ${getRelativeDate(item.dueDate)}`}
                                                                    {item.completed && item.completedDate && ` · Completed ${getRelativeDate(item.completedDate)}`}
                                                                </span>
                                                            }
                                                        />
                                                    </ListItem>
                                                    <Divider />
                                                </React.Fragment>
                                            ))}
                                        </List>
                                    )}
                                </div>
                            </div>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setMeetingDialogOpen(false)}>Close</Button>
                            {selectedMeeting.status === 'scheduled' && (
                                <>
                                    <Button
                                        onClick={() => {
                                            onCancelMeeting?.(selectedMeeting.id);
                                            setMeetingDialogOpen(false);
                                        }}
                                        color="error"
                                        variant="outlined"
                                    >
                                        Cancel Meeting
                                    </Button>
                                    <Button
                                        onClick={() => {
                                            onCompleteMeeting?.(selectedMeeting.id);
                                            setMeetingDialogOpen(false);
                                        }}
                                        color="primary"
                                        variant="contained"
                                    >
                                        Mark Complete
                                    </Button>
                                </>
                            )}
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Schedule Meeting Dialog */}
            <Dialog open={scheduleDialogOpen} onClose={() => setScheduleDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Schedule 1:1 Meeting</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 pt-2">
                        <FormControl fullWidth>
                            <InputLabel>Employee</InputLabel>
                            <Select value={selectedEmployee} onChange={(e) => setSelectedEmployee(e.target.value as string)} label="Employee">
                                {employees.map((emp) => (
                                    <MenuItem key={emp.id} value={emp.id}>
                                        {emp.name} - {emp.role}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <TextField
                            fullWidth
                            type="date"
                            label="Date"
                            value={newMeetingDate}
                            onChange={(e) => setNewMeetingDate(e.target.value)}
                            InputLabelProps={{ shrink: true }}
                        />

                        <TextField
                            fullWidth
                            type="time"
                            label="Time"
                            value={newMeetingTime}
                            onChange={(e) => setNewMeetingTime(e.target.value)}
                            InputLabelProps={{ shrink: true }}
                        />

                        <FormControl fullWidth>
                            <InputLabel>Duration</InputLabel>
                            <Select
                                value={newMeetingDuration}
                                onChange={(e: { target: { value: unknown } }) =>
                                    setNewMeetingDuration(Number(e.target.value))
                                }
                                label="Duration"
                            >
                                <MenuItem value={15}>15 minutes</MenuItem>
                                <MenuItem value={30}>30 minutes</MenuItem>
                                <MenuItem value={45}>45 minutes</MenuItem>
                                <MenuItem value={60}>60 minutes</MenuItem>
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Agenda Template (Optional)</InputLabel>
                            <Select
                                value={selectedTemplate}
                                onChange={(e) => setSelectedTemplate(e.target.value as string)}
                                label="Agenda Template (Optional)"
                            >
                                <MenuItem value="">
                                    <em>None</em>
                                </MenuItem>
                                {meetingTemplates.map((template) => (
                                    <MenuItem key={template.id} value={template.id}>
                                        {template.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        {selectedTemplate && (
                            <Alert severity="info">
                                <strong>Agenda items will include:</strong>
                                <ul className="mt-2 ml-4 list-disc">
                                    {meetingTemplates
                                        .find((t) => t.id === selectedTemplate)
                                        ?.agendaItems.map((item, idx) => <li key={idx}>{item}</li>)}
                                </ul>
                            </Alert>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button
                        onClick={() => {
                            setScheduleDialogOpen(false);
                            resetScheduleForm();
                        }}
                    >
                        Cancel
                    </Button>
                    <Button
                        onClick={handleScheduleNew}
                        variant="contained"
                        color="primary"
                        disabled={!newMeetingDate || !newMeetingTime || !selectedEmployee}
                    >
                        Schedule
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default OneOnOneTracker;
