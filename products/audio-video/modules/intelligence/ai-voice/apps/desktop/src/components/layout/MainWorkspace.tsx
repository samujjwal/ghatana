/**
 * AI Voice Production Studio - Main Workspace
 * 
 * @doc.type component
 * @doc.purpose Main content area that switches between views
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import type { View } from '../../types';
import { SettingsView } from '../views/SettingsView';
import { StudioWorkspace } from '../views/StudioWorkspace';
import { ProjectsView } from '../views/ProjectsView';
import { LibraryView } from '../views/LibraryView';

interface MainWorkspaceProps {
  currentView: View;
}

export const MainWorkspace: React.FC<MainWorkspaceProps> = ({ currentView }) => {
  const renderView = () => {
    switch (currentView) {
      case 'studio':
        return <StudioWorkspace />;
      case 'settings':
        return <SettingsView />;
      case 'projects':
        return <ProjectsView />;
      case 'library':
        return <LibraryView />;
      default:
        return <StudioWorkspace />;
    }
  };

  return (
    <main className="flex-1 min-h-0 bg-gray-900 flex flex-col">
      {renderView()}
    </main>
  );
};
