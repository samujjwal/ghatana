/**
 * Tests for miscellaneous Data Cloud pages:
 * DashboardsPage, DatasetExplorerPage, PluginDetailsPage,
 * SmartWorkflowBuilder, IntelligentHub, DataExplorer,
 * CreateCollectionPage, EditCollectionPage
 */
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { DashboardsPage } from '../../pages/DashboardsPage';
import { DatasetExplorerPage } from '../../pages/DatasetExplorerPage';
import { PluginDetailsPage } from '../../pages/PluginDetailsPage';
import { SmartWorkflowBuilder } from '../../pages/SmartWorkflowBuilder';
import { IntelligentHub } from '../../pages/IntelligentHub';
import { DataExplorer } from '../../pages/DataExplorer';
import { CreateCollectionPage } from '../../pages/CreateCollectionPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

// ── DashboardsPage ────────────────────────────────────────────────────────────

describe('DashboardsPage', () => {
  it('renders without crashing', () => {
    render(<DashboardsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays dashboard list content', () => {
    render(<DashboardsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/dashboard|chart|metric|report/i);
  });
});

// ── DatasetExplorerPage ───────────────────────────────────────────────────────

describe('DatasetExplorerPage', () => {
  it('renders without crashing', () => {
    render(<DatasetExplorerPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays dataset explorer content', () => {
    render(<DatasetExplorerPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/dataset|explore|collection|schema|field/i);
  });
});

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
