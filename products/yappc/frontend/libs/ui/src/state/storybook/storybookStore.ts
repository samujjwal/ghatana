import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/**
 * Storybook-specific store
 *
 * This file contains atoms that are specifically for use in Storybook stories.
 * These atoms provide mock state for components in the Storybook development environment,
 * allowing component demonstration without backend dependencies.
 * 
 * **Note:** These atoms are for development/documentation purposes only and should
 * not be used in production code.
 */

/**
 * Storybook theme atom with localStorage persistence
 * 
 * Controls color scheme in Storybook stories for testing components in different themes.
 * Persists across Storybook sessions using localStorage key 'storybook-theme'.
 * 
 * @example
 * ```tsx
 * // In a Storybook story
 * export const ThemedComponent: Story = {
 *   decorators: [
 *     (Story) => {
 *       const [theme] = useAtom(storybookThemeAtom);
 *       return (
 *         <div className={theme === 'dark' ? 'dark' : ''}>
 *           <Story />
 *         </div>
 *       );
 *     }
 *   ]
 * };
 * ```
 */
export const storybookThemeAtom = atomWithStorage<'light' | 'dark'>('storybook-theme', 'light');

/**
 * Storybook platform atom
 * 
 * Simulates different platform contexts (web/desktop/mobile) in Storybook stories
 * for testing responsive behavior and platform-specific features.
 * 
 * @example
 * ```tsx
 * // In a Storybook story
 * export const MobileView: Story = {
 *   decorators: [
 *     (Story) => {
 *       const [, setPlatform] = useAtom(storybookPlatformAtom);
 *       
 *       useEffect(() => {
 *         setPlatform('mobile');
 *       }, []);
 *       
 *       return <Story />;
 *     }
 *   ]
 * };
 * ```
 */
export const storybookPlatformAtom = atom<'web' | 'desktop' | 'mobile'>('web');

/**
 * Storybook user preferences atom with localStorage persistence
 * 
 * Mock user preferences for testing components that depend on user settings.
 * Persists across Storybook sessions using localStorage key 'storybook-preferences'.
 * 
 * @example
 * ```tsx
 * // In a component story
 * function NotificationSettings() {
 *   const [prefs, setPrefs] = useAtom(storybookPreferencesAtom);
 *   
 *   return (
 *     <div>
 *       <label>
 *         <input
 *           type="checkbox"
 *           checked={prefs.notifications}
 *           onChange={(e) => setPrefs({ ...prefs, notifications: e.target.checked })}
 *         />
 *         Enable Notifications
 *       </label>
 *     </div>
 *   );
 * }
 * ```
 */
export const storybookPreferencesAtom = atomWithStorage('storybook-preferences', {
  notifications: true,
  autoSave: true,
  compactView: false,
  developerMode: false,
});

/**
 * Storybook workspace interface
 * 
 * Represents a mock workspace for Storybook component demonstrations.
 * 
 * @example
 * ```tsx
 * const demoWorkspace: StorybookWorkspace = {
 *   id: 'ws-123',
 *   name: 'My Project',
 *   description: 'Project description',
 *   lastModified: new Date().toISOString(),
 *   favorite: true
 * };
 * ```
 */
export interface StorybookWorkspace {
  /** Unique workspace identifier */
  id: string;
  /** Workspace display name */
  name: string;
  /** Optional workspace description */
  description?: string;
  /** ISO 8601 timestamp of last modification */
  lastModified: string;
  /** Whether workspace is marked as favorite */
  favorite: boolean;
}

/**
 * Storybook workspaces atom with localStorage persistence
 * 
 * Mock workspace list for testing workspace selection, listing, and management components.
 * Persists across Storybook sessions using localStorage key 'storybook-workspaces'.
 * Includes 3 sample workspaces with varying states (favorite/non-favorite, different ages).
 * 
 * @example
 * ```tsx
 * // In a workspace list story
 * function WorkspaceList() {
 *   const [workspaces, setWorkspaces] = useAtom(storybookWorkspacesAtom);
 *   
 *   const toggleFavorite = (id: string) => {
 *     setWorkspaces(workspaces.map(ws =>
 *       ws.id === id ? { ...ws, favorite: !ws.favorite } : ws
 *     ));
 *   };
 *   
 *   return (
 *     <ul>
 *       {workspaces.map(ws => (
 *         <li key={ws.id}>
 *           {ws.name}
 *           <button onClick={() => toggleFavorite(ws.id)}>
 *             {ws.favorite ? '★' : '☆'}
 *           </button>
 *         </li>
 *       ))}
 *     </ul>
 *   );
 * }
 * ```
 */
export const storybookWorkspacesAtom = atomWithStorage<StorybookWorkspace[]>('storybook-workspaces', [
  {
    id: 'workspace-1',
    name: 'Demo Workspace',
    description: 'A demo workspace for Storybook',
    lastModified: new Date().toISOString(),
    favorite: true,
  },
  {
    id: 'workspace-2',
    name: 'Project X',
    description: 'Top secret project',
    lastModified: new Date(Date.now() - 86400000).toISOString(), // 1 day ago
    favorite: false,
  },
  {
    id: 'workspace-3',
    name: 'Mobile App',
    description: 'Mobile application prototype',
    lastModified: new Date(Date.now() - 172800000).toISOString(), // 2 days ago
    favorite: true,
  },
]);

/**
 * Storybook diagram node interface
 * 
 * Represents a node in a flow diagram for testing diagram/flowchart components.
 * Compatible with React Flow and similar diagram libraries.
 * 
 * @example
 * ```tsx
 * const processNode: StorybookDiagramNode = {
 *   id: 'node-1',
 *   type: 'process',
 *   position: { x: 250, y: 100 },
 *   data: {
 *     label: 'Data Processing',
 *     status: 'active',
 *     progress: 0.75
 *   }
 * };
 * ```
 */
export interface StorybookDiagramNode {
  /** Unique node identifier */
  id: string;
  /** Node type (e.g., 'default', 'input', 'output', 'process') */
  type: string;
  /** Node position in diagram coordinates */
  position: { x: number; y: number };
  /** Node data including label and custom properties */
  data: {
    /** Display label for the node */
    label: string;
    /** Additional custom data fields */
    [key: string]: unknown;
  };
}

/**
 * Storybook diagram nodes atom
 * 
 * Mock node collection for testing diagram/flowchart components.
 * Includes 3 sample nodes with different types (default, input, output) arranged horizontally.
 * 
 * @example
 * ```tsx
 * // In a diagram component story
 * function DiagramEditor() {
 *   const [nodes, setNodes] = useAtom(storybookDiagramNodesAtom);
 *   
 *   const addNode = () => {
 *     const newNode: StorybookDiagramNode = {
 *       id: `node-${Date.now()}`,
 *       type: 'default',
 *       position: { x: Math.random() * 500, y: Math.random() * 300 },
 *       data: { label: 'New Node' }
 *     };
 *     setNodes([...nodes, newNode]);
 *   };
 *   
 *   return (
 *     <>
 *       <ReactFlow nodes={nodes} />
 *       <button onClick={addNode}>Add Node</button>
 *     </>
 *   );
 * }
 * ```
 */
export const storybookDiagramNodesAtom = atom<StorybookDiagramNode[]>([
  {
    id: 'node-1',
    type: 'default',
    position: { x: 100, y: 100 },
    data: { label: 'Node 1' },
  },
  {
    id: 'node-2',
    type: 'input',
    position: { x: 300, y: 100 },
    data: { label: 'Node 2' },
  },
  {
    id: 'node-3',
    type: 'output',
    position: { x: 500, y: 100 },
    data: { label: 'Node 3' },
  },
]);

/**
 * Storybook diagram edge interface
 * 
 * Represents a connection between diagram nodes for testing diagram/flowchart components.
 * Compatible with React Flow and similar diagram libraries.
 * 
 * @example
 * ```tsx
 * const dataFlowEdge: StorybookDiagramEdge = {
 *   id: 'edge-1',
 *   source: 'node-input',
 *   target: 'node-output',
 *   type: 'smoothstep',
 *   label: 'Data Flow',
 *   animated: true
 * };
 * ```
 */
export interface StorybookDiagramEdge {
  /** Unique edge identifier */
  id: string;
  /** Source node ID */
  source: string;
  /** Target node ID */
  target: string;
  /** Edge type (e.g., 'default', 'smoothstep', 'step', 'straight') */
  type?: string;
  /** Optional edge label */
  label?: string;
  /** Whether edge animation is enabled */
  animated?: boolean;
}

/**
 * Storybook diagram edges atom
 * 
 * Mock edge collection for testing diagram/flowchart components.
 * Includes 2 sample edges connecting the nodes in storybookDiagramNodesAtom,
 * with one static and one animated edge.
 * 
 * @example
 * ```tsx
 * // In a diagram component story
 * function DiagramEditor() {
 *   const [edges, setEdges] = useAtom(storybookDiagramEdgesAtom);
 *   const [nodes] = useAtom(storybookDiagramNodesAtom);
 *   
 *   const connectNodes = (sourceId: string, targetId: string) => {
 *     const newEdge: StorybookDiagramEdge = {
 *       id: `edge-${Date.now()}`,
 *       source: sourceId,
 *       target: targetId,
 *       type: 'smoothstep',
 *       animated: false
 *     };
 *     setEdges([...edges, newEdge]);
 *   };
 *   
 *   return <ReactFlow nodes={nodes} edges={edges} />;
 * }
 * ```
 */
export const storybookDiagramEdgesAtom = atom<StorybookDiagramEdge[]>([
  {
    id: 'edge-1',
    source: 'node-1',
    target: 'node-2',
    type: 'default',
    animated: false,
  },
  {
    id: 'edge-2',
    source: 'node-2',
    target: 'node-3',
    type: 'default',
    animated: true,
  },
]);

/**
 * Storybook notification interface
 * 
 * Represents a notification message for testing notification components.
 * Includes type, message, optional title, timestamp, and read status.
 * 
 * @example
 * ```tsx
 * const notification: StorybookNotification = {
 *   id: 'notif-1',
 *   type: 'success',
 *   message: 'Your changes have been saved',
 *   title: 'Success',
 *   timestamp: new Date().toISOString(),
 *   read: false
 * };
 * ```
 */
export interface StorybookNotification {
  /** Unique notification identifier */
  id: string;
  /** Notification type affecting visual style */
  type: 'info' | 'success' | 'warning' | 'error';
  /** Notification message text */
  message: string;
  /** Optional notification title */
  title?: string;
  /** ISO 8601 timestamp when notification was created */
  timestamp: string;
  /** Whether notification has been marked as read */
  read: boolean;
}

/**
 * Storybook notifications atom
 * 
 * Mock notification list for testing notification components.
 * Includes 4 sample notifications covering all types (info, success, warning, error)
 * with varying ages and read states.
 * 
 * @example
 * ```tsx
 * // In a notification center story
 * function NotificationCenter() {
 *   const [notifications, setNotifications] = useAtom(storybookNotificationsAtom);
 *   
 *   const markAsRead = (id: string) => {
 *     setNotifications(notifications.map(n =>
 *       n.id === id ? { ...n, read: true } : n
 *     ));
 *   };
 *   
 *   const unreadCount = notifications.filter(n => !n.read).length;
 *   
 *   return (
 *     <div>
 *       <h3>Notifications ({unreadCount} unread)</h3>
 *       {notifications.map(n => (
 *         <div key={n.id} className={n.read ? 'read' : 'unread'}>
 *           <strong>{n.title}</strong>
 *           <p>{n.message}</p>
 *           {!n.read && <button onClick={() => markAsRead(n.id)}>Mark as read</button>}
 *         </div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export const storybookNotificationsAtom = atom<StorybookNotification[]>([
  {
    id: 'notification-1',
    type: 'info',
    message: 'Welcome to the Storybook environment',
    title: 'Welcome',
    timestamp: new Date().toISOString(),
    read: false,
  },
  {
    id: 'notification-2',
    type: 'success',
    message: 'Your changes have been saved successfully',
    title: 'Saved',
    timestamp: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
    read: true,
  },
  {
    id: 'notification-3',
    type: 'warning',
    message: 'Your session will expire in 5 minutes',
    title: 'Session Expiring',
    timestamp: new Date(Date.now() - 7200000).toISOString(), // 2 hours ago
    read: false,
  },
  {
    id: 'notification-4',
    type: 'error',
    message: 'Failed to connect to the server',
    title: 'Connection Error',
    timestamp: new Date(Date.now() - 10800000).toISOString(), // 3 hours ago
    read: false,
  },
]);

/**
 * Storybook user interface
 * 
 * Represents a mock user for testing user-related components.
 * Includes basic user information and role.
 * 
 * @example
 * ```tsx
 * const adminUser: StorybookUser = {
 *   id: 'user-123',
 *   name: 'Jane Admin',
 *   email: 'jane@example.com',
 *   avatar: 'https://i.pravatar.cc/150?u=jane',
 *   role: 'admin'
 * };
 * ```
 */
export interface StorybookUser {
  /** Unique user identifier */
  id: string;
  /** User's display name */
  name: string;
  /** User's email address */
  email: string;
  /** Optional avatar image URL */
  avatar?: string;
  /** User's role/permission level */
  role: 'admin' | 'user' | 'guest';
}

/**
 * Storybook user atom with localStorage persistence
 * 
 * Mock authenticated user for testing user profile, settings, and permission-based components.
 * Persists across Storybook sessions using localStorage key 'storybook-user'.
 * Default user is an admin with full permissions.
 * 
 * @example
 * ```tsx
 * // In a user profile story
 * function UserProfile() {
 *   const [user, setUser] = useAtom(storybookUserAtom);
 *   
 *   return (
 *     <div>
 *       <img src={user.avatar} alt={user.name} />
 *       <h2>{user.name}</h2>
 *       <p>{user.email}</p>
 *       <span className="badge">{user.role}</span>
 *     </div>
 *   );
 * }
 * 
 * // Testing different roles
 * export const GuestUserView: Story = {
 *   decorators: [
 *     (Story) => {
 *       const [, setUser] = useAtom(storybookUserAtom);
 *       
 *       useEffect(() => {
 *         setUser({
 *           id: 'guest-1',
 *           name: 'Guest User',
 *           email: 'guest@example.com',
 *           role: 'guest'
 *         });
 *       }, []);
 *       
 *       return <Story />;
 *     }
 *   ]
 * };
 * ```
 */
export const storybookUserAtom = atomWithStorage<StorybookUser>('storybook-user', {
  id: 'user-1',
  name: 'John Doe',
  email: 'john.doe@example.com',
  avatar: 'https://i.pravatar.cc/150?u=john.doe@example.com',
  role: 'admin',
});
