/**
 * @doc.type component
 * @doc.purpose Dashboard panel showing overview of all services
 * @doc.layer application
 * @doc.pattern dashboard component
 */

import React from 'react';
import { Card } from '@ghatana/audio-video-ui';

const DashboardPanel: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <Card title="Speech to Text" subtitle="Real-time transcription">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Status</span>
              <span className="text-sm font-medium text-green-600">Healthy</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Requests Today</span>
              <span className="text-sm font-medium">1,234</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Avg Response Time</span>
              <span className="text-sm font-medium">245ms</span>
            </div>
          </div>
        </Card>

        <Card title="Text to Speech" subtitle="Voice synthesis">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Status</span>
              <span className="text-sm font-medium text-green-600">Healthy</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Requests Today</span>
              <span className="text-sm font-medium">892</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Avg Response Time</span>
              <span className="text-sm font-medium">520ms</span>
            </div>
          </div>
        </Card>

        <Card title="AI Voice" subtitle="Voice enhancement">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Status</span>
              <span className="text-sm font-medium text-green-600">Healthy</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Requests Today</span>
              <span className="text-sm font-medium">456</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Avg Response Time</span>
              <span className="text-sm font-medium">180ms</span>
            </div>
          </div>
        </Card>
      </div>

      <Card title="System Overview" subtitle="Overall system health and performance">
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">5</div>
              <div className="text-sm text-gray-500">Active Services</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">99.8%</div>
              <div className="text-sm text-gray-500">Uptime</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-purple-600">2,582</div>
              <div className="text-sm text-gray-500">Total Requests</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-orange-600">342ms</div>
              <div className="text-sm text-gray-500">Avg Response</div>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default DashboardPanel;
