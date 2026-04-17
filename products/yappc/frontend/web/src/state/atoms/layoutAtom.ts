import { atom } from 'jotai';
import type { ReactNode } from 'react';

/**
 * Action type for header
 */
export interface HeaderAction {
  id: string;
  label: string;
  icon: unknown;
  onClick: () => void;
  disabled?: boolean;
  tooltip?: string;
  badge?: number;
  shortcut?: string;
  divider?: boolean;
}

/**
 * Phase info for header
 */
export interface HeaderPhaseInfo {
  phase: string;
  label: string;
  progress?: number;
  status?: 'active' | 'completed' | 'pending';
}

/**
 * Canvas mode type
 */
export type HeaderCanvasMode =
  | 'design'
  | 'architecture'
  | 'code'
  | 'deploy'
  | 'plan';

/**
 * Role info for header
 */
export interface HeaderRoleInfo {
  role: string;
  label: string;
  icon: ReactNode;
  color?: string;
}

/**
 * Atom to control the visibility of the global application header.
 * Defaults to true. Routes can set this to false to take full control of the layout.
 */
export const headerVisibleAtom = atom(true);

/**
 * Global Header State Atoms
 */
export const headerContextActionsAtom = atom<HeaderAction[]>([]);
export const headerActionContextAtom = atom<'global' | 'project' | 'canvas'>(
  'global'
);
export const headerPhaseInfoAtom = atom<HeaderPhaseInfo | undefined>(undefined);
export const headerRoleInfoAtom = atom<HeaderRoleInfo | undefined>(undefined);
export const headerCanvasModeAtom = atom<HeaderCanvasMode | undefined>(
  undefined
);
export const headerShowCanvasModeAtom = atom<boolean>(false);
export const headerOnCanvasModeChangeAtom = atom<
  ((mode: unknown) => void) | undefined
>(undefined);
export const headerNotificationCountAtom = atom<number>(0);
export const headerShowAgentActivityAtom = atom<boolean>(true);
