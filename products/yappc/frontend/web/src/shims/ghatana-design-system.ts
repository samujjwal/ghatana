/**
 * Ghatana Design System Shim
 * Re-exports MUI components as Ghatana Design System components
 */

import { createElement, forwardRef } from 'react';
import type { ReactNode, TextareaHTMLAttributes } from 'react';

export {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  AlertTitle,
  Avatar,
  AvatarGroup,
  Badge,
  BottomNavigation,
  BottomNavigationAction,
  Box,
  Breadcrumbs,
  Button,
  ButtonGroup,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  CardMedia,
  Checkbox,
  Chip,
  Collapse,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  Fab,
  Fade,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  Input,
  InputAdornment,
  InputLabel,
  List as InteractiveList,
  LinearProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemButton,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  Menu,
  MenuItem,
  Modal,
  Paper,
  Paper as Surface,
  Popper,
  Radio,
  Select,
  Slide,
  Slider,
  CircularProgress as Spinner,
  Snackbar,
  Snackbar as Toast,
  Breadcrumbs as Breadcrumb,
  LinearProgress as Progress,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  Stack,
  Step,
  StepLabel,
  Stepper,
  SwipeableDrawer,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
export {
  ThemeProvider,
  createTheme,
  styled,
  alpha,
  useTheme,
} from '@mui/material/styles';

// Stub implementations for design-system specific components
export const OperationStatus = () => null;
export const createOperationStatusFromContract = () => null;
export const AILabel = () => null;
export const AILabelOverlay = () => null;
export const ConfidenceBadge = () => null;
export const getConfidenceBand = () => 'medium';
export const ReviewRequiredBanner = () => null;
export const ToastProvider = ({ children }: { children: ReactNode }) => children;
export const useToast = () => ({ show: () => {}, hide: () => {} });

// Utility
export const cn = (...classes: (string | undefined | false)[]) => classes.filter(Boolean).join(' ');

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) =>
    createElement('textarea', {
      ...props,
      ref,
      className: cn(
        'rounded-md border border-border bg-bg-paper px-3 py-2 text-sm text-text-primary outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:opacity-60',
        className,
      ),
    }),
);
TextArea.displayName = 'TextArea';

export type DesignSystemChildren = ReactNode;
export type OperationState = 'idle' | 'loading' | 'success' | 'error';
export type OperationStatusProps = { state: OperationState };
export type AILabelProps = { label: string };
export type AILabelOverlayProps = { children: ReactNode };
export type ConfidenceBadgeProps = { confidence: number };
export type ConfidenceBand = 'low' | 'medium' | 'high';
export type ReviewRequiredBannerProps = { title?: string };
export type ToastData = { id: string; message: string };
export type ToastSeverity = 'info' | 'success' | 'warning' | 'error';
export type ToastPosition = 'top' | 'bottom' | 'top-right' | 'bottom-right';
export type ToastProps = { open: boolean; message: string };
