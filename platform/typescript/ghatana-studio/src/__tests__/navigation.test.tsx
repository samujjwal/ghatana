/**
 * @ghatana/ghatana-studio navigation test suite
 * Tests for Ghatana Studio navigation and routing
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import App from '../App';

describe('@ghatana/ghatana-studio - Navigation', () => {
  describe('Route Navigation', () => {
    it('should render App component', () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>
      );
      expect(screen.getByText('Ghatana Studio')).toBeTruthy();
    });

    it('should display all section links in sidebar', () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>
      );
      expect(screen.getByText('Builder Studio')).toBeTruthy();
      expect(screen.getByText('Theme Studio')).toBeTruthy();
      expect(screen.getByText('Component Playground')).toBeTruthy();
      expect(screen.getByText('Canvas Diagnostics')).toBeTruthy();
      expect(screen.getByText('AI Review Console')).toBeTruthy();
      expect(screen.getByText('Import/Migration Lab')).toBeTruthy();
      expect(screen.getByText('Preview Lab')).toBeTruthy();
    });

    it('should display header with version', () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>
      );
      expect(screen.getByText('v1.0.0')).toBeTruthy();
    });
  });

  describe('Sidebar Navigation', () => {
    it('should render sidebar with all sections', () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>
      );
      const sidebarLinks = screen.getAllByText(/Studio|Playground|Diagnostics|Console|Lab/);
      expect(sidebarLinks.length).toBeGreaterThan(0);
    });
  });
});
