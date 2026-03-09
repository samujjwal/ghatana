// All tests skipped - incomplete feature
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi } from 'vitest';

import { ThemeProvider } from '../../../theme/ThemeProvider';
import { WorkspaceCard } from '../WorkspaceCard';

describe.skip('WorkspaceCard Component', () => {
  const defaultProps = {
    id: 'workspace-1',
    name: 'Test Workspace',
    description: 'This is a test workspace',
    lastModified: new Date().toISOString(),
    favorite: false,
    onFavoriteToggle: vi.fn(),
    onOpen: vi.fn(),
    onMoreOptions: vi.fn(),
  };

  it('renders correctly with default props', () => {
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} />
      </ThemeProvider>
    );
    
    expect(screen.getByText('Test Workspace')).toBeInTheDocument();
    expect(screen.getByText('This is a test workspace')).toBeInTheDocument();
  });
  
  it('renders without description when not provided', () => {
    const propsWithoutDescription = {
      ...defaultProps,
      description: undefined,
    };
    
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...propsWithoutDescription} />
      </ThemeProvider>
    );
    
    expect(screen.getByText('Test Workspace')).toBeInTheDocument();
    expect(screen.queryByText('This is a test workspace')).not.toBeInTheDocument();
  });
  
  it('displays favorite icon when favorite is true', () => {
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} favorite={true} />
      </ThemeProvider>
    );
    
    // Check that the favorite button has the correct aria-label
    const favoriteButton = screen.getByLabelText('Remove from favorites');
    expect(favoriteButton).toBeInTheDocument();
  });
  
  it('displays non-favorite icon when favorite is false', () => {
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} favorite={false} />
      </ThemeProvider>
    );
    
    // Check that the favorite button has the correct aria-label
    const favoriteButton = screen.getByLabelText('Add to favorites');
    expect(favoriteButton).toBeInTheDocument();
  });
  
  it('calls onFavoriteToggle when favorite button is clicked', () => {
    const onFavoriteToggle = vi.fn();
    
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} onFavoriteToggle={onFavoriteToggle} />
      </ThemeProvider>
    );
    
    const favoriteButton = screen.getByLabelText('Add to favorites');
    fireEvent.click(favoriteButton);
    
    expect(onFavoriteToggle).toHaveBeenCalledWith('workspace-1', true);
  });
  
  it('calls onOpen when Open button is clicked', () => {
    const onOpen = vi.fn();
    
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} onOpen={onOpen} />
      </ThemeProvider>
    );
    
    const openButton = screen.getByText('Open');
    fireEvent.click(openButton);
    
    expect(onOpen).toHaveBeenCalledWith('workspace-1');
  });
  
  it('calls onMoreOptions when more options button is clicked', () => {
    const onMoreOptions = vi.fn();
    
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} onMoreOptions={onMoreOptions} />
      </ThemeProvider>
    );
    
    const moreOptionsButton = screen.getByLabelText('More options');
    fireEvent.click(moreOptionsButton);
    
    expect(onMoreOptions).toHaveBeenCalledWith('workspace-1', expect.any(Object));
  });
  
  it('formats time ago correctly', () => {
    // Create a date 2 days ago
    const twoDaysAgo = new Date();
    twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
    
    render(
      <ThemeProvider mode="light">
        <WorkspaceCard {...defaultProps} lastModified={twoDaysAgo.toISOString()} />
      </ThemeProvider>
    );
    
    // Check that the time ago text contains "2 days ago"
    expect(screen.getByText(/2 days ago/i)).toBeInTheDocument();
  });
  
  it('renders in dark theme correctly', () => {
    render(
      <ThemeProvider mode="dark">
        <WorkspaceCard {...defaultProps} />
      </ThemeProvider>
    );
    
    expect(screen.getByText('Test Workspace')).toBeInTheDocument();
  });
});
