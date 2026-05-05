import React from 'react';
import { useLocation } from 'react-router';

interface StubPageProps {
  title: string;
  description?: string;
}

export const StubPage: React.FC<StubPageProps> = ({ title, description }) => {
  const location = useLocation();

  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] p-8 text-center border-2 border-dashed border-border rounded-lg bg-surface/50">
      <div className="w-16 h-16 mb-4 text-fg-muted bg-surface/50 rounded-full flex items-center justify-center">
        🚧
      </div>
      <h1 className="text-2xl font-bold text-fg-muted mb-2">{title}</h1>
      <p className="text-fg-muted max-w-md mb-6">
        {description || 'This page is currently under construction.'}
      </p>
      <div className="px-3 py-1 text-xs font-mono text-fg-muted bg-surface rounded border border-border">
        Path: {location.pathname}
      </div>
    </div>
  );
};

export default StubPage;
