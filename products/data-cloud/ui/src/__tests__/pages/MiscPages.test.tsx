/**
 * Tests for miscellaneous Data Cloud pages:
 * PluginDetailsPage, SmartWorkflowBuilder, IntelligentHub,
 * DataExplorer, CreateCollectionPage
 */
import { describe, it, expect } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { PluginDetailsPage } from '../../pages/PluginDetailsPage';
import { SmartWorkflowBuilder } from '../../pages/SmartWorkflowBuilder';
import { IntelligentHub } from '../../pages/IntelligentHub';
import { DataExplorer } from '../../pages/DataExplorer';
import { CreateCollectionPage } from '../../pages/CreateCollectionPage';


// ── PluginDetailsPage ─────────────────────────────────────────────────────────

describe('PluginDetailsPage', () => {
  it('renders without crashing', () => {
    render(<PluginDetailsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays plugin detail content', () => {
    render(<PluginDetailsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/plugin|connector|version|install|config/i);
  });
});

// ── SmartWorkflowBuilder ──────────────────────────────────────────────────────

describe('SmartWorkflowBuilder', () => {
  it('renders without crashing', () => {
    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays workflow builder content', () => {
    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/workflow|builder|step|pipeline|node|trigger/i);
  });
});

// ── IntelligentHub ────────────────────────────────────────────────────────────

describe('IntelligentHub', () => {
  it('renders without crashing', () => {
    render(<IntelligentHub />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays hub/intelligence content', () => {
    render(<IntelligentHub />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/hub|intelligen|ai|insight|unified/i);
  });
});

// ── DataExplorer ──────────────────────────────────────────────────────────────

describe('DataExplorer', () => {
  it('renders without crashing', () => {
    render(<DataExplorer />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays data explorer content', () => {
    render(<DataExplorer />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/explor|data|record|collection|query|filter/i);
  });

  it('normalizes unsupported view params back to a safe default', async () => {
    window.history.pushState({}, '', '/data?view=cost');

    render(<DataExplorer />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(window.location.search).toBe('?view=table');
    });
  });
});

// ── CreateCollectionPage ──────────────────────────────────────────────────────

describe('CreateCollectionPage', () => {
  it('renders without crashing', () => {
    render(<CreateCollectionPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays create collection form', () => {
    render(<CreateCollectionPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/creat|collection|name|schema|field|new/i);
  });

  it('has form submission elements', () => {
    render(<CreateCollectionPage />, { wrapper: TestWrapper });
    const interactive = document.querySelectorAll('button, input, textarea, select');
    expect(interactive.length).toBeGreaterThan(0);
  });
});
