import { describe, it, expect } from 'vitest';

import {
  createIDGeneratorState,
  generateContentHash,
  generateID,
  checkCollision,
  registerID,
  validateID,
  normalizeID,
  batchGenerateIDs,
  getIDStatistics,
  resetIDGenerator,
  createIDRemapping,
  applyIDRemapping,
  type IDGeneratorState,
  type IDStrategy,
} from '../idStrategy';

describe('idStrategy', () => {
  describe('State Creation', () => {
    it('should create default ID generator state', () => {
      const state = createIDGeneratorState();
      
      expect(state.strategy).toBe('content-hash');
      expect(state.prefix).toBe('');
      expect(state.namespace).toBe('canvas');
      expect(state.counter).toBe(1);
      expect(state.generatedIds.size).toBe(0);
      expect(state.collisionCount).toBe(0);
    });

    it('should create state with custom options', () => {
      const state = createIDGeneratorState({
        strategy: 'sequential',
        prefix: 'node_',
        namespace: 'test',
        counter: 100,
      });
      
      expect(state.strategy).toBe('sequential');
      expect(state.prefix).toBe('node_');
      expect(state.namespace).toBe('test');
      expect(state.counter).toBe(100);
    });
  });

  describe('Content Hash Generation', () => {
    it('should generate deterministic hash from content', () => {
      const content = { type: 'rect', x: 0, y: 0 };
      const hash1 = generateContentHash(content);
      const hash2 = generateContentHash(content);
      
      expect(hash1).toBe(hash2);
      expect(hash1).toMatch(/^[0-9a-f]{8}$/);
    });

    it('should generate different hashes for different content', () => {
      const content1 = { type: 'rect', x: 0, y: 0 };
      const content2 = { type: 'rect', x: 1, y: 1 };
      
      const hash1 = generateContentHash(content1);
      const hash2 = generateContentHash(content2);
      
      expect(hash1).not.toBe(hash2);
    });

    it('should respect namespace option', () => {
      const content = { type: 'rect', x: 0, y: 0 };
      const hash1 = generateContentHash(content, { namespace: 'ns1' });
      const hash2 = generateContentHash(content, { namespace: 'ns2' });
      
      expect(hash1).not.toBe(hash2);
    });

    it('should add prefix when provided', () => {
      const content = { type: 'rect', x: 0, y: 0 };
      const hash = generateContentHash(content, { prefix: 'node_' });
      
      expect(hash).toMatch(/^node_[0-9a-f]{8}$/);
    });

    it('should be order-independent for object keys', () => {
      const content1 = { x: 0, y: 0, type: 'rect' };
      const content2 = { type: 'rect', x: 0, y: 0 };
      
      const hash1 = generateContentHash(content1);
      const hash2 = generateContentHash(content2);
      
      expect(hash1).toBe(hash2);
    });
  });

  describe('ID Generation - Content Hash Strategy', () => {
    it('should generate content-based ID', () => {
      const state = createIDGeneratorState({ strategy: 'content-hash' });
      const content = { type: 'rect', x: 0, y: 0 };
      
      const result = generateID(state, content);
      
      expect(result.id).toMatch(/^[0-9a-f]{8}$/);
      expect(result.state.generatedIds.has(result.id)).toBe(true);
    });

    it('should throw error if content not provided for content-hash', () => {
      const state = createIDGeneratorState({ strategy: 'content-hash' });
      
      expect(() => generateID(state)).toThrow('Content is required');
    });

    it('should generate same ID for same content', () => {
      const state = createIDGeneratorState({ strategy: 'content-hash' });
      const content = { type: 'rect', x: 0, y: 0 };
      
      const result1 = generateID(state, content);
      const result2 = generateID(result1.state, content);
      
      expect(result1.id).toBe(result2.id);
    });
  });

  describe('ID Generation - UUID Strategy', () => {
    it('should generate UUID', () => {
      const state = createIDGeneratorState({ strategy: 'uuid' });
      
      const result = generateID(state);
      
      expect(result.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    });

    it('should generate unique UUIDs', () => {
      const state = createIDGeneratorState({ strategy: 'uuid' });
      
      const result1 = generateID(state);
      const result2 = generateID(result1.state);
      
      expect(result1.id).not.toBe(result2.id);
    });

    it('should add prefix to UUID', () => {
      const state = createIDGeneratorState({ strategy: 'uuid', prefix: 'node_' });
      
      const result = generateID(state);
      
      expect(result.id).toMatch(/^node_[0-9a-f]{8}-/);
    });
  });

  describe('ID Generation - Sequential Strategy', () => {
    it('should generate sequential IDs', () => {
      const state = createIDGeneratorState({ strategy: 'sequential', prefix: 'id_' });
      
      const result1 = generateID(state);
      const result2 = generateID(result1.state);
      const result3 = generateID(result2.state);
      
      expect(result1.id).toBe('id_1');
      expect(result2.id).toBe('id_2');
      expect(result3.id).toBe('id_3');
    });

    it('should start from custom counter', () => {
      const state = createIDGeneratorState({ 
        strategy: 'sequential',
        prefix: 'node_',
        counter: 100 
      });
      
      const result = generateID(state);
      
      expect(result.id).toBe('node_100');
      expect(result.state.counter).toBe(101);
    });

    it('should increment counter correctly', () => {
      let state = createIDGeneratorState({ strategy: 'sequential' });
      
      for (let i = 1; i <= 5; i++) {
        const result = generateID(state);
        expect(result.id).toBe(i.toString());
        expect(result.state.counter).toBe(i + 1);
        state = result.state;
      }
    });
  });

  describe('ID Generation - Timestamp Strategy', () => {
    it('should generate timestamp-based ID', () => {
      const state = createIDGeneratorState({ strategy: 'timestamp' });
      
      const result = generateID(state);
      
      expect(result.id).toMatch(/^[0-9a-z]+$/);
      expect(result.id.length).toBeGreaterThan(5);
    });

    it('should generate unique timestamp IDs', () => {
      const state = createIDGeneratorState({ strategy: 'timestamp' });
      
      const result1 = generateID(state);
      const result2 = generateID(result1.state);
      
      expect(result1.id).not.toBe(result2.id);
    });

    it('should add prefix to timestamp ID', () => {
      const state = createIDGeneratorState({ strategy: 'timestamp', prefix: 'ts_' });
      
      const result = generateID(state);
      
      expect(result.id).toMatch(/^ts_[0-9a-z]+$/);
    });
  });

  describe('Collision Detection', () => {
    it('should detect no collision for new ID', () => {
      const state = createIDGeneratorState();
      const result = checkCollision('new-id', state);
      
      expect(result.hasCollision).toBe(false);
      expect(result.suggestedId).toBeUndefined();
    });

    it('should detect collision for existing ID', () => {
      const state = createIDGeneratorState();
      const result1 = generateID(state, { type: 'rect' });
      
      const collision = checkCollision(result1.id, result1.state);
      
      expect(collision.hasCollision).toBe(true);
      expect(collision.existingId).toBe(result1.id);
      expect(collision.suggestedId).toBe(`${result1.id}_1`);
    });

    it('should suggest alternative IDs on collision', () => {
      let state = createIDGeneratorState();
      state = registerID('test-id', state);
      state = registerID('test-id_1', state);
      state = registerID('test-id_2', state);
      
      const collision = checkCollision('test-id', state);
      
      expect(collision.hasCollision).toBe(true);
      expect(collision.suggestedId).toBe('test-id_3');
    });
  });

  describe('ID Registration', () => {
    it('should register ID in state', () => {
      const state = createIDGeneratorState();
      const newState = registerID('test-id', state);
      
      expect(newState.generatedIds.has('test-id')).toBe(true);
      expect(state.generatedIds.has('test-id')).toBe(false); // Original unchanged
    });

    it('should accumulate registered IDs', () => {
      let state = createIDGeneratorState();
      state = registerID('id1', state);
      state = registerID('id2', state);
      state = registerID('id3', state);
      
      expect(state.generatedIds.size).toBe(3);
      expect(state.generatedIds.has('id1')).toBe(true);
      expect(state.generatedIds.has('id2')).toBe(true);
      expect(state.generatedIds.has('id3')).toBe(true);
    });
  });

  describe('ID Validation', () => {
    it('should validate correct ID', () => {
      const result = validateID('valid-id-123');
      
      expect(result.valid).toBe(true);
      expect(result.errors).toEqual([]);
      expect(result.normalized).toBe('valid-id-123');
    });

    it('should reject empty ID', () => {
      const result = validateID('');
      
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('ID cannot be empty');
    });

    it('should reject ID with invalid characters', () => {
      const result = validateID('invalid<>id');
      
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toContain('invalid characters');
    });

    it('should reject ID exceeding max length', () => {
      const longId = 'a'.repeat(100);
      const result = validateID(longId, { maxLength: 50 });
      
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('ID exceeds maximum length of 50 characters');
    });

    it('should normalize ID with lowercase option', () => {
      const result = validateID('TestID-123', { lowercase: true });
      
      expect(result.valid).toBe(true);
      expect(result.normalized).toBe('testid-123');
    });

    it('should normalize ID with removeSpaces option', () => {
      const result = validateID('test id 123', { removeSpaces: true });
      
      expect(result.valid).toBe(true);
      expect(result.normalized).toBe('testid123');
    });

    it('should normalize ID with replaceSpecialChars option', () => {
      const result = validateID('test@id#123', { replaceSpecialChars: true });
      
      expect(result.valid).toBe(true);
      expect(result.normalized).toBe('test_id_123');
    });

    it('should apply multiple normalization options', () => {
      const result = validateID('Test ID@123', {
        lowercase: true,
        removeSpaces: true,
        replaceSpecialChars: true,
      });
      
      expect(result.valid).toBe(true);
      expect(result.normalized).toBe('testid_123');
    });
  });

  describe('ID Normalization', () => {
    it('should normalize valid ID', () => {
      const normalized = normalizeID('TestID', { lowercase: true });
      
      expect(normalized).toBe('testid');
    });

    it('should throw error for invalid ID', () => {
      expect(() => normalizeID('', { lowercase: true })).toThrow('Invalid ID');
    });

    it('should truncate to max length', () => {
      const normalized = normalizeID('verylongidentifier', { maxLength: 10 });
      
      expect(normalized).toBe('verylongid');
    });
  });

  describe('Batch ID Generation', () => {
    it('should generate IDs for multiple items', () => {
      const state = createIDGeneratorState({ strategy: 'sequential' });
      const items = [
        { type: 'rect' },
        { type: 'circle' },
        { type: 'triangle' },
      ];
      
      const result = batchGenerateIDs(items, state);
      
      expect(result.ids).toEqual(['1', '2', '3']);
      expect(result.state.counter).toBe(4);
    });

    it('should generate content-hash IDs for items', () => {
      const state = createIDGeneratorState({ strategy: 'content-hash' });
      const items = [
        { type: 'rect', x: 0 },
        { type: 'rect', x: 1 },
      ];
      
      const result = batchGenerateIDs(items, state);
      
      expect(result.ids.length).toBe(2);
      expect(result.ids[0]).not.toBe(result.ids[1]);
      expect(result.state.generatedIds.size).toBe(2);
    });

    it('should handle empty batch', () => {
      const state = createIDGeneratorState({ strategy: 'sequential' });
      const result = batchGenerateIDs([], state);
      
      expect(result.ids).toEqual([]);
      expect(result.state.counter).toBe(state.counter);
    });
  });

  describe('ID Statistics', () => {
    it('should return correct statistics', () => {
      let state = createIDGeneratorState({ strategy: 'sequential' });
      
      for (let i = 0; i < 5; i++) {
        const result = generateID(state);
        state = result.state;
      }
      
      const stats = getIDStatistics(state);
      
      expect(stats.totalGenerated).toBe(5);
      expect(stats.strategy).toBe('sequential');
      expect(stats.nextCounter).toBe(6);
    });

    it('should track collision count', () => {
      const state = createIDGeneratorState({ collisionCount: 0 } as unknown);
      const stats = getIDStatistics(state);
      
      expect(stats.collisionCount).toBe(0);
    });
  });

  describe('ID Generator Reset', () => {
    it('should reset generator to initial state', () => {
      let state = createIDGeneratorState({ strategy: 'sequential' });
      
      // Generate some IDs
      for (let i = 0; i < 5; i++) {
        const result = generateID(state);
        state = result.state;
      }
      
      expect(state.counter).toBe(6);
      expect(state.generatedIds.size).toBe(5);
      
      // Reset
      const resetState = resetIDGenerator(state);
      
      expect(resetState.counter).toBe(1);
      expect(resetState.generatedIds.size).toBe(0);
      expect(resetState.strategy).toBe('sequential');
    });

    it('should reset with new options', () => {
      const state = createIDGeneratorState({ strategy: 'sequential', prefix: 'old_' });
      const resetState = resetIDGenerator(state, { 
        strategy: 'uuid',
        prefix: 'new_',
      });
      
      expect(resetState.strategy).toBe('uuid');
      expect(resetState.prefix).toBe('new_');
    });
  });

  describe('ID Remapping', () => {
    it('should create ID remapping', () => {
      const oldIds = ['old1', 'old2', 'old3'];
      const newState = createIDGeneratorState({ strategy: 'sequential', prefix: 'new_' });
      
      const result = createIDRemapping(oldIds, newState);
      
      expect(result.mapping.size).toBe(3);
      expect(result.mapping.get('old1')).toBe('new_1');
      expect(result.mapping.get('old2')).toBe('new_2');
      expect(result.mapping.get('old3')).toBe('new_3');
    });

    it('should create content-based remapping', () => {
      const oldIds = ['id1', 'id2'];
      const contents = [
        { type: 'rect', x: 0 },
        { type: 'circle', x: 0 },
      ];
      const newState = createIDGeneratorState({ strategy: 'content-hash' });
      
      const result = createIDRemapping(oldIds, newState, contents);
      
      expect(result.mapping.size).toBe(2);
      expect(result.mapping.get('id1')).toMatch(/^[0-9a-f]{8}$/);
      expect(result.mapping.get('id2')).toMatch(/^[0-9a-f]{8}$/);
    });

    it('should apply ID remapping to object', () => {
      const mapping = new Map([
        ['old1', 'new1'],
        ['old2', 'new2'],
      ]);
      
      const obj = {
        id: 'old1',
        name: 'Test',
        parentId: 'old2',
      };
      
      const remapped = applyIDRemapping(obj, mapping, ['id', 'parentId']);
      
      expect(remapped.id).toBe('new1');
      expect(remapped.parentId).toBe('new2');
      expect(remapped.name).toBe('Test');
    });

    it('should not modify IDs not in mapping', () => {
      const mapping = new Map([['old1', 'new1']]);
      const obj = { id: 'old2', name: 'Test' };
      
      const remapped = applyIDRemapping(obj, mapping);
      
      expect(remapped.id).toBe('old2');
    });

    it('should handle multiple ID fields', () => {
      const mapping = new Map([
        ['id1', 'newId1'],
        ['id2', 'newId2'],
        ['id3', 'newId3'],
      ]);
      
      const obj = {
        id: 'id1',
        sourceId: 'id2',
        targetId: 'id3',
        data: 'unchanged',
      };
      
      const remapped = applyIDRemapping(obj, mapping, ['id', 'sourceId', 'targetId']);
      
      expect(remapped.id).toBe('newId1');
      expect(remapped.sourceId).toBe('newId2');
      expect(remapped.targetId).toBe('newId3');
      expect(remapped.data).toBe('unchanged');
    });
  });

  describe('Edge Cases', () => {
    it('should handle special characters in content hash', () => {
      const content = { 
        type: 'text',
        text: 'Special chars: <>"\'&',
      };
      
      const hash = generateContentHash(content);
      
      expect(hash).toMatch(/^[0-9a-f]{8}$/);
    });

    it('should handle nested objects in content hash', () => {
      const content = {
        type: 'group',
        children: [
          { id: 'child1', type: 'rect' },
          { id: 'child2', type: 'circle' },
        ],
      };
      
      const hash = generateContentHash(content);
      
      expect(hash).toMatch(/^[0-9a-f]{8}$/);
    });

    it('should handle arrays in content hash', () => {
      const content1 = { points: [1, 2, 3, 4] };
      const content2 = { points: [1, 2, 3, 4] };
      const content3 = { points: [1, 2, 3, 5] };
      
      const hash1 = generateContentHash(content1);
      const hash2 = generateContentHash(content2);
      const hash3 = generateContentHash(content3);
      
      expect(hash1).toBe(hash2);
      expect(hash1).not.toBe(hash3);
    });

    it('should handle null and undefined in content', () => {
      const content1 = { value: null };
      const content2 = { value: undefined };
      
      const hash1 = generateContentHash(content1);
      const hash2 = generateContentHash(content2);
      
      expect(hash1).not.toBe(hash2);
    });
  });
});
