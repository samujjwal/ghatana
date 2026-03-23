import * as React from 'react';

import { sxToStyle, type SxProps } from '../utils/sx';

type IconProps = Omit<React.SVGProps<SVGSVGElement>, 'color'> & {
    title?: string;
    /** MUI-like sx prop (limited support). */
    sx?: SxProps;
    /** MUI-like semantic color token (e.g., "success"). */
    color?: string;
};

function createIcon(displayName: string, pathD: string) {
    const Icon: React.FC<IconProps> = ({ title, sx, style, color: _color, ...props }) => (
        <svg
            viewBox="0 0 24 24"
            width="1em"
            height="1em"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden={title ? undefined : true}
            role={title ? 'img' : 'presentation'}
            style={{ ...style, ...sxToStyle(sx) }}
            {...props}
        >
            {title ? <title>{title}</title> : null}
            <path d={pathD} />
        </svg>
    );
    Icon.displayName = displayName;
    return Icon;
}

export const Search = createIcon('Search', 'M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16Zm10 2-4.35-4.35');
export const Close = createIcon('Close', 'M18 6 6 18M6 6l12 12');
export const Visibility = createIcon('Visibility', 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z');
export const Warning = createIcon('Warning', 'M12 9v4m0 4h.01M10.3 4.3h3.4L22 20H2L10.3 4.3Z');
export const CheckCircle = createIcon('CheckCircle', 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Zm-6-3-5 6-3-3');
export const Check = createIcon('Check', 'M20 6 9 17 4 12');
export const TrendingUp = createIcon('TrendingUp', 'M3 17l6-6 4 4 8-8M14 7h7v7');
export const TrendingDown = createIcon('TrendingDown', 'M3 7l6 6 4-4 8 8M14 17h7v-7');
export const Storage = createIcon('Storage', 'M4 6c0-1.1 3.6-2 8-2s8 .9 8 2-3.6 2-8 2-8-.9-8-2Zm0 6c0 1.1 3.6 2 8 2s8-.9 8-2M4 6v12c0 1.1 3.6 2 8 2s8-.9 8-2V6');
export const People = createIcon('People', 'M16 11a4 4 0 1 0-8 0m12 10c0-3.3-3.6-6-8-6s-8 2.7-8 6');
export const Speed = createIcon('Speed', 'M20 12a8 8 0 1 1-16 0m8 0 5-5');
export const Security = createIcon('Security', 'M12 2l8 4v6c0 5-3.4 9.7-8 10-4.6-.3-8-5-8-10V6l8-4Z');
export const Error = createIcon('Error', 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 6v5m0 3h.01');
export const Memory = createIcon('Memory', 'M9 2v2H7a2 2 0 0 0-2 2v2H3v2h2v4H3v2h2v2a2 2 0 0 0 2 2h2v2h2v-2h4v2h2v-2h2a2 2 0 0 0 2-2v-2h2v-2h-2v-4h2V8h-2V6a2 2 0 0 0-2-2h-2V2h-2v2h-4V2H9Zm-2 6h10v10H7V8Z');
export const CloudQueue = createIcon('CloudQueue', 'M7 18h10a4 4 0 0 0 0-8 5 5 0 0 0-9.7-1.5A4 4 0 0 0 7 18Z');
export const Block = createIcon('Block', 'M6.2 6.2a10 10 0 0 1 11.6 0m-11.6 0a10 10 0 0 0 0 11.6m0-11.6L17.8 17.8m0 0a10 10 0 0 1-11.6 0m11.6 0a10 10 0 0 0 0-11.6');
export const Person = createIcon('Person', 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z');
export const AdminPanelSettings = createIcon('AdminPanelSettings', 'M12 2l8 4v6c0 5-3.4 9.7-8 10-4.6-.3-8-5-8-10V6l8-4Zm0 6a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z');
export const Add = createIcon('Add', 'M12 5v14M5 12h14');
export const Delete = createIcon('Delete', 'M3 6h18M8 6V4h8v2m-7 3v10m6-10v10M6 6l1 16h10l1-16');
export const Remove = createIcon('Remove', 'M5 12h14');
export const Business = createIcon('Business', 'M4 22V4a2 2 0 0 1 2-2h8v6h6v14H4Zm4-4h2m-2-4h2m-2-4h2m4 8h2m-2-4h2m-2-4h2');
export const IntegrationInstructions = createIcon('IntegrationInstructions', 'M8 7h8M8 11h8M8 15h5M6 3h12a2 2 0 0 1 2 2v14l-4-2-4 2-4-2-4 2V5a2 2 0 0 1 2-2Z');
export const Celebration = createIcon('Celebration', 'M3 21l2-6 6-2 10-10 0 6-10 10-2 6H3Zm7-13 6 6');
export const Download = createIcon('Download', 'M12 3v10m0 0 4-4m-4 4-4-4M5 21h14');
export const CreditCard = createIcon('CreditCard', 'M3 7h18v10H3V7Zm0 4h18');
export const Receipt = createIcon('Receipt', 'M4 2h16v20l-2-1-2 1-2-1-2 1-2-1-2 1-2-1-2 1V2Zm4 6h8m-8 4h8m-8 4h5');
export const Edit = createIcon('Edit', 'M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z');
export const MoreVert = createIcon('MoreVert', 'M12 5h.01M12 12h.01M12 19h.01');
export const CalendarToday = createIcon('CalendarToday', 'M7 2v2M17 2v2M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z');
export const Schedule = createIcon('Schedule', 'M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z');
export const Assignment = createIcon('Assignment', 'M9 5h6M9 9h6M9 13h6M7 3h10a2 2 0 0 1 2 2v16l-2-1-2 1-2-1-2 1-2-1-2 1-2-1-2 1V5a2 2 0 0 1 2-2Z');
export const Star = createIcon('Star', 'M12 2l3 7h7l-5.5 4.1L18 21l-6-3.6L6 21l1.5-7.9L2 9h7l3-7Z');
export const EmojiEvents = createIcon('EmojiEvents', 'M8 21h8M12 17a5 5 0 0 0 5-5V6H7v6a5 5 0 0 0 5 5Zm-7-8H3V6h2m14 3h2V6h-2');
export const LocalFireDepartment = createIcon('LocalFireDepartment', 'M12 2s4 4 4 8a4 4 0 0 1-8 0c0-2.5 1.5-4.5 2.5-6.5C10.8 5 9 7 9 10a3 3 0 0 0 6 0c0-2-1.3-4-3-5.8Z');
export const Group = createIcon('Group', 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2m16 0v-2a4 4 0 0 0-3-3.9M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm11 10v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8');
export const Assessment = createIcon('Assessment', 'M3 3h18v18H3V3Zm4 14h2V8H7v9Zm4 0h2V5h-2v12Zm4 0h2V11h-2v6');
export const Flag = createIcon('Flag', 'M5 5v16M5 5h12l-1 4 1 4H5');
export const Comment = createIcon('Comment', 'M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8Z');
export const RadioButtonUnchecked = createIcon('RadioButtonUnchecked', 'M12 22a10 10 0 1 1 0-20 10 10 0 0 1 0 20Z');
export const Notes = createIcon('Notes', 'M4 4h16v16H4V4Zm3 4h10M7 12h10M7 16h7');
export const EmojiEmotions = createIcon('EmojiEmotions', 'M12 22a10 10 0 1 1 0-20 10 10 0 0 1 0 20Zm-3-9s1 2 3 2 3-2 3-2M9 10h.01M15 10h.01');
export const Folder = createIcon('Folder', 'M3 6a2 2 0 0 1 2-2h5l2 2h9a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6Z');
export const DragIndicator = createIcon('DragIndicator', 'M10 8h.01M14 8h.01M10 12h.01M14 12h.01M10 16h.01M14 16h.01');
export const AttachFile = createIcon('AttachFile', 'M21 11.5 12.5 20a5 5 0 0 1-7.1-7.1L14 4.3a3.5 3.5 0 1 1 5 5L10.8 17.5a2 2 0 1 1-2.8-2.8L15.8 7');
export const Timeline = createIcon('Timeline', 'M4 16l4-4 4 4 8-8M4 6h.01M4 16h.01M12 12h.01M20 8h.01');

export const Notifications = createIcon(
    'Notifications',
    'M18 8a6 6 0 1 0-12 0c0 7-3 7-3 7h18s-3 0-3-7Zm-6 14a2 2 0 0 0 2-2H10a2 2 0 0 0 2 2Z'
);

export const ChevronRight = createIcon('ChevronRight', 'M9 18l6-6-6-6');
export const ChevronLeft = createIcon('ChevronLeft', 'M15 18l-6-6 6-6');

export const PlayArrow = createIcon('PlayArrow', 'M8 5v14l11-7-11-7Z');

// Additional MUI-compat icons
export const Pause = createIcon('Pause', 'M6 4h4v16H6zM14 4h4v16h-4z');

// Alias for compatibility (outline vs filled not distinguished in this icon set)
export const CheckCircleOutline = CheckCircle;

export const School = createIcon('School', 'M12 3 2 8l10 5 10-5-10-5Zm-7 7v6c0 1.5 3.1 3 7 3s7-1.5 7-3v-6');
export const Code = createIcon('Code', 'M8 9 5 12l3 3M16 9l3 3-3 3');
export const Book = createIcon('Book', 'M6 4h12a2 2 0 0 1 2 2v14a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2V6a2 2 0 0 1 2-2Z');
export const Verified = createIcon('Verified', 'M12 2 15 4l3-1 1 3 3 2-2 3 2 3-3 2-1 3-3-1-3 2-3-2-3 1-1-3-3-2 2-3-2-3 3-2 1-3 3 1 3-2Zm-2 10 2 2 4-4');
