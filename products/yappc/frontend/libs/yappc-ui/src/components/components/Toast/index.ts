/**
 * Toast Notifications Module
 *
 * Production-grade toast notification system with Tailwind CSS styling.
 * Uses provider pattern with context-based hook API.
 *
 * @module ui/components/Toast
 * @doc.type module
 * @doc.purpose User feedback notifications
 * @doc.layer ui
 */

export { Toast, Snackbar, ToastProvider, useToast } from './Toast.tailwind';
export type {
  ToastProps,
  ToastSeverity,
  ToastPosition,
  ToastData,
} from './Toast.tailwind';

