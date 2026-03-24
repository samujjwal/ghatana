/**
 * @fileoverview Tests for C4 Model Synchronization
 * @module libs/canvas/src/devsecops/__tests__/c4Sync.test.ts
 */

import { describe, it, expect } from 'vitest';

import {
  createC4SyncConfig,
  createC4SyncState,
  parseC4DSL,
  c4WorkspaceToCanvas,
  importC4DSL,
  setActiveView,
  buildViewHierarchy,
  drillDown,
  navigateUp,
  setActiveEnvironment,
  getEnvironmentOverlay,
  type C4Workspace,
  type C4SyncState,
} from '../c4Sync';

describe.skip('Feature 2.21: C4 Modeling - c4Sync', () => {
  describe('Configuration Creation', () => {
    it('should create default C4 sync config', () => {
      const config = createC4SyncConfig();
      
      expect(config.autoSync).toBe(false);
      expect(config.validateOnSync).toBe(true);
      expect(config.defaultEnvironment).toBe('dev');
      expect(config.enableEnvironmentOverlays).toBe(true);
      expect(config.autoLayout).toBe(true);
    });

    it('should create config with overrides', () => {
      const config = createC4SyncConfig({
        autoSync: true,
        defaultEnvironment: 'production',
        enableEnvironmentOverlays: false,
      });
      
      expect(config.autoSync).toBe(true);
      expect(config.defaultEnvironment).toBe('production');
      expect(config.enableEnvironmentOverlays).toBe(false);
      expect(config.validateOnSync).toBe(true); // Default preserved
    });
  });

  describe('State Creation', () => {
    it('should create initial sync state', () => {
      const config = createC4SyncConfig();
      const state = createC4SyncState(config);
      
      expect(state.config).toBe(config);
      expect(state.workspace).toBeNull();
      expect(state.activeView).toBeNull();
      expect(state.activeEnvironment).toBe('dev');
      expect(state.breadcrumbs).toEqual([]);
      expect(state.lastSyncTimestamp).toBeNull();
    });
  });

  describe('C4 DSL Parsing', () => {
    it('should parse simple C4 DSL with system context', () => {
      const dsl = `
        workspace "My System" {
          model {
            user = person "User"
            system = softwareSystem "My System"
            user -> system "Uses"
          }
          views {
            systemContext system {
              include *
            }
          }
        }
      `;
      
      const workspace = parseC4DSL(dsl);
      
      expect(workspace.name).toBe('My System');
      expect(workspace.model.elements).toHaveLength(2);
      expect(workspace.model.elements[0]).toMatchObject({
        id: 'user',
        name: 'User',
        type: 'person',
      });
      expect(workspace.model.elements[1]).toMatchObject({
        id: 'system',
        name: 'My System',
        type: 'software-system',
      });
      expect(workspace.model.relationships).toHaveLength(1);
      expect(workspace.model.relationships[0]).toMatchObject({
        source: 'user',
        target: 'system',
        description: 'Uses',
      });
      expect(workspace.views).toHaveLength(1);
      expect(workspace.views[0]).toMatchObject({
        key: 'system-context-system',
        type: 'system-context',
        softwareSystemId: 'system',
      });
    });

    it('should parse C4 DSL with containers', () => {
      const dsl = `
        workspace "Container Example" {
          model {
            system = softwareSystem "System"
            api = container "API" "REST API" "Node.js"
            db = container "Database" "Data Store" "PostgreSQL"
            api -> db "Reads/Writes"
          }
          views {
            container system {
              include *
            }
          }
        }
      `;
      
      const workspace = parseC4DSL(dsl);
      
      expect(workspace.model.elements).toHaveLength(3);
      expect(workspace.model.elements[1]).toMatchObject({
        id: 'api',
        name: 'API',
        type: 'container',
        description: 'REST API',
        technology: 'Node.js',
      });
      expect(workspace.views[0].type).toBe('container');
    });

    it('should handle multiple views', () => {
      const dsl = `
        workspace "Multi-View" {
          model {
            system = softwareSystem "System"
            api = container "API"
          }
          views {
            systemContext system {
              include *
            }
            container system {
              include *
            }
          }
        }
      `;
      
      const workspace = parseC4DSL(dsl);
      
      expect(workspace.views).toHaveLength(2);
      expect(workspace.views[0].type).toBe('system-context');
      expect(workspace.views[1].type).toBe('container');
    });
  });

  describe('Workspace to Canvas Conversion', () => {
    const sampleWorkspace: C4Workspace = {
      name: 'Sample',
      model: {
        elements: [
          { id: 'user', name: 'User', type: 'person' },
          { id: 'api', name: 'API Gateway', type: 'software-system' },
          { id: 'db', name: 'Database', type: 'software-system' },
        ],
        relationships: [
          { id: 'rel-1', source: 'user', target: 'api', description: 'Uses' },
          { id: 'rel-2', source: 'api', target: 'db', description: 'Queries' },
        ],
      },
      views: [
        {
          key: 'context-1',
          type: 'system-context',
          softwareSystemId: 'api',
          include: ['*'],
        },
      ],
    };

    it('should convert workspace to canvas document', () => {
      const canvas = c4WorkspaceToCanvas(sampleWorkspace, 'context-1');
      
      expect(canvas.id).toBe('c4-view-context-1');
      expect(canvas.version).toBe('1.0.0');
      expect(canvas.elementOrder).toHaveLength(5); // 3 nodes + 2 edges
      expect(Object.keys(canvas.elements)).toHaveLength(5);
    });

    it('should create nodes with correct properties', () => {
      const canvas = c4WorkspaceToCanvas(sampleWorkspace, 'context-1');
      const userNode = canvas.elements['user'];
      
      expect(userNode).toBeDefined();
      expect(userNode.type).toBe('node');
      expect(userNode.data).toMatchObject({
        label: 'User',
      });
      expect(userNode.metadata).toMatchObject({
        c4Type: 'person',
      });
    });

    it('should create edges with correct relationships', () => {
      const canvas = c4WorkspaceToCanvas(sampleWorkspace, 'context-1');
      const edge = canvas.elements['rel-1'];
      
      expect(edge).toBeDefined();
      expect(edge.type).toBe('edge');
      if (edge.type === 'edge') {
        expect(edge.sourceId).toBe('user');
        expect(edge.targetId).toBe('api');
        expect(edge.metadata?.label).toBe('Uses');
      }
    });

    it('should throw error for non-existent view', () => {
      expect(() => {
        c4WorkspaceToCanvas(sampleWorkspace, 'non-existent');
      }).toThrow('View not found');
    });

    it('should respect include filters', () => {
      const workspace: C4Workspace = {
        ...sampleWorkspace,
        views: [
          {
            key: 'filtered',
            type: 'system-context',
            softwareSystemId: 'api',
            include: ['user', 'api'], // Exclude 'db'
          },
        ],
      };
      
      const canvas = c4WorkspaceToCanvas(workspace, 'filtered');
      
      expect(canvas.elements['user']).toBeDefined();
      expect(canvas.elements['api']).toBeDefined();
      expect(canvas.elements['db']).toBeUndefined();
      expect(canvas.elements['rel-1']).toBeDefined();
      expect(canvas.elements['rel-2']).toBeUndefined(); // Excluded because target is excluded
    });
  });

  describe('Import C4 DSL', () => {
    it('should import DSL and update state', () => {
      const config = createC4SyncConfig();
      const state = createC4SyncState(config);
      
      const dsl = `
        workspace "Import Test" {
          model {
            user = person "User"
            system = softwareSystem "System"
            user -> system "Uses"
          }
          views {
            systemContext system {
              include *
            }
          }
        }
      `;
      
      const updated = importC4DSL(state, dsl);
      
      expect(updated.workspace).toBeDefined();
      expect(updated.workspace?.name).toBe('Import Test');
      expect(updated.workspace?.model.elements).toHaveLength(2);
      expect(updated.activeView).toBe('system-context-system');
      expect(updated.lastSyncTimestamp).toBeDefined();
    });
  });

  describe('Active View Management', () => {
    let state: C4SyncState;

    beforeEach(() => {
      const config = createC4SyncConfig();
      state = createC4SyncState(config);
      const dsl = `
        workspace "View Test" {
          model {
            system = softwareSystem "System"
          }
          views {
            systemContext system "View 1" {
              include *
            }
            container system "View 2" {
              include *
            }
          }
        }
      `;
      state = importC4DSL(state, dsl);
    });

    it('should set active view', () => {
      const updated = setActiveView(state, 'container-system');
      
      expect(updated.activeView).toBe('container-system');
    });

    it('should throw error for non-existent view', () => {
      expect(() => {
        setActiveView(state, 'non-existent');
      }).toThrow('View not found');
    });

    it('should throw error when no workspace loaded', () => {
      const emptyState = createC4SyncState(createC4SyncConfig());
      
      expect(() => {
        setActiveView(emptyState, 'any-view');
      }).toThrow('No workspace loaded');
    });
  });

  describe('View Hierarchy', () => {
    it('should build view hierarchy', () => {
      const workspace: C4Workspace = {
        name: 'Hierarchy Test',
        model: {
          elements: [],
          relationships: [],
        },
        views: [
          { key: 'context-1', type: 'system-context', softwareSystemId: 'sys1', include: [] },
          { key: 'container-1', type: 'container', softwareSystemId: 'sys1', include: [] },
          { key: 'container-2', type: 'container', softwareSystemId: 'sys1', include: [] },
          { key: 'component-1', type: 'component', containerId: 'api', include: [] },
        ],
      };
      
      const hierarchy = buildViewHierarchy(workspace);
      
      expect(hierarchy.systemContext).toHaveLength(1);
      expect(hierarchy.containers.get('sys1')).toHaveLength(2);
      expect(hierarchy.components.get('api')).toHaveLength(1);
    });

    it('should handle empty workspace', () => {
      const workspace: C4Workspace = {
        name: 'Empty',
        model: { elements: [], relationships: [] },
        views: [],
      };
      
      const hierarchy = buildViewHierarchy(workspace);
      
      expect(hierarchy.systemContext).toHaveLength(0);
      expect(hierarchy.containers.size).toBe(0);
      expect(hierarchy.components.size).toBe(0);
    });
  });

  describe('Drill-Down Navigation', () => {
    let state: C4SyncState;

    beforeEach(() => {
      const config = createC4SyncConfig();
      state = createC4SyncState(config);
      const workspace: C4Workspace = {
        name: 'Drill Test',
        model: {
          elements: [],
          relationships: [],
        },
        views: [
          { key: 'context-sys', type: 'system-context', softwareSystemId: 'sys', include: [], title: 'System Context' },
          { key: 'container-sys', type: 'container', softwareSystemId: 'sys', include: [], title: 'Containers' },
          { key: 'component-api', type: 'component', containerId: 'api', include: [], title: 'Components' },
        ],
      };
      state = { ...state, workspace, activeView: 'context-sys' };
    });

    it('should drill down from context to container', () => {
      const updated = drillDown(state, 'sys');
      
      expect(updated.activeView).toBe('container-sys');
      expect(updated.breadcrumbs).toHaveLength(1);
      expect(updated.breadcrumbs[0]).toMatchObject({
        viewKey: 'context-sys',
        viewType: 'system-context',
        elementId: 'sys',
      });
    });

    it('should drill down from container to component', () => {
      state = { ...state, activeView: 'container-sys' };
      const updated = drillDown(state, 'api');
      
      expect(updated.activeView).toBe('component-api');
      expect(updated.breadcrumbs).toHaveLength(1);
    });

    it('should throw error when no child view exists', () => {
      expect(() => {
        drillDown(state, 'non-existent');
      }).toThrow('No child view found');
    });

    it('should throw error when no workspace', () => {
      const emptyState = createC4SyncState(createC4SyncConfig());
      
      expect(() => {
        drillDown(emptyState, 'sys');
      }).toThrow('No workspace or active view');
    });
  });

  describe('Breadcrumb Navigation', () => {
    it('should navigate up using breadcrumbs', () => {
      const config = createC4SyncConfig();
      let state = createC4SyncState(config);
      const workspace: C4Workspace = {
        name: 'Nav Test',
        model: { elements: [], relationships: [] },
        views: [
          { key: 'context-sys', type: 'system-context', softwareSystemId: 'sys', include: [] },
          { key: 'container-sys', type: 'container', softwareSystemId: 'sys', include: [] },
          { key: 'component-api', type: 'component', containerId: 'api', include: [] },
        ],
      };
      state = { ...state, workspace, activeView: 'context-sys' };
      
      // Drill down twice
      state = drillDown(state, 'sys');
      state = drillDown(state, 'api');
      
      expect(state.breadcrumbs).toHaveLength(2);
      expect(state.activeView).toBe('component-api');
      
      // Navigate up one level
      const updated = navigateUp(state, 0);
      
      expect(updated.activeView).toBe('context-sys');
      expect(updated.breadcrumbs).toHaveLength(0);
    });

    it('should throw error for invalid breadcrumb index', () => {
      const state = createC4SyncState(createC4SyncConfig());
      
      expect(() => {
        navigateUp(state, 5);
      }).toThrow('Invalid breadcrumb index');
    });
  });

  describe('Environment Management', () => {
    it('should set active environment', () => {
      const config = createC4SyncConfig();
      const state = createC4SyncState(config);
      
      const updated = setActiveEnvironment(state, 'production');
      
      expect(updated.activeEnvironment).toBe('production');
    });

    it('should get environment overlay', () => {
      const config = createC4SyncConfig({ enableEnvironmentOverlays: true });
      let state = createC4SyncState(config);
      const workspace: C4Workspace = {
        name: 'Env Test',
        model: {
          elements: [
            { id: 'dev-svc', name: 'Dev Service', type: 'container', environment: 'dev' },
            { id: 'prod-svc', name: 'Prod Service', type: 'container', environment: 'production' },
            { id: 'all-svc', name: 'All Envs', type: 'container' },
          ],
          relationships: [],
        },
        views: [],
      };
      state = { ...state, workspace, activeEnvironment: 'production' };
      
      const overlay = getEnvironmentOverlay(state);
      
      expect(overlay.environment).toBe('production');
      expect(overlay.elementsToShow).toContain('prod-svc');
      expect(overlay.elementsToShow).toContain('all-svc');
      expect(overlay.elementsToHide).toContain('dev-svc');
      expect(overlay.styleOverrides.get('prod-svc')).toBeDefined();
    });

    it('should return empty overlay when disabled', () => {
      const config = createC4SyncConfig({ enableEnvironmentOverlays: false });
      const state = createC4SyncState(config);
      
      const overlay = getEnvironmentOverlay(state);
      
      expect(overlay.elementsToShow).toHaveLength(0);
      expect(overlay.elementsToHide).toHaveLength(0);
      expect(overlay.styleOverrides.size).toBe(0);
    });
  });
});
