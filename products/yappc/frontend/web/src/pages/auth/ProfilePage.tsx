import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { yappcApi } from '@/lib/api/client';
import { Button } from '../../components/ui/Button';
import { useTranslation } from '@ghatana/i18n';

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
  const { t } = useTranslation('common');

  const { data: profile, isLoading } = useQuery<UserProfile>({
    queryKey: ['profile'],
    queryFn: () => yappcApi.userProfile.get(),
  });

  const mutation = useMutation({
    mutationFn: (updates: Partial<UserProfile>) => yappcApi.userProfile.update(updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      setEditing(false);
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold text-fg-muted">{t('profile.title')}</h1>
      <div className="bg-surface border border-border rounded-lg p-6 space-y-4">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 bg-primary rounded-full flex items-center justify-center text-xl font-bold text-white">
            {profile?.name?.[0]?.toUpperCase() ?? '?'}
          </div>
          <div>
            <h2 className="text-lg font-semibold text-fg-muted">{profile?.name}</h2>
            <p className="text-sm text-fg-muted">{profile?.email}</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 pt-4 border-t border-border">
          <div>
            <p className="text-xs text-fg-muted uppercase">{t('profile.roleLabel')}</p>
            <p className="text-sm text-fg-muted">{profile?.role}</p>
          </div>
          <div>
            <p className="text-xs text-fg-muted uppercase">{t('profile.memberSince')}</p>
            <p className="text-sm text-fg-muted">{profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : 'N/A'}</p>
          </div>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setEditing(!editing)}
          className="px-4 py-2 bg-surface hover:bg-surface-muted text-fg-muted text-sm rounded-lg transition-colors"
        >
          {editing ? t('profile.cancel') : t('profile.editProfile')}
        </Button>
      </div>
    </div>
  );
};

export default ProfilePage;
