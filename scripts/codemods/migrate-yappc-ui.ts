#!/usr/bin/env tsx
/**
 * Codemod: Migrate @ghatana/yappc-ui → @ghatana/ui
 * 
 * This script rewrites imports from the deprecated @ghatana/yappc-ui package
 * to the platform-level @ghatana/ui package. Components that have 1:1 
 * equivalents are remapped directly. YAPPC-specific components stay in
 * a slimmed @ghatana/yappc-ui (without MUI re-exports).
 * 
 * Usage: npx tsx scripts/codemods/migrate-yappc-ui.ts [--dry-run]
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const DRY_RUN = process.argv.includes('--dry-run');
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');

// ─── Component Mapping ────────────────────────────────────────────────
// Maps MUI component names (as exported by @ghatana/yappc-ui) to @ghatana/ui equivalents
// Components NOT in this map are considered YAPPC-specific and stay in @ghatana/yappc-ui

const DIRECT_MAPPING: Record<string, string> = {
  // Layout
  'Container': 'Container',
  'Box': 'Box',
  'Grid': 'Grid',
  'Stack': 'Stack',

  // Atoms
  'Button': 'Button',
  'IconButton': 'IconButton',
  'Input': 'Input',
  'InputAdornment': 'InputAdornment',
  'InputLabel': 'InputLabel',
  'FormControl': 'FormControl',
  'FormControlLabel': 'FormControlLabel',
  'Checkbox': 'Checkbox',
  'Radio': 'Radio',
  'RadioGroup': 'RadioGroup',
  'Select': 'Select',
  'Switch': 'Switch',
  'Slider': 'Slider',
  'Rating': 'Rating',
  'Badge': 'Badge',
  'Chip': 'Chip',
  'Tooltip': 'Tooltip',
  'Skeleton': 'Skeleton',
  'Avatar': 'Avatar',
  'Divider': 'Divider',
  'Fab': 'Fab',
  'ToggleButton': 'ToggleButton',
  'BottomNavigation': 'BottomNavigation',

  // Molecules
  'Alert': 'Alert',
  'Card': 'Card',
  'Dialog': 'Dialog',
  'Modal': 'Modal',
  'Table': 'Table',
  'Tabs': 'Tabs',
  'Menu': 'Menu',
  'Pagination': 'Pagination',
  'Stepper': 'Stepper',
  'AvatarGroup': 'AvatarGroup',

  // Organisms
  'ErrorBoundary': 'ErrorBoundary',

  // Feedback
  'LinearProgress': 'LinearProgress',
  'Progress': 'Progress',

  // Components previously MUI-only, now built in @ghatana/ui
  'TextField': 'TextField',
  'MenuItem': 'MenuItem',
  'Drawer': 'Drawer',
  'SwipeableDrawer': 'SwipeableDrawer',
  'Breadcrumbs': 'Breadcrumbs',
  'ButtonGroup': 'ButtonGroup',
  'Icon': 'Icon',
  'Link': 'Link',
  'ListItemAvatar': 'ListItemAvatar',
  'ListItemButton': 'ListItemButton',
  'ListItemSecondaryAction': 'ListItemSecondaryAction',
  'CardMedia': 'CardMedia',
  'ToggleButtonGroup': 'ToggleButtonGroup',
  'Snackbar': 'Snackbar',
  'Backdrop': 'Backdrop',
  'Collapse': 'Collapse',
  'Fade': 'Fade',
  'Grow': 'Grow',
  'Slide': 'Slide',
  'Zoom': 'Zoom',
  'SpeedDial': 'SpeedDial',
  'SpeedDialAction': 'SpeedDialAction',
  'SpeedDialIcon': 'SpeedDialIcon',
  'cn': 'cn',
};

// Components that need renaming (MUI name → @ghatana/ui name)
const RENAME_MAPPING: Record<string, string> = {
  'CircularProgress': 'Spinner',
  'Paper': 'Surface',
  'List': 'InteractiveList',
  'Typography': 'Typography', // Keep same name — @ghatana/ui has Typography
  'LoadingSpinner': 'Spinner',
};

// Components that exist in @ghatana/ui but with sub-component patterns
// These need special handling
const COMPOUND_MAPPING: Record<string, string> = {
  // Card family
  'CardHeader': 'CardHeader',
  'CardContent': 'CardContent',
  'CardActions': 'CardActions',

  // Dialog family
  'DialogTitle': 'DialogTitle',
  'DialogContent': 'DialogContent',
  'DialogActions': 'DialogActions',

  // Table family
  'TableBody': 'TableBody',
  'TableCell': 'TableCell',
  'TableContainer': 'TableContainer',
  'TableHead': 'TableHead',
  'TableRow': 'TableRow',

  // List family
  'ListItem': 'ListItem',
  'ListItemText': 'ListItemText',
  'ListItemIcon': 'ListItemIcon',

  // Tabs family
  'Tab': 'Tab',

  // Alert family
  'AlertTitle': 'AlertTitle',

  // AppBar / Toolbar
  'AppBar': 'AppBar',
  'Toolbar': 'Toolbar',

  // Form
  'FormGroup': 'FormGroup',
  'FormHelperText': 'FormHelperText',
  'FormLabel': 'FormLabel',
};

// Components that should stay in @ghatana/yappc-ui (YAPPC-specific)
const YAPPC_ONLY: Set<string> = new Set([
  // MUI-specific types (consumers should remove these)
  'SxProps', 'SystemSxProps', 'Theme', 'MuiTheme', 'Breakpoint',
  'Components', 'CSSObject', 'Palette', 'PaletteColor',
  'PaletteOptions', 'ThemeOptions', 'TypographyVariants',
  'TypographyVariantsOptions',

  // MUI utilities (consumers should remove these)
  'alpha', 'useMuiTheme',

  // Input variants (rare usage — keep in yappc-ui for now)
  'OutlinedInput', 'FilledInput',
  'TablePagination', 'TableSortLabel',
  'BottomNavigationAction',

  // YAPPC-specific components & hooks
  'ThemeProvider', 'PlatformWrapper',
  'DevSecOps', 'DomainSelector', 'WorkflowRenderer',
  'TaskListView',
  'lightTheme', 'darkTheme', 'useKeyboardShortcuts',
  'resolveMuiColor', 'getPaletteMain',
  'palette',

  // YAPPC-specific DevSecOps/domain components
  'SecurityDashboard', 'AlertCard', 'IncidentTimeline', 'TimelineEvent',
  'LogViewer', 'LogEntry', 'LogLevel', 'AlertSeverity', 'AlertStatus',
  'VelocityChart', 'BurndownChart', 'DeploymentCard', 'CodeReviewCard',
  'StoryCard', 'RetroItem', 'TeamMoodPicker', 'SprintBoard',
  'FeatureFlagRow', 'ResourcesList', 'Resource',
  'PresetCard', 'InitializationPreset', 'PresetCategory',
  'AIChatInterface', 'ValidationPanel', 'ProjectCanvas',
  'TabNavigation', 'TabNavigationItem', 'Breadcrumb',
  'ShortcutHelper', 'usePersonas', 'Command',
]);

// Build combined lookup
const ALL_UI_COMPONENTS = new Map<string, string>();
for (const [from, to] of Object.entries(DIRECT_MAPPING)) {
  ALL_UI_COMPONENTS.set(from, to);
}
for (const [from, to] of Object.entries(RENAME_MAPPING)) {
  ALL_UI_COMPONENTS.set(from, to);
}
for (const [from, to] of Object.entries(COMPOUND_MAPPING)) {
  ALL_UI_COMPONENTS.set(from, to);
}

// ─── File Discovery ───────────────────────────────────────────────────

function findFiles(dir: string, extensions: string[]): string[] {
  const results: string[] = [];

  function walk(d: string) {
    if (!fs.existsSync(d)) return;
    const entries = fs.readdirSync(d, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(d, entry.name);
      if (entry.isDirectory()) {
        if (['node_modules', 'dist', 'build', '.git', '.next'].includes(entry.name)) continue;
        walk(fullPath);
      } else if (extensions.some(ext => entry.name.endsWith(ext))) {
        results.push(fullPath);
      }
    }
  }

  walk(dir);
  return results;
}

// ─── Import Parser ────────────────────────────────────────────────────

interface ParsedImport {
  fullMatch: string;
  specifiers: string[];  // individual imported names
  source: string;        // module path
  isType: boolean;       // import type { ... }
}

function parseImports(content: string): ParsedImport[] {
  const imports: ParsedImport[] = [];
  // Match: import { A, B, C } from '@ghatana/yappc-ui';
  // Also: import type { A } from '@ghatana/yappc-ui';
  const importRegex = /import\s+(type\s+)?{([^}]+)}\s+from\s+['"](@ghatana\/yappc-ui)(?:\/[^'"]*)?['"]\s*;?/g;

  let match;
  while ((match = importRegex.exec(content)) !== null) {
    const isType = !!match[1];
    const specifiersStr = match[2];
    const source = match[3];
    const specifiers = specifiersStr
      .split(',')
      .map(s => s.trim())
      .filter(s => s.length > 0)
      .map(s => {
        // Handle "type X" prefix within import { type X, Y }
        return s.replace(/^type\s+/, '');
      });

    imports.push({
      fullMatch: match[0],
      specifiers,
      source,
      isType,
    });
  }

  return imports;
}

// ─── Import Rewriter ──────────────────────────────────────────────────

interface RewriteResult {
  newContent: string;
  changes: string[];
}

function rewriteFile(filePath: string, content: string): RewriteResult {
  const changes: string[] = [];
  let newContent = content;

  const imports = parseImports(content);

  for (const imp of imports) {
    const uiSpecifiers: string[] = [];          // → @ghatana/ui
    const renamedSpecifiers: string[] = [];     // → @ghatana/ui with rename
    const yappcSpecifiers: string[] = [];       // Stay in @ghatana/yappc-ui
    const typeSpecifiers: string[] = [];        // Type imports that stay

    for (const spec of imp.specifiers) {
      // Handle "X as Y" pattern
      const parts = spec.split(/\s+as\s+/);
      const importedName = parts[0].trim();
      const localAlias = parts.length > 1 ? parts[1].trim() : null;

      if (ALL_UI_COMPONENTS.has(importedName)) {
        const mappedName = ALL_UI_COMPONENTS.get(importedName)!;
        if (mappedName !== importedName) {
          // Needs rename
          if (localAlias) {
            renamedSpecifiers.push(`${mappedName} as ${localAlias}`);
          } else {
            renamedSpecifiers.push(`${mappedName} as ${importedName}`);
          }
        } else {
          // Direct mapping, same name
          if (localAlias) {
            uiSpecifiers.push(`${importedName} as ${localAlias}`);
          } else {
            uiSpecifiers.push(importedName);
          }
        }
      } else if (YAPPC_ONLY.has(importedName)) {
        if (localAlias) {
          yappcSpecifiers.push(`${importedName} as ${localAlias}`);
        } else {
          yappcSpecifiers.push(importedName);
        }
      } else {
        // Unknown — keep in yappc-ui
        if (localAlias) {
          yappcSpecifiers.push(`${importedName} as ${localAlias}`);
        } else {
          yappcSpecifiers.push(importedName);
        }
      }
    }

    // Build replacement imports
    const newImports: string[] = [];
    const allUi = [...uiSpecifiers, ...renamedSpecifiers];

    if (allUi.length > 0) {
      const typePrefix = imp.isType ? 'type ' : '';
      if (allUi.length <= 3) {
        newImports.push(`import ${typePrefix}{ ${allUi.join(', ')} } from '@ghatana/ui';`);
      } else {
        const formatted = allUi.map(s => `  ${s},`).join('\n');
        newImports.push(`import ${typePrefix}{\n${formatted}\n} from '@ghatana/ui';`);
      }
    }

    if (yappcSpecifiers.length > 0) {
      const typePrefix = imp.isType ? 'type ' : '';
      if (yappcSpecifiers.length <= 3) {
        newImports.push(`import ${typePrefix}{ ${yappcSpecifiers.join(', ')} } from '@ghatana/yappc-ui';`);
      } else {
        const formatted = yappcSpecifiers.map(s => `  ${s},`).join('\n');
        newImports.push(`import ${typePrefix}{\n${formatted}\n} from '@ghatana/yappc-ui';`);
      }
    }

    if (newImports.length > 0) {
      const replacement = newImports.join('\n');
      if (replacement !== imp.fullMatch) {
        newContent = newContent.replace(imp.fullMatch, replacement);
        changes.push(
          `  Migrated: ${imp.specifiers.join(', ')}` +
          (renamedSpecifiers.length > 0 ? ` (renamed: ${renamedSpecifiers.join(', ')})` : '')
        );
      }
    }
  }

  return { newContent, changes };
}

// ─── Main ─────────────────────────────────────────────────────────────

function main() {
  console.log(`\n🔄 Migrating @ghatana/yappc-ui → @ghatana/ui${DRY_RUN ? ' (DRY RUN)' : ''}\n`);

  const searchDirs = [
    path.join(ROOT, 'products/yappc/frontend'),
    path.join(ROOT, 'products/software-org/client'),
  ];

  const extensions = ['.ts', '.tsx', '.js', '.jsx'];
  let totalFiles = 0;
  let modifiedFiles = 0;
  let totalChanges = 0;

  for (const dir of searchDirs) {
    const files = findFiles(dir, extensions);

    for (const filePath of files) {
      const content = fs.readFileSync(filePath, 'utf-8');

      // Quick check — skip files without @ghatana/yappc-ui imports
      if (!content.includes('@ghatana/yappc-ui')) continue;

      totalFiles++;
      const result = rewriteFile(filePath, content);

      if (result.changes.length > 0) {
        modifiedFiles++;
        totalChanges += result.changes.length;

        const relPath = path.relative(ROOT, filePath);
        console.log(`📝 ${relPath}`);
        result.changes.forEach(c => console.log(c));

        if (!DRY_RUN) {
          fs.writeFileSync(filePath, result.newContent, 'utf-8');
        }
      }
    }
  }

  console.log(`\n✅ Summary:`);
  console.log(`   Files scanned with @ghatana/yappc-ui: ${totalFiles}`);
  console.log(`   Files modified: ${modifiedFiles}`);
  console.log(`   Total import changes: ${totalChanges}`);
  if (DRY_RUN) {
    console.log(`   ⚠️  DRY RUN — no files were actually modified`);
  }
}

main();
