/**
 * Shared UI hooks exposed by @ghatana/ui
 */

export { useControllableState } from './useControllableState';
export { useDisclosure } from './useDisclosure';
export { useDialog } from './useDialog';
export type { UseDialogOptions, UseDialogReturn } from './useDialog';
export { useFocusRing } from './useFocusRing';
export { useFocusTrap, FOCUSABLE_SELECTOR, getFocusableElements } from './useFocusTrap';
export type { UseFocusTrapOptions } from './useFocusTrap';
export { useId } from './useId';
export { usePrefersReducedMotion } from './usePrefersReducedMotion';

// Keyboard navigation hooks
export {
  useKeyboardNavigation,
  useFocusRestore,
  useFocusVisible,
} from './useKeyboardNavigation';
export type { UseKeyboardNavigationOptions } from './useKeyboardNavigation';

// Motion preference hooks
export {
  useReducedMotion,
  useAnimationClass,
  useSafeMotion,
} from './useReducedMotion';

// Swipe gesture hooks
export {
  useSwipeGesture,
  useHorizontalSwipe,
  useVerticalSwipe,
} from './useSwipeGesture';
export type {
  SwipeDirection,
  SwipeGestureOptions,
  SwipeCallbacks,
  SwipeState,
} from './useSwipeGesture';

// Form validation hooks
export {
  useFormValidation,
  validators,
} from './useFormValidation';
export type {
  ValidationRule,
  FieldValidation,
  FormValidationSchema,
  FieldState,
  FormState,
  UseFormValidationOptions,
  UseFormValidationResult,
} from './useFormValidation';

// Image optimization hooks
export {
  useImageOptimization,
} from './useImageOptimization';
export type {
  UseImageOptimizationProps,
  UseImageOptimizationResult,
} from './useImageOptimization';

// Optimistic UI hooks
export {
  useOptimisticUpdate,
  useOptimisticList,
} from './useOptimisticUpdate';
export type {
  OptimisticUpdateOptions,
  OptimisticUpdateResult,
  OptimisticListOptions,
  OptimisticListResult,
} from './useOptimisticUpdate';

// Accessible ID hook
export { useAccessibleId } from './useAccessibleId';

// Media query hook (migrated from @ghatana/design-system)
export { useMediaQuery } from './useMediaQuery';
export type { Breakpoint } from './useMediaQuery';

