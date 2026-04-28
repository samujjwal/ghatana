/**
 * useUrlContext tests (SIMP-Y16)
 */

import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import React from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { useUrlContext } from '../useUrlContext';

function wrapWithRoute(path: string, url: string) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path={path} element={<>{children}</>} />
        </Routes>
      </MemoryRouter>
    );
  };
}

describe('useUrlContext (SIMP-Y16)', () => {
  it('extracts workspaceId and projectId from URL', () => {
    const wrapper = wrapWithRoute(
      '/w/:workspaceId/projects/:projectId',
      '/w/ws-123/projects/proj-456'
    );
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.workspaceId).toBe('ws-123');
    expect(result.current.projectId).toBe('proj-456');
    expect(result.current.isProjectScoped).toBe(true);
  });

  it('returns undefined for missing segments', () => {
    const wrapper = wrapWithRoute('/w/:workspaceId', '/w/ws-123');
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.workspaceId).toBe('ws-123');
    expect(result.current.projectId).toBeUndefined();
    expect(result.current.isProjectScoped).toBe(false);
  });

  it('extracts sprintId from URL', () => {
    const wrapper = wrapWithRoute(
      '/w/:workspaceId/projects/:projectId/sprints/:sprintId',
      '/w/ws-1/projects/proj-1/sprints/sprint-42'
    );
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.sprintId).toBe('sprint-42');
  });

  it('extracts epicId from URL', () => {
    const wrapper = wrapWithRoute(
      '/w/:workspaceId/projects/:projectId/epics/:epicId',
      '/w/ws-1/projects/proj-1/epics/epic-7'
    );
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.epicId).toBe('epic-7');
  });

  it('extracts runId from URL', () => {
    const wrapper = wrapWithRoute(
      '/w/:workspaceId/projects/:projectId/runs/:runId',
      '/w/ws-1/projects/proj-1/runs/run-99'
    );
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.runId).toBe('run-99');
  });

  it('returns all undefined and isProjectScoped false on root path', () => {
    const wrapper = wrapWithRoute('/', '/');
    const { result } = renderHook(() => useUrlContext(), { wrapper });

    expect(result.current.workspaceId).toBeUndefined();
    expect(result.current.projectId).toBeUndefined();
    expect(result.current.isProjectScoped).toBe(false);
  });
});
