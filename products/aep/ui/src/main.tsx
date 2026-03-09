import React from 'react';
import ReactDOM from 'react-dom/client';
import { ErrorBoundary } from '@ghatana/ui/components/ErrorBoundary';
import { PipelineBuilderPage } from '@/pages/PipelineBuilderPage';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <PipelineBuilderPage />
    </ErrorBoundary>
  </React.StrictMode>,
);
