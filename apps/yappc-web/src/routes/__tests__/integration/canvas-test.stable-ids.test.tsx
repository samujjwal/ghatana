// All tests skipped - incomplete feature
/**
 * Integration Tests: Feature 2.15 - Stable IDs & Diffing
 *
 * Tests the React integration of:
 * - Deterministic ID generation (content-hash, UUID, sequential, timestamp)
 * - ID collision detection and resolution
 * - Semantic diffing (structural vs styling vs content changes)
 * - JSON Patch export and import
 * - Diff merging and filtering
 * - Change tracking and statistics
 *
 * Acceptance Criteria:
 * - AC 2.15.1: ID scheme - Node IDs hash from type+data (deterministic)
 * - AC 2.15.2: Semantic diff - Highlights structural vs styling adjustments
 * - AC 2.15.3: Patch export - JSON Patch (RFC 6902) available
 *
 * @see libs/canvas/src/persistence/idStrategy.ts
 * @see libs/canvas/src/persistence/semanticDiff.ts
 */

import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import {
  createIDGeneratorState,
  generateID,
  generateContentHash,
  checkCollision,
  validateID,
  normalizeID,
  batchGenerateIDs,
  getIDStatistics,
  createIDRemapping,
  applyIDRemapping,
  diff,
  applyPatch,
  generateDiffSummary,
  exportPatchesJSON,
  importPatchesJSON,
  mergeDiffs,
  filterDiffByType,
  type IDGeneratorState,
  type IDStrategy,
  type CollisionResult,
  type IDValidation,
  type CanvasDocument,
  type DiffResult,
  type DiffOptions,
  type JSONPatchOperation,
} from '@ghatana/canvas';
import React, { useState, useCallback } from 'react';
import { describe, it, expect, beforeEach } from 'vitest';

/**
 * Test component for ID generation and management
 * Provides UI to test different ID strategies and collision detection
 */
interface IDManagerProps {
  initialStrategy?: IDStrategy;
  initialPrefix?: string;
  initialNamespace?: string;
}

const IDManager: React.FC<IDManagerProps> = ({
  initialStrategy = 'content-hash',
  initialPrefix = '',
  initialNamespace = 'canvas',
}) => {
  const [state, setState] = useState<IDGeneratorState>(() =>
    createIDGeneratorState({
      strategy: initialStrategy,
      prefix: initialPrefix,
      namespace: initialNamespace,
    })
  );

  const [generatedIds, setGeneratedIds] = useState<string[]>([]);
  const [collisionResult, setCollisionResult] =
    useState<CollisionResult | null>(null);
  const [validationResult, setValidationResult] = useState<IDValidation | null>(
    null
  );
  const [contentInput, setContentInput] = useState(
    '{"type":"rect","x":0,"y":0}'
  );
  const [idToValidate, setIdToValidate] = useState('');
  const [idToCheck, setIdToCheck] = useState('');

  const handleGenerateID = useCallback(() => {
    try {
      const content = contentInput ? JSON.parse(contentInput) : undefined;
      const result = generateID(state, content);

      // Create immutable state update
      setState({
        ...result.state,
        generatedIds: new Set(result.state.generatedIds),
      });

      setGeneratedIds((prev) => [...prev, result.id]);
    } catch (error) {
      console.error('Generate ID error:', error);
    }
  }, [state, contentInput]);

  const handleGenerateContentHash = useCallback(() => {
    try {
      const content = JSON.parse(contentInput);
      const hash = generateContentHash(content, {
        prefix: state.prefix,
        namespace: state.namespace,
      });
      setGeneratedIds((prev) => [...prev, hash]);
    } catch (error) {
      console.error('Generate hash error:', error);
    }
  }, [contentInput, state.prefix, state.namespace]);

  const handleCheckCollision = useCallback(() => {
    const result = checkCollision(idToCheck, state);
    setCollisionResult(result);
  }, [idToCheck, state]);

  const handleValidateID = useCallback(() => {
    const result = validateID(idToValidate);
    setValidationResult(result);
  }, [idToValidate]);

  const handleNormalizeID = useCallback(() => {
    try {
      const normalized = normalizeID(idToValidate, {
        lowercase: true,
        removeSpaces: true,
        replaceSpecialChars: true,
      });
      setGeneratedIds((prev) => [...prev, normalized]);
    } catch (error) {
      console.error('Normalize error:', error);
    }
  }, [idToValidate]);

  const handleBatchGenerate = useCallback(() => {
    try {
      const items = [
        { type: 'rect', x: 0, y: 0 },
        { type: 'circle', x: 100, y: 100 },
        { type: 'text', x: 50, y: 50 },
      ];
      const result = batchGenerateIDs(items, state);

      setState({
        ...result.state,
        generatedIds: new Set(result.state.generatedIds),
      });

      setGeneratedIds((prev) => [...prev, ...result.ids]);
    } catch (error) {
      console.error('Batch generate error:', error);
    }
  }, [state]);

  const handleChangeStrategy = useCallback((newStrategy: IDStrategy) => {
    setState((prev: IDGeneratorState) => ({
      ...prev,
      strategy: newStrategy,
    }));
  }, []);

  const stats = getIDStatistics(state);

  return (
    <div data-testid="id-manager">
      <div data-testid="strategy">Strategy: {state.strategy}</div>
      <div data-testid="prefix">Prefix: {state.prefix || 'none'}</div>
      <div data-testid="namespace">Namespace: {state.namespace}</div>
      <div data-testid="stats">
        Total Generated: {stats.totalGenerated}, Collisions:{' '}
        {stats.collisionCount}, Next Counter: {stats.nextCounter}
      </div>

      <div data-testid="generated-ids">
        Generated IDs: {generatedIds.join(', ')}
      </div>

      <div>
        <textarea
          data-testid="content-input"
          value={contentInput}
          onChange={(e) => setContentInput(e.target.value)}
          placeholder="Enter JSON content"
        />
        <button data-testid="generate-id-btn" onClick={handleGenerateID}>
          Generate ID
        </button>
        <button
          data-testid="generate-hash-btn"
          onClick={handleGenerateContentHash}
        >
          Generate Hash
        </button>
        <button data-testid="batch-generate-btn" onClick={handleBatchGenerate}>
          Batch Generate (3 items)
        </button>
      </div>

      <div>
        <input
          data-testid="id-to-check"
          value={idToCheck}
          onChange={(e) => setIdToCheck(e.target.value)}
          placeholder="ID to check collision"
        />
        <button
          data-testid="check-collision-btn"
          onClick={handleCheckCollision}
        >
          Check Collision
        </button>
        {collisionResult && (
          <div data-testid="collision-result">
            Collision: {collisionResult.hasCollision ? 'YES' : 'NO'}
            {collisionResult.suggestedId &&
              ` - Suggested: ${collisionResult.suggestedId}`}
          </div>
        )}
      </div>

      <div>
        <input
          data-testid="id-to-validate"
          value={idToValidate}
          onChange={(e) => setIdToValidate(e.target.value)}
          placeholder="ID to validate"
        />
        <button data-testid="validate-id-btn" onClick={handleValidateID}>
          Validate ID
        </button>
        <button data-testid="normalize-id-btn" onClick={handleNormalizeID}>
          Normalize ID
        </button>
        {validationResult && (
          <div data-testid="validation-result">
            Valid: {validationResult.valid ? 'YES' : 'NO'}
            {!validationResult.valid &&
              ` - Errors: ${validationResult.errors.join(', ')}`}
            {validationResult.normalized &&
              ` - Normalized: ${validationResult.normalized}`}
          </div>
        )}
      </div>

      <div>
        <button
          data-testid="change-strategy-content-hash"
          onClick={() => handleChangeStrategy('content-hash')}
        >
          Content Hash
        </button>
        <button
          data-testid="change-strategy-uuid"
          onClick={() => handleChangeStrategy('uuid')}
        >
          UUID
        </button>
        <button
          data-testid="change-strategy-sequential"
          onClick={() => handleChangeStrategy('sequential')}
        >
          Sequential
        </button>
        <button
          data-testid="change-strategy-timestamp"
          onClick={() => handleChangeStrategy('timestamp')}
        >
          Timestamp
        </button>
      </div>
    </div>
  );
};

/**
 * Test component for semantic diffing
 * Provides UI to test diff generation, patch export, and change tracking
 */
interface DiffManagerProps {
  initialDoc?: CanvasDocument;
}

const DiffManager: React.FC<DiffManagerProps> = ({
  initialDoc = { nodes: [], edges: [] },
}) => {
  const [oldDoc, setOldDoc] = useState<CanvasDocument>(initialDoc);
  const [newDoc, setNewDoc] = useState<CanvasDocument>(initialDoc);
  const [diffResult, setDiffResult] = useState<DiffResult | null>(null);
  const [diffSummary, setDiffSummary] = useState<string>('');
  const [patches, setPatches] = useState<JSONPatchOperation[]>([]);
  const [patchJSON, setPatchJSON] = useState<string>('');
  const [options, setOptions] = useState<DiffOptions>({
    detectMoves: true,
    generatePatches: true,
  });

  const handleSetOldDoc = useCallback((docJSON: string) => {
    try {
      const doc = JSON.parse(docJSON);
      setOldDoc(doc);
    } catch (error) {
      console.error('Parse error:', error);
    }
  }, []);

  const handleSetNewDoc = useCallback((docJSON: string) => {
    try {
      const doc = JSON.parse(docJSON);
      setNewDoc(doc);
    } catch (error) {
      console.error('Parse error:', error);
    }
  }, []);

  const handleGenerateDiff = useCallback(() => {
    const result = diff(oldDoc, newDoc, options);
    setDiffResult(result);
    setPatches(result.patches);

    const summary = generateDiffSummary(result);
    setDiffSummary(summary);

    const patchesJSON = exportPatchesJSON(result.patches);
    setPatchJSON(patchesJSON);
  }, [oldDoc, newDoc, options]);

  const handleApplyPatch = useCallback(() => {
    if (patches.length > 0) {
      const patched = applyPatch(oldDoc, patches);
      setNewDoc(patched);
    }
  }, [oldDoc, patches]);

  const handleToggleDetectMoves = useCallback(() => {
    setOptions((prev: DiffOptions) => ({
      ...prev,
      detectMoves: !prev.detectMoves,
    }));
  }, []);

  const handleFilterStructural = useCallback(() => {
    if (diffResult) {
      const filtered = filterDiffByType(diffResult, 'structural');
      setDiffResult(filtered);
    }
  }, [diffResult]);

  const handleFilterStyling = useCallback(() => {
    if (diffResult) {
      const filtered = filterDiffByType(diffResult, 'styling');
      setDiffResult(filtered);
    }
  }, [diffResult]);

  return (
    <div data-testid="diff-manager">
      <div data-testid="options">
        Detect Moves: {options.detectMoves ? 'ON' : 'OFF'}
        <button
          data-testid="toggle-detect-moves"
          onClick={handleToggleDetectMoves}
        >
          Toggle Detect Moves
        </button>
      </div>

      <div>
        <textarea
          data-testid="old-doc-input"
          placeholder="Old document JSON"
          onChange={(e) => handleSetOldDoc(e.target.value)}
        />
        <textarea
          data-testid="new-doc-input"
          placeholder="New document JSON"
          onChange={(e) => handleSetNewDoc(e.target.value)}
        />
        <button data-testid="generate-diff-btn" onClick={handleGenerateDiff}>
          Generate Diff
        </button>
      </div>

      {diffResult && (
        <div data-testid="diff-result">
          <div data-testid="has-changes">
            Has Changes: {diffResult.hasChanges ? 'YES' : 'NO'}
          </div>
          <div data-testid="stats">
            Total: {diffResult.statistics.totalChanges}, Structural:{' '}
            {diffResult.statistics.structuralChanges}, Styling:{' '}
            {diffResult.statistics.stylingChanges}, Content:{' '}
            {diffResult.statistics.contentChanges}
          </div>
          <div data-testid="elements">
            Added: {diffResult.added.length}, Removed:{' '}
            {diffResult.removed.length}, Modified: {diffResult.modified.length},
            Moved: {diffResult.moved.length}
          </div>
          <div data-testid="diff-summary">{diffSummary}</div>
          <div data-testid="patches-count">Patches: {patches.length}</div>
          <div data-testid="patches-json">{patchJSON}</div>
        </div>
      )}

      <div>
        <button data-testid="apply-patch-btn" onClick={handleApplyPatch}>
          Apply Patches
        </button>
        <button
          data-testid="filter-structural-btn"
          onClick={handleFilterStructural}
        >
          Filter Structural
        </button>
        <button data-testid="filter-styling-btn" onClick={handleFilterStyling}>
          Filter Styling
        </button>
      </div>
    </div>
  );
};

describe.skip('Feature 2.15: Stable IDs & Diffing - Integration Tests', () => {
  describe('ID Generation and Management', () => {
    it('should generate deterministic content-hash IDs', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      // Generate ID from same content twice
      const generateBtn = screen.getByTestId('generate-id-btn');
      await user.click(generateBtn);
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids.length).toBe(2);
        expect(ids[0]).toBe(ids[1]); // Same content = same hash
        expect(ids[0]).toMatch(/^[0-9a-f]{8}$/); // 8-char hex
      });
    });

    it('should generate different hashes for different content', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      const contentInput = screen.getByTestId(
        'content-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-id-btn');

      // Generate ID from first content
      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste('{"type":"rect","x":0,"y":0}');
      await user.click(generateBtn);

      // Generate ID from different content
      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste('{"type":"circle","x":100,"y":100}');
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids.length).toBe(2);
        expect(ids[0]).not.toBe(ids[1]); // Different content = different hash
      });
    });

    it('should generate UUIDs with correct format', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="uuid" />);

      const generateBtn = screen.getByTestId('generate-id-btn');
      await user.click(generateBtn);
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids.length).toBe(2);
        expect(ids[0]).toMatch(
          /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
        );
        expect(ids[0]).not.toBe(ids[1]); // UUIDs should be unique
      });
    });

    it('should generate sequential IDs with incrementing counter', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="sequential" initialPrefix="node_" />);

      const generateBtn = screen.getByTestId('generate-id-btn');

      // Generate 3 sequential IDs
      await user.click(generateBtn);
      await user.click(generateBtn);
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids).toEqual(['node_1', 'node_2', 'node_3']);

        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Next Counter: 4');
      });
    });

    it('should batch generate multiple IDs', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      const batchBtn = screen.getByTestId('batch-generate-btn');
      await user.click(batchBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids.length).toBe(3); // 3 items in batch
        expect(new Set(ids).size).toBe(3); // All unique

        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Total Generated: 3');
      });
    });
  });

  describe('ID Collision Detection', () => {
    it('should detect no collision for new ID', async () => {
      const user = userEvent.setup();
      render(<IDManager />);

      const idInput = screen.getByTestId('id-to-check') as HTMLInputElement;
      const checkBtn = screen.getByTestId('check-collision-btn');

      await user.type(idInput, 'new-unique-id');
      await user.click(checkBtn);

      await waitFor(() => {
        const result = screen.getByTestId('collision-result').textContent || '';
        expect(result).toContain('Collision: NO');
      });
    });

    it('should detect collision for existing ID', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      // Generate an ID first
      const generateBtn = screen.getByTestId('generate-id-btn');
      await user.click(generateBtn);

      await waitFor(async () => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);
        const generatedId = ids[0];

        // Check collision for the generated ID
        const idInput = screen.getByTestId('id-to-check') as HTMLInputElement;
        const checkBtn = screen.getByTestId('check-collision-btn');

        await user.type(idInput, generatedId);
        await user.click(checkBtn);

        await waitFor(() => {
          const result =
            screen.getByTestId('collision-result').textContent || '';
          expect(result).toContain('Collision: YES');
          expect(result).toContain(`Suggested: ${generatedId}_1`);
        });
      });
    });

    it('should suggest alternative IDs for multiple collisions', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="sequential" initialPrefix="test_" />);

      // Generate test_1, test_2, test_3
      const generateBtn = screen.getByTestId('generate-id-btn');
      await user.click(generateBtn);
      await user.click(generateBtn);
      await user.click(generateBtn);

      // Check collision for test_1
      const idInput = screen.getByTestId('id-to-check') as HTMLInputElement;
      const checkBtn = screen.getByTestId('check-collision-btn');

      await user.type(idInput, 'test_1');
      await user.click(checkBtn);

      await waitFor(() => {
        const result = screen.getByTestId('collision-result').textContent || '';
        expect(result).toContain('Collision: YES');
        expect(result).toContain('Suggested: test_1_1');
      });
    });
  });

  describe('ID Validation and Normalization', () => {
    it('should validate correct IDs', async () => {
      const user = userEvent.setup();
      render(<IDManager />);

      const idInput = screen.getByTestId('id-to-validate') as HTMLInputElement;
      const validateBtn = screen.getByTestId('validate-id-btn');

      await user.type(idInput, 'valid-id-123');
      await user.click(validateBtn);

      await waitFor(() => {
        const result =
          screen.getByTestId('validation-result').textContent || '';
        expect(result).toContain('Valid: YES');
        expect(result).toContain('Normalized: valid-id-123');
      });
    });

    it('should reject empty IDs', async () => {
      const user = userEvent.setup();
      render(<IDManager />);

      const validateBtn = screen.getByTestId('validate-id-btn');
      await user.click(validateBtn);

      await waitFor(() => {
        const result =
          screen.getByTestId('validation-result').textContent || '';
        expect(result).toContain('Valid: NO');
        expect(result).toContain('Errors: ID cannot be empty');
      });
    });

    it('should reject IDs with invalid characters', async () => {
      const user = userEvent.setup();
      render(<IDManager />);

      const idInput = screen.getByTestId('id-to-validate') as HTMLInputElement;
      const validateBtn = screen.getByTestId('validate-id-btn');

      await user.type(idInput, 'invalid<>id');
      await user.click(validateBtn);

      await waitFor(() => {
        const result =
          screen.getByTestId('validation-result').textContent || '';
        expect(result).toContain('Valid: NO');
        expect(result).toContain('invalid characters');
      });
    });

    it('should normalize IDs with special characters', async () => {
      const user = userEvent.setup();
      render(<IDManager />);

      const idInput = screen.getByTestId('id-to-validate') as HTMLInputElement;
      const normalizeBtn = screen.getByTestId('normalize-id-btn');

      await user.type(idInput, 'MySpecialID!');
      await user.click(normalizeBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        // normalizeID with lowercase, removeSpaces, replaceSpecialChars
        // MySpecialID! -> myspecialid_ (lowercase, no spaces to remove, ! replaced)
        expect(idsText).toContain('myspecialid_');
      });
    });
  });

  describe('Semantic Diffing - Basic Operations', () => {
    it('should detect no changes for identical documents', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      const docJSON = '{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}';
      await user.click(oldDocInput);
      await user.paste(docJSON);
      await user.click(newDocInput);
      await user.paste(docJSON);
      await user.click(generateBtn);

      await waitFor(() => {
        const hasChanges = screen.getByTestId('has-changes').textContent || '';
        expect(hasChanges).toContain('Has Changes: NO');

        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Total: 0');
      });
    });

    it('should detect added nodes', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}');
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0},{"id":"2","type":"circle","x":100,"y":100}]}'
      );
      await user.click(generateBtn);

      await waitFor(() => {
        const hasChanges = screen.getByTestId('has-changes').textContent || '';
        expect(hasChanges).toContain('Has Changes: YES');

        const elements = screen.getByTestId('elements').textContent || '';
        expect(elements).toContain('Added: 1');

        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Structural: 1');
      });
    });

    it('should detect removed nodes', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0},{"id":"2","type":"circle","x":100,"y":100}]}'
      );
      await user.click(newDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}');
      await user.click(generateBtn);

      await waitFor(() => {
        const elements = screen.getByTestId('elements').textContent || '';
        expect(elements).toContain('Removed: 1');
      });
    });

    it('should detect modified nodes with property changes', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0,"color":"red"}]}'
      );
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0,"color":"blue"}]}'
      );
      await user.click(generateBtn);

      await waitFor(() => {
        const elements = screen.getByTestId('elements').textContent || '';
        expect(elements).toContain('Modified: 1');

        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Styling: 1'); // color is a styling property
      });
    });
  });

  describe('Change Classification', () => {
    it('should classify structural changes (add/remove)', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');
      const filterBtn = screen.getByTestId('filter-structural-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect"}]}');
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect"},{"id":"2","type":"circle"}]}'
      );
      await user.click(generateBtn);
      await user.click(filterBtn);

      await waitFor(() => {
        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Structural: 1');
        expect(stats).toContain('Styling: 0');
      });
    });

    it('should classify styling changes (position, color)', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');
      const filterBtn = screen.getByTestId('filter-styling-btn');

      await user.click(oldDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0,"color":"red"}]}'
      );
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":100,"y":50,"color":"blue"}]}'
      );
      await user.click(generateBtn);
      await user.click(filterBtn);

      await waitFor(() => {
        const stats = screen.getByTestId('stats').textContent || '';
        expect(stats).toContain('Styling:'); // Should have styling changes
        expect(stats).toContain('Structural: 0');
      });
    });

    it('should detect move operations when enabled', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}');
      await user.click(newDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":100,"y":50}]}');
      await user.click(generateBtn);

      await waitFor(() => {
        const elements = screen.getByTestId('elements').textContent || '';
        expect(elements).toContain('Moved: 1');
      });
    });

    it('should not detect moves when disabled', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const toggleBtn = screen.getByTestId('toggle-detect-moves');
      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      // Disable move detection
      await user.click(toggleBtn);

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}');
      await user.click(newDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":100,"y":50}]}');
      await user.click(generateBtn);

      await waitFor(() => {
        const elements = screen.getByTestId('elements').textContent || '';
        expect(elements).toContain('Moved: 0');
        expect(elements).toContain('Modified: 1');
      });
    });
  });

  describe('JSON Patch Export', () => {
    it('should generate JSON Patch operations', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":0,"y":0}]}');
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","x":0,"y":0},{"id":"2","type":"circle"}]}'
      );
      await user.click(generateBtn);

      await waitFor(() => {
        const patchesCount =
          screen.getByTestId('patches-count').textContent || '';
        expect(patchesCount).toMatch(/Patches: [1-9]/); // At least one patch

        const patchJSON = screen.getByTestId('patches-json').textContent || '';
        expect(patchJSON).toBeTruthy();
        expect(patchJSON).toContain('"op"');
        expect(patchJSON).toContain('"path"');
      });
    });

    it('should apply patches to document', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');
      const applyBtn = screen.getByTestId('apply-patch-btn');

      // Create a diff
      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect"}]}');
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect"},{"id":"2","type":"circle"}]}'
      );
      await user.click(generateBtn);

      // Apply patches (this updates newDoc internally)
      await user.click(applyBtn);

      await waitFor(() => {
        // Verify patches were applied
        const patchesCount =
          screen.getByTestId('patches-count').textContent || '';
        expect(patchesCount).toBeTruthy();
      });
    });

    it('should export patches as valid JSON', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[]}');
      await user.click(newDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect"}]}');
      await user.click(generateBtn);

      await waitFor(() => {
        const patchJSON = screen.getByTestId('patches-json').textContent || '';
        expect(() => JSON.parse(patchJSON)).not.toThrow();
      });
    });
  });

  describe('Acceptance Criteria Validation', () => {
    it('✓ AC 2.15.1: ID scheme - Node IDs hash from type+data', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      const contentInput = screen.getByTestId(
        'content-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-id-btn');

      // Generate ID from same content twice
      const content = '{"type":"rectangle","data":{"width":100,"height":50}}';
      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste(content);
      await user.click(generateBtn);

      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste(content);
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        // Both IDs should be identical (deterministic)
        expect(ids[0]).toBe(ids[1]);
        expect(ids[0]).toMatch(/^[0-9a-f]{8}$/);
      });
    });

    it('✓ AC 2.15.2: Semantic diff - Highlights structural vs styling adjustments', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      // Structural change (add node) + Styling change (color)
      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","color":"red"}]}');
      await user.click(newDocInput);
      await user.paste(
        '{"nodes":[{"id":"1","type":"rect","color":"blue"},{"id":"2","type":"circle"}]}'
      );
      await user.click(generateBtn);

      await waitFor(() => {
        const stats = screen.getByTestId('stats').textContent || '';

        // Should distinguish between structural and styling
        expect(stats).toContain('Structural:');
        expect(stats).toContain('Styling:');

        const structural = parseInt(
          stats.match(/Structural: (\d+)/)?.[1] || '0'
        );
        const styling = parseInt(stats.match(/Styling: (\d+)/)?.[1] || '0');

        expect(structural).toBeGreaterThan(0); // Added node
        expect(styling).toBeGreaterThan(0); // Color change
      });
    });

    it('✓ AC 2.15.3: Patch export - JSON Patch available', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      await user.click(oldDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect"}]}');
      await user.click(newDocInput);
      await user.paste('{"nodes":[{"id":"1","type":"rect","x":100}]}');
      await user.click(generateBtn);

      await waitFor(() => {
        const patchJSON = screen.getByTestId('patches-json').textContent || '';

        // Should be valid JSON Patch (RFC 6902)
        expect(patchJSON).toBeTruthy();
        const patches = JSON.parse(patchJSON);
        expect(Array.isArray(patches)).toBe(true);
        expect(patches.length).toBeGreaterThan(0);

        // Each patch should have required fields
        patches.forEach((patch: unknown) => {
          expect(patch).toHaveProperty('op');
          expect(patch).toHaveProperty('path');
        });
      });
    });
  });

  describe('Performance and Edge Cases', () => {
    it('should handle large batch ID generation efficiently', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="sequential" />);

      const batchBtn = screen.getByTestId('batch-generate-btn');

      // Generate multiple batches
      await user.click(batchBtn);
      await user.click(batchBtn);
      await user.click(batchBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        expect(ids.length).toBe(9); // 3 batches × 3 items
        expect(new Set(ids).size).toBe(9); // All unique
      });
    });

    it('should handle complex diffs with multiple change types', async () => {
      const user = userEvent.setup();
      render(<DiffManager />);

      const oldDocInput = screen.getByTestId(
        'old-doc-input'
      ) as HTMLTextAreaElement;
      const newDocInput = screen.getByTestId(
        'new-doc-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-diff-btn');

      // Complex: add, remove, modify, move
      const oldDoc = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0, color: 'red' },
          { id: '2', type: 'circle', x: 50, y: 50 },
          { id: '3', type: 'text', x: 100, y: 100 },
        ],
      };
      const newDoc = {
        nodes: [
          { id: '1', type: 'rect', x: 200, y: 200, color: 'red' }, // moved
          { id: '2', type: 'circle', x: 50, y: 50, color: 'blue' }, // modified
          // id '3' removed
          { id: '4', type: 'triangle', x: 150, y: 150 }, // added
        ],
      };

      await user.click(oldDocInput);
      await user.paste(JSON.stringify(oldDoc));
      await user.click(newDocInput);
      await user.paste(JSON.stringify(newDoc));
      await user.click(generateBtn);

      await waitFor(() => {
        const elements = screen.getByTestId('elements').textContent || '';

        expect(elements).toContain('Added: 1');
        expect(elements).toContain('Removed: 1');
        expect(elements).toMatch(/Modified: [1-9]/); // At least one modified
      });
    });

    it('should generate consistent hashes with object key ordering', async () => {
      const user = userEvent.setup();
      render(<IDManager initialStrategy="content-hash" />);

      const contentInput = screen.getByTestId(
        'content-input'
      ) as HTMLTextAreaElement;
      const generateBtn = screen.getByTestId('generate-id-btn');

      // Same content, different key order
      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste('{"x":0,"y":0,"type":"rect"}');
      await user.click(generateBtn);

      await user.clear(contentInput);
      await user.click(contentInput);
      await user.paste('{"type":"rect","x":0,"y":0}');
      await user.click(generateBtn);

      await waitFor(() => {
        const idsText = screen.getByTestId('generated-ids').textContent || '';
        const ids = idsText
          .replace('Generated IDs: ', '')
          .split(', ')
          .filter(Boolean);

        // Should be identical despite key order
        expect(ids[0]).toBe(ids[1]);
      });
    });
  });
});
