/**
 * Dashboard Page
 * Main landing page showing recent activity and quick actions
 */

import { Link } from 'react-router-dom';
import { useSearchMoments, useSpheres } from '../hooks/use-api';
import Layout from '../components/Layout';
import { StatsDashboardSkeleton, MomentsListSkeleton } from '../components/SkeletonLoaders';
import { PlusCircle, FileText, Layers, TrendingUp } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

export default function DashboardPage() {
  const { data: recentMoments, isLoading: momentsLoading } = useSearchMoments({ limit: 5 });
  const { data: spheres, isLoading: spheresLoading } = useSpheres();

  const isLoading = momentsLoading || spheresLoading;
  const totalMoments = recentMoments?.totalCount || 0;
  const totalSpheres = spheres?.length || 0;

  return (
    <Layout>
      <div className="space-y-8">
        {/* Welcome Header */}
        <div>
          <h1 
            className="text-3xl font-bold text-gray-900"
            role="heading"
            aria-level={1}
          >
            Dashboard
          </h1>
          <p className="mt-2 text-gray-600">
            Welcome back! Here's your context at a glance.
          </p>
        </div>

        {/* Stats */}
        {isLoading ? (
          <StatsDashboardSkeleton />
        ) : (
          <div 
            className="grid grid-cols-1 md:grid-cols-3 gap-6"
            role="region"
            aria-label="Dashboard statistics"
          >
            <div 
              className="card"
              role="article"
              aria-label={`Total moments: ${totalMoments}`}
            >
              <div className="flex items-center">
                <div 
                  className="p-3 bg-primary-100 rounded-lg"
                  aria-hidden="true"
                >
                  <FileText className="w-6 h-6 text-primary-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-600">Total Moments</p>
                  <p className="text-2xl font-bold text-gray-900">{totalMoments}</p>
                </div>
              </div>
            </div>

            <div 
              className="card"
              role="article"
              aria-label={`Spheres: ${totalSpheres}`}
            >
              <div className="flex items-center">
                <div 
                  className="p-3 bg-green-100 rounded-lg"
                  aria-hidden="true"
                >
                  <Layers className="w-6 h-6 text-green-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-600">Spheres</p>
                  <p className="text-2xl font-bold text-gray-900">{totalSpheres}</p>
                </div>
              </div>
            </div>

            <div 
              className="card"
              role="article"
              aria-label={`This week: ${recentMoments?.moments?.filter(m =>
                new Date(m.capturedAt) > new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
              ).length || 0} moments`}
            >
              <div className="flex items-center">
                <div 
                  className="p-3 bg-purple-100 rounded-lg"
                  aria-hidden="true"
                >
                  <TrendingUp className="w-6 h-6 text-purple-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-600">This Week</p>
                  <p className="text-2xl font-bold text-gray-900">
                    {recentMoments?.moments?.filter(m =>
                      new Date(m.capturedAt) > new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
                    ).length || 0}
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Quick Actions */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Link to="/capture" className="card hover:shadow-md transition-shadow">
            <div className="flex items-center">
              <PlusCircle className="w-8 h-8 text-primary-600 mr-4" />
              <div>
                <h3 className="text-lg font-semibold text-gray-900">Capture a Moment</h3>
                <p className="text-sm text-gray-600">Record your thoughts and experiences</p>
              </div>
            </div>
          </Link>

          <Link to="/moments" className="card hover:shadow-md transition-shadow">
            <div className="flex items-center">
              <FileText className="w-8 h-8 text-primary-600 mr-4" />
              <div>
                <h3 className="text-lg font-semibold text-gray-900">View Moments</h3>
                <p className="text-sm text-gray-600">Browse and search your captured moments</p>
              </div>
            </div>
          </Link>
        </div>

        {/* Recent Moments */}
        <div className="card">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-semibold text-gray-900">Recent Moments</h2>
            <Link to="/moments" className="text-sm text-primary-600 hover:text-primary-700 font-medium">
              View all →
            </Link>
          </div>

          {recentMoments && recentMoments.moments.length > 0 ? (
            <div className="space-y-4">
              {recentMoments.moments.map((moment) => (
                <div key={moment.id} className="border-l-4 border-primary-500 pl-4 py-2">
                  <div className="flex justify-between items-start mb-2">
                    <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-gray-100 text-gray-700">
                      {moment.sphere.name}
                    </span>
                    <span className="text-xs text-gray-500">
                      {formatDistanceToNow(new Date(moment.capturedAt), { addSuffix: true })}
                    </span>
                  </div>
                  <p className="text-gray-800 line-clamp-2">{moment.contentText}</p>
                  {moment.emotions.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {moment.emotions.map((emotion) => (
                        <span
                          key={emotion}
                          className="inline-flex items-center px-2 py-0.5 rounded text-xs bg-primary-50 text-primary-700"
                        >
                          {emotion}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8 text-gray-500">
              <FileText className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>No moments captured yet</p>
              <Link to="/capture" className="text-primary-600 hover:text-primary-700 font-medium">
                Capture your first moment →
              </Link>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}

