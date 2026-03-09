/**
 * Organisms — Complex Component Combinations
 *
 * Organisms are complex components made up of molecules and/or atoms.
 * They represent complete functional sections of the UI.
 *
 * @packageDocumentation
 */

// Re-export organisms from parent directory for backward compatibility
// This allows gradual migration while maintaining existing imports

export { Form } from '../Form';
export type { FormProps } from '../Form';
export { FormField } from '../Form/FormField';
export { FormInput } from '../Form/FormInput';

export { Card } from '../Card';
export type { CardProps } from '../Card';

export { Modal } from '../Modal';
export type { ModalProps } from '../Modal';

export { Dialog } from '../Dialog';
export type { DialogProps } from '../Dialog';

export { Menu } from '../Menu';
export type { MenuProps } from '../Menu';

export * from '../Navigation';

export * from '../Tabs';

export * from '../Table';

export * from '../DataTable';

export * from '../Dashboard';

export * from '../Shell';

export * from '../Page';

export { Grid } from '../Grid';
export type { GridProps } from '../Grid';

export { Stack } from '../Stack';
export type { StackProps } from '../Stack';

export { Container } from '../Container';
export type { ContainerProps } from '../Container';

export { WorkspaceCard } from '../WorkspaceCard';
export type { WorkspaceCardProps } from '../WorkspaceCard';

/**
 * Organism components are complex, feature-rich sections of the UI.
 * They combine multiple molecules and atoms to create complete experiences.
 *
 * @example Page Layout
 * ```tsx
 * import { Page, Navigation, Dashboard } from '@ghatana/yappc-shared-ui-core/organisms';
 * import { Button } from '@ghatana/yappc-shared-ui-core/atoms';
 *
 * function HomePage() {
 *   return (
 *     <Page title="Dashboard">
 *       <Navigation
 *         items={[
 *           { label: 'Home', href: '/' },
 *           { label: 'Projects', href: '/projects' },
 *           { label: 'Settings', href: '/settings' }
 *         ]}
 *       />
 *
 *       <Dashboard
 *         widgets={[
 *           { type: 'stats', data: statsData },
 *           { type: 'chart', data: chartData }
 *         ]}
 *       />
 *     </Page>
 *   );
 * }
 * ```
 *
 * @example Data Display
 * ```tsx
 * import { Card, DataTable, Modal } from '@ghatana/yappc-shared-ui-core/organisms';
 * import { Button, Badge } from '@ghatana/yappc-shared-ui-core/atoms';
 *
 * function UserManagement() {
 *   const [selectedUser, setSelectedUser] = useState(null);
 *
 *   return (
 *     <Card
 *       title="Users"
 *       action={<Button>Add User</Button>}
 *     >
 *       <DataTable
 *         columns={columns}
 *         data={users}
 *         onRowClick={setSelectedUser}
 *         pagination
 *         sorting
 *         filtering
 *       />
 *
 *       {selectedUser && (
 *         <Modal
 *           open={!!selectedUser}
 *           onClose={() => setSelectedUser(null)}
 *           title="User Details"
 *         >
 *           User details content here
 *         </Modal>
 *       )}
 *     </Card>
 *   );
 * }
 * ```
 *
 * @example Form Layout
 * ```tsx
 * import { Form, Card, Tabs } from '@ghatana/yappc-shared-ui-core/organisms';
 * import { TextField, Select } from '@ghatana/yappc-shared-ui-core/molecules';
 * import { Button } from '@ghatana/yappc-shared-ui-core/atoms';
 *
 * function UserSettings() {
 *   return (
 *     <Card title="Settings">
 *       <Tabs
 *         tabs={[
 *           {
 *             label: 'Profile',
 *             content: (
 *               <Form onSubmit={handleProfileSubmit}>
 *                 <TextField label="Name" />
 *                 <TextField label="Email" type="email" />
 *                 <Button type="submit">Save Profile</Button>
 *               </Form>
 *             )
 *           },
 *           {
 *             label: 'Security',
 *             content: (
 *               <Form onSubmit={handleSecuritySubmit}>
 *                 <TextField label="Current Password" type="password" />
 *                 <TextField label="New Password" type="password" />
 *                 <Button type="submit">Update Password</Button>
 *               </Form>
 *             )
 *           }
 *         ]}
 *       />
 *     </Card>
 *   );
 * }
 * ```
 */
