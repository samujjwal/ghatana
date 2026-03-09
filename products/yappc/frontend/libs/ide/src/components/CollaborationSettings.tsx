/**
 * @ghatana/yappc-ide - Collaboration Settings Component
 * 
 * Settings interface for collaboration features including
 * permissions, visibility options, and conflict resolution.
 * 
 * @doc.type component
 * @doc.purpose Collaboration settings interface for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback } from 'react';

import { useCollaborativeEditing } from '../hooks/useCollaborativeEditing';
import type { CollaborationSettings as CollaborationSettingsType } from '../hooks/useCollaborativeEditing';

/**
 * User permission levels
 */
export type PermissionLevel = 'read' | 'comment' | 'edit' | 'admin';

/**
 * User permission entry
 */
interface UserPermission {
  userId: string;
  userName: string;
  permission: PermissionLevel;
  grantedAt: number;
  grantedBy: string;
}

/**
 * Collaboration settings props
 */
export interface CollaborationSettingsProps {
  isOpen: boolean;
  onClose: () => void;
  workspaceId?: string;
  className?: string;
}

/**
 * Permission level badge
 */
interface PermissionBadgeProps {
  level: PermissionLevel;
  onChange?: (level: PermissionLevel) => void;
  editable?: boolean;
}

const PermissionBadge: React.FC<PermissionBadgeProps> = ({
  level,
  onChange,
  editable = false,
}) => {
  const getLevelColor = useCallback((level: PermissionLevel) => {
    switch (level) {
      case 'read':
        return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300';
      case 'comment':
        return 'bg-blue-100 text-blue-700 dark:bg-blue-900/20 dark:text-blue-300';
      case 'edit':
        return 'bg-green-100 text-green-700 dark:bg-green-900/20 dark:text-green-300';
      case 'admin':
        return 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-300';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  }, []);

  const getLevelDescription = useCallback((level: PermissionLevel) => {
    switch (level) {
      case 'read':
        return 'Can view files';
      case 'comment':
        return 'Can view and comment';
      case 'edit':
        return 'Can view, comment, and edit';
      case 'admin':
        return 'Full access including settings';
      default:
        return 'Unknown permission';
    }
  }, []);

  if (editable && onChange) {
    return (
      <select
        value={level}
        onChange={(e) => onChange(e.target.value as PermissionLevel)}
        className="px-2 py-1 text-xs border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
      >
        <option value="read">Read</option>
        <option value="comment">Comment</option>
        <option value="edit">Edit</option>
        <option value="admin">Admin</option>
      </select>
    );
  }

  return (
    <div className="flex flex-col">
      <span className={`px-2 py-1 text-xs rounded font-medium ${getLevelColor(level)}`}>
        {level.charAt(0).toUpperCase() + level.slice(1)}
      </span>
      <span className="text-xs text-gray-500 dark:text-gray-400 mt-1">
        {getLevelDescription(level)}
      </span>
    </div>
  );
};

/**
 * User permission item
 */
interface UserPermissionItemProps {
  permission: UserPermission;
  onChange: (userId: string, level: PermissionLevel) => void;
  onRemove: (userId: string) => void;
}

const UserPermissionItem: React.FC<UserPermissionItemProps> = ({
  permission,
  onChange,
  onRemove,
}) => {
  return (
    <div className="flex items-center gap-3 p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
      <div className="flex-1">
        <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
          {permission.userName}
        </div>
        <div className="text-xs text-gray-500 dark:text-gray-400">
          Added {new Date(permission.grantedAt).toLocaleDateString()} by {permission.grantedBy}
        </div>
      </div>
      <PermissionBadge
        level={permission.permission}
        onChange={(level) => onChange(permission.userId, level)}
        editable
      />
      <button
        onClick={() => onRemove(permission.userId)}
        className="p-1 text-red-500 hover:text-red-700 dark:hover:text-red-400 transition-colors"
        title="Remove user"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
};

/**
 * Collaboration Settings Component
 */
export const CollaborationSettings: React.FC<CollaborationSettingsProps> = ({
  isOpen,
  onClose,
  workspaceId,
}) => {
  const { settings, updateSettings, activeUsers } = useCollaborativeEditing();

  // Local state
  const [activeTab, setActiveTab] = useState<'general' | 'permissions' | 'advanced'>('general');
  const [userPermissions, setUserPermissions] = useState<UserPermission[]>([
    {
      userId: 'user1',
      userName: 'John Doe',
      permission: 'edit',
      grantedAt: Date.now() - 86400000,
      grantedBy: 'me',
    },
    {
      userId: 'user2',
      userName: 'Jane Smith',
      permission: 'comment',
      grantedAt: Date.now() - 172800000,
      grantedBy: 'me',
    },
  ]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [invitePermission, setInvitePermission] = useState<PermissionLevel>('edit');

  // Handle settings update
  const handleSettingChange = useCallback(<K extends keyof CollaborationSettingsType>(
    key: K,
    value: CollaborationSettingsType[K]
  ) => {
    updateSettings({ [key]: value });
  }, [updateSettings]);

  // Handle permission change
  const handlePermissionChange = useCallback((userId: string, level: PermissionLevel) => {
    setUserPermissions(prev => prev.map(p =>
      p.userId === userId ? { ...p, permission: level } : p
    ));
  }, []);

  // Handle user removal
  const handleUserRemove = useCallback((userId: string) => {
    setUserPermissions(prev => prev.filter(p => p.userId !== userId));
  }, []);

  // Handle user invitation
  const handleInviteUser = useCallback(() => {
    if (!inviteEmail.trim()) return;

    const newPermission: UserPermission = {
      userId: inviteEmail,
      userName: inviteEmail.split('@')[0],
      permission: invitePermission,
      grantedAt: Date.now(),
      grantedBy: 'me',
    };

    setUserPermissions(prev => [...prev, newPermission]);
    setInviteEmail('');
  }, [inviteEmail, invitePermission]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="w-full max-w-2xl bg-white dark:bg-gray-900 rounded-lg shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Collaboration Settings
          </h2>
          <button
            onClick={onClose}
            className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-gray-200 dark:border-gray-700">
          {[
            { id: 'general', label: 'General' },
            { id: 'permissions', label: 'Permissions' },
            { id: 'advanced', label: 'Advanced' },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as 'general' | 'permissions' | 'advanced')}
              className={`
                px-4 py-2 text-sm font-medium transition-colors
                ${activeTab === tab.id
                  ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600 dark:border-blue-400'
                  : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                }
              `}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="p-4 max-h-96 overflow-y-auto">
          {/* General Settings */}
          {activeTab === 'general' && (
            <div className="space-y-4">
              <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
                Visual Settings
              </h3>

              <div className="space-y-3">
                <label className="flex items-center justify-between">
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Show other users' cursors
                  </span>
                  <input
                    type="checkbox"
                    checked={settings.showCursors}
                    onChange={(e) => handleSettingChange('showCursors', e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                </label>

                <label className="flex items-center justify-between">
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Show other users' selections
                  </span>
                  <input
                    type="checkbox"
                    checked={settings.showSelections}
                    onChange={(e) => handleSettingChange('showSelections', e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                </label>

                <label className="flex items-center justify-between">
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Show user avatars
                  </span>
                  <input
                    type="checkbox"
                    checked={settings.showAvatars}
                    onChange={(e) => handleSettingChange('showAvatars', e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                </label>

                <label className="flex items-center justify-between">
                  <span className="text-sm text-gray-700 dark:text-gray-300">
                    Show typing indicators
                  </span>
                  <input
                    type="checkbox"
                    checked={settings.enableTypingIndicators}
                    onChange={(e) => handleSettingChange('enableTypingIndicators', e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                  />
                </label>
              </div>

              <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
                  Conflict Resolution
                </h3>

                <div className="space-y-3">
                  <label className="flex items-center justify-between">
                    <span className="text-sm text-gray-700 dark:text-gray-300">
                      Auto-resolve conflicts
                    </span>
                    <input
                      type="checkbox"
                      checked={settings.autoResolveConflicts}
                      onChange={(e) => handleSettingChange('autoResolveConflicts', e.target.checked)}
                      className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                  </label>

                  <div>
                    <label className="text-sm text-gray-700 dark:text-gray-300">
                      Default resolution strategy
                    </label>
                    <select
                      value={settings.conflictResolutionStrategy}
                      onChange={(e) => handleSettingChange('conflictResolutionStrategy', e.target.value as 'latest-wins' | 'manual' | 'merge')}
                      className="mt-1 w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
                    >
                      <option value="latest-wins">Latest Wins</option>
                      <option value="manual">Manual Resolution</option>
                      <option value="merge">Auto Merge</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Permissions */}
          {activeTab === 'permissions' && (
            <div className="space-y-4">
              <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
                User Permissions
              </h3>

              {/* Invite user */}
              <div className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                <div className="flex gap-2">
                  <input
                    type="email"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    placeholder="Enter email address"
                    className="flex-1 px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
                  />
                  <select
                    value={invitePermission}
                    onChange={(e) => setInvitePermission(e.target.value as 'read' | 'comment' | 'edit' | 'admin')}
                    className="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300"
                  >
                    <option value="read">Read</option>
                    <option value="comment">Comment</option>
                    <option value="edit">Edit</option>
                    <option value="admin">Admin</option>
                  </select>
                  <button
                    onClick={handleInviteUser}
                    className="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
                  >
                    Invite
                  </button>
                </div>
              </div>

              {/* User list */}
              <div className="space-y-2">
                {userPermissions.map(permission => (
                  <UserPermissionItem
                    key={permission.userId}
                    permission={permission}
                    onChange={handlePermissionChange}
                    onRemove={handleUserRemove}
                  />
                ))}
              </div>

              {/* Active users */}
              {activeUsers.length > 0 && (
                <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                  <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
                    Currently Active ({activeUsers.length})
                  </h3>
                  <div className="space-y-2">
                    {activeUsers.map(user => (
                      <div key={user.userId} className="flex items-center gap-2 p-2 bg-gray-50 dark:bg-gray-800 rounded">
                        <div
                          className="w-3 h-3 rounded-full"
                          style={{ backgroundColor: user.userColor }}
                        />
                        <span className="text-sm text-gray-700 dark:text-gray-300">
                          {user.userName}
                        </span>
                        <span className="text-xs text-gray-500 dark:text-gray-400">
                          {user.activity === 'typing' ? 'Typing...' :
                            user.activity === 'selecting' ? 'Selecting...' :
                              'Idle'}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Advanced */}
          {activeTab === 'advanced' && (
            <div className="space-y-4">
              <h3 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-3">
                Advanced Settings
              </h3>

              <div className="space-y-3">
                <div className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <h4 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-2">
                    Session Management
                  </h4>
                  <div className="space-y-2">
                    <button className="w-full px-3 py-2 text-sm text-left bg-gray-50 dark:bg-gray-800 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
                      Export Session Data
                    </button>
                    <button className="w-full px-3 py-2 text-sm text-left bg-gray-50 dark:bg-gray-800 rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
                      Import Session Data
                    </button>
                    <button className="w-full px-3 py-2 text-sm text-left bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300 rounded hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors">
                      Clear All Session Data
                    </button>
                  </div>
                </div>

                <div className="p-3 border border-gray-200 dark:border-gray-700 rounded-lg">
                  <h4 className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-2">
                    Debug Information
                  </h4>
                  <div className="text-xs text-gray-600 dark:text-gray-400 space-y-1">
                    <div>Workspace ID: {workspaceId || 'N/A'}</div>
                    <div>Active Users: {activeUsers.length}</div>
                    <div>Session Duration: {Math.floor((Date.now() - (Date.now() - 3600000)) / 60000)} minutes</div>
                    <div>WebSocket Status: Connected</div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 p-4 border-t border-gray-200 dark:border-gray-700">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-800 rounded hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
          >
            Save Settings
          </button>
        </div>
      </div>
    </div>
  );
};
