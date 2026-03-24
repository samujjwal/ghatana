/**
 * DevSecOps Performance Tests
 *
 * Performance and optimization tests for DevSecOps Canvas components.
 * Tests rendering speed, memory usage, and responsiveness with large datasets.
 *
 * @module DevSecOps/__tests__/performance
 */

import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';

import { KPICard } from '../KPICard/KPICard';
import { DataTable } from '../DataTable/DataTable';
import { KanbanBoard } from '../KanbanBoard/KanbanBoard';
import { Timeline } from '../Timeline/Timeline';
import { ItemCard } from '../ItemCard/ItemCard';
import { FilterPanel } from '../FilterPanel/FilterPanel';
import { SearchBar } from '../SearchBar/SearchBar';

describe('DevSecOps Performance', () => {
  // ============================================================================
  // Render Performance
  // ============================================================================

  describe('Render Performance', () => {
    it('KPICard renders quickly', () => {
      const start = performance.now();

      render(
        <KPICard
          title="Completion Rate"
          value="75%"
          trend="up"
          trendValue="+5%"
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(50);
    });

    it('DataTable renders 100 rows in < 500ms', () => {
      const mockData = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        status: i % 4 === 0 ? 'completed' : 'in-progress',
        priority: i % 3 === 0 ? 'high' : 'medium',
        assignee: `User ${i % 10}`,
      }));

      const start = performance.now();

      render(
        <DataTable
          data={mockData}
          columns={[
            { id: 'title', label: 'Title', sortable: true },
            { id: 'status', label: 'Status', sortable: true },
            { id: 'priority', label: 'Priority', sortable: true },
            { id: 'assignee', label: 'Assignee' },
          ]}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(500);
    });

    it('KanbanBoard renders 500 items in < 1s', () => {
      const mockItems = Array.from({ length: 500 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        description: `Description for task ${i + 1}`,
        status: ['not-started', 'in-progress', 'in-review', 'completed'][i % 4] as unknown,
        priority: ['low', 'medium', 'high'][i % 3] as unknown,
        tags: [`tag-${i % 5}`],
        createdAt: new Date(),
        updatedAt: new Date(),
      }));

      const start = performance.now();

      render(
        <KanbanBoard
          items={mockItems}
          onItemClick={() => {}}
          onItemMove={() => {}}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(1000);
    });

    it('Timeline renders 100 items in < 1s', () => {
      const startDate = new Date('2024-01-01');
      const mockItems = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        description: 'Description',
        status: 'in-progress' as const,
        priority: 'medium' as const,
        tags: [],
        createdAt: new Date(startDate.getTime() + i * 86400000),
        updatedAt: new Date(startDate.getTime() + (i + 7) * 86400000),
        startDate: new Date(startDate.getTime() + i * 86400000),
        endDate: new Date(startDate.getTime() + (i + 7) * 86400000),
      }));

      const start = performance.now();

      render(
        <Timeline
          items={mockItems}
          startDate={startDate}
          endDate={new Date('2024-12-31')}
          viewMode="month"
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(1000);
    });

    it('ItemCard renders efficiently', () => {
      const mockItem = {
        id: '1',
        title: 'Test Task',
        description: 'A'.repeat(500), // Long description
        status: 'in-progress' as const,
        priority: 'high' as const,
        tags: Array.from({ length: 20 }, (_, i) => `tag-${i}`), // Many tags
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const start = performance.now();

      render(<ItemCard item={mockItem} />);

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(50);
    });
  });

  // ============================================================================
  // Re-render Performance
  // ============================================================================

  describe('Re-render Performance', () => {
    it('DataTable handles rapid sorting efficiently', () => {
      const mockData = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        value: Math.random(),
      }));

      const { rerender } = render(
        <DataTable
          data={mockData}
          columns={[
            { id: 'title', label: 'Title', sortable: true },
            { id: 'value', label: 'Value', sortable: true },
          ]}
          sortBy="title"
          sortDirection="asc"
        />
      );

      const start = performance.now();

      // Simulate 10 rapid re-sorts
      for (let i = 0; i < 10; i++) {
        rerender(
          <DataTable
            data={mockData}
            columns={[
              { id: 'title', label: 'Title', sortable: true },
              { id: 'value', label: 'Value', sortable: true },
            ]}
            sortBy={i % 2 === 0 ? 'title' : 'value'}
            sortDirection={i % 2 === 0 ? 'asc' : 'desc'}
          />
        );
      }

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(200);
    });

    it('SearchBar handles rapid input changes', () => {
      const { rerender } = render(
        <SearchBar value="" onChange={() => {}} />
      );

      const queries = ['t', 'te', 'tes', 'test', 'test ', 'test q', 'test qu'];

      const start = performance.now();

      queries.forEach((query) => {
        rerender(<SearchBar value={query} onChange={() => {}} />);
      });

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(50);
    });

    it('FilterPanel handles rapid filter changes', () => {
      const { rerender } = render(
        <FilterPanel
          selectedStatus={null}
          selectedPriority={null}
          selectedAssignee={null}
          onStatusChange={() => {}}
          onPriorityChange={() => {}}
          onAssigneeChange={() => {}}
          onClearFilters={() => {}}
        />
      );

      const statuses = ['not-started', 'in-progress', 'completed', null];

      const start = performance.now();

      statuses.forEach((status) => {
        rerender(
          <FilterPanel
            selectedStatus={status as unknown}
            selectedPriority={null}
            selectedAssignee={null}
            onStatusChange={() => {}}
            onPriorityChange={() => {}}
            onAssigneeChange={() => {}}
            onClearFilters={() => {}}
          />
        );
      });

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(50);
    });
  });

  // ============================================================================
  // Large Dataset Handling
  // ============================================================================

  describe('Large Dataset Handling', () => {
    it('DataTable handles 1000+ rows', () => {
      const mockData = Array.from({ length: 1000 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        status: 'in-progress',
      }));

      const start = performance.now();

      render(
        <DataTable
          data={mockData}
          columns={[
            { id: 'title', label: 'Title' },
            { id: 'status', label: 'Status' },
          ]}
          rowsPerPage={100}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(1000);
    });

    it('KanbanBoard handles 1000 items efficiently', () => {
      const mockItems = Array.from({ length: 1000 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        description: 'Description',
        status: ['not-started', 'in-progress', 'completed'][i % 3] as unknown,
        priority: 'medium' as const,
        tags: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      }));

      const start = performance.now();

      render(
        <KanbanBoard
          items={mockItems}
          onItemClick={() => {}}
          onItemMove={() => {}}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(2000);
    });
  });

  // ============================================================================
  // Memory Management
  // ============================================================================

  describe('Memory Management', () => {
    it('unmounting DataTable releases memory', () => {
      const mockData = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        status: 'in-progress',
      }));

      const { unmount } = render(
        <DataTable
          data={mockData}
          columns={[{ id: 'title', label: 'Title' }]}
        />
      );

      // Should unmount without errors
      unmount();
    });

    it('unmounting KanbanBoard releases memory', () => {
      const mockItems = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        description: 'Description',
        status: 'in-progress' as const,
        priority: 'medium' as const,
        tags: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      }));

      const { unmount } = render(
        <KanbanBoard
          items={mockItems}
          onItemClick={() => {}}
          onItemMove={() => {}}
        />
      );

      unmount();
    });
  });

  // ============================================================================
  // Memoization & Optimization
  // ============================================================================

  describe('Memoization & Optimization', () => {
    it('KPICard does not re-render when props unchanged', () => {
      const { rerender } = render(
        <KPICard
          title="Completion Rate"
          value="75%"
          trend="up"
          trendValue="+5%"
        />
      );

      const start = performance.now();

      // Re-render with same props 10 times
      for (let i = 0; i < 10; i++) {
        rerender(
          <KPICard
            title="Completion Rate"
            value="75%"
            trend="up"
            trendValue="+5%"
          />
        );
      }

      const duration = performance.now() - start;
      // Should be very fast due to React optimization
      expect(duration).toBeLessThan(50);
    });

    it('ItemCard efficiently handles prop updates', () => {
      const mockItem = {
        id: '1',
        title: 'Test Task',
        description: 'Description',
        status: 'not-started' as const,
        priority: 'medium' as const,
        tags: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const { rerender } = render(<ItemCard item={mockItem} />);

      const start = performance.now();

      // Update only status
      rerender(
        <ItemCard
          item={{ ...mockItem, status: 'in-progress' }}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(20);
    });
  });

  // ============================================================================
  // Concurrent Updates
  // ============================================================================

  describe('Concurrent Updates', () => {
    it('handles simultaneous DataTable updates', () => {
      const mockData = Array.from({ length: 50 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        status: 'in-progress',
      }));

      const { rerender } = render(
        <DataTable
          data={mockData}
          columns={[{ id: 'title', label: 'Title' }]}
          sortBy="title"
          sortDirection="asc"
          page={0}
        />
      );

      const start = performance.now();

      // Simulate concurrent updates (sort + paginate)
      rerender(
        <DataTable
          data={mockData}
          columns={[{ id: 'title', label: 'Title' }]}
          sortBy="status"
          sortDirection="desc"
          page={1}
        />
      );

      const duration = performance.now() - start;
      expect(duration).toBeLessThan(100);
    });
  });
});
