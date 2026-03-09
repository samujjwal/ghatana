/**
 * Social Features and Sphere Sharing Components for Flashit Web App
 * User interface for collaboration, sharing, and social interactions
 *
 * @doc.type component
 * @doc.purpose Social collaboration interface components
 * @doc.layer product
 * @doc.pattern ReactComponent
 */

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

// Types
interface User {
  id: string;
  email: string;
  displayName: string;
  avatar?: string;
}

interface SphereShare {
  id: string;
  sphereId: string;
  sharedByUserId: string;
  sharedWithUserId?: string;
  sharedWithEmail?: string;
  permissionLevel: 'viewer' | 'commenter' | 'editor' | 'admin';
  acceptedAt?: string;
  createdAt: string;
}

interface Collaborator {
  userId: string;
  email: string;
  displayName: string;
  permissionLevel: string;
  joinedAt: string;
  lastActivity?: string;
}

interface ActivityFeedItem {
  id: string;
  actorUserId: string;
  actorDisplayName: string;
  activityType: string;
  sphereName?: string;
  activityData: any;
  readAt?: string;
  createdAt: string;
}

/**
 * Sphere Sharing Dialog Component
 */
export function SphereShareDialog({
  sphereId,
  sphereName,
  isOpen,
  onClose
}: {
  sphereId: string;
  sphereName: string;
  isOpen: boolean;
  onClose: () => void;
}) {
  const [email, setEmail] = useState('');
  const [permissionLevel, setPermissionLevel] = useState<'viewer' | 'commenter' | 'editor' | 'admin'>('viewer');
  const [message, setMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const queryClient = useQueryClient();

  const shareMutation = useMutation({
    mutationFn: async (data: { sphereId: string; sharedWithEmail: string; permissionLevel: string; message?: string }) => {
      const response = await fetch('/api/collaboration/spheres/share', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        throw new Error('Failed to share sphere');
      }

      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sphere-collaborators', sphereId] });
      setEmail('');
      setMessage('');
      onClose();
    },
  });

  const handleShare = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      await shareMutation.mutateAsync({
        sphereId,
        sharedWithEmail: email,
        permissionLevel,
        message: message.trim() || undefined,
      });
    } catch (error) {
      console.error('Failed to share sphere:', error);
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md mx-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-900">Share "{sphereName}"</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleShare}>
          <div className="mb-4">
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
              Email address
            </label>
            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="colleague@example.com"
              required
            />
          </div>

          <div className="mb-4">
            <label htmlFor="permission" className="block text-sm font-medium text-gray-700 mb-2">
              Permission level
            </label>
            <select
              id="permission"
              value={permissionLevel}
              onChange={(e) => setPermissionLevel(e.target.value as any)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="viewer">Viewer - Can view moments</option>
              <option value="commenter">Commenter - Can view and comment</option>
              <option value="editor">Editor - Can view, comment, and edit</option>
              <option value="admin">Admin - Full control</option>
            </select>
          </div>

          <div className="mb-4">
            <label htmlFor="message" className="block text-sm font-medium text-gray-700 mb-2">
              Personal message (optional)
            </label>
            <textarea
              id="message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              rows={3}
              placeholder="I'd like to share this sphere with you..."
              maxLength={500}
            />
          </div>

          <div className="flex justify-end space-x-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="px-4 py-2 bg-blue-500 text-white rounded-md hover:bg-blue-600 disabled:opacity-50 transition-colors"
            >
              {isLoading ? 'Sharing...' : 'Share Sphere'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/**
 * Collaborators List Component
 */
export function CollaboratorsList({ sphereId }: { sphereId: string }) {
  const { data: collaborators, isLoading } = useQuery({
    queryKey: ['sphere-collaborators', sphereId],
    queryFn: async () => {
      const response = await fetch(`/api/collaboration/spheres/${sphereId}/collaborators`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
      });

      if (!response.ok) {
        throw new Error('Failed to fetch collaborators');
      }

      return response.json();
    },
  });

  const getPermissionIcon = (level: string) => {
    switch (level) {
      case 'admin': return '👑';
      case 'editor': return '✏️';
      case 'commenter': return '💬';
      case 'viewer': return '👀';
      default: return '👤';
    }
  };

  const getPermissionColor = (level: string) => {
    switch (level) {
      case 'admin': return 'text-purple-600 bg-purple-50';
      case 'editor': return 'text-green-600 bg-green-50';
      case 'commenter': return 'text-blue-600 bg-blue-50';
      case 'viewer': return 'text-gray-600 bg-gray-50';
      default: return 'text-gray-600 bg-gray-50';
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="animate-pulse flex items-center space-x-3">
            <div className="w-10 h-10 bg-gray-200 rounded-full"></div>
            <div className="flex-1">
              <div className="h-4 bg-gray-200 rounded w-1/3 mb-1"></div>
              <div className="h-3 bg-gray-200 rounded w-1/4"></div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  const collaboratorsList = collaborators?.collaborators || [];

  return (
    <div className="space-y-3">
      <h4 className="font-semibold text-gray-900">Collaborators ({collaboratorsList.length})</h4>

      {collaboratorsList.length === 0 ? (
        <p className="text-gray-500 text-sm">No collaborators yet. Share this sphere to start collaborating!</p>
      ) : (
        <div className="space-y-2">
          {collaboratorsList.map((collaborator: Collaborator) => (
            <div key={collaborator.userId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 bg-blue-500 rounded-full flex items-center justify-center text-white font-semibold">
                  {collaborator.displayName.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div className="font-medium text-gray-900">{collaborator.displayName}</div>
                  <div className="text-sm text-gray-500">{collaborator.email}</div>
                  {collaborator.lastActivity && (
                    <div className="text-xs text-gray-400">
                      Last active: {new Date(collaborator.lastActivity).toLocaleDateString()}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex items-center space-x-2">
                <span className={`px-2 py-1 text-xs font-medium rounded-full ${getPermissionColor(collaborator.permissionLevel)}`}>
                  {getPermissionIcon(collaborator.permissionLevel)} {collaborator.permissionLevel}
                </span>
                {collaborator.lastActivity && new Date(collaborator.lastActivity) > new Date(Date.now() - 5 * 60 * 1000) && (
                  <span className="w-2 h-2 bg-green-400 rounded-full" title="Online"></span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Activity Feed Component
 */
export function ActivityFeed({ limit = 20 }: { limit?: number }) {
  const [showUnreadOnly, setShowUnreadOnly] = useState(false);

  const { data: activityData, isLoading } = useQuery({
    queryKey: ['activity-feed', { limit, unreadOnly: showUnreadOnly }],
    queryFn: async () => {
      const params = new URLSearchParams({
        limit: limit.toString(),
        unreadOnly: showUnreadOnly.toString(),
      });

      const response = await fetch(`/api/collaboration/activity-feed?${params}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
      });

      if (!response.ok) {
        throw new Error('Failed to fetch activity feed');
      }

      return response.json();
    },
    refetchInterval: 30000, // Refetch every 30 seconds
  });

  const getActivityIcon = (activityType: string) => {
    switch (activityType) {
      case 'sphere_shared': return '🔗';
      case 'moment_shared': return '📝';
      case 'comment_added': return '💬';
      case 'moment_liked': return '❤️';
      case 'user_followed': return '👤';
      case 'reflection_generated': return '🧠';
      case 'milestone_achieved': return '🎉';
      default: return '📢';
    }
  };

  const formatActivityMessage = (activity: ActivityFeedItem) => {
    switch (activity.activityType) {
      case 'sphere_shared':
        return `shared the sphere "${activity.sphereName}" with you`;
      case 'comment_added':
        return `commented on a moment in "${activity.sphereName}"`;
      case 'moment_liked':
        return `liked your moment in "${activity.sphereName}"`;
      case 'user_followed':
        return 'started following you';
      case 'reflection_generated':
        return `generated new insights for "${activity.sphereName}"`;
      default:
        return 'had some activity';
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 5 }, (_, i) => (
          <div key={i} className="animate-pulse flex items-start space-x-3">
            <div className="w-10 h-10 bg-gray-200 rounded-full"></div>
            <div className="flex-1">
              <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
              <div className="h-3 bg-gray-200 rounded w-1/2"></div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  const activities = activityData?.activities || [];
  const unreadCount = activityData?.unreadCount || 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">
          Activity Feed
          {unreadCount > 0 && (
            <span className="ml-2 px-2 py-1 bg-red-500 text-white text-xs rounded-full">
              {unreadCount}
            </span>
          )}
        </h3>

        <div className="flex items-center space-x-2">
          <label className="flex items-center space-x-2 text-sm text-gray-600">
            <input
              type="checkbox"
              checked={showUnreadOnly}
              onChange={(e) => setShowUnreadOnly(e.target.checked)}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <span>Unread only</span>
          </label>
        </div>
      </div>

      {activities.length === 0 ? (
        <div className="text-center py-8">
          <div className="text-4xl mb-4">📢</div>
          <h4 className="text-lg font-medium text-gray-900 mb-2">No activity yet</h4>
          <p className="text-gray-600">
            {showUnreadOnly ? 'All caught up! No unread activities.' : 'Start collaborating to see activity here.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {activities.map((activity: ActivityFeedItem) => (
            <div
              key={activity.id}
              className={`p-4 border rounded-lg ${
                !activity.readAt ? 'bg-blue-50 border-blue-200' : 'bg-white border-gray-200'
              }`}
            >
              <div className="flex items-start space-x-3">
                <div className="flex-shrink-0 text-2xl">
                  {getActivityIcon(activity.activityType)}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-900">
                    <span className="font-medium">{activity.actorDisplayName}</span>
                    {' '}
                    <span>{formatActivityMessage(activity)}</span>
                  </p>
                  <p className="text-xs text-gray-500 mt-1">
                    {new Date(activity.createdAt).toLocaleString()}
                  </p>
                </div>
                {!activity.readAt && (
                  <div className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-2"></div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * User Search and Follow Component
 */
export function UserSearchAndFollow() {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [followedUsers, setFollowedUsers] = useState<Set<string>>(new Set());

  const queryClient = useQueryClient();

  const followMutation = useMutation({
    mutationFn: async (userIdToFollow: string) => {
      const response = await fetch('/api/collaboration/follow', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
        body: JSON.stringify({ userIdToFollow }),
      });

      if (!response.ok) {
        throw new Error('Failed to follow user');
      }

      return response.json();
    },
    onSuccess: (data, userIdToFollow) => {
      if (data.isFollowing) {
        setFollowedUsers(prev => new Set(prev).add(userIdToFollow));
      } else {
        setFollowedUsers(prev => {
          const newSet = new Set(prev);
          newSet.delete(userIdToFollow);
          return newSet;
        });
      }
      queryClient.invalidateQueries({ queryKey: ['collaboration-stats'] });
    },
  });

  const handleSearch = async (query: string) => {
    if (query.length < 2) {
      setSearchResults([]);
      return;
    }

    setIsSearching(true);
    try {
      // Mock search implementation - in production, implement user search API
      await new Promise(resolve => setTimeout(resolve, 500));
      setSearchResults([
        {
          id: '1',
          email: 'john@example.com',
          displayName: 'John Smith'
        },
        {
          id: '2',
          email: 'jane@example.com',
          displayName: 'Jane Doe'
        },
      ].filter(user =>
        user.displayName.toLowerCase().includes(query.toLowerCase()) ||
        user.email.toLowerCase().includes(query.toLowerCase())
      ));
    } catch (error) {
      console.error('Search failed:', error);
      setSearchResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const handleFollow = async (userId: string) => {
    try {
      await followMutation.mutateAsync(userId);
    } catch (error) {
      console.error('Failed to follow user:', error);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-3">Discover Users</h3>
        <div className="relative">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              handleSearch(e.target.value);
            }}
            placeholder="Search for users by name or email..."
            className="w-full px-4 py-2 pl-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <svg
            className="absolute left-3 top-2.5 w-5 h-5 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
      </div>

      {isSearching && (
        <div className="flex items-center justify-center py-4">
          <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
          <span className="ml-2 text-gray-600">Searching...</span>
        </div>
      )}

      {searchResults.length > 0 && (
        <div className="space-y-2">
          {searchResults.map((user) => (
            <div key={user.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 bg-gray-500 rounded-full flex items-center justify-center text-white font-semibold">
                  {user.displayName.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div className="font-medium text-gray-900">{user.displayName}</div>
                  <div className="text-sm text-gray-500">{user.email}</div>
                </div>
              </div>

              <button
                onClick={() => handleFollow(user.id)}
                disabled={followMutation.isPending}
                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                  followedUsers.has(user.id)
                    ? 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                    : 'bg-blue-500 text-white hover:bg-blue-600'
                }`}
              >
                {followMutation.isPending
                  ? '...'
                  : followedUsers.has(user.id)
                    ? 'Unfollow'
                    : 'Follow'
                }
              </button>
            </div>
          ))}
        </div>
      )}

      {searchQuery.length >= 2 && !isSearching && searchResults.length === 0 && (
        <div className="text-center py-4 text-gray-500">
          No users found for "{searchQuery}"
        </div>
      )}
    </div>
  );
}

export default {
  SphereShareDialog,
  CollaboratorsList,
  ActivityFeed,
  UserSearchAndFollow,
};
