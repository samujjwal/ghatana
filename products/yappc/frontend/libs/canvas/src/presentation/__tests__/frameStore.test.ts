import { describe, it, expect } from 'vitest';

import {
  createPresentationState,
  createFrame,
  getFrame,
  getAllFrames,
  updateFrame,
  deleteFrame,
  reorderFrames,
  startPresentation,
  endPresentation,
  nextFrame,
  previousFrame,
  jumpToFrame,
  applyNavigation,
  getCurrentFrame,
  canGoForward,
  canGoBackward,
  getFrameCount,
  getCurrentFrameNumber,
  togglePresenterMode,
  updateAudienceConfig,
  generateShareLink,
  sanitizeFrameForAudience,
  getAudienceFrames,
  duplicateFrame,
  searchFrames,
  getPresentationProgress,
  getPresentationStats,
  exportPresentation,
  importPresentation,
  type PresentationState,
  type Frame,
} from '../frameStore';

describe('frameStore', () => {
  describe('State Creation', () => {
    it('should create default presentation state', () => {
      const state = createPresentationState();
      
      expect(state.frames.size).toBe(0);
      expect(state.frameOrder).toEqual([]);
      expect(state.currentFrameIndex).toBe(-1);
      expect(state.isPresenting).toBe(false);
      expect(state.presenterMode).toBe(true);
      expect(state.audienceConfig.readOnly).toBe(true);
    });

    it('should create state with custom options', () => {
      const state = createPresentationState({
        presenterMode: false,
        audienceConfig: {
          showControls: true,
          allowNavigation: true,
        },
      });
      
      expect(state.presenterMode).toBe(false);
      expect(state.audienceConfig.showControls).toBe(true);
      expect(state.audienceConfig.allowNavigation).toBe(true);
    });
  });

  describe('Frame CRUD', () => {
    it('should create a frame', () => {
      const state = createPresentationState();
      const newState = createFrame(state, {
        id: 'frame1',
        name: 'Intro',
        viewport: { x: 0, y: 0, zoom: 1 },
      });
      
      expect(newState.frames.size).toBe(1);
      expect(newState.frameOrder).toEqual(['frame1']);
      expect(newState.frames.get('frame1')?.order).toBe(0);
    });

    it('should create multiple frames in order', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Intro',
        viewport: { x: 0, y: 0, zoom: 1 },
      });
      state = createFrame(state, {
        id: 'frame2',
        name: 'Content',
        viewport: { x: 100, y: 100, zoom: 1.5 },
      });
      
      expect(state.frames.size).toBe(2);
      expect(state.frameOrder).toEqual(['frame1', 'frame2']);
      expect(state.frames.get('frame1')?.order).toBe(0);
      expect(state.frames.get('frame2')?.order).toBe(1);
    });

    it('should get frame by ID', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Intro',
        viewport: { x: 0, y: 0, zoom: 1 },
      });
      
      const frame = getFrame(state, 'frame1');
      
      expect(frame).toBeDefined();
      expect(frame?.id).toBe('frame1');
      expect(frame?.name).toBe('Intro');
    });

    it('should return undefined for non-existent frame', () => {
      const state = createPresentationState();
      const frame = getFrame(state, 'nonexistent');
      
      expect(frame).toBeUndefined();
    });

    it('should get all frames in order', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const frames = getAllFrames(state);
      
      expect(frames).toHaveLength(3);
      expect(frames[0].name).toBe('A');
      expect(frames[1].name).toBe('B');
      expect(frames[2].name).toBe('C');
    });

    it('should update frame properties', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Original',
        viewport: { x: 0, y: 0, zoom: 1 },
      });
      
      const newState = updateFrame(state, 'frame1', {
        name: 'Updated',
        speakerNotes: 'New notes',
      });
      
      const frame = getFrame(newState, 'frame1');
      expect(frame?.name).toBe('Updated');
      expect(frame?.speakerNotes).toBe('New notes');
      expect(frame?.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
    });

    it('should not modify state when updating non-existent frame', () => {
      const state = createPresentationState();
      const newState = updateFrame(state, 'nonexistent', { name: 'Test' });
      
      expect(newState).toBe(state);
    });

    it('should delete frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const newState = deleteFrame(state, 'frame2');
      
      expect(newState.frames.size).toBe(2);
      expect(newState.frameOrder).toEqual(['frame1', 'frame3']);
      expect(getFrame(newState, 'frame1')?.order).toBe(0);
      expect(getFrame(newState, 'frame3')?.order).toBe(1);
    });

    it('should adjust current index when deleting frames', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 2);
      
      const newState = deleteFrame(state, 'frame3');
      
      expect(newState.currentFrameIndex).toBe(1);
    });
  });

  describe('Frame Reordering', () => {
    it('should reorder frames', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const newState = reorderFrames(state, ['frame3', 'frame1', 'frame2']);
      
      expect(newState.frameOrder).toEqual(['frame3', 'frame1', 'frame2']);
      expect(getFrame(newState, 'frame3')?.order).toBe(0);
      expect(getFrame(newState, 'frame1')?.order).toBe(1);
      expect(getFrame(newState, 'frame2')?.order).toBe(2);
    });

    it('should not reorder if invalid frame IDs provided', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const newState = reorderFrames(state, ['frame1', 'nonexistent']);
      
      expect(newState).toBe(state);
    });
  });

  describe('Presentation Control', () => {
    it('should start presentation', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const newState = startPresentation(state);
      
      expect(newState.isPresenting).toBe(true);
      expect(newState.currentFrameIndex).toBe(0);
    });

    it('should start presentation at specific index', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const newState = startPresentation(state, 1);
      
      expect(newState.currentFrameIndex).toBe(1);
    });

    it('should not start presentation if no frames', () => {
      const state = createPresentationState();
      const newState = startPresentation(state);
      
      expect(newState.isPresenting).toBe(false);
    });

    it('should end presentation', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const newState = endPresentation(state);
      
      expect(newState.isPresenting).toBe(false);
    });
  });

  describe('Frame Navigation', () => {
    it('should navigate to next frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = nextFrame(state);
      
      expect(result.success).toBe(true);
      expect(result.currentIndex).toBe(1);
      expect(result.frame?.id).toBe('frame2');
    });

    it('should not navigate past last frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = nextFrame(state);
      
      expect(result.success).toBe(false);
      expect(result.error).toBe('Already at last frame');
    });

    it('should not navigate if not presenting', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const result = nextFrame(state);
      
      expect(result.success).toBe(false);
      expect(result.error).toBe('Presentation not active');
    });

    it('should navigate to previous frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 1);
      
      const result = previousFrame(state);
      
      expect(result.success).toBe(true);
      expect(result.currentIndex).toBe(0);
      expect(result.frame?.id).toBe('frame1');
    });

    it('should not navigate before first frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = previousFrame(state);
      
      expect(result.success).toBe(false);
      expect(result.error).toBe('Already at first frame');
    });

    it('should jump to specific frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = jumpToFrame(state, 'frame3');
      
      expect(result.success).toBe(true);
      expect(result.currentIndex).toBe(2);
      expect(result.frame?.id).toBe('frame3');
    });

    it('should not jump to non-existent frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = jumpToFrame(state, 'nonexistent');
      
      expect(result.success).toBe(false);
      expect(result.error).toBe('Frame not found');
    });

    it('should apply successful navigation', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = nextFrame(state);
      const newState = applyNavigation(state, result);
      
      expect(newState.currentFrameIndex).toBe(1);
    });

    it('should not apply failed navigation', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      const result = nextFrame(state);
      const newState = applyNavigation(state, result);
      
      expect(newState.currentFrameIndex).toBe(0);
    });
  });

  describe('Frame Queries', () => {
    it('should get current frame', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 1);
      
      const current = getCurrentFrame(state);
      
      expect(current?.id).toBe('frame2');
    });

    it('should return undefined if no current frame', () => {
      const state = createPresentationState();
      const current = getCurrentFrame(state);
      
      expect(current).toBeUndefined();
    });

    it('should check if can go forward', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state);
      
      expect(canGoForward(state)).toBe(true);
      
      state = applyNavigation(state, nextFrame(state));
      expect(canGoForward(state)).toBe(false);
    });

    it('should check if can go backward', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 1);
      
      expect(canGoBackward(state)).toBe(true);
      
      state = applyNavigation(state, previousFrame(state));
      expect(canGoBackward(state)).toBe(false);
    });

    it('should get frame count', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      
      expect(getFrameCount(state)).toBe(3);
    });

    it('should get current frame number (1-indexed)', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'frame1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'frame2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 1);
      
      expect(getCurrentFrameNumber(state)).toBe(2);
    });
  });

  describe('Presenter/Audience Mode', () => {
    it('should toggle presenter mode', () => {
      let state = createPresentationState({ presenterMode: true });
      
      state = togglePresenterMode(state);
      expect(state.presenterMode).toBe(false);
      
      state = togglePresenterMode(state);
      expect(state.presenterMode).toBe(true);
    });

    it('should update audience configuration', () => {
      let state = createPresentationState();
      
      state = updateAudienceConfig(state, {
        showControls: true,
        allowNavigation: true,
      });
      
      expect(state.audienceConfig.showControls).toBe(true);
      expect(state.audienceConfig.allowNavigation).toBe(true);
      expect(state.audienceConfig.readOnly).toBe(true);
    });

    it('should generate share link', () => {
      const state = createPresentationState();
      const link = generateShareLink(state, 'https://example.com');
      
      expect(link).toMatch(/^https:\/\/example\.com\/presentation\/[a-z0-9]+$/);
    });

    it('should sanitize frame for audience', () => {
      const frame: Frame = {
        id: 'frame1',
        name: 'Test',
        order: 0,
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Secret notes',
      };
      
      const sanitized = sanitizeFrameForAudience(frame);
      
      expect(sanitized.id).toBe('frame1');
      expect(sanitized.name).toBe('Test');
      expect('speakerNotes' in sanitized).toBe(false);
    });

    it('should get audience frames without speaker notes', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'A',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Notes 1',
      });
      state = createFrame(state, {
        id: 'frame2',
        name: 'B',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Notes 2',
      });
      
      const audienceFrames = getAudienceFrames(state);
      
      expect(audienceFrames).toHaveLength(2);
      expect('speakerNotes' in audienceFrames[0]).toBe(false);
      expect('speakerNotes' in audienceFrames[1]).toBe(false);
    });
  });

  describe('Frame Utilities', () => {
    it('should duplicate frame', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Original',
        viewport: { x: 100, y: 100, zoom: 2 },
        speakerNotes: 'Notes',
      });
      
      const newState = duplicateFrame(state, 'frame1', 'Copy');
      
      expect(newState.frames.size).toBe(2);
      expect(newState.frameOrder).toHaveLength(2);
      
      const copy = getAllFrames(newState)[1];
      expect(copy.name).toBe('Copy');
      expect(copy.viewport).toEqual({ x: 100, y: 100, zoom: 2 });
      expect(copy.speakerNotes).toBe('Notes');
    });

    it('should generate default name for duplicate', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Original',
        viewport: { x: 0, y: 0, zoom: 1 },
      });
      
      const newState = duplicateFrame(state, 'frame1');
      const copy = getAllFrames(newState)[1];
      
      expect(copy.name).toBe('Original (Copy)');
    });

    it('should search frames by name', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'f1', name: 'Introduction', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'f2', name: 'Main Content', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'f3', name: 'Conclusion', viewport: { x: 0, y: 0, zoom: 1 } });
      
      const results = searchFrames(state, 'content');
      
      expect(results).toHaveLength(1);
      expect(results[0].name).toBe('Main Content');
    });

    it('should search frames by speaker notes', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'f1',
        name: 'A',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Important point here',
      });
      state = createFrame(state, {
        id: 'f2',
        name: 'B',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Other notes',
      });
      
      const results = searchFrames(state, 'important');
      
      expect(results).toHaveLength(1);
      expect(results[0].id).toBe('f1');
    });

    it('should calculate presentation progress', () => {
      let state = createPresentationState();
      state = createFrame(state, { id: 'f1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'f2', name: 'B', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'f3', name: 'C', viewport: { x: 0, y: 0, zoom: 1 } });
      state = createFrame(state, { id: 'f4', name: 'D', viewport: { x: 0, y: 0, zoom: 1 } });
      state = startPresentation(state, 1);
      
      const progress = getPresentationProgress(state);
      
      expect(progress).toBe(0.5); // 2/4
    });

    it('should return 0 progress for empty presentation', () => {
      const state = createPresentationState();
      const progress = getPresentationProgress(state);
      
      expect(progress).toBe(0);
    });
  });

  describe('Statistics', () => {
    it('should get presentation statistics', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'f1',
        name: 'A',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Notes',
        duration: 5000,
      });
      state = createFrame(state, {
        id: 'f2',
        name: 'B',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'More notes',
      });
      state = createFrame(state, {
        id: 'f3',
        name: 'C',
        viewport: { x: 0, y: 0, zoom: 1 },
        duration: 3000,
      });
      state = startPresentation(state, 1);
      
      const stats = getPresentationStats(state);
      
      expect(stats.totalFrames).toBe(3);
      expect(stats.currentFrame).toBe(2);
      expect(stats.progress).toBe(2 / 3);
      expect(stats.framesWithNotes).toBe(2);
      expect(stats.framesWithDuration).toBe(2);
      expect(stats.estimatedDuration).toBe(8000);
    });
  });

  describe('Export/Import', () => {
    it('should export presentation to JSON', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Intro',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Welcome',
      });
      state = updateAudienceConfig(state, { showControls: true });
      
      const json = exportPresentation(state);
      const parsed = JSON.parse(json);
      
      expect(parsed.frames).toHaveLength(1);
      expect(parsed.frames[0].name).toBe('Intro');
      expect(parsed.audienceConfig.showControls).toBe(true);
    });

    it('should import presentation from JSON', () => {
      const json = JSON.stringify({
        frames: [
          { id: 'f1', name: 'A', viewport: { x: 0, y: 0, zoom: 1 } },
          { id: 'f2', name: 'B', viewport: { x: 100, y: 100, zoom: 1.5 }, speakerNotes: 'Notes' },
        ],
        audienceConfig: { showControls: true },
        metadata: { author: 'Test' },
      });
      
      const state = importPresentation(json);
      
      expect(getFrameCount(state)).toBe(2);
      expect(getFrame(state, 'f1')?.name).toBe('A');
      expect(getFrame(state, 'f2')?.speakerNotes).toBe('Notes');
      expect(state.audienceConfig.showControls).toBe(true);
      expect(state.metadata?.author).toBe('Test');
    });

    it('should throw error for invalid JSON', () => {
      expect(() => importPresentation('invalid json')).toThrow('Failed to import');
    });

    it('should handle empty frames array', () => {
      const json = JSON.stringify({ frames: [] });
      const state = importPresentation(json);
      
      expect(getFrameCount(state)).toBe(0);
    });
  });

  describe('Speaker Notes', () => {
    it('should store speaker notes', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Test',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Remember to mention the key points',
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.speakerNotes).toBe('Remember to mention the key points');
    });

    it('should update speaker notes', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Test',
        viewport: { x: 0, y: 0, zoom: 1 },
        speakerNotes: 'Original notes',
      });
      
      state = updateFrame(state, 'frame1', {
        speakerNotes: 'Updated notes',
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.speakerNotes).toBe('Updated notes');
    });
  });

  describe('Element Visibility', () => {
    it('should set visible elements for frame', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Filtered View',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleElements: ['elem1', 'elem2', 'elem3'],
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.visibleElements).toEqual(['elem1', 'elem2', 'elem3']);
    });

    it('should set highlighted elements for frame', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'With Highlights',
        viewport: { x: 0, y: 0, zoom: 1 },
        highlightedElements: ['important1', 'important2'],
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.highlightedElements).toEqual(['important1', 'important2']);
    });
  });

  describe('Transitions', () => {
    it('should set transition type for frame', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'With Transition',
        viewport: { x: 0, y: 0, zoom: 1 },
        transition: 'fade',
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.transition).toBe('fade');
    });

    it('should set auto-advance duration', () => {
      let state = createPresentationState();
      state = createFrame(state, {
        id: 'frame1',
        name: 'Auto Advance',
        viewport: { x: 0, y: 0, zoom: 1 },
        duration: 5000,
      });
      
      const frame = getFrame(state, 'frame1');
      expect(frame?.duration).toBe(5000);
    });
  });
});
