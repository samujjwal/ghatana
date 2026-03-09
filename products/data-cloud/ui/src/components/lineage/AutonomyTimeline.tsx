import React, { useEffect, useState } from 'react';
import { DashboardCard } from '../cards/DashboardCard';

interface AutonomyLog {
  id: string;
  actionType: string;
  timestamp: string;
  outcome: string;
}

export const AutonomyTimeline: React.FC = () => {
  const [logs, setLogs] = useState<AutonomyLog[]>([]);

  useEffect(() => {
    fetch('/api/v1/autonomy/logs')
      .then(res => res.json())
      .then(data => {
          if (Array.isArray(data)) setLogs(data);
      })
      .catch(err => console.error('Failed to fetch autonomy logs', err));
  }, []);

  return (
    <DashboardCard title="Autonomy Timeline" viewAllLink="/autonomy">
      <div className="relative border-l border-gray-200 ml-3">
        {logs.map(log => (
          <div key={log.id} className="mb-10 ml-4">
            <div className="absolute w-3 h-3 bg-gray-200 rounded-full mt-1.5 -left-1.5 border border-white"></div>
            <time className="mb-1 text-sm font-normal leading-none text-gray-400">{new Date(log.timestamp).toLocaleTimeString()}</time>
            <h3 className="text-lg font-semibold text-gray-900">{log.actionType}</h3>
            <p className="mb-4 text-base font-normal text-gray-500">{log.outcome}</p>
          </div>
        ))}
        {logs.length === 0 && <div className="ml-4 text-gray-500">No recent autonomy actions</div>}
      </div>
    </DashboardCard>
  );
};

