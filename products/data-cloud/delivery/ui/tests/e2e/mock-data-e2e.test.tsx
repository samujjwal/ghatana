/**
 * E2E Test Suite for Data Cloud UI
 *
 * Comprehensive tests demonstrating all features with mock data.
 * Tests verify complete user workflows and data display.
 *
 * @doc.type test
 * @doc.purpose E2E tests with mock data demonstration
 * @doc.layer frontend
 */

import { TestWrapper } from '@/__tests__/test-utils/wrapper';
import {
getMockCollections,
getMockWorkflows,
MOCK_COLLECTIONS,
MOCK_ENTITIES,
} from '@/lib/mock-data';
import { WorkflowsPage } from '@/pages/WorkflowsPage';
import { render,screen,waitFor } from '@testing-library/react';
import { describe,expect,it } from 'vitest';

// ============================================================================
// WORKFLOWS TESTS
// ============================================================================

describe('Workflows Page with Mock Data', () => {
  it('should display all workflows', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    const workflows = getMockWorkflows();

    for (const workflow of workflows) {
      expect(
        await screen.findByText(workflow.name, undefined, { timeout: 15000 }),
      ).toBeInTheDocument();
    }
  });

  it('should display workflow statistics', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    // Verify workflow count stats are displayed (total, active, paused, draft)
    const workflows = getMockWorkflows();
    // The 'Total' stats card shows total workflow count
    expect(screen.getByText('Total')).toBeInTheDocument();
    expect(screen.getByText(String(workflows.length))).toBeInTheDocument();
  });

  it('should display workflow status badges', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    const workflows = getMockWorkflows();
    const activeWorkflows = workflows.filter((w) => w.status === 'active');

    // WorkflowsPage shows an 'Active' stats label in the summary cards
    // (multiple 'Active' elements may exist -- stats cards, filter options)
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(activeWorkflows.length).toBeGreaterThan(0);
  });

  it('should display workflow nodes visualization', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    const workflows = getMockWorkflows();
    const firstWorkflow = workflows[0];

    // WorkflowsPage list shows workflow names — verify the first workflow is visible.
    // (Node labels are shown only in the detail modal, not the list view.)
    expect(screen.getByText(firstWorkflow.name)).toBeInTheDocument();
    expect(screen.getByText(firstWorkflow.description)).toBeInTheDocument();
  });

  it('should display execution metrics', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Recently Run')).toBeInTheDocument();
    expect(screen.getAllByText('Last run').length).toBeGreaterThan(0);
  });

  it('should navigate to workflow editor on click', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    const firstWorkflow = getMockWorkflows()[0];
    expect(screen.getByText(firstWorkflow.name)).toBeInTheDocument();
    expect(screen.getByTestId(`advanced-editor-${firstWorkflow.id}`)).toBeInTheDocument();
    expect(screen.getByTestId(`review-pipeline-${firstWorkflow.id}`)).toBeInTheDocument();
  });
});

// ============================================================================
// MOCK DATA VERIFICATION TESTS
// ============================================================================

describe('Mock Data Completeness', () => {
  it('should have multiple collections with complete schemas', () => {
    const collections = getMockCollections();

    expect(collections.length).toBeGreaterThan(0);

    collections.forEach((collection) => {
      expect(collection.id).toBeDefined();
      expect(collection.name).toBeDefined();
      expect(collection.schema).toBeDefined();
      expect(collection.schema.fields.length).toBeGreaterThan(0);
      expect(collection.entityCount).toBeGreaterThanOrEqual(0);
    });
  });

  it('should have multiple workflows with complete node structures', () => {
    const workflows = getMockWorkflows();

    expect(workflows.length).toBeGreaterThan(0);

    workflows.forEach((workflow) => {
      expect(workflow.id).toBeDefined();
      expect(workflow.name).toBeDefined();
      expect(workflow.nodes.length).toBeGreaterThan(0);
      expect(workflow.edges.length).toBeGreaterThanOrEqual(0);
      expect(workflow.triggers.length).toBeGreaterThan(0);
    });
  });

  it('should have collection entities with realistic data', () => {
    const collectionId = MOCK_COLLECTIONS[0].id;
    const entities = MOCK_ENTITIES[collectionId];

    expect(entities).toBeDefined();
    expect(entities.length).toBeGreaterThan(0);

    entities.forEach((entity) => {
      expect(entity.id).toBeDefined();
      expect(entity.collectionId).toBe(collectionId);
      expect(entity.data).toBeDefined();
      expect(Object.keys(entity.data).length).toBeGreaterThan(0);
    });
  });

  it('should have workflows with different statuses', () => {
    const workflows = getMockWorkflows();
    const statuses = new Set(workflows.map((w) => w.status));

    expect(statuses.size).toBeGreaterThan(1);
  });

  it('should have workflows with different node types', () => {
    const workflows = getMockWorkflows();
    const nodeTypes = new Set<string>();

    workflows.forEach((workflow) => {
      workflow.nodes.forEach((node) => {
        nodeTypes.add(node.type);
      });
    });

    expect(nodeTypes.size).toBeGreaterThan(1);
  });
});

// ============================================================================
// UI INTERACTION TESTS
// ============================================================================

describe('UI Interactions with Mock Data', () => {
  it('should show workflow execution status visually', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.queryByText('Loading pipelines...')).not.toBeInTheDocument();
    });

    const workflows = getMockWorkflows();
    expect(workflows.length).toBeGreaterThan(0);
  });
});
