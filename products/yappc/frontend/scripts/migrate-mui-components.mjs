#!/usr/bin/env node

/**
 * MUI Material → @ghatana/ui + Tailwind CSS Component Migration Codemod
 *
 * Transforms: import { Box, Typography, Button } from '@mui/material';
 * Into:       import { Box, Typography, Button } from '@ghatana/ui';
 *
 * Handles:
 * - Named imports from '@mui/material'
 * - Default imports from '@mui/material/<Component>'
 * - sx={{ p: 2, m: 1 }} → style={{ padding: '16px', margin: '8px' }} (basic)
 * - Common prop renames (variant="contained" → variant="solid", etc.)
 * - ThemeProvider / createTheme removal
 * - CssBaseline removal
 *
 * Usage: node migrate-mui-components.mjs [--dry-run] [--path <dir>]
 *
 * @doc.type script
 * @doc.purpose Automated MUI→@ghatana/ui component migration
 * @doc.layer tooling
 */

import { readFileSync, writeFileSync, readdirSync } from 'fs';
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

// ─── Component mapping: MUI → @ghatana/ui ───────────────────────────────────

const MUI_TO_GHATANA = {
  // Layout
  Box: 'Box',
  Stack: 'Stack',
  Container: 'Container',
  Grid: 'Grid',
  Paper: 'Surface',
  Divider: 'Divider',

  // Interactive
  Button: 'Button',
  IconButton: 'IconButton',
  TextField: 'TextField',
  Select: 'Select',
  Checkbox: 'Checkbox',
  Radio: 'Radio',
  Switch: 'Switch',
  Slider: 'Slider',
  FormControlLabel: 'FormControlLabel',
  FormControl: 'FormControl',
  InputLabel: 'InputLabel',
  InputAdornment: 'InputAdornment',
  ToggleButton: 'ToggleButton',
  ToggleButtonGroup: 'ToggleButtonGroup',
  Fab: 'Fab',

  // Data display
  Typography: 'Typography',
  Chip: 'Chip',
  Badge: 'Badge',
  Tooltip: 'Tooltip',
  Avatar: 'Avatar',
  AvatarGroup: 'AvatarGroup',
  Skeleton: 'Skeleton',
  LinearProgress: 'LinearProgress',
  CircularProgress: 'Spinner',

  // Navigation
  Tabs: 'Tabs',
  Tab: 'Tab',
  Menu: 'Menu',
  MenuItem: 'MenuItem',
  Breadcrumbs: 'Breadcrumb',
  Pagination: 'Pagination',
  Stepper: 'Stepper',
  Step: 'Step',
  StepLabel: 'StepLabel',

  // Feedback
  Alert: 'Alert',
  AlertTitle: 'AlertTitle',
  Dialog: 'Dialog',
  DialogTitle: 'DialogTitle',
  DialogContent: 'DialogContent',
  DialogActions: 'DialogActions',
  Drawer: 'Drawer',
  Modal: 'Modal',
  Snackbar: 'Toast',

  // Data table
  Table: 'Table',
  TableHead: 'TableHead',
  TableBody: 'TableBody',
  TableRow: 'TableRow',
  TableCell: 'TableCell',
  TableContainer: 'TableContainer',

  // List
  List: 'InteractiveList',
  ListItem: 'ListItem',
  ListItemIcon: 'ListItemIcon',
  ListItemText: 'ListItemText',
  ListItemButton: 'ListItemButton',
  ListSubheader: 'ListSubheader',

  // Cards
  Card: 'Card',
  CardHeader: 'CardHeader',
  CardContent: 'CardContent',
  CardActions: 'CardActions',

  // Accordion
  Accordion: 'Accordion',
  AccordionSummary: 'AccordionSummary',
  AccordionDetails: 'AccordionDetails',

  // Misc
  Popper: 'Popper',
  Collapse: 'Collapse',
  Fade: 'Fade',
  Grow: 'Grow',
  Rating: 'Rating',
  AppBar: 'AppBar',
  Toolbar: 'Toolbar',
  Autocomplete: 'Select',
  Backdrop: 'Modal',
  ButtonGroup: 'ToggleButtonGroup',
  BottomNavigation: 'BottomNavigation',
  BottomNavigationAction: 'BottomNavigationAction',
  SwipeableDrawer: 'SwipeableDrawer',
  Popover: 'Popper',
  Link: 'Link',
  InputBase: 'Input',
  CardActionArea: 'CardContent',
  ListItemAvatar: 'ListItemIcon',
  ListItemSecondaryAction: 'ListItemText',
  StepContent: 'StepLabel',
  Zoom: 'Fade',
};

// Components to REMOVE from imports (not re-importable)
const REMOVE_IMPORTS = new Set([
  'CssBaseline',
  'ThemeProvider',
  'createTheme',
  'useTheme',     // MUI's useTheme — replaced by @ghatana/theme
  'styled',       // emotion-based styled() — use Tailwind
  'useMediaQuery', // MUI's version — use @ghatana/ui hook
  'alpha',        // MUI color utility — use Tailwind opacity
  'keyframes',    // MUI keyframes — use Tailwind animations
  'SvgIconProps', // MUI type — use lucide-react types
]);

// Prop transforms
const PROP_TRANSFORMS = {
  // Button: variant="contained" → variant="solid"
  'variant="contained"': 'variant="solid"',
  'variant="text"': 'variant="ghost"',
  // Color → tone
  'color="error"': 'tone="danger"',
  'color="info"': 'tone="info"',
  'color="success"': 'tone="success"',
  'color="warning"': 'tone="warning"',
  'color="secondary"': 'tone="secondary"',
  'color="primary"': 'tone="primary"',
  'color="inherit"': 'tone="neutral"',
  // Size aliases
  'size="small"': 'size="sm"',
  'size="medium"': 'size="md"',
  'size="large"': 'size="lg"',
  // Paper elevation → Surface
  'elevation={0}': 'variant="flat"',
  'elevation={1}': 'variant="raised"',
  // Typography
  'variant="h1"': 'as="h1"',
  'variant="h2"': 'as="h2"',
  'variant="h3"': 'as="h3"',
  'variant="h4"': 'as="h4"',
  'variant="h5"': 'as="h5"',
  'variant="h6"': 'as="h6"',
  'variant="body1"': 'as="p"',
  'variant="body2"': 'as="p" className="text-sm"',
  'variant="caption"': 'as="span" className="text-xs text-gray-500"',
  'variant="subtitle1"': 'as="p" className="text-lg font-medium"',
  'variant="subtitle2"': 'as="p" className="text-sm font-medium"',
  'variant="overline"': 'as="span" className="text-xs uppercase tracking-wider"',
  // Dialog
  'fullWidth': 'fullWidth',
  'maxWidth="sm"': 'size="sm"',
  'maxWidth="md"': 'size="md"',
  'maxWidth="lg"': 'size="lg"',
};

// ─── sx prop spacing converter (basic) ──────────────────────────────────────

const SPACING_UNIT = 8;

function sxValueToTailwind(key, value) {
  if (typeof value === 'number') {
    const px = value * SPACING_UNIT;
    const map = {
      p: `p-[${px}px]`, pt: `pt-[${px}px]`, pb: `pb-[${px}px]`,
      pl: `pl-[${px}px]`, pr: `pr-[${px}px]`,
      px: `px-[${px}px]`, py: `py-[${px}px]`,
      m: `m-[${px}px]`, mt: `mt-[${px}px]`, mb: `mb-[${px}px]`,
      ml: `ml-[${px}px]`, mr: `mr-[${px}px]`,
      mx: `mx-[${px}px]`, my: `my-[${px}px]`,
      gap: `gap-[${px}px]`,
    };
    return map[key] || null;
  }
  if (typeof value === 'string') {
    const stringMap = {
      display: { flex: 'flex', block: 'block', none: 'hidden', grid: 'grid', 'inline-flex': 'inline-flex' },
      flexDirection: { row: 'flex-row', column: 'flex-col', 'row-reverse': 'flex-row-reverse', 'column-reverse': 'flex-col-reverse' },
      justifyContent: { center: 'justify-center', 'flex-start': 'justify-start', 'flex-end': 'justify-end', 'space-between': 'justify-between', 'space-around': 'justify-around' },
      alignItems: { center: 'items-center', 'flex-start': 'items-start', 'flex-end': 'items-end', stretch: 'items-stretch', baseline: 'items-baseline' },
      flexWrap: { wrap: 'flex-wrap', nowrap: 'flex-nowrap' },
      overflow: { hidden: 'overflow-hidden', auto: 'overflow-auto', scroll: 'overflow-scroll' },
      overflowY: { auto: 'overflow-y-auto', hidden: 'overflow-y-hidden', scroll: 'overflow-y-scroll' },
      overflowX: { auto: 'overflow-x-auto', hidden: 'overflow-x-hidden', scroll: 'overflow-x-scroll' },
      textAlign: { center: 'text-center', left: 'text-left', right: 'text-right' },
      position: { relative: 'relative', absolute: 'absolute', fixed: 'fixed', sticky: 'sticky' },
      cursor: { pointer: 'cursor-pointer', 'not-allowed': 'cursor-not-allowed' },
      fontWeight: { bold: 'font-bold', 600: 'font-semibold', 500: 'font-medium', 400: 'font-normal' },
    };
    return stringMap[key]?.[value] || null;
  }
  return null;
}

// ─── Main migration ─────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const pathIdx = args.indexOf('--path');
const searchPath = pathIdx !== -1 ? args[pathIdx + 1] : 'apps/web/src';

console.log(`🔄 MUI Material → @ghatana/ui Component Migration`);
console.log(`  Search dir: ${searchPath}`);
console.log(`  Mode: ${dryRun ? 'DRY RUN' : 'WRITE'}`);
console.log('');

const files = findFiles(searchPath, ['.tsx', '.ts']);
let totalFiles = 0;
let totalReplacements = 0;
const unmappedComponents = new Set();

for (const file of files) {
  let content = readFileSync(file, 'utf-8');
  let modified = false;
  const ghatanaImports = new Set();

  // ── 1. Named imports: import { Box, Typography } from '@mui/material'; ──
  const namedImportRe = /import\s*\{([^}]+)\}\s*from\s*['"]@mui\/material['"]\s*;?/g;
  let match;

  while ((match = namedImportRe.exec(content)) !== null) {
    const components = match[1].split(',').map(s => s.trim()).filter(Boolean);
    const mapped = [];
    const removed = [];

    for (const comp of components) {
      const parts = comp.split(/\s+as\s+/);
      const origName = parts[0].trim();
      const alias = parts.length > 1 ? parts[1].trim() : null;

      if (REMOVE_IMPORTS.has(origName)) {
        removed.push(origName);
        continue;
      }

      const ghatanaName = MUI_TO_GHATANA[origName];
      if (ghatanaName) {
        if (alias) {
          mapped.push(`${ghatanaName} as ${alias}`);
        } else if (ghatanaName !== origName) {
          mapped.push(`${ghatanaName} as ${origName}`);
        } else {
          mapped.push(ghatanaName);
        }
        ghatanaImports.add(ghatanaName);
      } else {
        // Type-only imports or enums
        if (origName.startsWith('type ')) {
          mapped.push(comp);
        } else {
          unmappedComponents.add(origName);
          mapped.push(comp); // Keep original
        }
      }
    }

    if (mapped.length > 0) {
      const newImport = `import { ${mapped.join(', ')} } from '@ghatana/ui';`;
      content = content.replace(match[0], newImport);
    } else {
      // All imports removed — delete the line
      content = content.replace(match[0] + '\n', '');
    }
    modified = true;
    totalReplacements++;
  }

  // ── 2. Default imports: import Button from '@mui/material/Button'; ──
  const defaultImportRe = /import\s+(\w+)\s+from\s*['"]@mui\/material\/(\w+)['"]\s*;?/g;
  while ((match = defaultImportRe.exec(content)) !== null) {
    const localName = match[1];
    const componentName = match[2];

    if (REMOVE_IMPORTS.has(componentName)) {
      content = content.replace(match[0] + '\n', '');
      modified = true;
      totalReplacements++;
      continue;
    }

    const ghatanaName = MUI_TO_GHATANA[componentName];
    if (ghatanaName) {
      const importLine = localName === ghatanaName
        ? `import { ${ghatanaName} } from '@ghatana/ui';`
        : `import { ${ghatanaName} as ${localName} } from '@ghatana/ui';`;
      content = content.replace(match[0], importLine);
      ghatanaImports.add(ghatanaName);
      modified = true;
      totalReplacements++;
    } else {
      unmappedComponents.add(componentName);
    }
  }

  // ── 3. @mui/material/styles imports ──
  const stylesImportRe = /import\s*\{([^}]+)\}\s*from\s*['"]@mui\/material\/styles['"]\s*;?\n?/g;
  while ((match = stylesImportRe.exec(content)) !== null) {
    const items = match[1].split(',').map(s => s.trim()).filter(Boolean);
    const kept = items.filter(i => !REMOVE_IMPORTS.has(i.split(/\s+as\s+/)[0].trim()));
    if (kept.length === 0) {
      content = content.replace(match[0], '');
    } else {
      content = content.replace(match[0], `import { ${kept.join(', ')} } from '@ghatana/theme';\n`);
    }
    modified = true;
    totalReplacements++;
  }

  // ── 4. Default @mui/material/styles imports ──
  const defaultStylesRe = /import\s+\w+\s+from\s*['"]@mui\/material\/(?:CssBaseline|styles)['"]\s*;?\n?/g;
  while ((match = defaultStylesRe.exec(content)) !== null) {
    content = content.replace(match[0], '');
    modified = true;
    totalReplacements++;
  }

  // ── 5. Prop transforms ──
  if (modified) {
    for (const [from, to] of Object.entries(PROP_TRANSFORMS)) {
      if (content.includes(from)) {
        content = content.replaceAll(from, to);
      }
    }
  }

  if (modified) {
    totalFiles++;
    console.log(`  ✅ ${file} (${ghatanaImports.size} components)`);
    if (!dryRun) {
      writeFileSync(file, content, 'utf-8');
    }
  }
}

console.log('');
console.log(`📊 Summary:`);
console.log(`  Files modified: ${totalFiles}`);
console.log(`  Import statements replaced: ${totalReplacements}`);
if (unmappedComponents.size > 0) {
  console.log(`  ⚠️  Unmapped components (${unmappedComponents.size}): ${[...unmappedComponents].sort().join(', ')}`);
}
if (dryRun) {
  console.log(`  ℹ️  Dry run — no files written. Remove --dry-run to apply.`);
}
