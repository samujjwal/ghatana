import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface UserProfile {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  role: string;
  createdAt: string;
}

/**
 * ProfilePage — User profile management.
 *
 * @doc.type component
 * @doc.purpose User profile viewing and editing
 * @doc.layer product
 */
const ProfilePage: React.FC = () => {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [name_, setName] = useState('');

  const { data: profile, isLoading } = useQuery<UserProfile>({
    queryKey: ['profile'],
    queryFn: async () => {
      const res = await fetch('/api/user/profile', {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) throw new Error('Failed to load profile');
      return res.json();
    },
  });

  const mutation = useMutation({
    mutationFn: async (updates: Partial<UserProfile>) => {
      const res = await fetch('/api/user/profile', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
        },
        body: JSON.stringify(updates),
      });
      if (!res.ok) throw new Error('Failed to update profile');
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      setEditing(false);
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold text-zinc-100">Your Profile</h1>
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6 space-y-4">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-blue-600 rounded-full flex items-center justify-center text-xl font-bold text-white">
            {profile?.name?.[0]?.toUpperCase() ?? '?'}
          </div>
          <div>
            <h2 className="text-lg font-semibold text-zinc-100">{profile?.name}</h2>
            <p className="text-sm text-zinc-400">{profile?.email}</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 pt-4 border-t border-zinc-800">
          <div>
            <p className="text-xs text-zinc-500 uppercase">Role</p>
            <p className="text-sm text-zinc-300">{profile?.role}</p>
          </div>
          <div>
            <p className="text-xs text-zinc-500 uppercase">Member Since</p>
            <p className="text-sm text-zinc-300">{profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : 'N/A'}</p>
          </div>
        </div>
        <button
          onClick={() => setEditing(!editing)}
          className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-sm rounded-lg transition-colors"
        >
          {editing ? 'Cancel' : 'Edit Profile'}
        </button>
      </div>
    </div>
  );
};

export default ProfilePage;
