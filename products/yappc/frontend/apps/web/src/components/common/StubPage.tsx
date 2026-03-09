import React from 'react';
import { useLocation } from 'react-router';

interface StubPageProps {
  title: string;
  description?: string;
}

export const StubPage: React.FC<StubPageProps> = ({ title, description }) => {
  const location = useLocation();

  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] p-8 text-center border-2 border-dashed border-zinc-800 rounded-lg bg-zinc-900/50">
      <div className="w-16 h-16 mb-4 text-zinc-600 bg-zinc-800/50 rounded-full flex items-center justify-center">
        🚧
      </div>
      <h1 className="text-2xl font-bold text-zinc-100 mb-2">{title}</h1>
      <p className="text-zinc-400 max-w-md mb-6">
        {description || 'This page is currently under construction.'}
      </p>
      <div className="px-3 py-1 text-xs font-mono text-zinc-500 bg-zinc-900 rounded border border-zinc-800">
        Path: {location.pathname}
      </div>
    </div>
  );
};

export default StubPage;
