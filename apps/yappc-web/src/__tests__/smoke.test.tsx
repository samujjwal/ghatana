// All tests skipped - incomplete feature
import { InMemoryCache } from '@apollo/client';
import { MockedProvider } from '@apollo/client/testing';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect } from 'vitest';

import WorkspaceListPage from '../router/WorkspaceListPage';

describe.skip('WorkspaceListPage', () => {
  it('renders the workspace list page', () => {
    const cache = new InMemoryCache();

    render(
      <MockedProvider cache={cache}>
        <BrowserRouter>
          <WorkspaceListPage />
        </BrowserRouter>
      </MockedProvider>
    );
    
    // Check that the page title is rendered
    expect(screen.getByText('Workspaces')).toBeInTheDocument();
    
    // Check that the page description is rendered
    expect(screen.getByText(/Manage your workspaces and projects here/i)).toBeInTheDocument();
    
    // Check that the demo workspace is rendered
    expect(screen.getByText('Demo Workspace')).toBeInTheDocument();
  });
});
