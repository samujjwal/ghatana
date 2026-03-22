import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Single user cursor with name label
 */
export const UserCursor = ({ user }) => {
    if (!user.cursor)
        return null;
    return (_jsxs("div", { className: "pointer-events-none absolute z-50 transition-all duration-75", style: {
            left: user.cursor.x,
            top: user.cursor.y,
            transform: 'translate(-2px, -2px)',
        }, children: [_jsx("svg", { width: "24", height: "24", viewBox: "0 0 24 24", fill: "none", style: { filter: 'drop-shadow(0 1px 2px rgba(0,0,0,0.3))' }, children: _jsx("path", { d: "M5 3L19 12L12 13L9 20L5 3Z", fill: user.color, stroke: "white", strokeWidth: "1.5" }) }), _jsx("div", { className: "absolute left-4 top-4 whitespace-nowrap rounded px-2 py-0.5 text-xs font-medium text-white shadow-sm", style: { backgroundColor: user.color }, children: user.name })] }));
};
UserCursor.displayName = 'UserCursor';
/**
 * Container for all collaboration cursors
 */
export const CollaborationCursors = ({ users, className = '', }) => {
    const onlineUsers = Object.values(users).filter((u) => u.isOnline && u.cursor);
    if (onlineUsers.length === 0)
        return null;
    return (_jsx("div", { className: `pointer-events-none absolute inset-0 overflow-hidden ${className}`, children: onlineUsers.map((user) => (_jsx(UserCursor, { user: user }, user.id))) }));
};
CollaborationCursors.displayName = 'CollaborationCursors';
/**
 * Status bar showing connection and user presence
 */
export const CollaborationStatusBar = ({ isConnected, syncStatus, users, currentUser, className = '', }) => {
    const onlineUsers = Object.values(users).filter((u) => u.isOnline);
    const totalUsers = onlineUsers.length + 1; // +1 for current user
    return (_jsxs("div", { className: `flex items-center gap-3 rounded-lg bg-white px-3 py-2 shadow-sm dark:bg-gray-800 ${className}`, children: [_jsxs("div", { className: "flex items-center gap-1.5", children: [_jsx("div", { className: `h-2 w-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-gray-400'}` }), _jsx("span", { className: "text-xs text-gray-600 dark:text-gray-400", children: syncStatus === 'synced' ? 'Synced' : syncStatus === 'syncing' ? 'Syncing...' : 'Offline' })] }), _jsx("div", { className: "h-4 w-px bg-gray-200 dark:bg-gray-700" }), _jsxs("div", { className: "flex -space-x-2", children: [_jsx("div", { className: "flex h-6 w-6 items-center justify-center rounded-full border-2 border-white text-xs font-medium text-white dark:border-gray-800", style: { backgroundColor: currentUser.color }, title: `${currentUser.name} (you)`, children: currentUser.name.charAt(0).toUpperCase() }), onlineUsers.slice(0, 5).map((user) => (_jsx("div", { className: "flex h-6 w-6 items-center justify-center rounded-full border-2 border-white text-xs font-medium text-white dark:border-gray-800", style: { backgroundColor: user.color }, title: user.name, children: user.name.charAt(0).toUpperCase() }, user.id))), onlineUsers.length > 5 && (_jsxs("div", { className: "flex h-6 w-6 items-center justify-center rounded-full border-2 border-white bg-gray-100 text-xs font-medium text-gray-600 dark:border-gray-800 dark:bg-gray-700 dark:text-gray-300", children: ["+", onlineUsers.length - 5] }))] }), _jsxs("span", { className: "text-xs text-gray-500 dark:text-gray-400", children: [totalUsers, " ", totalUsers === 1 ? 'user' : 'users'] })] }));
};
CollaborationStatusBar.displayName = 'CollaborationStatusBar';
//# sourceMappingURL=CollaborationComponents.js.map