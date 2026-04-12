/**
 * Ghatana Studio - Unified Maintainer App
 *
 * Combines all Phase 6 maintainer tools:
 * - Builder Studio
 * - Theme Studio
 * - Component Playground
 * - Canvas Diagnostics
 * - AI Review Console
 * - Import/Migration Lab
 * - Preview Lab
 *
 * @doc.type component
 * @doc.purpose Unified maintainer app for all platform tools
 * @doc.layer platform
 */

import React from 'react';
import { Routes, Route, Link, useLocation } from 'react-router';
import {
  Layout,
  LayoutSidebar,
  LayoutContent,
  LayoutHeader,
} from '@ghatana/design-system';

// Studio sections
import BuilderStudio from './sections/BuilderStudio';
import ThemeStudio from './sections/ThemeStudio';
import ComponentPlayground from './sections/ComponentPlayground';
import CanvasDiagnostics from './sections/CanvasDiagnostics';
import AIReviewConsole from './sections/AIReviewConsole';
import ImportMigrationLab from './sections/ImportMigrationLab';
import PreviewLab from './sections/PreviewLab';

const SECTIONS = [
  { id: 'builder', label: 'Builder Studio', path: '/builder', icon: '🏗️' },
  { id: 'theme', label: 'Theme Studio', path: '/theme', icon: '🎨' },
  { id: 'components', label: 'Component Playground', path: '/components', icon: '🧩' },
  { id: 'canvas', label: 'Canvas Diagnostics', path: '/canvas', icon: '🔍' },
  { id: 'ai', label: 'AI Review Console', path: '/ai', icon: '🤖' },
  { id: 'import', label: 'Import/Migration Lab', path: '/import', icon: '📥' },
  { id: 'preview', label: 'Preview Lab', path: '/preview', icon: '👁️' },
];

function Sidebar() {
  const location = useLocation();

  return (
    <LayoutSidebar>
      <div className="p-4">
        <h1 className="text-xl font-bold text-gray-900 mb-1">Ghatana Studio</h1>
        <p className="text-sm text-gray-500 mb-6">Platform Maintainer Tools</p>
        
        <nav className="space-y-1">
          {SECTIONS.map((section) => {
            const isActive = location.pathname === section.path;
            return (
              <Link
                key={section.id}
                to={section.path}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <span className="text-lg">{section.icon}</span>
                {section.label}
              </Link>
            );
          })}
        </nav>
      </div>
    </LayoutSidebar>
  );
}

export default function App() {
  return (
    <Layout className="h-screen">
      <LayoutHeader className="border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">
            Platform Development Environment
          </h2>
          <div className="flex items-center gap-4 text-sm text-gray-500">
            <span>v1.0.0</span>
            <a
              href="https://docs.ghatana.dev"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-600 hover:text-blue-700"
            >
              Documentation
            </a>
          </div>
        </div>
      </LayoutHeader>
      
      <div className="flex flex-1 overflow-hidden">
        <Sidebar />
        
        <LayoutContent className="overflow-auto">
          <Routes>
            <Route path="/" element={<BuilderStudio />} />
            <Route path="/builder" element={<BuilderStudio />} />
            <Route path="/theme" element={<ThemeStudio />} />
            <Route path="/components" element={<ComponentPlayground />} />
            <Route path="/canvas" element={<CanvasDiagnostics />} />
            <Route path="/ai" element={<AIReviewConsole />} />
            <Route path="/import" element={<ImportMigrationLab />} />
            <Route path="/preview" element={<PreviewLab />} />
          </Routes>
        </LayoutContent>
      </div>
    </Layout>
  );
}
