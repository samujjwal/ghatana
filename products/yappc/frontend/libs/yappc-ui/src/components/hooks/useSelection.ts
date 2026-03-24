/**
 * Selection Management Hook (re-export wrapper)
 * Preserves backward-compatible import paths by re-exporting from modularized folder.
 */

export { useSelection } from './useSelection/index';

export type {
  UseSelectionReturn,
  SelectionItem,
  SelectionRange,
  SelectionAction,
  UseSelectionOptions,
} from './useSelection/types';
