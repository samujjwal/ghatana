/**
 * Flashit Shared Library
 * Shared types, utilities, and API client for all Flashit applications
 *
 * @doc.type library
 * @doc.purpose Provide shared types, state, hooks, and API client for Flashit applications
 * @doc.layer product
 * @doc.pattern SharedLibrary
 */

// Export types
export * from './types';

// Export validation schemas
export * from './validation';

// Export API client
export * from './api/client';

// Export utilities
export * from './utils';

// Export Jotai atoms (state management)
export * from './atoms';
export { createFlashitAtoms } from './atoms';

// Export hooks
export * from './hooks/media';
export * from './hooks/media-playback';
export * from './hooks/device-capabilities';

// Constants
export const EMOTION_OPTIONS = [
  'happy', 'sad', 'excited', 'anxious', 'calm', 'frustrated',
  'proud', 'grateful', 'angry', 'peaceful', 'energized', 'tired'
] as const;

export const TAG_SUGGESTIONS = [
  'work', 'personal', 'family', 'health', 'learning', 'creative',
  'social', 'reflection', 'goal', 'challenge', 'win', 'idea'
] as const;

export type EmotionOption = typeof EMOTION_OPTIONS[number];
export type TagSuggestion = typeof TAG_SUGGESTIONS[number];
