import type { ReactNode } from 'react';

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

export { cn } from '@ghatana/platform-utils';

// AI visibility primitives from platform design-system
export {
  OperationStatus,
  createOperationStatusFromContract,
} from '../../../../../platform/typescript/design-system/src/atoms/OperationStatus';
export type {
  OperationState,
  OperationStatusProps,
} from '../../../../../platform/typescript/design-system/src/atoms/OperationStatus';
export {
  AILabel,
  AILabelOverlay,
} from '../../../../../platform/typescript/design-system/src/atoms/AILabel';
export type {
  AILabelProps,
  AILabelOverlayProps,
} from '../../../../../platform/typescript/design-system/src/atoms/AILabel';
export {
  ConfidenceBadge,
  getConfidenceBand,
} from '../../../../../platform/typescript/design-system/src/atoms/ConfidenceBadge';
export type {
  ConfidenceBadgeProps,
  ConfidenceBand,
} from '../../../../../platform/typescript/design-system/src/atoms/ConfidenceBadge';
export {
  ReviewRequiredBanner,
} from '../../../../../platform/typescript/design-system/src/molecules/ReviewRequiredBanner';
export type {
  ReviewRequiredBannerProps,
} from '../../../../../platform/typescript/design-system/src/molecules/ReviewRequiredBanner';
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