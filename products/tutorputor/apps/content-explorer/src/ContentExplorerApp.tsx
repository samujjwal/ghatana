import React, { Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { SearchPage } from './pages/SearchPage';
import { AssetDetailPage } from './pages/AssetDetailPage';
import { BrowsePage } from './pages/BrowsePage';
import { LoadingSpinner } from './components/ui/LoadingSpinner';

/**
 * Content Explorer App
 * 
 * Dedicated content discovery interface with search, browsing, and detailed asset views.
 * Integrates with semantic search and recommendation engines.
 * 
 * @doc.type component
 * @doc.purpose Main application component for content explorer
 * @doc.layer product
 * @doc.pattern App
 */
export function ContentExplorerApp(): React.ReactElement {
  return (
    <div className="content-explorer-app">
      <header className="explorer-header">
        <div className="header-content">
          <h1 className="explorer-title">Content Explorer</h1>
          <p className="explorer-subtitle">Discover learning content across all domains</p>
        </div>
      </header>

      <main className="explorer-main">
        <Suspense fallback={<LoadingSpinner />}>
          <Routes>
            <Route path="/" element={<SearchPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/browse" element={<BrowsePage />} />
            <Route path="/asset/:assetId" element={<AssetDetailPage />} />
            <Route path="/domain/:domain" element={<BrowsePage />} />
          </Routes>
        </Suspense>
      </main>

      <footer className="explorer-footer">
        <p>&copy; 2026 TutorPutor - Content Intelligence System</p>
      </footer>
    </div>
  );
}
