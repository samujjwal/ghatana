/**
 * @audio-video/ui
 *
 * Shared UI components and hooks for the audio-video application.
 *
 * Base UI primitives (Button, Input, Card, etc.) are re-exported from
 * @ghatana/design-system — the canonical platform design system.
 *
 * Audio-video-specific hooks (speech synthesis, speech recognition, memory pressure)
 * are implemented here and have no platform equivalent.
 *
 * @doc.type component
 * @doc.purpose Shared UI components for audio-video application
 * @doc.layer shared
 * @doc.pattern component library
 */

// =============================================================================
// Platform design system — canonical source for all base UI primitives
// =============================================================================
export {
  Button,
  Input,
  Card,
  Modal,
  Tabs,
  Spinner,
  Badge,
  Checkbox,
  Select,
  Tooltip,
} from '@ghatana/design-system';

export type {
  ButtonProps,
  CardProps,
  ModalProps,
  TabsProps,
  SpinnerProps,
  BadgeProps,
} from '@ghatana/design-system';

// Re-export theme tokens for audio-video theming (tokens takes precedence to avoid validateTokens conflict)
export * from '@ghatana/tokens';

// =============================================================================
// Audio-video-specific types (no platform equivalent)
// =============================================================================

import React from 'react';

/**
 * Base props shared by all @audio-video/ui components.
 * Kept for backward compatibility with existing audio-video consumers.
 */
export interface BaseComponentProps {
  className?: string;
  children?: React.ReactNode;
  testId?: string;
}

// =============================================================================
// Audio-video-specific hooks — speech synthesis, recognition, memory pressure
// =============================================================================
export {
  useSpeechSynthesis,
  useSpeechRecognition,
} from './hooks';
export type {
  UseSpeechSynthesisResult,
  SpeechSynthesisOptions,
  UseSpeechRecognitionResult,
  SpeechRecognitionOptions,
  SpeechRecognitionCallbacks,
} from './hooks';


