import React, { useEffect, useState } from 'react';
import { DashboardCard } from './DashboardCard';

interface SpotlightItem {
  id: string;
  summary: string;
  salienceScore: { score: number };
  emergency: boolean;
}

export const SpotlightRing: React.FC = () => {
  const [items, setItems] = useState<SpotlightItem[]>([]);

  useEffect(() => {
    fetch('/api/v1/workspace/spotlight')
      .then(res => res.json())
      .then(data => setItems(data))
      .catch(err => console.error('Failed to fetch spotlight', err));
  }, []);

  return (
    <DashboardCard title="Global Spotlight" viewAllLink="/workspace">
      <div className="space-y-4">
        {items.map(item => (
          <div key={item.id} className={`p-4 border rounded ${item.emergency ? 'border-red-500 bg-red-50' : 'border-gray-200'}`}>
            <div className="flex justify-between">
              <span className="font-medium">{item.summary}</span>
              <span className="text-sm text-gray-500">Score: {item.salienceScore.score.toFixed(2)}</span>
            </div>
          </div>
        ))}
        {items.length === 0 && <div className="text-gray-500 text-center">No active spotlight items</div>}
      </div>
    </DashboardCard>
  );
};

