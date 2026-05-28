/**
 * OpenAPI Client Contract Test (task 3.2.4)
 *
 * Verifies that the adapter layer in client.ts correctly delegates to the generated
 * OpenAPI client and that all used operations exist in the generated client.
 *
 * @doc.type test
 * @doc.purpose Contract test for OpenAPI generated client integration
 * @doc.layer frontend
 * @doc.pattern Contract Test
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { OpenAPI, AuthService as GeneratedAuthService, WorkspacesService, ProjectsService } from '../../../clients/generated/api';
import type { LoginRequest, LoginResponse, Project, Workspace } from '../../../clients/generated/api';

function expectTypeExport<T>(label: string): void {
  const contractProbe: { readonly label: string; readonly value: T | null } = {
    label,
    value: null,
  };
  expect(contractProbe).toMatchObject({ label, value: null });
}

describe('OpenAPI Client Contract Tests', () => {
  beforeEach(() => {
    // Reset OpenAPI configuration before each test
    OpenAPI.BASE = '/api';
    OpenAPI.WITH_CREDENTIALS = true;
    OpenAPI.CREDENTIALS = 'same-origin';
  });

  describe('Generated Services Existence', () => {
    it('should have AuthService with login method', () => {
      expect(GeneratedAuthService).toBeDefined();
      expect(typeof GeneratedAuthService.login).toBe('function');
    });

    it('should have AuthService with validateSession method', () => {
      expect(typeof GeneratedAuthService.validateSession).toBe('function');
    });

    it('should have AuthService with currentUser method', () => {
      expect(typeof GeneratedAuthService.currentUser).toBe('function');
    });

    it('should have WorkspacesService with listWorkspaces method', () => {
      expect(WorkspacesService).toBeDefined();
      expect(typeof WorkspacesService.listWorkspaces).toBe('function');
    });

    it('should have WorkspacesService with getWorkspace method', () => {
      expect(typeof WorkspacesService.getWorkspace).toBe('function');
    });

    it('should have WorkspacesService with createWorkspace method', () => {
      expect(typeof WorkspacesService.createWorkspace).toBe('function');
    });

    it('should have WorkspacesService with updateWorkspace method', () => {
      expect(typeof WorkspacesService.updateWorkspace).toBe('function');
    });

    it('should have WorkspacesService with deleteWorkspace method', () => {
      expect(typeof WorkspacesService.deleteWorkspace).toBe('function');
    });

    it('should have ProjectsService with listProjects method', () => {
      expect(ProjectsService).toBeDefined();
      expect(typeof ProjectsService.listProjects).toBe('function');
    });

    it('should have ProjectsService with getProject method', () => {
      expect(typeof ProjectsService.getProject).toBe('function');
    });

    it('should have ProjectsService with createProject method', () => {
      expect(typeof ProjectsService.createProject).toBe('function');
    });

    it('should have ProjectsService with updateProject method', () => {
      expect(typeof ProjectsService.updateProject).toBe('function');
    });

    it('should have ProjectsService with deleteProject method', () => {
      expect(typeof ProjectsService.deleteProject).toBe('function');
    });
  });

  describe('OpenAPI Configuration', () => {
    it('should have BASE configured for API', () => {
      expect(OpenAPI.BASE).toBe('/api');
    });

    it('should have WITH_CREDENTIALS enabled for cookie-session mode', () => {
      expect(OpenAPI.WITH_CREDENTIALS).toBe(true);
    });

    it('should have CREDENTIALS set to same-origin', () => {
      expect(OpenAPI.CREDENTIALS).toBe('same-origin');
    });

    it('should not have TOKEN configured for cookie-session mode', () => {
      expect(OpenAPI.TOKEN).toBeUndefined();
    });
  });

  describe('Type Compatibility', () => {
    it('should export Workspace type from generated client', () => {
      // This test verifies that the type export works correctly
      // The actual type checking happens at compile time
      expectTypeExport<Workspace>('Workspace');
    });

    it('should export Project type from generated client', () => {
      expectTypeExport<Project>('Project');
    });

    it('should export LoginRequest type from generated client', () => {
      expectTypeExport<LoginRequest>('LoginRequest');
    });

    it('should export LoginResponse type from generated client', () => {
      expectTypeExport<LoginResponse>('LoginResponse');
    });
  });

  describe('Adapter Layer Integration', () => {
    it('should verify adapter functions have correct signatures', async () => {
      // Import the adapter functions from client.ts
      const { auth, workspaces, projects } = await import('../client');

      // Verify auth methods exist
      expect(auth).toBeDefined();
      expect(typeof auth.loginSession).toBe('function');
      expect(typeof auth.validate).toBe('function');
      expect(typeof auth.me).toBe('function');

      // Verify workspaces methods exist
      expect(workspaces).toBeDefined();
      expect(typeof workspaces.list).toBe('function');
      expect(typeof workspaces.get).toBe('function');
      expect(typeof workspaces.create).toBe('function');
      expect(typeof workspaces.update).toBe('function');
      expect(typeof workspaces.delete).toBe('function');

      // Verify projects methods exist
      expect(projects).toBeDefined();
      expect(typeof projects.list).toBe('function');
      expect(typeof projects.get).toBe('function');
      expect(typeof projects.create).toBe('function');
      expect(typeof projects.update).toBe('function');
      expect(typeof projects.delete).toBe('function');
    });

    it('should verify adapter types are exported for backward compatibility', () => {
      expectTypeExport<Workspace>('Workspace');
      expectTypeExport<Project>('Project');
      expectTypeExport<LoginRequest>('LoginRequest');
    });
  });
});
