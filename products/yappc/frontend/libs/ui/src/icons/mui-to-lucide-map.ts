/**
 * MUI → Lucide Icon Migration Map
 *
 * Maps MUI icon names to their lucide-react equivalents.
 * Used during the MUI→Tailwind migration (Phase 2).
 *
 * Usage:
 *   import { Search } from 'lucide-react';
 *   // replaces: import { Search } from 'lucide-react';
 *
 * @doc.type utility
 * @doc.purpose MUI to Lucide icon name mapping
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * MUI icon name → lucide-react icon name mapping.
 * If a MUI icon has no exact Lucide counterpart, a close equivalent is chosen.
 */
export const MUI_TO_LUCIDE_MAP: Record<string, string> = {
  // Navigation & Actions
  Close: 'X',
  Add: 'Plus',
  Remove: 'Minus',
  Edit: 'Pencil',
  Delete: 'Trash2',
  DeleteOutline: 'Trash',
  Save: 'Save',
  Search: 'Search',
  Settings: 'Settings',
  Refresh: 'RefreshCw',
  Home: 'Home',
  ArrowBack: 'ArrowLeft',
  ArrowForward: 'ArrowRight',
  ArrowUpward: 'ArrowUp',
  ArrowDownward: 'ArrowDown',
  ArrowRightAlt: 'MoveRight',
  ChevronRight: 'ChevronRight',
  ChevronLeft: 'ChevronLeft',
  NavigateNext: 'ChevronRight',
  ExpandMore: 'ChevronDown',
  ExpandLess: 'ChevronUp',
  MoreVert: 'MoreVertical',
  Clear: 'XCircle',
  Cancel: 'XCircle',
  Fullscreen: 'Maximize2',
  Minimize: 'Minimize2',
  OpenInNew: 'ExternalLink',
  OpenInFull: 'Maximize',

  // Status & Feedback
  Check: 'Check',
  CheckCircle: 'CheckCircle',
  Error: 'AlertCircle',
  Warning: 'AlertTriangle',
  WarningAmber: 'AlertTriangle',
  Info: 'Info',
  Pending: 'Clock',
  Block: 'Ban',

  // Content & Data
  ContentCopy: 'Copy',
  Description: 'FileText',
  Folder: 'Folder',
  FolderOpen: 'FolderOpen',
  AttachFile: 'Paperclip',
  FileDownload: 'Download',
  Download: 'Download',
  Upload: 'Upload',
  Print: 'Printer',
  Link: 'Link',
  LinkOff: 'Unlink',
  Send: 'Send',
  Email: 'Mail',
  Message: 'MessageSquare',

  // Users & People
  Person: 'User',
  People: 'Users',
  Group: 'Users',

  // Notifications & Alerts
  Notifications: 'Bell',
  Favorite: 'Heart',
  FavoriteBorder: 'Heart',
  ThumbUp: 'ThumbsUp',
  ThumbDown: 'ThumbsDown',
  Flag: 'Flag',

  // Time & History
  History: 'History',
  Schedule: 'Clock',
  AccessTime: 'Clock',
  Timer: 'Timer',
  Undo: 'Undo2',
  Redo: 'Redo2',
  Restore: 'RotateCcw',
  HourglassEmpty: 'Hourglass',

  // View & Layout
  ViewList: 'List',
  ViewModule: 'LayoutGrid',
  ViewColumn: 'Columns',
  ViewKanban: 'Kanban',
  ViewAgenda: 'ListTodo',
  ViewQuilt: 'LayoutDashboard',
  GridView: 'Grid3x3',
  Dashboard: 'LayoutDashboard',
  DashboardCustomize: 'LayoutDashboard',
  FilterList: 'Filter',
  Layers: 'Layers',
  ListAlt: 'ListOrdered',

  // Charts & Analytics
  TrendingUp: 'TrendingUp',
  TrendingDown: 'TrendingDown',
  Timeline: 'Activity',
  Speed: 'Gauge',
  Assessment: 'BarChart3',
  TableChart: 'Table',

  // Development & Code
  Code: 'Code',
  Build: 'Hammer',
  BugReport: 'Bug',
  AccountTree: 'GitBranch',
  Memory: 'Cpu',
  Storage: 'HardDrive',
  Bolt: 'Zap',

  // AI & Creative
  AutoAwesome: 'Sparkles',
  Lightbulb: 'Lightbulb',
  LightbulbOutlined: 'Lightbulb',
  AutoFixHigh: 'Wand2',
  Psychology: 'Brain',

  // Security & Auth
  Security: 'Shield',
  Lock: 'Lock',
  Visibility: 'Eye',

  // Canvas & Drawing
  Brush: 'Paintbrush',
  Palette: 'Palette',
  ColorLens: 'Palette',
  Gesture: 'PenTool',
  PanTool: 'Hand',
  Mouse: 'MousePointer',
  Camera: 'Camera',
  PhotoLibrary: 'Image',
  ZoomIn: 'ZoomIn',
  ZoomOut: 'ZoomOut',
  Circle: 'Circle',
  Map: 'Map',

  // Architecture & Structure
  Architecture: 'Building2',
  Schema: 'Network',
  RouteOutlined: 'Route',
  Widgets: 'Component',

  // Canvas-specific (persona icons)
  FactCheck: 'ClipboardCheck',
  RocketLaunch: 'Rocket',
  Assignment: 'ClipboardList',
  Share: 'Share2',

  // Devices
  Laptop: 'Laptop',
  Smartphone: 'Smartphone',
  Tablet: 'Tablet',

  // Misc
  DarkMode: 'Moon',
  Business: 'Building',
  CloudOff: 'CloudOff',
  Accessible: 'Accessibility',
  Accessibility: 'Accessibility',
  Play: 'Play',
  Apple: 'Apple',
  UnfoldLessOutlined: 'ChevronsDownUp',
  UnfoldMoreOutlined: 'ChevronsUpDown',
};
