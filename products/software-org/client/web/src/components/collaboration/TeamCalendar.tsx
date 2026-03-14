import React, { useState } from 'react';
import {
    Box,
    Typography,
    Card,
    CardContent,
    Chip,
    Button,
    TextField,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Avatar,
    Alert,
    MenuItem,
    Select,
    FormControl,
    InputLabel,
    IconButton,
    AvatarGroup,
} from '@ghatana/design-system';

// Types
export type CalendarView = 'month' | 'week' | 'day';
export type EventType = 'one-on-one' | 'team-meeting' | 'all-hands' | 'interview' | 'training' | 'other';
export type AttendeeStatus = 'accepted' | 'declined' | 'tentative' | 'pending';

export interface Attendee {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    status: AttendeeStatus;
    isOrganizer?: boolean;
}

export interface CalendarEvent {
    id: string;
    title: string;
    description?: string;
    type: EventType;
    startTime: string;
    endTime: string;
    location?: string;
    isVirtual: boolean;
    meetingLink?: string;
    attendees: Attendee[];
    color?: string;
    isRecurring?: boolean;
    recurrencePattern?: string;
}

export interface TeamCalendarProps {
    onCreateEvent?: (event: Omit<CalendarEvent, 'id'>) => void;
    onUpdateEvent?: (eventId: string, updates: Partial<CalendarEvent>) => void;
    onDeleteEvent?: (eventId: string) => void;
    onRSVP?: (eventId: string, userId: string, status: AttendeeStatus) => void;
    currentUserId?: string;
}

// Mock data
const mockEvents: CalendarEvent[] = [
    {
        id: '1',
        title: '1:1 with Sarah Johnson',
        description: 'Weekly check-in',
        type: 'one-on-one',
        startTime: '2024-01-15T10:00:00Z',
        endTime: '2024-01-15T10:30:00Z',
        isVirtual: true,
        meetingLink: 'https://meet.company.com/abc123',
        attendees: [
            {
                id: 'user1',
                name: 'Sarah Johnson',
                email: 'sarah.johnson@company.com',
                status: 'accepted',
            },
            {
                id: 'user2',
                name: 'Current User',
                email: 'current.user@company.com',
                status: 'accepted',
                isOrganizer: true,
            },
        ],
        color: '#4CAF50',
        isRecurring: true,
        recurrencePattern: 'Weekly on Monday',
    },
    {
        id: '2',
        title: 'Team Sprint Planning',
        description: 'Planning for Sprint 24',
        type: 'team-meeting',
        startTime: '2024-01-15T14:00:00Z',
        endTime: '2024-01-15T15:30:00Z',
        location: 'Conference Room A',
        isVirtual: false,
        attendees: [
            {
                id: 'user2',
                name: 'Current User',
                email: 'current.user@company.com',
                status: 'accepted',
                isOrganizer: true,
            },
            {
                id: 'user3',
                name: 'Michael Chen',
                email: 'michael.chen@company.com',
                status: 'accepted',
            },
            {
                id: 'user4',
                name: 'Emily White',
                email: 'emily.white@company.com',
                status: 'tentative',
            },
            {
                id: 'user5',
                name: 'David Park',
                email: 'david.park@company.com',
                status: 'pending',
            },
        ],
        color: '#2196F3',
    },
    {
        id: '3',
        title: 'All Hands Meeting',
        description: 'Company quarterly update',
        type: 'all-hands',
        startTime: '2024-01-16T09:00:00Z',
        endTime: '2024-01-16T10:00:00Z',
        isVirtual: true,
        meetingLink: 'https://meet.company.com/allhands',
        attendees: [
            {
                id: 'user6',
                name: 'CEO',
                email: 'ceo@company.com',
                status: 'accepted',
                isOrganizer: true,
            },
            {
                id: 'user2',
                name: 'Current User',
                email: 'current.user@company.com',
                status: 'accepted',
            },
        ],
        color: '#9C27B0',
    },
    {
        id: '4',
        title: 'Interview: Senior Engineer Candidate',
        description: 'Technical interview round 2',
        type: 'interview',
        startTime: '2024-01-16T15:00:00Z',
        endTime: '2024-01-16T16:00:00Z',
        isVirtual: true,
        meetingLink: 'https://meet.company.com/interview456',
        attendees: [
            {
                id: 'user2',
                name: 'Current User',
                email: 'current.user@company.com',
                status: 'accepted',
                isOrganizer: true,
            },
            {
                id: 'user7',
                name: 'Lisa Wang',
                email: 'lisa.wang@company.com',
                status: 'accepted',
            },
        ],
        color: '#FF9800',
    },
    {
        id: '5',
        title: 'React Workshop',
        description: 'Advanced React patterns training',
        type: 'training',
        startTime: '2024-01-17T13:00:00Z',
        endTime: '2024-01-17T16:00:00Z',
        location: 'Training Room B',
        isVirtual: false,
        attendees: [
            {
                id: 'user8',
                name: 'Training Team',
                email: 'training@company.com',
                status: 'accepted',
                isOrganizer: true,
            },
            {
                id: 'user2',
                name: 'Current User',
                email: 'current.user@company.com',
                status: 'accepted',
            },
            {
                id: 'user3',
                name: 'Michael Chen',
                email: 'michael.chen@company.com',
                status: 'declined',
            },
        ],
        color: '#FF5722',
    },
];

const eventTypeLabels: Record<EventType, string> = {
    'one-on-one': '1:1',
    'team-meeting': 'Team Meeting',
    'all-hands': 'All Hands',
    interview: 'Interview',
    training: 'Training',
    other: 'Other',
};

const eventTypeColors: Record<EventType, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    'one-on-one': 'success',
    'team-meeting': 'primary',
    'all-hands': 'secondary',
    interview: 'warning',
    training: 'error',
    other: 'info',
};

const attendeeStatusColors: Record<AttendeeStatus, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    accepted: 'success',
    declined: 'error',
    tentative: 'warning',
    pending: 'secondary',
};

const formatTime = (timestamp: string): string => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
    });
};

const formatDate = (timestamp: string): string => {
    const date = new Date(timestamp);
    return date.toLocaleDateString('en-US', {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
    });
};

const getDuration = (startTime: string, endTime: string): string => {
    const start = new Date(startTime);
    const end = new Date(endTime);
    const diffMs = end.getTime() - start.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const hours = Math.floor(diffMins / 60);
    const mins = diffMins % 60;
    if (hours > 0) {
        return `${hours}h ${mins}m`;
    }
    return `${mins}m`;
};

const isToday = (timestamp: string): boolean => {
    const date = new Date(timestamp);
    const today = new Date();
    return (
        date.getDate() === today.getDate() &&
        date.getMonth() === today.getMonth() &&
        date.getFullYear() === today.getFullYear()
    );
};

const isTomorrow = (timestamp: string): boolean => {
    const date = new Date(timestamp);
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    return (
        date.getDate() === tomorrow.getDate() &&
        date.getMonth() === tomorrow.getMonth() &&
        date.getFullYear() === tomorrow.getFullYear()
    );
};

const getDateLabel = (timestamp: string): string => {
    if (isToday(timestamp)) return 'Today';
    if (isTomorrow(timestamp)) return 'Tomorrow';
    return formatDate(timestamp);
};

export const TeamCalendar: React.FC<TeamCalendarProps> = ({
    onCreateEvent,
    onUpdateEvent,
    onDeleteEvent,
    onRSVP,
    currentUserId = 'user2',
}) => {
    const [events] = useState<CalendarEvent[]>(mockEvents);
    const [view, setView] = useState<CalendarView>('week');
    const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
    const [detailDialogOpen, setDetailDialogOpen] = useState(false);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);

    // Form state
    const [formTitle, setFormTitle] = useState('');
    const [formDescription, setFormDescription] = useState('');
    const [formType, setFormType] = useState<EventType>('team-meeting');
    const [formStartTime, setFormStartTime] = useState('');
    const [formEndTime, setFormEndTime] = useState('');
    const [formLocation, setFormLocation] = useState('');
    const [formIsVirtual, setFormIsVirtual] = useState(true);
    const [formMeetingLink, setFormMeetingLink] = useState('');

    const handleViewDetails = (event: CalendarEvent) => {
        setSelectedEvent(event);
        setDetailDialogOpen(true);
    };

    const handleCloseDetail = () => {
        setDetailDialogOpen(false);
        setSelectedEvent(null);
    };

    const handleOpenCreate = () => {
        setCreateDialogOpen(true);
    };

    const handleCloseCreate = () => {
        setCreateDialogOpen(false);
        setFormTitle('');
        setFormDescription('');
        setFormType('team-meeting');
        setFormStartTime('');
        setFormEndTime('');
        setFormLocation('');
        setFormIsVirtual(true);
        setFormMeetingLink('');
    };

    const handleCreateEvent = () => {
        if (onCreateEvent && formTitle && formStartTime && formEndTime) {
            onCreateEvent({
                title: formTitle,
                description: formDescription,
                type: formType,
                startTime: formStartTime,
                endTime: formEndTime,
                location: formLocation,
                isVirtual: formIsVirtual,
                meetingLink: formMeetingLink,
                attendees: [
                    {
                        id: currentUserId,
                        name: 'Current User',
                        email: 'current.user@company.com',
                        status: 'accepted',
                        isOrganizer: true,
                    },
                ],
            });
        }
        handleCloseCreate();
    };

    const handleRSVP = (status: AttendeeStatus) => {
        if (!selectedEvent) return;
        onRSVP?.(selectedEvent.id, currentUserId, status);
        handleCloseDetail();
    };

    const handleDeleteEvent = () => {
        if (!selectedEvent) return;
        onDeleteEvent?.(selectedEvent.id);
        handleCloseDetail();
    };

    const getEventsByDate = () => {
        const eventsByDate: Record<string, CalendarEvent[]> = {};
        events.forEach((event) => {
            const dateKey = new Date(event.startTime).toDateString();
            if (!eventsByDate[dateKey]) {
                eventsByDate[dateKey] = [];
            }
            eventsByDate[dateKey].push(event);
        });

        // Sort events within each date by start time
        Object.keys(eventsByDate).forEach((date) => {
            eventsByDate[date].sort((a, b) =>
                new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
            );
        });

        return eventsByDate;
    };

    const getUpcomingEvents = () => {
        const now = new Date();
        return events
            .filter((event) => new Date(event.startTime) > now)
            .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
            .slice(0, 10);
    };

    const getCurrentUserAttendee = (event: CalendarEvent): Attendee | undefined => {
        return event.attendees.find((a) => a.id === currentUserId);
    };

    const isOrganizer = (event: CalendarEvent): boolean => {
        const attendee = getCurrentUserAttendee(event);
        return attendee?.isOrganizer || false;
    };

    const hasConflict = (event: CalendarEvent): boolean => {
        const eventStart = new Date(event.startTime);
        const eventEnd = new Date(event.endTime);

        return events.some((e) => {
            if (e.id === event.id) return false;
            const eStart = new Date(e.startTime);
            const eEnd = new Date(e.endTime);
            return (
                (eventStart >= eStart && eventStart < eEnd) ||
                (eventEnd > eStart && eventEnd <= eEnd) ||
                (eventStart <= eStart && eventEnd >= eEnd)
            );
        });
    };

    const eventsByDate = getEventsByDate();
    const upcomingEvents = getUpcomingEvents();

    return (
        <Box>
            {/* Header */}
            <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                    <Typography variant="h4" className="dark:text-white">
                        Team Calendar
                    </Typography>
                    <Typography variant="body2" className="dark:text-gray-400">
                        Schedule and manage team meetings and events
                    </Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1 }}>
                    <FormControl size="small" sx={{ minWidth: 120 }}>
                        <Select value={view} onChange={(e) => setView(e.target.value as CalendarView)}>
                            <MenuItem value="month">Month</MenuItem>
                            <MenuItem value="week">Week</MenuItem>
                            <MenuItem value="day">Day</MenuItem>
                        </Select>
                    </FormControl>
                    <Button variant="contained" color="primary" onClick={handleOpenCreate}>
                        New Event
                    </Button>
                </Box>
            </Box>

            {/* Quick Stats */}
            <Box sx={{ mb: 3, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 2 }}>
                <Card className="dark:bg-gray-800">
                    <CardContent>
                        <Typography variant="h4" className="dark:text-white">
                            {upcomingEvents.length}
                        </Typography>
                        <Typography variant="body2" className="dark:text-gray-400">
                            Upcoming Events
                        </Typography>
                    </CardContent>
                </Card>
                <Card className="dark:bg-gray-800">
                    <CardContent>
                        <Typography variant="h4" className="dark:text-white">
                            {events.filter((e) => isToday(e.startTime)).length}
                        </Typography>
                        <Typography variant="body2" className="dark:text-gray-400">
                            Today's Meetings
                        </Typography>
                    </CardContent>
                </Card>
                <Card className="dark:bg-gray-800">
                    <CardContent>
                        <Typography variant="h4" className="dark:text-white">
                            {events.filter((e) => getCurrentUserAttendee(e)?.status === 'pending').length}
                        </Typography>
                        <Typography variant="body2" className="dark:text-gray-400">
                            Pending Invitations
                        </Typography>
                    </CardContent>
                </Card>
                <Card className="dark:bg-gray-800">
                    <CardContent>
                        <Typography variant="h4" className="dark:text-white">
                            {events.filter((e) => hasConflict(e)).length}
                        </Typography>
                        <Typography variant="body2" className="dark:text-gray-400">
                            Scheduling Conflicts
                        </Typography>
                    </CardContent>
                </Card>
            </Box>

            {/* Events List (Week View) */}
            <Box>
                <Typography variant="h6" className="dark:text-white" sx={{ mb: 2 }}>
                    {view === 'week' && 'This Week'}
                    {view === 'day' && 'Today'}
                    {view === 'month' && 'This Month'}
                </Typography>

                {upcomingEvents.length === 0 ? (
                    <Alert severity="info">No upcoming events scheduled.</Alert>
                ) : (
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                        {Object.entries(eventsByDate)
                            .sort(([dateA], [dateB]) => new Date(dateA).getTime() - new Date(dateB).getTime())
                            .slice(0, view === 'day' ? 1 : view === 'week' ? 7 : 30)
                            .map(([dateKey, dateEvents]) => (
                                <Box key={dateKey}>
                                    <Typography
                                        variant="subtitle1"
                                        className="dark:text-gray-300"
                                        sx={{ mb: 1, fontWeight: 600 }}
                                    >
                                        {getDateLabel(dateEvents[0].startTime)}
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                                        {dateEvents.map((event) => {
                                            const userAttendee = getCurrentUserAttendee(event);
                                            const conflict = hasConflict(event);

                                            return (
                                                <Card
                                                    key={event.id}
                                                    className="dark:bg-gray-800"
                                                    sx={{
                                                        borderLeft: `4px solid ${event.color || '#2196F3'}`,
                                                        cursor: 'pointer',
                                                        '&:hover': { bgcolor: 'action.hover' },
                                                    }}
                                                    onClick={() => handleViewDetails(event)}
                                                >
                                                    <CardContent>
                                                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                                                            <Box sx={{ flex: 1 }}>
                                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                                                                    <Typography variant="h6" className="dark:text-white">
                                                                        {event.title}
                                                                    </Typography>
                                                                    <Chip
                                                                        label={eventTypeLabels[event.type]}
                                                                        size="small"
                                                                        color={eventTypeColors[event.type]}
                                                                    />
                                                                    {event.isRecurring && (
                                                                        <Chip label="Recurring" size="small" variant="outlined" />
                                                                    )}
                                                                    {conflict && (
                                                                        <Chip label="Conflict" size="small" color="error" />
                                                                    )}
                                                                    {userAttendee && (
                                                                        <Chip
                                                                            label={userAttendee.status.charAt(0).toUpperCase() + userAttendee.status.slice(1)}
                                                                            size="small"
                                                                            color={attendeeStatusColors[userAttendee.status]}
                                                                        />
                                                                    )}
                                                                </Box>

                                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                                                                    <Typography variant="body2" className="dark:text-gray-400">
                                                                        🕐 {formatTime(event.startTime)} - {formatTime(event.endTime)} ({getDuration(event.startTime, event.endTime)})
                                                                    </Typography>
                                                                    {event.isVirtual ? (
                                                                        <Typography variant="body2" className="dark:text-gray-400">
                                                                            💻 Virtual
                                                                        </Typography>
                                                                    ) : event.location ? (
                                                                        <Typography variant="body2" className="dark:text-gray-400">
                                                                            📍 {event.location}
                                                                        </Typography>
                                                                    ) : null}
                                                                </Box>

                                                                {event.description && (
                                                                    <Typography variant="body2" className="dark:text-gray-400" sx={{ mb: 1 }}>
                                                                        {event.description}
                                                                    </Typography>
                                                                )}

                                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                                                    <AvatarGroup max={5} sx={{ justifyContent: 'flex-start' }}>
                                                                        {event.attendees.map((attendee) => (
                                                                            <Avatar
                                                                                key={attendee.id}
                                                                                src={attendee.avatar}
                                                                                sx={{ width: 28, height: 28 }}
                                                                                title={`${attendee.name} (${attendee.status})`}
                                                                            >
                                                                                {attendee.name.charAt(0)}
                                                                            </Avatar>
                                                                        ))}
                                                                    </AvatarGroup>
                                                                    <Typography variant="caption" className="dark:text-gray-400">
                                                                        {event.attendees.length} attendee{event.attendees.length > 1 ? 's' : ''}
                                                                    </Typography>
                                                                </Box>
                                                            </Box>

                                                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, ml: 2 }}>
                                                                {event.isVirtual && event.meetingLink && (
                                                                    <Button
                                                                        variant="outlined"
                                                                        size="small"
                                                                        onClick={(e) => {
                                                                            e.stopPropagation();
                                                                            window.open(event.meetingLink, '_blank');
                                                                        }}
                                                                    >
                                                                        Join
                                                                    </Button>
                                                                )}
                                                                {userAttendee?.status === 'pending' && (
                                                                    <Chip label="RSVP Required" size="small" color="warning" />
                                                                )}
                                                            </Box>
                                                        </Box>
                                                    </CardContent>
                                                </Card>
                                            );
                                        })}
                                    </Box>
                                </Box>
                            ))}
                    </Box>
                )}
            </Box>

            {/* Event Detail Dialog */}
            <Dialog
                open={detailDialogOpen}
                onClose={handleCloseDetail}
                maxWidth="sm"
                fullWidth
            >
                {selectedEvent && (
                    <>
                        <DialogTitle className="dark:bg-gray-800 dark:text-white">
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <Typography variant="h6">{selectedEvent.title}</Typography>
                                <Chip
                                    label={eventTypeLabels[selectedEvent.type]}
                                    size="small"
                                    color={eventTypeColors[selectedEvent.type]}
                                />
                            </Box>
                        </DialogTitle>
                        <DialogContent className="dark:bg-gray-800">
                            <Box sx={{ mt: 2 }}>
                                {/* Event Details */}
                                <Box sx={{ mb: 3 }}>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                        When
                                    </Typography>
                                    <Typography variant="body2" className="dark:text-white">
                                        {formatDate(selectedEvent.startTime)}
                                    </Typography>
                                    <Typography variant="body2" className="dark:text-white">
                                        {formatTime(selectedEvent.startTime)} - {formatTime(selectedEvent.endTime)} ({getDuration(selectedEvent.startTime, selectedEvent.endTime)})
                                    </Typography>
                                    {selectedEvent.isRecurring && (
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Recurring: {selectedEvent.recurrencePattern}
                                        </Typography>
                                    )}
                                </Box>

                                <Box sx={{ mb: 3 }}>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                        Where
                                    </Typography>
                                    {selectedEvent.isVirtual ? (
                                        <Box>
                                            <Typography variant="body2" className="dark:text-white" sx={{ mb: 1 }}>
                                                💻 Virtual Meeting
                                            </Typography>
                                            {selectedEvent.meetingLink && (
                                                <Button
                                                    variant="outlined"
                                                    size="small"
                                                    onClick={() => window.open(selectedEvent.meetingLink, '_blank')}
                                                >
                                                    Join Meeting
                                                </Button>
                                            )}
                                        </Box>
                                    ) : (
                                        <Typography variant="body2" className="dark:text-white">
                                            📍 {selectedEvent.location || 'No location specified'}
                                        </Typography>
                                    )}
                                </Box>

                                {selectedEvent.description && (
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                            Description
                                        </Typography>
                                        <Typography variant="body2" className="dark:text-gray-400">
                                            {selectedEvent.description}
                                        </Typography>
                                    </Box>
                                )}

                                {hasConflict(selectedEvent) && (
                                    <Alert severity="warning" sx={{ mb: 3 }}>
                                        This event conflicts with another event in your calendar.
                                    </Alert>
                                )}

                                {/* Attendees */}
                                <Box>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                        Attendees ({selectedEvent.attendees.length})
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                                        {selectedEvent.attendees.map((attendee) => (
                                            <Box
                                                key={attendee.id}
                                                sx={{
                                                    display: 'flex',
                                                    justifyContent: 'space-between',
                                                    alignItems: 'center',
                                                    p: 1.5,
                                                    bgcolor: 'background.paper',
                                                    borderRadius: 1,
                                                }}
                                                className="dark:bg-gray-700"
                                            >
                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                                                    <Avatar
                                                        src={attendee.avatar}
                                                        sx={{ width: 36, height: 36 }}
                                                    >
                                                        {attendee.name.charAt(0)}
                                                    </Avatar>
                                                    <Box>
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            <Typography variant="body2" className="dark:text-white">
                                                                {attendee.name}
                                                            </Typography>
                                                            {attendee.isOrganizer && (
                                                                <Chip label="Organizer" size="small" />
                                                            )}
                                                        </Box>
                                                        <Typography variant="caption" className="dark:text-gray-400">
                                                            {attendee.email}
                                                        </Typography>
                                                    </Box>
                                                </Box>
                                                <Chip
                                                    label={attendee.status.charAt(0).toUpperCase() + attendee.status.slice(1)}
                                                    size="small"
                                                    color={attendeeStatusColors[attendee.status]}
                                                />
                                            </Box>
                                        ))}
                                    </Box>
                                </Box>
                            </Box>
                        </DialogContent>
                        <DialogActions className="dark:bg-gray-800">
                            {isOrganizer(selectedEvent) ? (
                                <>
                                    <Button color="error" onClick={handleDeleteEvent}>
                                        Delete Event
                                    </Button>
                                    <Button onClick={handleCloseDetail}>Close</Button>
                                </>
                            ) : (
                                <>
                                    {getCurrentUserAttendee(selectedEvent)?.status === 'pending' && (
                                        <>
                                            <Button
                                                variant="contained"
                                                color="success"
                                                onClick={() => handleRSVP('accepted')}
                                            >
                                                Accept
                                            </Button>
                                            <Button
                                                variant="outlined"
                                                color="warning"
                                                onClick={() => handleRSVP('tentative')}
                                            >
                                                Tentative
                                            </Button>
                                            <Button
                                                variant="outlined"
                                                color="error"
                                                onClick={() => handleRSVP('declined')}
                                            >
                                                Decline
                                            </Button>
                                        </>
                                    )}
                                    <Button onClick={handleCloseDetail}>Close</Button>
                                </>
                            )}
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Create Event Dialog */}
            <Dialog open={createDialogOpen} onClose={handleCloseCreate} maxWidth="sm" fullWidth>
                <DialogTitle className="dark:bg-gray-800 dark:text-white">
                    New Calendar Event
                </DialogTitle>
                <DialogContent className="dark:bg-gray-800">
                    <Box sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <TextField
                            fullWidth
                            label="Title"
                            value={formTitle}
                            onChange={(e) => setFormTitle(e.target.value)}
                            required
                        />
                        <TextField
                            fullWidth
                            label="Description"
                            value={formDescription}
                            onChange={(e) => setFormDescription(e.target.value)}
                            multiline
                            rows={3}
                        />
                        <FormControl fullWidth>
                            <InputLabel>Event Type</InputLabel>
                            <Select
                                value={formType}
                                label="Event Type"
                                onChange={(e) => setFormType(e.target.value as EventType)}
                            >
                                {Object.entries(eventTypeLabels).map(([value, label]) => (
                                    <MenuItem key={value} value={value}>
                                        {label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            fullWidth
                            label="Start Time"
                            type="datetime-local"
                            value={formStartTime}
                            onChange={(e) => setFormStartTime(e.target.value)}
                            InputLabelProps={{ shrink: true }}
                            required
                        />
                        <TextField
                            fullWidth
                            label="End Time"
                            type="datetime-local"
                            value={formEndTime}
                            onChange={(e) => setFormEndTime(e.target.value)}
                            InputLabelProps={{ shrink: true }}
                            required
                        />
                        <FormControl fullWidth>
                            <InputLabel>Location Type</InputLabel>
                            <Select
                                value={formIsVirtual ? 'virtual' : 'physical'}
                                label="Location Type"
                                onChange={(e) => setFormIsVirtual(e.target.value === 'virtual')}
                            >
                                <MenuItem value="virtual">Virtual Meeting</MenuItem>
                                <MenuItem value="physical">Physical Location</MenuItem>
                            </Select>
                        </FormControl>
                        {formIsVirtual ? (
                            <TextField
                                fullWidth
                                label="Meeting Link"
                                value={formMeetingLink}
                                onChange={(e) => setFormMeetingLink(e.target.value)}
                                placeholder="https://meet.company.com/..."
                            />
                        ) : (
                            <TextField
                                fullWidth
                                label="Location"
                                value={formLocation}
                                onChange={(e) => setFormLocation(e.target.value)}
                                placeholder="Conference Room A"
                            />
                        )}
                        <Alert severity="info">
                            In a real implementation, you would add attendees from a user directory.
                        </Alert>
                    </Box>
                </DialogContent>
                <DialogActions className="dark:bg-gray-800">
                    <Button onClick={handleCloseCreate}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleCreateEvent}
                        disabled={!formTitle || !formStartTime || !formEndTime}
                    >
                        Create Event
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
