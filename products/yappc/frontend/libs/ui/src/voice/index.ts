/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Voice Components Export
 */

// Voice command handling
export {
  useVoiceCommands,
  VOICE_COMMAND_HELP,
} from './useVoiceCommands';

// Voice UI
export { VoiceOverlay, voiceOverlayStyles } from './VoiceOverlay';

// Intent processing
export {
  defaultVoiceActions,
  executeVoiceCommand,
} from './voiceIntents';

// Types from voice commands
export type {
  VoiceIntent,
  VoiceCommand,
  VoiceHandlerConfig,
} from './useVoiceCommands';

// Types from voice intents
export type {
  VoiceActionResult,
  VoiceActionHandler,
  VoiceActionContext,
} from './voiceIntents';

// Combined styles
export const voiceStyles = `
${voiceOverlayStyles}
`;
