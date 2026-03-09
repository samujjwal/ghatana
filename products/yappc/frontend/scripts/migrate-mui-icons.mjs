#!/usr/bin/env node

/**
 * MUI Icons → Lucide React Migration Codemod
 *
 * Transforms: import { Search, Close } from '@mui/icons-material';
 * Into:       import { Search, X } from 'lucide-react';
 *
 * Handles:
 * - Named imports from '@mui/icons-material'
 * - Individual path imports (e.g., '@mui/icons-material/Search')
 * - fontSize="small" → size={16}, fontSize="large" → size={32}
 * - Renames based on MUI_TO_LUCIDE_MAP
 *
 * Usage: node migrate-mui-icons.mjs [--dry-run] [--path <glob>]
 *
 * @doc.type script
 * @doc.purpose Automated MUI→Lucide icon migration
 * @doc.layer tooling
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import path from 'path';

/** Recursively find files matching extensions */
function findFiles(dir, extensions, ignore = ['node_modules', '.bak']) {
  const results = [];
  try {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (ignore.some(i => entry.name.includes(i))) continue;
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        results.push(...findFiles(fullPath, extensions, ignore));
      } else if (extensions.some(ext => entry.name.endsWith(ext))) {
        results.push(fullPath);
      }
    }
  } catch { /* ignore permission errors */ }
  return results;
}

// Icon mapping (inline for standalone use)
const MUI_TO_LUCIDE = {
  Close: 'X', Add: 'Plus', Remove: 'Minus', Edit: 'Pencil',
  Delete: 'Trash2', DeleteOutline: 'Trash', Save: 'Save', Search: 'Search',
  Settings: 'Settings', Refresh: 'RefreshCw', Home: 'Home',
  ArrowBack: 'ArrowLeft', ArrowForward: 'ArrowRight', ArrowUpward: 'ArrowUp',
  ArrowDownward: 'ArrowDown', ArrowRightAlt: 'MoveRight',
  ChevronRight: 'ChevronRight', ChevronLeft: 'ChevronLeft',
  NavigateNext: 'ChevronRight', ExpandMore: 'ChevronDown',
  ExpandLess: 'ChevronUp', MoreVert: 'MoreVertical',
  Clear: 'XCircle', Cancel: 'XCircle', Fullscreen: 'Maximize2',
  Minimize: 'Minimize2', OpenInNew: 'ExternalLink', OpenInFull: 'Maximize',
  Check: 'Check', CheckCircle: 'CheckCircle', Error: 'AlertCircle',
  Warning: 'AlertTriangle', WarningAmber: 'AlertTriangle', Info: 'Info',
  Pending: 'Clock', Block: 'Ban', ContentCopy: 'Copy',
  Description: 'FileText', Folder: 'Folder', FolderOpen: 'FolderOpen',
  AttachFile: 'Paperclip', FileDownload: 'Download', Download: 'Download',
  Upload: 'Upload', Print: 'Printer', Link: 'Link', LinkOff: 'Unlink',
  Send: 'Send', Email: 'Mail', Message: 'MessageSquare',
  Person: 'User', People: 'Users', Group: 'Users',
  Notifications: 'Bell', Favorite: 'Heart', FavoriteBorder: 'Heart',
  ThumbUp: 'ThumbsUp', ThumbDown: 'ThumbsDown', Flag: 'Flag',
  History: 'History', Schedule: 'Clock', AccessTime: 'Clock',
  Timer: 'Timer', Undo: 'Undo2', Redo: 'Redo2', Restore: 'RotateCcw',
  HourglassEmpty: 'Hourglass',
  ViewList: 'List', ViewModule: 'LayoutGrid', ViewColumn: 'Columns',
  ViewKanban: 'Kanban', ViewAgenda: 'ListTodo', ViewQuilt: 'LayoutDashboard',
  GridView: 'Grid3x3', Dashboard: 'LayoutDashboard',
  DashboardCustomize: 'LayoutDashboard', FilterList: 'Filter',
  Layers: 'Layers', ListAlt: 'ListOrdered',
  TrendingUp: 'TrendingUp', TrendingDown: 'TrendingDown',
  Timeline: 'Activity', Speed: 'Gauge', Assessment: 'BarChart3',
  TableChart: 'Table',
  Code: 'Code', Build: 'Hammer', BugReport: 'Bug',
  AccountTree: 'GitBranch', Memory: 'Cpu', Storage: 'HardDrive', Bolt: 'Zap',
  AutoAwesome: 'Sparkles', Lightbulb: 'Lightbulb',
  LightbulbOutlined: 'Lightbulb', AutoFixHigh: 'Wand2', Psychology: 'Brain',
  Security: 'Shield', Lock: 'Lock', Visibility: 'Eye',
  Brush: 'Paintbrush', Palette: 'Palette', ColorLens: 'Palette',
  Gesture: 'PenTool', PanTool: 'Hand', Mouse: 'MousePointer',
  Camera: 'Camera', PhotoLibrary: 'Image', ZoomIn: 'ZoomIn',
  ZoomOut: 'ZoomOut', Circle: 'Circle', Map: 'Map',
  Architecture: 'Building2', Schema: 'Network', RouteOutlined: 'Route',
  Widgets: 'Component',
  FactCheck: 'ClipboardCheck', RocketLaunch: 'Rocket',
  Assignment: 'ClipboardList', Share: 'Share2',
  Laptop: 'Laptop', Smartphone: 'Smartphone', Tablet: 'Tablet',
  DarkMode: 'Moon', Business: 'Building', CloudOff: 'CloudOff',
  Accessible: 'Accessibility', Accessibility: 'Accessibility',
  Play: 'Play', Apple: 'Apple',
  UnfoldLessOutlined: 'ChevronsDownUp', UnfoldMoreOutlined: 'ChevronsUpDown',

  // ── Extended mappings (unmapped icons from dry-run) ──────────────────
  RadioButtonUnchecked: 'Circle', RadioButtonChecked: 'CircleDot',
  PlayArrow: 'Play', PlayArrowOutlined: 'Play', PlayCircle: 'PlayCircle',
  Pause: 'Pause',
  EmojiEvents: 'Trophy', School: 'GraduationCap',
  ContentPaste: 'ClipboardPaste', SelectAll: 'BoxSelect',
  FitScreen: 'Maximize2', GridOn: 'Grid3x3',
  CloudUpload: 'CloudUpload', Cloud: 'Cloud', CloudDone: 'CloudCheck',
  CloudQueue: 'Cloud', CloudQueueOutlined: 'Cloud', CloudOutlined: 'Cloud',
  CloudSync: 'CloudCog',
  Keyboard: 'Keyboard', KeyboardArrowDown: 'ChevronDown',
  KeyboardArrowRight: 'ChevronRight', KeyboardOutlined: 'Keyboard',
  KeyboardCommandKey: 'Command',
  FormatAlignLeft: 'AlignLeft', FormatAlignCenter: 'AlignCenter',
  FormatAlignRight: 'AlignRight',
  VerticalAlignTop: 'AlignStartVertical', VerticalAlignCenter: 'AlignCenterVertical',
  VerticalAlignBottom: 'AlignEndVertical',
  LockOutlined: 'Lock', LockOpenOutlined: 'LockOpen', LockOpen: 'LockOpen',
  CallSplit: 'GitFork', SmartToy: 'Bot', SmartToyOutlined: 'Bot',
  RemoveCircle: 'MinusCircle', HelpOutline: 'HelpCircle', Help: 'HelpCircle',
  Science: 'FlaskConical', CheckCircleOutline: 'CheckCircle',
  Api: 'Plug', Computer: 'Monitor', Cable: 'Cable',
  Autorenew: 'RefreshCw', Image: 'Image', PictureAsPdf: 'FileType',
  BarChart: 'BarChart3', Shield: 'Shield', Terminal: 'Terminal',
  DrawOutlined: 'PenLine', VerifiedOutlined: 'BadgeCheck', Verified: 'BadgeCheck',
  CodeOutlined: 'Code', MonitorHeartOutlined: 'HeartPulse', MonitorHeart: 'HeartPulse',
  TrendingUpOutlined: 'TrendingUp', CenterFocusStrong: 'Focus',
  Extension: 'Puzzle', Comment: 'MessageSquare', Reply: 'Reply',
  DataObject: 'Braces', Class: 'FileCode', InsertDriveFile: 'File',
  BlockOutlined: 'Ban', Book: 'Book', Public: 'Globe',
  ErrorOutline: 'AlertCircle', VisibilityOff: 'EyeOff',
  DragIndicator: 'GripVertical', Feedback: 'MessageCircle',
  Rocket: 'Rocket', StickyNote2: 'StickyNote', Category: 'Tag',
  ViewInAr: 'Box', Tune: 'SlidersHorizontal', Hub: 'Network',
  Article: 'Newspaper', Checklist: 'ListChecks',
  TheaterComedy: 'Drama', SwapHoriz: 'ArrowLeftRight',
  MouseOutlined: 'MousePointer', Explore: 'Compass',
  Apps: 'LayoutGrid', EmojiObjects: 'Lightbulb',
  Crop: 'Crop', PanoramaFishEye: 'Circle',
  Sync: 'RefreshCw', Navigation: 'Navigation',
  ViewSidebar: 'PanelLeft', AddCircle: 'PlusCircle',
  Tag: 'Tag', BubbleChart: 'ScatterChart', CropFree: 'ScanLine',
  CropSquare: 'Square', TextFields: 'Type',
  CircleOutlined: 'Circle', HorizontalRule: 'Minus',
  StorageOutlined: 'HardDrive',
  Star: 'Star', IosShare: 'Share', CreateNewFolder: 'FolderPlus',
  DataUsageOutlined: 'PieChart', SecurityOutlined: 'Shield',
  Launch: 'ExternalLink', CalendarToday: 'Calendar',
  Inventory: 'Package', Draw: 'PenLine', WorkOutline: 'Briefcase',
  GetApp: 'Download', Logout: 'LogOut', AccountCircle: 'UserCircle',
  LightMode: 'Sun', TrendingFlat: 'MoveRight', Topic: 'Hash',
  Analytics: 'LineChart', LibraryBooks: 'Library', Gavel: 'Gavel',
  Replay: 'RotateCcw', HistoryEdu: 'BookOpen', MenuBook: 'BookOpen',
  Policy: 'ShieldCheck', TipsAndUpdates: 'Lightbulb',
  PhotoCamera: 'Camera', TouchApp: 'Pointer', PhoneIphone: 'Smartphone',
  PersonAdd: 'UserPlus', AdminPanelSettings: 'Settings',
  Mail: 'Mail', Workspaces: 'Boxes', Language: 'Globe',
  Key: 'Key', Archive: 'Archive', Wifi: 'Wifi',
  ExitToApp: 'LogOut', Vibration: 'Vibrate',
  SupervisorAccount: 'UserCog', VerifiedUser: 'ShieldCheck',
  Groups: 'Users', BusinessCenter: 'BriefcaseBusiness',
  SupportAgent: 'Headset', Headset: 'Headset',
  CameraAlt: 'Camera',
};

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const pathIdx = args.indexOf('--path');
const searchPath = pathIdx !== -1 ? args[pathIdx + 1] : 'apps/web/src';

console.log(`🔄 MUI → Lucide Icon Migration`);
console.log(`  Search dir: ${searchPath}`);
console.log(`  Mode: ${dryRun ? 'DRY RUN' : 'WRITE'}`);
console.log('');

const files = findFiles(searchPath, ['.tsx', '.ts']);
let totalFiles = 0;
let totalReplacements = 0;
const unmapped = new Set();

for (const file of files) {
  let content = readFileSync(file, 'utf-8');
  let modified = false;
  const lucideImports = new Set();

  // Match: import { Icon1, Icon2 } from '@mui/icons-material';
  const namedImportRe = /import\s*\{([^}]+)\}\s*from\s*['"]@mui\/icons-material['"]\s*;?/g;
  let match;

  while ((match = namedImportRe.exec(content)) !== null) {
    const icons = match[1].split(',').map(s => s.trim()).filter(Boolean);
    const mapped = [];
    for (const icon of icons) {
      // Handle 'Icon as Alias' syntax
      const parts = icon.split(/\s+as\s+/);
      const origName = parts[0].trim();
      const alias = parts.length > 1 ? parts[1].trim() : null;
      const lucideName = MUI_TO_LUCIDE[origName];

      if (lucideName) {
        if (alias) {
          mapped.push(`${lucideName} as ${alias}`);
        } else if (lucideName !== origName) {
          // Need to rename usages in the file
          mapped.push(`${lucideName} as ${origName}`);
        } else {
          mapped.push(lucideName);
        }
        lucideImports.add(lucideName);
      } else {
        unmapped.add(origName);
        mapped.push(icon); // Keep original if no mapping
      }
    }

    const newImport = `import { ${mapped.join(', ')} } from 'lucide-react';`;
    content = content.replace(match[0], newImport);
    modified = true;
    totalReplacements++;
  }

  // Match: import SearchIcon from '@mui/icons-material/Search';
  const defaultImportRe = /import\s+(\w+)\s+from\s*['"]@mui\/icons-material\/(\w+)['"]\s*;?/g;
  while ((match = defaultImportRe.exec(content)) !== null) {
    const localName = match[1];
    const iconName = match[2];
    const lucideName = MUI_TO_LUCIDE[iconName];

    if (lucideName) {
      const importLine = localName === lucideName
        ? `import { ${lucideName} } from 'lucide-react';`
        : `import { ${lucideName} as ${localName} } from 'lucide-react';`;
      content = content.replace(match[0], importLine);
      lucideImports.add(lucideName);
      modified = true;
      totalReplacements++;
    } else {
      unmapped.add(iconName);
    }
  }

  // Transform fontSize props: fontSize="small" → size={16}
  if (modified) {
    content = content.replace(/fontSize="small"/g, 'size={16}');
    content = content.replace(/fontSize="medium"/g, 'size={20}');
    content = content.replace(/fontSize="large"/g, 'size={32}');
    content = content.replace(/fontSize="inherit"/g, 'size={undefined}');
  }

  if (modified) {
    totalFiles++;
    console.log(`  ✅ ${file} (${lucideImports.size} icons)`);
    if (!dryRun) {
      writeFileSync(file, content, 'utf-8');
    }
  }
}

console.log('');
console.log(`📊 Summary:`);
console.log(`  Files modified: ${totalFiles}`);
console.log(`  Import statements replaced: ${totalReplacements}`);
if (unmapped.size > 0) {
  console.log(`  ⚠️  Unmapped icons (${unmapped.size}): ${[...unmapped].join(', ')}`);
}
if (dryRun) {
  console.log(`  ℹ️  Dry run — no files written. Remove --dry-run to apply.`);
}
