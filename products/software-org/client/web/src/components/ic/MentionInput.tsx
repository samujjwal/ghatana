import React, { useState, useRef, useEffect } from 'react';
import {
    Box,
    TextField,
    Paper,
    List,
    ListItem,
    ListItemButton,
    ListItemAvatar,
    ListItemText,
    Avatar,
    Popper,
    Typography,
    Chip,
    Stack,
} from '@ghatana/ui';

/**
 * User mention
 */
export interface UserMention {
    id: string;
    name: string;
    email: string;
    avatarUrl?: string;
    role?: string;
}

/**
 * Props for MentionInput
 */
interface MentionInputProps {
    value: string;
    onChange: (value: string, mentions: UserMention[]) => void;
    placeholder?: string;
    label?: string;
    multiline?: boolean;
    rows?: number;
    maxRows?: number;
    users?: UserMention[];
    disabled?: boolean;
    error?: boolean;
    helperText?: string;
    onMentionSelect?: (mention: UserMention) => void;
}

/**
 * Mention Input Component
 *
 * Rich text input with @mention autocomplete:
 * - Real-time user search as you type @
 * - Keyboard navigation (up/down/enter)
 * - Click to select from dropdown
 * - Highlighted mentions in text
 * - Returns both text and mentioned users
 * - Supports multiline mode
 */
export const MentionInput: React.FC<MentionInputProps> = ({
    value,
    onChange,
    placeholder = 'Type @ to mention someone...',
    label,
    multiline = true,
    rows = 4,
    maxRows = 10,
    users = [],
    disabled = false,
    error = false,
    helperText,
    onMentionSelect,
}) => {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [filteredUsers, setFilteredUsers] = useState<UserMention[]>([]);
    const [selectedIndex, setSelectedIndex] = useState(0);
    const [mentionQuery, setMentionQuery] = useState('');
    const [cursorPosition, setCursorPosition] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);

    // Mock users if none provided
    const mockUsers: UserMention[] = users.length > 0 ? users : [
        {
            id: 'u1',
            name: 'Alice Johnson',
            email: 'alice@company.com',
            role: 'Senior Engineer',
        },
        {
            id: 'u2',
            name: 'Bob Smith',
            email: 'bob@company.com',
            role: 'Tech Lead',
        },
        {
            id: 'u3',
            name: 'Carol Williams',
            email: 'carol@company.com',
            role: 'Product Manager',
        },
        {
            id: 'u4',
            name: 'David Brown',
            email: 'david@company.com',
            role: 'Engineering Manager',
        },
        {
            id: 'u5',
            name: 'Eve Davis',
            email: 'eve@company.com',
            role: 'Designer',
        },
    ];

    // Extract current mentions from text
    const extractMentions = (text: string): UserMention[] => {
        const mentionPattern = /@\[([^\]]+)\]\(([^)]+)\)/g;
        const mentions: UserMention[] = [];
        let match;

        while ((match = mentionPattern.exec(text)) !== null) {
            const userId = match[2];
            const user = mockUsers.find((u) => u.id === userId);
            if (user) {
                mentions.push(user);
            }
        }

        return mentions;
    };

    // Check if we're typing a mention
    useEffect(() => {
        const textUpToCursor = value.substring(0, cursorPosition);
        const lastAtIndex = textUpToCursor.lastIndexOf('@');

        if (lastAtIndex !== -1) {
            const textAfterAt = textUpToCursor.substring(lastAtIndex + 1);
            const hasSpace = textAfterAt.includes(' ');

            if (!hasSpace) {
                // We're typing a mention
                setMentionQuery(textAfterAt.toLowerCase());
                const filtered = mockUsers.filter(
                    (user) =>
                        user.name.toLowerCase().includes(textAfterAt.toLowerCase()) ||
                        user.email.toLowerCase().includes(textAfterAt.toLowerCase())
                );
                setFilteredUsers(filtered);
                setShowSuggestions(filtered.length > 0);
                setSelectedIndex(0);
                setAnchorEl(inputRef.current);
            } else {
                setShowSuggestions(false);
            }
        } else {
            setShowSuggestions(false);
        }
    }, [value, cursorPosition, mockUsers]);

    // Handle text change
    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const newValue = event.target.value;
        const newCursorPos = event.target.selectionStart || 0;

        onChange(newValue, extractMentions(newValue));
        setCursorPosition(newCursorPos);
    };

    // Handle mention selection
    const selectMention = (user: UserMention) => {
        const textUpToCursor = value.substring(0, cursorPosition);
        const lastAtIndex = textUpToCursor.lastIndexOf('@');

        if (lastAtIndex !== -1) {
            const beforeMention = value.substring(0, lastAtIndex);
            const afterMention = value.substring(cursorPosition);
            const mention = `@[${user.name}](${user.id})`;
            const newValue = beforeMention + mention + ' ' + afterMention;

            onChange(newValue, extractMentions(newValue));
            onMentionSelect?.(user);
            setShowSuggestions(false);

            // Move cursor after mention
            setTimeout(() => {
                const newCursorPos = beforeMention.length + mention.length + 1;
                if (inputRef.current) {
                    inputRef.current.focus();
                    inputRef.current.setSelectionRange(newCursorPos, newCursorPos);
                }
                setCursorPosition(newCursorPos);
            }, 0);
        }
    };

    // Handle keyboard navigation
    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (!showSuggestions) return;

        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                setSelectedIndex((prev) => (prev + 1) % filteredUsers.length);
                break;
            case 'ArrowUp':
                event.preventDefault();
                setSelectedIndex((prev) => (prev - 1 + filteredUsers.length) % filteredUsers.length);
                break;
            case 'Enter':
                if (filteredUsers.length > 0) {
                    event.preventDefault();
                    selectMention(filteredUsers[selectedIndex]);
                }
                break;
            case 'Escape':
                event.preventDefault();
                setShowSuggestions(false);
                break;
        }
    };

    // Get display text (convert mentions to @name format for display)
    const getDisplayText = (text: string): string => {
        return text.replace(/@\[([^\]]+)\]\(([^)]+)\)/g, '@$1');
    };

    // Get initials from name
    const getInitials = (name: string): string => {
        return name
            .split(' ')
            .map((part) => part[0])
            .join('')
            .toUpperCase()
            .substring(0, 2);
    };

    return (
        <Box>
            <TextField
                fullWidth
                inputRef={inputRef}
                label={label}
                placeholder={placeholder}
                value={getDisplayText(value)}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                multiline={multiline}
                rows={multiline ? rows : undefined}
                maxRows={multiline ? maxRows : undefined}
                disabled={disabled}
                error={error}
                helperText={helperText}
                onClick={(e) => {
                    const target = e.target as HTMLInputElement;
                    setCursorPosition(target.selectionStart || 0);
                }}
                onKeyUp={(e) => {
                    const target = e.target as HTMLInputElement;
                    setCursorPosition(target.selectionStart || 0);
                }}
            />

            {/* Mention Suggestions Popover */}
            <Popper
                open={showSuggestions}
                anchorEl={anchorEl}
                placement="bottom-start"
                style={{ zIndex: 1300 }}
            >
                <Paper elevation={8} sx={{ mt: 1, maxWidth: 400, maxHeight: 300, overflow: 'auto' }}>
                    <List dense>
                        {filteredUsers.map((user, index) => (
                            <ListItem key={user.id} disablePadding>
                                <ListItemButton
                                    selected={index === selectedIndex}
                                    onClick={() => selectMention(user)}
                                    onMouseEnter={() => setSelectedIndex(index)}
                                >
                                    <ListItemAvatar>
                                        <Avatar src={user.avatarUrl} alt={user.name}>
                                            {getInitials(user.name)}
                                        </Avatar>
                                    </ListItemAvatar>
                                    <ListItemText
                                        primary={user.name}
                                        secondary={
                                            <Stack direction="row" spacing={1} alignItems="center">
                                                <Typography variant="caption">{user.email}</Typography>
                                                {user.role && (
                                                    <>
                                                        <Typography variant="caption">•</Typography>
                                                        <Typography variant="caption">{user.role}</Typography>
                                                    </>
                                                )}
                                            </Stack>
                                        }
                                    />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                </Paper>
            </Popper>
        </Box>
    );
};

/**
 * Display component for showing mentions as chips
 */
interface MentionsDisplayProps {
    text: string;
    onMentionClick?: (userId: string) => void;
}

export const MentionsDisplay: React.FC<MentionsDisplayProps> = ({ text, onMentionClick }) => {
    // Parse text and split into segments
    const mentionPattern = /@\[([^\]]+)\]\(([^)]+)\)/g;
    const segments: Array<{ type: 'text' | 'mention'; content: string; userId?: string }> = [];
    let lastIndex = 0;
    let match;

    while ((match = mentionPattern.exec(text)) !== null) {
        // Add text before mention
        if (match.index > lastIndex) {
            segments.push({
                type: 'text',
                content: text.substring(lastIndex, match.index),
            });
        }

        // Add mention
        segments.push({
            type: 'mention',
            content: match[1], // User name
            userId: match[2], // User ID
        });

        lastIndex = match.index + match[0].length;
    }

    // Add remaining text
    if (lastIndex < text.length) {
        segments.push({
            type: 'text',
            content: text.substring(lastIndex),
        });
    }

    return (
        <Typography variant="body2" component="div">
            {segments.map((segment, index) => {
                if (segment.type === 'mention') {
                    return (
                        <Chip
                            key={index}
                            label={`@${segment.content}`}
                            size="small"
                            color="primary"
                            variant="outlined"
                            onClick={() => segment.userId && onMentionClick?.(segment.userId)}
                            sx={{ mx: 0.5, cursor: onMentionClick ? 'pointer' : 'default' }}
                        />
                    );
                }
                return <span key={index}>{segment.content}</span>;
            })}
        </Typography>
    );
};
