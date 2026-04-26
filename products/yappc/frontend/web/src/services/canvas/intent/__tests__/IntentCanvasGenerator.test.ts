import { describe, it, expect } from 'vitest';
import {
  parseIntent,
  generateNodes,
  generateConnections,
  generatePreview,
  type IntentParseResult,
} from '../IntentCanvasGenerator';

describe('IntentCanvasGenerator', () => {
  describe('parseIntent', () => {
    it('detects FULL_STACK by default', () => {
      const result = parseIntent('build a generic system');
      expect(result.projectType).toBe('FULL_STACK');
    });

    it('detects MOBILE from keywords', () => {
      const result = parseIntent('create a mobile app for iOS');
      expect(result.projectType).toBe('MOBILE');
    });

    it('detects BACKEND from keywords', () => {
      const result = parseIntent('build a backend api server');
      expect(result.projectType).toBe('BACKEND');
    });

    it('detects UI from keywords', () => {
      const result = parseIntent('design a frontend component library');
      expect(result.projectType).toBe('UI');
    });

    it('detects DESKTOP from keywords', () => {
      const result = parseIntent('create a desktop app for windows');
      expect(result.projectType).toBe('DESKTOP');
    });

    it('extracts quoted project name', () => {
      const result = parseIntent('build "My Awesome App" with auth');
      expect(result.projectName).toBe('My Awesome App');
    });

    it('extracts called project name', () => {
      const result = parseIntent('create an app called TaskMaster');
      expect(result.projectName).toBe('TaskMaster');
    });

    it('detects features from keywords', () => {
      const result = parseIntent('app with user login, dashboard, and payment');
      expect(result.features).toContain('User Authentication');
      expect(result.features).toContain('Dashboard');
      expect(result.features).toContain('Payment Integration');
    });

    it('detects tech stack from keywords', () => {
      const result = parseIntent('build with react, node.js, and postgres');
      expect(result.techStack).toContain('React');
      expect(result.techStack).toContain('Node.js');
      expect(result.techStack).toContain('PostgreSQL');
    });
  });

  describe('generateNodes', () => {
    it('generates entry nodes for empty intent', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: [], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.length).toBeGreaterThanOrEqual(2);
      expect(nodes.some(n => n.label === 'Home Page')).toBe(true);
    });

    it('generates auth nodes when User Authentication detected', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['User Authentication'], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.some(n => n.label.includes('Login'))).toBe(true);
      expect(nodes.some(n => n.label.includes('Auth Service'))).toBe(true);
    });

    it('generates dashboard nodes when Dashboard detected', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['Dashboard'], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.some(n => n.label === 'Dashboard')).toBe(true);
      expect(nodes.some(n => n.label === 'Stats Widget')).toBe(true);
    });

    it('generates mobile screens for MOBILE type', () => {
      const parsed: IntentParseResult = { projectType: 'MOBILE', features: [], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.some(n => n.label === 'Home Screen')).toBe(true);
      expect(nodes.some(n => n.label === 'Main Navigation')).toBe(true);
    });

    it('generates database node for backend projects', () => {
      const parsed: IntentParseResult = { projectType: 'BACKEND', features: [], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.some(n => n.label === 'Database')).toBe(true);
      expect(nodes.some(n => n.label === 'REST API')).toBe(true);
    });

    it('generates e-commerce nodes', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['E-commerce'], techStack: [] };
      const nodes = generateNodes(parsed);
      expect(nodes.some(n => n.label === 'Product Catalog')).toBe(true);
      expect(nodes.some(n => n.label === 'Cart')).toBe(true);
    });

    it('positions nodes in a grid', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['Dashboard', 'User Authentication'], techStack: [] };
      const nodes = generateNodes(parsed);
      const xs = nodes.map(n => n.x);
      const ys = nodes.map(n => n.y);
      expect(new Set(xs).size).toBeGreaterThan(1);
      expect(new Set(ys).size).toBeGreaterThanOrEqual(1);
    });
  });

  describe('generateConnections', () => {
    it('connects pages to APIs', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['User Authentication'], techStack: [] };
      const nodes = generateNodes(parsed);
      const connections = generateConnections(nodes);
      expect(connections.length).toBeGreaterThanOrEqual(1);
      const conn = connections[0];
      const fromNode = nodes.find(n => n.id === conn.from);
      const toNode = nodes.find(n => n.id === conn.to);
      expect(fromNode).toBeDefined();
      expect(toNode).toBeDefined();
    });

    it('does not create self-connections', () => {
      const parsed: IntentParseResult = { projectType: 'FULL_STACK', features: ['Dashboard'], techStack: [] };
      const nodes = generateNodes(parsed);
      const connections = generateConnections(nodes);
      for (const c of connections) {
        expect(c.from).not.toBe(c.to);
      }
    });
  });

  describe('generatePreview', () => {
    it('returns a complete preview object', () => {
      const preview = generatePreview('build a blog with user auth and dashboard');
      expect(preview.nodes.length).toBeGreaterThan(0);
      expect(preview.confidence).toBeGreaterThan(0);
      expect(preview.confidence).toBeLessThanOrEqual(1);
      expect(preview.rationale.length).toBeGreaterThan(0);
      expect(preview.estimatedComplexity).toMatch(/low|medium|high/);
    });

    it('includes detected features in preview', () => {
      const preview = generatePreview('create an e-commerce site with payments');
      expect(preview.detectedFeatures).toContain('E-commerce');
      expect(preview.detectedFeatures).toContain('Payment Integration');
    });

    it('estimates low complexity for simple intents', () => {
      const preview = generatePreview('simple landing page');
      expect(preview.estimatedComplexity).toBe('low');
    });

    it('estimates high complexity for rich intents', () => {
      const preview = generatePreview('build a full stack app with auth, dashboard, payments, real-time chat, search, file uploads, and AI recommendations');
      expect(preview.estimatedComplexity).toBe('high');
    });
  });
});
