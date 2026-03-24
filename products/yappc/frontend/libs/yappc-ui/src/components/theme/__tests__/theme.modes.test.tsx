import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';

import { ThemeToggle } from '../../components/ThemeToggle/ThemeToggle';
import { renderWithTheme } from '../testing';
import { ThemeProvider, useTheme } from '../ThemeContext';

// Test component that displays current theme mode
const ThemeDisplay = () => {
  const { mode } = useTheme();
  return <div data-testid="theme-mode">{mode}</div>;
};

// Test component with theme toggle
const ThemeTest = () => {
  const { mode, toggleTheme } = useTheme();
  return (
    <div>
      <div data-testid="theme-mode">{mode}</div>
      <button data-testid="theme-toggle" onClick={toggleTheme}>
        Toggle Theme
      </button>
    </div>
  );
};

describe('Theme Modes', () => {
  it('should render with light theme by default', () => {
    renderWithTheme(<ThemeDisplay />, {}, 'light');
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('light');
  });

  it('should render with dark theme when specified', () => {
    renderWithTheme(<ThemeDisplay />, {}, 'dark');
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('dark');
  });

  it('should toggle between light and dark themes', () => {
    render(
      <ThemeProvider defaultMode="light">
        <ThemeTest />
      </ThemeProvider>
    );

    // Initial theme should be light
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('light');

    // Toggle to dark
    fireEvent.click(screen.getByTestId('theme-toggle'));
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('dark');

    // Toggle back to light
    fireEvent.click(screen.getByTestId('theme-toggle'));
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('light');
  });

  it('should persist theme preference', () => {
    // Mock localStorage
    const getItemMock = jest.spyOn(Storage.prototype, 'getItem');
    const setItemMock = jest.spyOn(Storage.prototype, 'setItem');
    
    // Render with theme provider
    render(
      <ThemeProvider defaultMode="light" storageKey="test-theme-key">
        <ThemeTest />
      </ThemeProvider>
    );

    // Toggle theme
    fireEvent.click(screen.getByTestId('theme-toggle'));
    
    // Check if localStorage was updated
    expect(setItemMock).toHaveBeenCalledWith('test-theme-key', 'dark');
    
    // Cleanup
    getItemMock.mockRestore();
    setItemMock.mockRestore();
  });

  it('should respect system preference when available', () => {
    // Mock matchMedia for dark mode preference
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: jest.fn().mockImplementation(query => ({
        matches: query === '(prefers-color-scheme: dark)',
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
      })),
    });

    // Clear any stored preference
    localStorage.removeItem('theme-mode');
    
    // Render with theme provider (no explicit default)
    render(
      <ThemeProvider>
        <ThemeDisplay />
      </ThemeProvider>
    );
    
    // Should use system preference (dark)
    expect(screen.getByTestId('theme-mode')).toHaveTextContent('dark');
  });

  it('should render ThemeToggle component correctly', () => {
    renderWithTheme(<ThemeToggle data-testid="toggle-button" />, {}, 'light');
    
    const toggleButton = screen.getByRole('button');
    expect(toggleButton).toBeInTheDocument();
    expect(toggleButton).toHaveAttribute('aria-label', 'Switch to dark mode');
    
    // Toggle theme
    fireEvent.click(toggleButton);
    
  // Re-render with updated theme — ensure previous render is unmounted first
  // so we don't end up with duplicate ThemeToggle buttons in the document.
  // renderWithTheme calls cleanup() internally, but call it here for clarity.
   
  const { cleanup } = require('@testing-library/react');
  cleanup();
  renderWithTheme(<ThemeToggle data-testid="toggle-button" />, {}, 'dark');
    const updatedToggleButton = screen.getByRole('button');
    expect(updatedToggleButton).toHaveAttribute('aria-label', 'Switch to light mode');
  });
});
