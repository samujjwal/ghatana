import React, { useState, useEffect } from 'react';
import { 
  Search, 
  Database, 
  FolderOpen, 
  Activity, 
  Plus, 
  Settings, 
  Bell,
  CheckCircle,
  AlertTriangle,
  XCircle,
  TrendingUp,
  Clock,
  Zap
} from 'lucide-react';
import { SimplifiedDataService, SimplifiedDashboard, SearchRequest } from '../api/simplified-data.service';

interface SimplifiedDashboardProps {
  baseUrl: string;
  tenantId: string;
}

/**
 * Simplified Dashboard Component
 * 
 * Provides a zero-cognitive-load interface for Data Cloud with:
 * - Unified search across all resources
 * - Quick actions for common tasks
 * - Clear visual status indicators
 * - Minimal navigation complexity
 */
export const SimplifiedDashboard: React.FC<SimplifiedDashboardProps> = ({ 
  baseUrl, 
  tenantId 
}) => {
  const [dashboard, setDashboard] = useState<SimplifiedDashboard | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any>(null);
  const [quickActions, setQuickActions] = useState<any[]>([]);
  const [systemStatus, setSystemStatus] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const dataService = new SimplifiedDataService(baseUrl, tenantId);

  useEffect(() => {
    loadDashboard();
    loadQuickActions();
    loadSystemStatus();
    
    // Auto-refresh every 30 seconds
    const interval = setInterval(loadDashboard, 30000);
    return () => clearInterval(interval);
  }, []);

  const loadDashboard = async () => {
    try {
      const data = await dataService.getDashboard();
      setDashboard(data);
      setError(null);
    } catch (err) {
      setError('Failed to load dashboard');
      console.error('Dashboard load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadQuickActions = async () => {
    try {
      const actions = await dataService.quickActions();
      setQuickActions(actions);
    } catch (err) {
      console.error('Quick actions load error:', err);
    }
  };

  const loadSystemStatus = async () => {
    try {
      const status = await dataService.systemStatus();
      setSystemStatus(status);
    } catch (err) {
      console.error('System status load error:', err);
    }
  };

  const handleSearch = async (query: string) => {
    if (!query.trim()) {
      setSearchResults(null);
      return;
    }

    try {
      const results = await dataService.search({ query });
      setSearchResults(results);
    } catch (err) {
      console.error('Search error:', err);
    }
  };

  const handleQuickAction = async (actionId: string) => {
    try {
      await dataService.executeQuickAction(actionId);
      loadDashboard(); // Refresh dashboard
    } catch (err) {
      console.error('Quick action error:', err);
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'healthy':
      case 'active':
      case 'connected':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'warning':
        return <AlertTriangle className="w-5 h-5 text-yellow-500" />;
      case 'error':
      case 'inactive':
      case 'disconnected':
        return <XCircle className="w-5 h-5 text-red-500" />;
      default:
        return <Activity className="w-5 h-5 text-gray-500" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'healthy':
      case 'active':
      case 'connected':
        return 'text-green-600 bg-green-50';
      case 'warning':
        return 'text-yellow-600 bg-yellow-50';
      case 'error':
      case 'inactive':
      case 'disconnected':
        return 'text-red-600 bg-red-50';
      default:
        return 'text-gray-600 bg-gray-50';
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <Activity className="w-12 h-12 text-blue-500 animate-spin mx-auto mb-4" />
          <p className="text-gray-600">Loading Data Cloud...</p>
        </div>
      </div>
    );
  }

  if (error || !dashboard) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <XCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
          <p className="text-gray-600">{error || 'Dashboard unavailable'}</p>
          <button 
            onClick={loadDashboard}
            className="mt-4 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <Database className="w-8 h-8 text-blue-500 mr-3" />
              <h1 className="text-xl font-semibold text-gray-900">Data Cloud</h1>
            </div>
            
            <div className="flex items-center space-x-4">
              {/* System Status Indicator */}
              {systemStatus && (
                <div className="flex items-center space-x-2">
                  {getStatusIcon(systemStatus.status)}
                  <span className="text-sm text-gray-600">
                    {systemStatus.status}
                  </span>
                </div>
              )}
              
              <button className="p-2 text-gray-400 hover:text-gray-600">
                <Bell className="w-5 h-5" />
              </button>
              
              <button className="p-2 text-gray-400 hover:text-gray-600">
                <Settings className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Search Bar */}
        <div className="mb-8">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search entities, collections, pipelines..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                handleSearch(e.target.value);
              }}
              className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
          
          {/* Search Results */}
          {searchResults && (
            <div className="mt-2 bg-white border border-gray-200 rounded-lg shadow-lg">
              <div className="p-4">
                <p className="text-sm text-gray-600 mb-2">
                  Found {searchResults.total} results
                </p>
                {searchResults.items.slice(0, 5).map((item: any) => (
                  <div key={item.id} className="py-2 border-b border-gray-100 last:border-0">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-medium text-gray-900">{item.name}</p>
                        <p className="text-sm text-gray-600">{item.type}</p>
                      </div>
                      <span className={`px-2 py-1 text-xs rounded-full ${getStatusColor(item.status)}`}>
                        {item.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Quick Stats */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Total Entities</p>
                <p className="text-2xl font-bold text-gray-900">{dashboard.totalEntities}</p>
              </div>
              <Database className="w-8 h-8 text-blue-500" />
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Collections</p>
                <p className="text-2xl font-bold text-gray-900">{dashboard.totalCollections}</p>
              </div>
              <FolderOpen className="w-8 h-8 text-green-500" />
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Data Sources</p>
                <p className="text-2xl font-bold text-gray-900">{dashboard.totalDataSources}</p>
              </div>
              <Activity className="w-8 h-8 text-purple-500" />
            </div>
          </div>
          
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-600">Active Pipelines</p>
                <p className="text-2xl font-bold text-gray-900">{dashboard.activePipelines}</p>
              </div>
              <TrendingUp className="w-8 h-8 text-orange-500" />
            </div>
          </div>
        </div>

        {/* Quick Actions */}
        {quickActions.length > 0 && (
          <div className="mb-8">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {quickActions.map((action) => (
                <button
                  key={action.id}
                  onClick={() => handleQuickAction(action.id)}
                  className="bg-white p-4 rounded-lg shadow-sm border border-gray-200 hover:shadow-md transition-shadow text-left"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-blue-50 rounded-lg">
                      <Zap className="w-5 h-5 text-blue-500" />
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{action.name}</p>
                      <p className="text-sm text-gray-600">{action.description}</p>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Recent Activity */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h2>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200">
              {dashboard.recentActivity.length > 0 ? (
                <div className="divide-y divide-gray-200">
                  {dashboard.recentActivity.map((activity) => (
                    <div key={activity.id} className="p-4">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-medium text-gray-900">{activity.description}</p>
                          <p className="text-sm text-gray-600">{activity.type}</p>
                        </div>
                        <div className="flex items-center text-sm text-gray-500">
                          <Clock className="w-4 h-4 mr-1" />
                          {new Date(activity.timestamp).toLocaleTimeString()}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center text-gray-500">
                  No recent activity
                </div>
              )}
            </div>
          </div>

          {/* System Health */}
          <div>
            <h2 className="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <div className="flex items-center justify-between mb-4">
                <span className="text-gray-900">Overall Status</span>
                <div className="flex items-center space-x-2">
                  {getStatusIcon(dashboard.systemHealth)}
                  <span className="capitalize">{dashboard.systemHealth}</span>
                </div>
              </div>
              
              {systemStatus && (
                <div className="space-y-3">
                  {Object.entries(systemStatus.services).map(([service, status]: [string, any]) => (
                    <div key={service} className="flex items-center justify-between">
                      <span className="text-gray-700 capitalize">{service}</span>
                      <div className="flex items-center space-x-2">
                        {getStatusIcon(status.status)}
                        <span className="text-sm capitalize">{status.status}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default SimplifiedDashboard;
