/**
 * Collaboration Page
 * Shared spheres, invitations, activity feed, and team interaction
 */

import { useState, useEffect } from 'react';
import { useSpheres } from '../hooks/use-api';
import Layout from '../components/Layout';
import { apiClient } from '../lib/api-client';
import {
  Users,
  Share2,
  MessageSquare,
  Activity,
  Mail,
  CheckCircle,
  Clock,
  UserPlus,
} from 'lucide-react';

type CollabTab = 'shared' | 'invitations' | 'activity';

interface SharedSphere {
  id: string;
  name: string;
  description?: string;
  collaboratorCount?: number;
  role?: string;
}

interface Invitation {
  id: string;
  sphereName?: string;
  inviterName?: string;
  role?: string;
  status?: string;
  createdAt?: string;
}

interface ActivityItem {
  id: string;
  type: string;
  message: string;
  userName?: string;
  createdAt: string;
}

export default function CollaborationPage() {
  const [activeTab, setActiveTab] = useState<CollabTab>('shared');
  const { data: spheres } = useSpheres();
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [activities, setActivities] = useState<ActivityItem[]>([]);
  const [loading, setLoading] = useState(false);

  const sharedSpheres: SharedSphere[] = (spheres || []).filter(
    (s: { visibility?: string; type?: string }) => s.visibility === 'SHARED' || s.type === 'SHARED'
  );

  useEffect(() => {
    if (activeTab === 'invitations') {
      loadInvitations();
    } else if (activeTab === 'activity') {
      loadActivity();
    }
  }, [activeTab]);

  const loadInvitations = async () => {
    setLoading(true);
    try {
      const data = await apiClient.request('/api/collaboration/invitations');
      setInvitations(Array.isArray(data) ? data : data.invitations || []);
    } catch {
      setInvitations([]);
    } finally {
      setLoading(false);
    }
  };

  const loadActivity = async () => {
    setLoading(true);
    try {
      const data = await apiClient.request('/api/collaboration/activity');
      setActivities(Array.isArray(data) ? data : data.activities || []);
    } catch {
      setActivities([]);
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { id: 'shared' as CollabTab, label: 'Shared Spheres', icon: Share2 },
    { id: 'invitations' as CollabTab, label: 'Invitations', icon: Mail },
    { id: 'activity' as CollabTab, label: 'Activity', icon: Activity },
  ];

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Collaboration</h1>
            <p className="mt-1 text-sm text-gray-500">
              Share spheres, manage invitations, and track team activity
            </p>
          </div>
          <button className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-md hover:bg-primary-700 flex items-center gap-2">
            <UserPlus className="h-4 w-4" />
            Invite
          </button>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="flex -mb-px space-x-8" aria-label="Collaboration tabs">
            {tabs.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => setActiveTab(id)}
                className={`flex items-center gap-2 px-1 py-3 border-b-2 text-sm font-medium ${
                  activeTab === id
                    ? 'border-primary-500 text-primary-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
                aria-current={activeTab === id ? 'page' : undefined}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </nav>
        </div>

        {/* Shared Spheres */}
        {activeTab === 'shared' && (
          <div>
            {sharedSpheres.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {sharedSpheres.map((sphere) => (
                  <div key={sphere.id} className="bg-white rounded-lg border border-gray-200 p-4 hover:shadow-md transition-shadow">
                    <div className="flex items-start justify-between">
                      <div>
                        <h3 className="font-medium text-gray-900">{sphere.name}</h3>
                        {sphere.description && (
                          <p className="text-sm text-gray-500 mt-1 line-clamp-2">{sphere.description}</p>
                        )}
                      </div>
                      <Users className="h-5 w-5 text-gray-400 flex-shrink-0" />
                    </div>
                    <div className="mt-3 flex items-center justify-between">
                      <span className="text-xs text-gray-500">
                        {sphere.collaboratorCount || 0} collaborator{(sphere.collaboratorCount || 0) !== 1 ? 's' : ''}
                      </span>
                      {sphere.role && (
                        <span className="px-2 py-0.5 text-xs font-medium rounded bg-primary-50 text-primary-700">
                          {sphere.role}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={<Share2 className="h-12 w-12 text-gray-300" />}
                title="No shared spheres"
                description="Share a sphere to start collaborating with others"
              />
            )}
          </div>
        )}

        {/* Invitations */}
        {activeTab === 'invitations' && (
          <div>
            {loading ? (
              <LoadingSkeleton />
            ) : invitations.length > 0 ? (
              <div className="space-y-3">
                {invitations.map((inv) => (
                  <div key={inv.id} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-primary-100 flex items-center justify-center">
                        <Mail className="h-5 w-5 text-primary-600" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          {inv.inviterName || 'Someone'} invited you to <strong>{inv.sphereName || 'a sphere'}</strong>
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                          {inv.role && (
                            <span className="text-xs text-gray-500">Role: {inv.role}</span>
                          )}
                          {inv.createdAt && (
                            <span className="flex items-center text-xs text-gray-400">
                              <Clock className="h-3 w-3 mr-1" />
                              {new Date(inv.createdAt).toLocaleDateString()}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                    {inv.status === 'PENDING' && (
                      <div className="flex gap-2">
                        <button className="px-3 py-1.5 bg-primary-600 text-white text-xs font-medium rounded-md hover:bg-primary-700">
                          Accept
                        </button>
                        <button className="px-3 py-1.5 border border-gray-300 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-50">
                          Decline
                        </button>
                      </div>
                    )}
                    {inv.status === 'ACCEPTED' && (
                      <CheckCircle className="h-5 w-5 text-green-500" />
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={<Mail className="h-12 w-12 text-gray-300" />}
                title="No invitations"
                description="You have no pending invitations"
              />
            )}
          </div>
        )}

        {/* Activity Feed */}
        {activeTab === 'activity' && (
          <div>
            {loading ? (
              <LoadingSkeleton />
            ) : activities.length > 0 ? (
              <div className="space-y-3">
                {activities.map((item) => (
                  <div key={item.id} className="flex items-start gap-3">
                    <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center flex-shrink-0">
                      <MessageSquare className="h-4 w-4 text-gray-500" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-gray-900">{item.message}</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {new Date(item.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={<Activity className="h-12 w-12 text-gray-300" />}
                title="No recent activity"
                description="Collaboration activity will appear here"
              />
            )}
          </div>
        )}
      </div>
    </Layout>
  );
}

function EmptyState({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return (
    <div className="text-center py-16">
      <div className="flex justify-center">{icon}</div>
      <h3 className="mt-4 text-lg font-medium text-gray-900">{title}</h3>
      <p className="mt-1 text-sm text-gray-500">{description}</p>
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 animate-pulse">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-gray-200"></div>
            <div className="flex-1">
              <div className="h-4 bg-gray-200 rounded w-3/4"></div>
              <div className="h-3 bg-gray-200 rounded w-1/2 mt-2"></div>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
