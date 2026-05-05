import React, { useEffect, useState } from 'react';
import { 
  BarChart3, 
  Campaign, 
  Wallet, 
  FileText, 
  CheckCircle, 
  Clock, 
  AlertTriangle,
  TrendingUp,
  Users,
  Activity,
  Bell,
  ArrowRight,
  RefreshCw
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useCampaigns } from '../hooks/useCampaigns';
import { useApprovals } from '../hooks/useApprovals';
import { useNotifications } from '../hooks/useNotifications';
import { useFeatureFlags } from '../hooks/useFeatureFlags';

/**
 * P1-015: Dashboard Command Center
 *
 * Central hub for marketing operations providing:
 * - Quick actions for common workflows
 * - Campaign status overview with visual indicators
 * - Pending approvals requiring attention
 * - Budget utilization tracking
 * - Recent activity feed
 * - Performance metrics snapshot
 * - Alert notifications
 * - Feature-flagged widgets
 */

interface QuickAction {
  id: string;
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
  variant: 'primary' | 'secondary' | 'outline';
  featureFlag?: string;
}

interface DashboardWidget {
  id: string;
  title: string;
  component: React.ReactNode;
  size: 'small' | 'medium' | 'large';
  priority: number;
  featureFlag?: string;
}

interface CampaignSummary {
  id: string;
  name: string;
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PUBLISHED' | 'PAUSED';
  budget: { used: number; total: number };
  performance?: { impressions: number; clicks: number; conversions: number };
  lastUpdated: Date;
}

interface ApprovalItem {
  id: string;
  type: 'CAMPAIGN' | 'BUDGET' | 'STRATEGY';
  title: string;
  requestedBy: string;
  requestedAt: Date;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export const DashboardCommandCenter: React.FC = () => {
  const navigate = useNavigate();
  const { campaigns, isLoading: campaignsLoading, refresh: refreshCampaigns } = useCampaigns();
  const { pendingApprovals, isLoading: approvalsLoading } = useApprovals();
  const { notifications, unreadCount } = useNotifications();
  const { isEnabled } = useFeatureFlags();
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  // Quick actions configuration
  const quickActions: QuickAction[] = [
    {
      id: 'create-campaign',
      label: 'Create Campaign',
      icon: <Campaign className="w-5 h-5" />,
      onClick: () => navigate('/campaigns/new'),
      variant: 'primary'
    },
    {
      id: 'view-approvals',
      label: `Review Approvals ${pendingApprovals.length > 0 ? `(${pendingApprovals.length})` : ''}`,
      icon: <CheckCircle className="w-5 h-5" />,
      onClick: () => navigate('/approvals'),
      variant: pendingApprovals.length > 0 ? 'primary' : 'secondary'
    },
    {
      id: 'generate-strategy',
      label: 'AI Strategy',
      icon: <FileText className="w-5 h-5" />,
      onClick: () => navigate('/strategies/generate'),
      variant: 'outline',
      featureFlag: 'ai-strategy-generation'
    },
    {
      id: 'view-analytics',
      label: 'Analytics',
      icon: <BarChart3 className="w-5 h-5" />,
      onClick: () => navigate('/analytics'),
      variant: 'outline',
      featureFlag: 'advanced-analytics'
    }
  ].filter(action => !action.featureFlag || isEnabled(action.featureFlag));

  // Stats calculation
  const stats = {
    totalCampaigns: campaigns.length,
    activeCampaigns: campaigns.filter(c => c.status === 'PUBLISHED').length,
    pendingApprovals: pendingApprovals.length,
    totalBudget: campaigns.reduce((sum, c) => sum + (c.budget?.total || 0), 0),
    usedBudget: campaigns.reduce((sum, c) => sum + (c.budget?.used || 0), 0),
    alerts: notifications.filter(n => n.type === 'ALERT').length
  };

  const budgetUtilization = stats.totalBudget > 0 
    ? (stats.usedBudget / stats.totalBudget) * 100 
    : 0;

  // Handlers
  const handleRefresh = async () => {
    await refreshCampaigns();
    setLastRefresh(new Date());
  };

  const handleApprovalAction = (approvalId: string, action: 'approve' | 'reject') => {
    navigate(`/approvals/${approvalId}?action=${action}`);
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Command Center</h1>
            <p className="text-gray-600 mt-1">
              Overview of your marketing operations
            </p>
          </div>
          <div className="flex items-center gap-4">
            <button
              onClick={handleRefresh}
              className="flex items-center gap-2 px-4 py-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg transition-colors"
              data-testid="dashboard-refresh"
            >
              <RefreshCw className="w-4 h-4" />
              <span className="text-sm">Refresh</span>
            </button>
            <span className="text-sm text-gray-500">
              Last updated: {lastRefresh.toLocaleTimeString()}
            </span>
            {unreadCount > 0 && (
              <button 
                onClick={() => navigate('/notifications')}
                className="relative p-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg"
                data-testid="notification-bell"
              >
                <Bell className="w-5 h-5" />
                <span className="absolute top-1 right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">
                  {unreadCount}
                </span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Quick Actions Bar */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
        <div className="flex flex-wrap gap-3">
          {quickActions.map(action => (
            <button
              key={action.id}
              onClick={action.onClick}
              className={`flex items-center gap-2 px-4 py-3 rounded-lg font-medium transition-all ${
                action.variant === 'primary'
                  ? 'bg-blue-600 text-white hover:bg-blue-700 shadow-sm'
                  : action.variant === 'secondary'
                    ? 'bg-blue-100 text-blue-700 hover:bg-blue-200'
                    : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
              }`}
              data-testid={`quick-action-${action.id}`}
            >
              {action.icon}
              <span>{action.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        {/* Total Campaigns */}
        <div 
          className="bg-white p-6 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
          onClick={() => navigate('/campaigns')}
          data-testid="stat-card-campaigns"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-600 mb-1">Total Campaigns</p>
              <p className="text-2xl font-bold text-gray-900">{stats.totalCampaigns}</p>
              <p className="text-sm text-green-600 mt-1">
                {stats.activeCampaigns} active
              </p>
            </div>
            <div className="p-3 bg-blue-100 rounded-lg">
              <Campaign className="w-6 h-6 text-blue-600" />
            </div>
          </div>
        </div>

        {/* Pending Approvals */}
        <div 
          className="bg-white p-6 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
          onClick={() => navigate('/approvals')}
          data-testid="stat-card-approvals"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-600 mb-1">Pending Approvals</p>
              <p className="text-2xl font-bold text-gray-900">{stats.pendingApprovals}</p>
              <p className="text-sm text-orange-600 mt-1">
                Requires attention
              </p>
            </div>
            <div className="p-3 bg-orange-100 rounded-lg">
              <Clock className="w-6 h-6 text-orange-600" />
            </div>
          </div>
        </div>

        {/* Budget Utilization */}
        <div 
          className="bg-white p-6 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
          onClick={() => navigate('/budgets')}
          data-testid="stat-card-budget"
        >
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <p className="text-sm text-gray-600 mb-1">Budget Utilization</p>
              <p className="text-2xl font-bold text-gray-900">
                ${(stats.usedBudget / 1000).toFixed(1)}k
              </p>
              <div className="mt-2">
                <div className="flex justify-between text-xs mb-1">
                  <span className="text-gray-600">{budgetUtilization.toFixed(1)}%</span>
                  <span className="text-gray-500">of ${(stats.totalBudget / 1000).toFixed(0)}k</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div 
                    className={`h-2 rounded-full ${
                      budgetUtilization > 90 ? 'bg-red-500' : 
                      budgetUtilization > 70 ? 'bg-yellow-500' : 'bg-green-500'
                    }`}
                    style={{ width: `${Math.min(budgetUtilization, 100)}%` }}
                  />
                </div>
              </div>
            </div>
            <div className="p-3 bg-green-100 rounded-lg ml-4">
              <Wallet className="w-6 h-6 text-green-600" />
            </div>
          </div>
        </div>

        {/* Alerts */}
        <div 
          className="bg-white p-6 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
          onClick={() => navigate('/notifications')}
          data-testid="stat-card-alerts"
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-gray-600 mb-1">Alerts</p>
              <p className="text-2xl font-bold text-gray-900">{stats.alerts}</p>
              <p className={`text-sm mt-1 ${stats.alerts > 0 ? 'text-red-600' : 'text-green-600'}`}>
                {stats.alerts > 0 ? 'Action required' : 'All clear'}
              </p>
            </div>
            <div className={`p-3 rounded-lg ${stats.alerts > 0 ? 'bg-red-100' : 'bg-green-100'}`}>
              <AlertTriangle className={`w-6 h-6 ${stats.alerts > 0 ? 'text-red-600' : 'text-green-600'}`} />
            </div>
          </div>
        </div>
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Pending Approvals Widget */}
        <div className="lg:col-span-2 bg-white rounded-xl shadow-sm border border-gray-200">
          <div className="p-6 border-b border-gray-200">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">Pending Approvals</h3>
              <button 
                onClick={() => navigate('/approvals')}
                className="text-blue-600 hover:text-blue-700 text-sm font-medium flex items-center gap-1"
              >
                View All <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
          <div className="p-6">
            {approvalsLoading ? (
              <div className="flex justify-center py-8">
                <RefreshCw className="w-6 h-6 text-gray-400 animate-spin" />
              </div>
            ) : pendingApprovals.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                <CheckCircle className="w-12 h-12 text-green-400 mx-auto mb-3" />
                <p>No pending approvals</p>
                <p className="text-sm mt-1">All caught up!</p>
              </div>
            ) : (
              <div className="space-y-4">
                {pendingApprovals.slice(0, 5).map((approval) => (
                  <div 
                    key={approval.id}
                    className="flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                    data-testid={`approval-item-${approval.id}`}
                  >
                    <div className="flex items-center gap-4">
                      <div className={`p-2 rounded-lg ${
                        approval.priority === 'HIGH' ? 'bg-red-100' :
                        approval.priority === 'MEDIUM' ? 'bg-yellow-100' : 'bg-blue-100'
                      }`}>
                        {approval.type === 'CAMPAIGN' ? <Campaign className="w-5 h-5" /> :
                         approval.type === 'BUDGET' ? <Wallet className="w-5 h-5" /> :
                         <FileText className="w-5 h-5" />}
                      </div>
                      <div>
                        <p className="font-medium text-gray-900">{approval.title}</p>
                        <p className="text-sm text-gray-600">
                          Requested by {approval.requestedBy} • {new Date(approval.requestedAt).toLocaleDateString()}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-1 text-xs font-medium rounded ${
                        approval.priority === 'HIGH' ? 'bg-red-100 text-red-700' :
                        approval.priority === 'MEDIUM' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-blue-100 text-blue-700'
                      }`}>
                        {approval.priority}
                      </span>
                      <button
                        onClick={() => handleApprovalAction(approval.id, 'approve')}
                        className="px-3 py-1.5 bg-green-600 text-white text-sm rounded hover:bg-green-700 transition-colors"
                      >
                        Approve
                      </button>
                      <button
                        onClick={() => handleApprovalAction(approval.id, 'reject')}
                        className="px-3 py-1.5 bg-white text-gray-700 border border-gray-300 text-sm rounded hover:bg-gray-50 transition-colors"
                      >
                        Reject
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Side Widgets */}
        <div className="space-y-6">
          {/* Recent Activity */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h3>
            <div className="space-y-4">
              {notifications.slice(0, 5).map((notification) => (
                <div 
                  key={notification.id}
                  className="flex items-start gap-3"
                  data-testid={`activity-item-${notification.id}`}
                >
                  <div className={`p-2 rounded-lg flex-shrink-0 ${
                    notification.type === 'SUCCESS' ? 'bg-green-100' :
                    notification.type === 'WARNING' ? 'bg-yellow-100' :
                    notification.type === 'ALERT' ? 'bg-red-100' :
                    'bg-blue-100'
                  }`}>
                    <Activity className={`w-4 h-4 ${
                      notification.type === 'SUCCESS' ? 'text-green-600' :
                      notification.type === 'WARNING' ? 'text-yellow-600' :
                      notification.type === 'ALERT' ? 'text-red-600' :
                      'text-blue-600'
                    }`} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-gray-900 font-medium truncate">
                      {notification.title}
                    </p>
                    <p className="text-xs text-gray-500 mt-0.5">
                      {new Date(notification.timestamp).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
              ))}
              {notifications.length === 0 && (
                <p className="text-sm text-gray-500 text-center py-4">No recent activity</p>
              )}
            </div>
          </div>

          {/* Performance Snapshot - Feature Flagged */}
          {isEnabled('advanced-analytics') && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Performance</h3>
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600">Impressions</span>
                  <span className="text-sm font-medium text-gray-900">124.5K</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600">Clicks</span>
                  <span className="text-sm font-medium text-gray-900">8.2K</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600">CTR</span>
                  <span className="text-sm font-medium text-green-600">6.58%</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600">Conversions</span>
                  <span className="text-sm font-medium text-gray-900">342</span>
                </div>
                <div className="pt-3 border-t border-gray-200">
                  <div className="flex items-center gap-2 text-green-600">
                    <TrendingUp className="w-4 h-4" />
                    <span className="text-sm font-medium">+12.5% vs last week</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Team Activity - Feature Flagged */}
          {isEnabled('team-activity-widget') && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Team Activity</h3>
              <div className="flex -space-x-2 mb-3">
                {[1, 2, 3, 4].map((i) => (
                  <div 
                    key={i}
                    className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-400 to-blue-600 border-2 border-white flex items-center justify-center text-white text-xs font-medium"
                  >
                    U{i}
                  </div>
                ))}
                <div className="w-8 h-8 rounded-full bg-gray-200 border-2 border-white flex items-center justify-center text-gray-600 text-xs font-medium">
                  +3
                </div>
              </div>
              <p className="text-sm text-gray-600">
                7 team members active today
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Campaign Performance Table */}
      <div className="mt-8 bg-white rounded-xl shadow-sm border border-gray-200">
        <div className="p-6 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold text-gray-900">Campaign Performance</h3>
            <button 
              onClick={() => navigate('/campaigns')}
              className="text-blue-600 hover:text-blue-700 text-sm font-medium flex items-center gap-1"
            >
              View All <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Campaign</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Budget</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Performance</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Last Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {campaignsLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto" />
                  </td>
                </tr>
              ) : campaigns.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    No campaigns yet. <button onClick={() => navigate('/campaigns/new')} className="text-blue-600 hover:underline">Create one</button>
                  </td>
                </tr>
              ) : (
                campaigns.slice(0, 5).map((campaign) => (
                  <tr 
                    key={campaign.id}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => navigate(`/campaigns/${campaign.id}`)}
                    data-testid={`campaign-row-${campaign.id}`}
                  >
                    <td className="px-6 py-4">
                      <div>
                        <p className="font-medium text-gray-900">{campaign.name}</p>
                        <p className="text-sm text-gray-500">ID: {campaign.id.slice(0, 8)}</p>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2 py-1 text-xs font-medium rounded ${
                        campaign.status === 'PUBLISHED' ? 'bg-green-100 text-green-700' :
                        campaign.status === 'APPROVED' ? 'bg-blue-100 text-blue-700' :
                        campaign.status === 'PENDING_APPROVAL' ? 'bg-yellow-100 text-yellow-700' :
                        campaign.status === 'DRAFT' ? 'bg-gray-100 text-gray-700' :
                        'bg-red-100 text-red-700'
                      }`}>
                        {campaign.status.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div>
                        <p className="text-sm text-gray-900">
                          ${((campaign.budget?.used || 0) / 1000).toFixed(1)}k / ${((campaign.budget?.total || 0) / 1000).toFixed(0)}k
                        </p>
                        <div className="w-24 bg-gray-200 rounded-full h-1.5 mt-1">
                          <div 
                            className="h-1.5 rounded-full bg-blue-500"
                            style={{ 
                              width: `${Math.min(((campaign.budget?.used || 0) / (campaign.budget?.total || 1)) * 100, 100)}%` 
                            }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {campaign.performance ? (
                        <div className="text-sm">
                          <p className="text-gray-900">{(campaign.performance.impressions / 1000).toFixed(1)}K impressions</p>
                          <p className="text-gray-500">{campaign.performance.clicks.toLocaleString()} clicks</p>
                        </div>
                      ) : (
                        <span className="text-sm text-gray-400">No data</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500">
                      {new Date(campaign.lastUpdated).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DashboardCommandCenter;
