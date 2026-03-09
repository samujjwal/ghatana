/**
 * Frame Store - Presentation Mode with Frame Sequencing
 * 
 * Provides presentation capabilities:
 * - Frame-based slide sequencing
 * - Speaker notes (presenter-only)
 * - Presenter vs audience view separation
 * - Navigation controls (next/previous/jump)
 * - Presentation state management
 * - Transitions and animations
 * 
 * @module frameStore
 */

/**
 * Frame represents a single slide/view in presentation
 */
export interface Frame {
  id: string;
  name: string;
  order: number;
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  visibleElements?: string[]; // Element IDs to show (empty = all)
  highlightedElements?: string[]; // Element IDs to emphasize
  speakerNotes?: string;
  duration?: number; // Auto-advance after N milliseconds
  transition?: TransitionType;
  metadata?: Record<string, unknown>;
}

/**
 * Transition types between frames
 */
export type TransitionType = 
  | 'none'
  | 'fade'
  | 'slide'
  | 'zoom'
  | 'crossfade';

/**
 * Presentation state
 */
export interface PresentationState {
  frames: Map<string, Frame>;
  frameOrder: string[]; // Ordered list of frame IDs
  currentFrameIndex: number;
  isPresenting: boolean;
  presenterMode: boolean; // True = presenter view, False = audience view
  audienceConfig: AudienceConfig;
  metadata?: Record<string, unknown>;
}

/**
 * Audience view configuration
 */
export interface AudienceConfig {
  readOnly: boolean;
  showControls: boolean;
  allowNavigation: boolean;
  shareLink?: string;
}

/**
 * Frame navigation result
 */
export interface NavigationResult {
  success: boolean;
  previousIndex: number;
  currentIndex: number;
  frame?: Frame;
  error?: string;
}

/**
 * Create initial presentation state
 */
export function createPresentationState(
  options: {
    presenterMode?: boolean;
    audienceConfig?: Partial<AudienceConfig>;
  } = {}
): PresentationState {
  return {
    frames: new Map(),
    frameOrder: [],
    currentFrameIndex: -1,
    isPresenting: false,
    presenterMode: options.presenterMode ?? true,
    audienceConfig: {
      readOnly: true,
      showControls: false,
      allowNavigation: false,
      ...options.audienceConfig,
    },
  };
}

/**
 * Create a new frame
 */
export function createFrame(
  state: PresentationState,
  frame: Omit<Frame, 'order'>
): PresentationState {
  const order = state.frameOrder.length;
  const newFrame: Frame = { ...frame, order };
  
  return {
    ...state,
    frames: new Map([...state.frames, [frame.id, newFrame]]),
    frameOrder: [...state.frameOrder, frame.id],
  };
}

/**
 * Get frame by ID
 */
export function getFrame(
  state: PresentationState,
  frameId: string
): Frame | undefined {
  return state.frames.get(frameId);
}

/**
 * Get all frames in order
 */
export function getAllFrames(state: PresentationState): Frame[] {
  return state.frameOrder
    .map(id => state.frames.get(id))
    .filter((f): f is Frame => f !== undefined);
}

/**
 * Update frame
 */
export function updateFrame(
  state: PresentationState,
  frameId: string,
  updates: Partial<Omit<Frame, 'id' | 'order'>>
): PresentationState {
  const frame = state.frames.get(frameId);
  if (!frame) {
    return state;
  }
  
  const updatedFrame = { ...frame, ...updates };
  return {
    ...state,
    frames: new Map([...state.frames, [frameId, updatedFrame]]),
  };
}

/**
 * Delete frame
 */
export function deleteFrame(
  state: PresentationState,
  frameId: string
): PresentationState {
  const newFrames = new Map(state.frames);
  newFrames.delete(frameId);
  
  const newOrder = state.frameOrder.filter(id => id !== frameId);
  
  // Recompute orders
  const reorderedFrames = new Map<string, Frame>();
  newOrder.forEach((id, index) => {
    const frame = newFrames.get(id);
    if (frame) {
      reorderedFrames.set(id, { ...frame, order: index });
    }
  });
  
  return {
    ...state,
    frames: reorderedFrames,
    frameOrder: newOrder,
    currentFrameIndex: Math.min(state.currentFrameIndex, newOrder.length - 1),
  };
}

/**
 * Reorder frames
 */
export function reorderFrames(
  state: PresentationState,
  newOrder: string[]
): PresentationState {
  // Validate all frame IDs exist
  if (newOrder.some(id => !state.frames.has(id))) {
    return state;
  }
  
  // Update order properties
  const reorderedFrames = new Map<string, Frame>();
  newOrder.forEach((id, index) => {
    const frame = state.frames.get(id)!;
    reorderedFrames.set(id, { ...frame, order: index });
  });
  
  return {
    ...state,
    frames: reorderedFrames,
    frameOrder: newOrder,
  };
}

/**
 * Start presentation
 */
export function startPresentation(
  state: PresentationState,
  startIndex: number = 0
): PresentationState {
  if (state.frameOrder.length === 0) {
    return state;
  }
  
  return {
    ...state,
    isPresenting: true,
    currentFrameIndex: Math.max(0, Math.min(startIndex, state.frameOrder.length - 1)),
  };
}

/**
 * End presentation
 */
export function endPresentation(state: PresentationState): PresentationState {
  return {
    ...state,
    isPresenting: false,
  };
}

/**
 * Navigate to next frame
 */
export function nextFrame(state: PresentationState): NavigationResult {
  if (!state.isPresenting) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Presentation not active',
    };
  }
  
  if (state.currentFrameIndex >= state.frameOrder.length - 1) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Already at last frame',
    };
  }
  
  const newIndex = state.currentFrameIndex + 1;
  const frameId = state.frameOrder[newIndex];
  const frame = state.frames.get(frameId);
  
  return {
    success: true,
    previousIndex: state.currentFrameIndex,
    currentIndex: newIndex,
    frame,
  };
}

/**
 * Navigate to previous frame
 */
export function previousFrame(state: PresentationState): NavigationResult {
  if (!state.isPresenting) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Presentation not active',
    };
  }
  
  if (state.currentFrameIndex <= 0) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Already at first frame',
    };
  }
  
  const newIndex = state.currentFrameIndex - 1;
  const frameId = state.frameOrder[newIndex];
  const frame = state.frames.get(frameId);
  
  return {
    success: true,
    previousIndex: state.currentFrameIndex,
    currentIndex: newIndex,
    frame,
  };
}

/**
 * Jump to specific frame
 */
export function jumpToFrame(
  state: PresentationState,
  frameId: string
): NavigationResult {
  if (!state.isPresenting) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Presentation not active',
    };
  }
  
  const index = state.frameOrder.indexOf(frameId);
  if (index === -1) {
    return {
      success: false,
      previousIndex: state.currentFrameIndex,
      currentIndex: state.currentFrameIndex,
      error: 'Frame not found',
    };
  }
  
  const frame = state.frames.get(frameId);
  
  return {
    success: true,
    previousIndex: state.currentFrameIndex,
    currentIndex: index,
    frame,
  };
}

/**
 * Apply navigation result to state (immutable update)
 */
export function applyNavigation(
  state: PresentationState,
  result: NavigationResult
): PresentationState {
  if (!result.success) {
    return state;
  }
  
  return {
    ...state,
    currentFrameIndex: result.currentIndex,
  };
}

/**
 * Get current frame
 */
export function getCurrentFrame(state: PresentationState): Frame | undefined {
  if (state.currentFrameIndex < 0 || state.currentFrameIndex >= state.frameOrder.length) {
    return undefined;
  }
  
  const frameId = state.frameOrder[state.currentFrameIndex];
  return state.frames.get(frameId);
}

/**
 * Check if can navigate forward
 */
export function canGoForward(state: PresentationState): boolean {
  return (
    state.isPresenting &&
    state.currentFrameIndex < state.frameOrder.length - 1
  );
}

/**
 * Check if can navigate backward
 */
export function canGoBackward(state: PresentationState): boolean {
  return state.isPresenting && state.currentFrameIndex > 0;
}

/**
 * Get frame count
 */
export function getFrameCount(state: PresentationState): number {
  return state.frameOrder.length;
}

/**
 * Get current frame number (1-indexed)
 */
export function getCurrentFrameNumber(state: PresentationState): number {
  return state.currentFrameIndex + 1;
}

/**
 * Toggle presenter mode
 */
export function togglePresenterMode(state: PresentationState): PresentationState {
  return {
    ...state,
    presenterMode: !state.presenterMode,
  };
}

/**
 * Update audience configuration
 */
export function updateAudienceConfig(
  state: PresentationState,
  config: Partial<AudienceConfig>
): PresentationState {
  return {
    ...state,
    audienceConfig: {
      ...state.audienceConfig,
      ...config,
    },
  };
}

/**
 * Generate share link for audience view
 */
export function generateShareLink(
  state: PresentationState,
  baseUrl: string = window?.location?.origin || 'https://example.com'
): string {
  const shareId = Math.random().toString(36).substring(2, 10);
  return `${baseUrl}/presentation/${shareId}`;
}

/**
 * Filter speaker notes from frame (for audience view)
 */
export function sanitizeFrameForAudience(frame: Frame): Omit<Frame, 'speakerNotes'> {
  const { speakerNotes, ...audienceFrame } = frame;
  return audienceFrame;
}

/**
 * Get frames for audience (without speaker notes)
 */
export function getAudienceFrames(state: PresentationState): Array<Omit<Frame, 'speakerNotes'>> {
  return getAllFrames(state).map(sanitizeFrameForAudience);
}

/**
 * Duplicate frame
 */
export function duplicateFrame(
  state: PresentationState,
  frameId: string,
  newName?: string
): PresentationState {
  const frame = state.frames.get(frameId);
  if (!frame) {
    return state;
  }
  
  const newFrameId = `${frameId}_copy_${Date.now()}`;
  const newFrame: Frame = {
    ...frame,
    id: newFrameId,
    name: newName || `${frame.name} (Copy)`,
    order: state.frameOrder.length,
  };
  
  return {
    ...state,
    frames: new Map([...state.frames, [newFrameId, newFrame]]),
    frameOrder: [...state.frameOrder, newFrameId],
  };
}

/**
 * Search frames by name or speaker notes
 */
export function searchFrames(
  state: PresentationState,
  query: string
): Frame[] {
  const lowerQuery = query.toLowerCase();
  return getAllFrames(state).filter(frame =>
    frame.name.toLowerCase().includes(lowerQuery) ||
    frame.speakerNotes?.toLowerCase().includes(lowerQuery)
  );
}

/**
 * Get presentation progress (0-1)
 */
export function getPresentationProgress(state: PresentationState): number {
  if (state.frameOrder.length === 0) {
    return 0;
  }
  return (state.currentFrameIndex + 1) / state.frameOrder.length;
}

/**
 * Get presentation statistics
 */
export function getPresentationStats(state: PresentationState): {
  totalFrames: number;
  currentFrame: number;
  progress: number;
  framesWithNotes: number;
  framesWithDuration: number;
  estimatedDuration: number; // Total ms if all durations set
} {
  const frames = getAllFrames(state);
  const framesWithNotes = frames.filter(f => f.speakerNotes).length;
  const framesWithDuration = frames.filter(f => f.duration).length;
  const estimatedDuration = frames.reduce((sum, f) => sum + (f.duration || 0), 0);
  
  return {
    totalFrames: frames.length,
    currentFrame: state.currentFrameIndex + 1,
    progress: getPresentationProgress(state),
    framesWithNotes,
    framesWithDuration,
    estimatedDuration,
  };
}

/**
 * Export presentation as JSON
 */
export function exportPresentation(state: PresentationState): string {
  const exportData = {
    frames: getAllFrames(state),
    audienceConfig: state.audienceConfig,
    metadata: state.metadata,
  };
  return JSON.stringify(exportData, null, 2);
}

/**
 * Import presentation from JSON
 */
export function importPresentation(
  json: string,
  options: { presenterMode?: boolean } = {}
): PresentationState {
  try {
    const data = JSON.parse(json);
    const state = createPresentationState(options);
    
    let newState = state;
    for (const frame of data.frames || []) {
      newState = createFrame(newState, frame);
    }
    
    if (data.audienceConfig) {
      newState = updateAudienceConfig(newState, data.audienceConfig);
    }
    
    if (data.metadata) {
      newState = { ...newState, metadata: data.metadata };
    }
    
    return newState;
  } catch (error) {
    throw new Error(`Failed to import presentation: ${(error as Error).message}`);
  }
}
