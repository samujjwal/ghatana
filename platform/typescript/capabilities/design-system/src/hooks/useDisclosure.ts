import * as React from 'react';
import { useControllableState } from './useControllableState';

/**
 * Options for the disclosure hook
 */
export interface UseDisclosureOptions {
  /**
   * Controlled open state
   */
  isOpen?: boolean;

  /**
   * Default open state for uncontrolled usage
   */
  defaultOpen?: boolean;

  /**
   * Callback fired whenever open state changes
   */
  onOpenChange?: (isOpen: boolean) => void;
}

/**
 * Hooks returned by useDisclosure
 */
export interface UseDisclosureResult {
  /**
   * Current open state
   */
  isOpen: boolean;

  /**
   * Opens the disclosure
   */
  onOpen: () => void;

  /**
   * Closes the disclosure
   */
  onClose: () => void;

  /**
   * Toggles the disclosure
   */
  onToggle: () => void;

  /**
   * Sets the disclosure state directly
   */
  setOpen: (next: boolean) => void;
}

/**
 * useDisclosure
 *
 * Manages open/close state for components like Dialog, Popover, Tooltip, etc.
 * Supports controlled and uncontrolled usage.
 */
export function useDisclosure(options: UseDisclosureOptions = {}): UseDisclosureResult {
  const { isOpen, defaultOpen = false, onOpenChange } = options;

  const [open, setOpen] = useControllableState<boolean>({
    value: isOpen,
    defaultValue: defaultOpen,
    onChange: onOpenChange,
  });

  const handleOpen = React.useCallback(() => setOpen(true), [setOpen]);
  const handleClose = React.useCallback(() => setOpen(false), [setOpen]);
  const handleToggle = React.useCallback(() => setOpen((prev) => !prev), [setOpen]);

  return {
    isOpen: open,
    onOpen: handleOpen,
    onClose: handleClose,
    onToggle: handleToggle,
    setOpen,
  };
}
