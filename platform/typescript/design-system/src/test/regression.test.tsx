/**
 * Design System Regression Tests
 * 
 * These tests ensure that the design-system package exports remain stable
 * after the split from the ui package.
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';

// Test that all expected exports are available
import {
  // Atoms
  Button,
  IconButton,
  Input,
  TextArea,
  Badge,
  Spinner,
  Checkbox,
  Radio,
  Switch,
  Skeleton,
  Tooltip,
  Chip,
  Select,
  FormControl,
  FormControlLabel,
  InputLabel,
  Slider,
  DatePicker,
  ToggleButton,
  BottomNavigation,
  Fab,
  Collapse,
  ButtonGroup,
  Icon,
  
  // Molecules
  FormField,
  Alert,
  Card,
  Modal,
  Dialog,
  Table,
  Tabs,
  Popper,
  Breadcrumb,
  Pagination,
  Toast,
  Menu,
  RadioGroup,
  Stepper,
  List,
  AvatarGroup,
  Timeline,
  AppBar,
  ConfirmDialog,
  ActionSheet,
  TreeView,
  Form,
  NavLink,
  Sidebar,
  Drawer,
  Toolbar,
  Snackbar,
  SpeedDial,
  Autocomplete,
  CommandPalette,
  DateRangePicker,
  AppListItem,
  UsageStatsCard,
  PolicyCard,
  
  // Organisms
  DashboardLayout,
  AppHeader,
  AppSidebar,
  ErrorBoundary,
  ProtectedRoute,
  DynamicForm,
  ActivityFeed,
  DataGrid,
  StatsDashboard,
  AppListContainer,
  
  // Layout
  Box,
  Stack,
  Container,
  Grid,
  Surface,
  Spacer,
  
  // Hooks
  useTheme,
  useKeyboardNavigation,
  useReducedMotion,
  useSwipeGesture,
  useFormValidation,
  useImageOptimization,
  useOptimisticUpdate,
  useAccessibleId,
  useMediaQuery,
  
  // Utils
  cn,
  colorContrast,
  AccessibilityAuditService,
  DesignPromptService,
  
  // Tokens
  animations,
  semanticColors,
  
  // Theme
  darkMode,
  Typography,
  
  // Styles
  tailwindTheme,
} from '../src/index';

describe('Design System Regression Tests', () => {
  
  describe('Export Stability', () => {
    it('should export all expected atoms', () => {
      // Test that atom components are exported and can be instantiated
      expect(Button).toBeDefined();
      expect(Input).toBeDefined();
      expect(Badge).toBeDefined();
      expect(Spinner).toBeDefined();
      expect(Checkbox).toBeDefined();
      expect(Radio).toBeDefined();
      expect(Switch).toBeDefined();
      expect(Skeleton).toBeDefined();
      expect(Tooltip).toBeDefined();
      expect(Chip).toBeDefined();
      expect(Select).toBeDefined();
    });

    it('should export all expected molecules', () => {
      // Test that molecule components are exported
      expect(FormField).toBeDefined();
      expect(Alert).toBeDefined();
      expect(Card).toBeDefined();
      expect(Modal).toBeDefined();
      expect(Dialog).toBeDefined();
      expect(Table).toBeDefined();
      expect(Tabs).toBeDefined();
      expect(Menu).toBeDefined();
      expect(Stepper).toBeDefined();
      expect(List).toBeDefined();
    });

    it('should export all expected organisms', () => {
      // Test that organism components are exported
      expect(DashboardLayout).toBeDefined();
      expect(AppHeader).toBeDefined();
      expect(AppSidebar).toBeDefined();
      expect(ErrorBoundary).toBeDefined();
      expect(ProtectedRoute).toBeDefined();
      expect(DynamicForm).toBeDefined();
      expect(ActivityFeed).toBeDefined();
      expect(DataGrid).toBeDefined();
    });

    it('should export all expected layout components', () => {
      // Test that layout components are exported
      expect(Box).toBeDefined();
      expect(Stack).toBeDefined();
      expect(Container).toBeDefined();
      expect(Grid).toBeDefined();
      expect(Surface).toBeDefined();
      expect(Spacer).toBeDefined();
    });

    it('should export all expected hooks', () => {
      // Test that hooks are exported
      expect(useTheme).toBeDefined();
      expect(useKeyboardNavigation).toBeDefined();
      expect(useReducedMotion).toBeDefined();
      expect(useSwipeGesture).toBeDefined();
      expect(useFormValidation).toBeDefined();
      expect(useImageOptimization).toBeDefined();
      expect(useOptimisticUpdate).toBeDefined();
      expect(useAccessibleId).toBeDefined();
      expect(useMediaQuery).toBeDefined();
    });

    it('should export all expected utilities', () => {
      // Test that utilities are exported
      expect(cn).toBeDefined();
      expect(colorContrast).toBeDefined();
      expect(AccessibilityAuditService).toBeDefined();
      expect(DesignPromptService).toBeDefined();
    });

    it('should export all expected tokens and theme', () => {
      // Test that tokens and theme are exported
      expect(animations).toBeDefined();
      expect(semanticColors).toBeDefined();
      expect(darkMode).toBeDefined();
      expect(Typography).toBeDefined();
      expect(tailwindTheme).toBeDefined();
    });
  });

  describe('Component Functionality', () => {
    it('should render Button component without errors', () => {
      render(<Button>Test Button</Button>);
      expect(screen.getByRole('button')).toBeInTheDocument();
      expect(screen.getByText('Test Button')).toBeInTheDocument();
    });

    it('should render Input component without errors', () => {
      render(<Input placeholder="Test input" />);
      expect(screen.getByPlaceholderText('Test input')).toBeInTheDocument();
    });

    it('should render Badge component without errors', () => {
      render(<Badge>Test Badge</Badge>);
      expect(screen.getByText('Test Badge')).toBeInTheDocument();
    });

    it('should render Card component without errors', () => {
      render(
        <Card>
          <Card.Header>Test Card</Card.Header>
          <Card.Content>Card content</Card.Content>
        </Card>
      );
      expect(screen.getByText('Test Card')).toBeInTheDocument();
      expect(screen.getByText('Card content')).toBeInTheDocument();
    });

    it('should render Alert component without errors', () => {
      render(<Alert severity="info">Test Alert</Alert>);
      expect(screen.getByText('Test Alert')).toBeInTheDocument();
    });
  });

  describe('Hook Functionality', () => {
    it('should provide theme hook functionality', () => {
      // Test that useTheme hook can be called
      expect(() => {
        const TestComponent = () => {
          const theme = useTheme();
          return <div data-theme={theme.mode}>Test</div>;
        };
        render(<TestComponent />);
      }).not.toThrow();
    });

    it('should provide utility function functionality', () => {
      // Test that utility functions work
      expect(typeof cn).toBe('function');
      expect(typeof colorContrast).toBe('function');
      
      // Test cn utility
      const className = cn('base-class', { 'active': true }, 'additional-class');
      expect(className).toContain('base-class');
      expect(className).toContain('active');
      expect(className).toContain('additional-class');
    });
  });

  describe('Service Classes', () => {
    it('should instantiate DesignPromptService', () => {
      // Test that DesignPromptService can be instantiated
      const mockAI = {
        complete: jest.fn().mockResolvedValue({ content: 'test response' })
      };
      
      expect(() => {
        const service = new DesignPromptService(mockAI);
        expect(service).toBeInstanceOf(DesignPromptService);
      }).not.toThrow();
    });

    it('should instantiate AccessibilityAuditService', () => {
      // Test that AccessibilityAuditService can be instantiated
      const mockAI = {
        complete: jest.fn().mockResolvedValue({ content: 'test response' })
      };
      
      expect(() => {
        const service = new AccessibilityAuditService(mockAI);
        expect(service).toBeInstanceOf(AccessibilityAuditService);
      }).not.toThrow();
    });
  });

  describe('Theme and Tokens', () => {
    it('should provide theme tokens', () => {
      // Test that theme tokens are available
      expect(animations).toBeDefined();
      expect(typeof animations).toBe('object');
      
      expect(semanticColors).toBeDefined();
      expect(typeof semanticColors).toBe('object');
      
      expect(darkMode).toBeDefined();
      expect(typeof darkMode).toBe('object');
    });

    it('should provide typography system', () => {
      // Test that typography system is available
      expect(Typography).toBeDefined();
      expect(typeof Typography).toBe('object');
      
      // Test that common typography variants exist
      expect(Typography.h1).toBeDefined();
      expect(Typography.h2).toBeDefined();
      expect(Typography.body1).toBeDefined();
      expect(Typography.caption).toBeDefined();
    });
  });
});
