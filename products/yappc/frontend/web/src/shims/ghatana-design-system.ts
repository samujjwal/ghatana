import type { ReactNode } from 'react';

export * from '@mui/material';
export {
  List as InteractiveList,
  Paper,
  Paper as Surface,
  CircularProgress as Spinner,
  Snackbar as Toast,
  Breadcrumbs as Breadcrumb,
  LinearProgress as Progress,
} from '@mui/material';
export {
  ThemeProvider,
  createTheme,
  styled,
  alpha,
  useTheme,
} from '@mui/material/styles';

export { cn } from '@ghatana/platform-utils';
export {
  ToastProvider,
  useToast,
} from '../../../libs/yappc-ui/src/components/components/Toast';
export type {
  ToastData,
  ToastSeverity,
  ToastPosition,
  ToastProps,
} from '../../../libs/yappc-ui/src/components/components/Toast';

export type DesignSystemChildren = ReactNode;