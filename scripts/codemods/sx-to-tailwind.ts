#!/usr/bin/env tsx
/**
 * Codemod: Convert MUI sx= props to Tailwind className
 * 
 * Transforms simple sx={{ ... }} patterns into className="..." with Tailwind classes.
 * Complex or dynamic sx props are left as-is with a TODO comment.
 * 
 * Usage: npx tsx scripts/codemods/sx-to-tailwind.ts [--dry-run]
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const DRY_RUN = process.argv.includes('--dry-run');
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');

// ─── MUI sx → Tailwind Mapping ───────────────────────────────────────

// Spacing scale: MUI uses 8px base, Tailwind uses 4px base (1 unit = 0.25rem)
// MUI 1 = 8px = Tailwind 2 (0.5rem)
function muiSpacingToTw(value: number | string): string {
  if (typeof value === 'string') return value;
  const twValue = value * 2; // MUI 1 = 8px, TW 2 = 8px (0.5rem)
  // Handle common fractional values
  if (twValue === 0) return '0';
  if (twValue === 0.5) return '0.5';
  if (twValue === 1) return '1';
  if (twValue === 1.5) return '1.5';
  if (Number.isInteger(twValue) && twValue <= 96) return String(twValue);
  return `[${value * 8}px]`; // Arbitrary value fallback
}

// Static property → Tailwind class mapping
const SX_TO_TAILWIND: Record<string, (value: string) => string | null> = {
  // Display
  "display: 'flex'": () => 'flex',
  "display: 'block'": () => 'block',
  "display: 'inline-flex'": () => 'inline-flex',
  "display: 'inline'": () => 'inline',
  "display: 'inline-block'": () => 'inline-block',
  "display: 'grid'": () => 'grid',
  "display: 'none'": () => 'hidden',

  // Flex direction
  "flexDirection: 'column'": () => 'flex-col',
  "flexDirection: 'row'": () => 'flex-row',
  "flexDirection: 'column-reverse'": () => 'flex-col-reverse',
  "flexDirection: 'row-reverse'": () => 'flex-row-reverse',

  // Align
  "alignItems: 'center'": () => 'items-center',
  "alignItems: 'flex-start'": () => 'items-start',
  "alignItems: 'flex-end'": () => 'items-end',
  "alignItems: 'stretch'": () => 'items-stretch',
  "alignItems: 'baseline'": () => 'items-baseline',

  // Justify
  "justifyContent: 'center'": () => 'justify-center',
  "justifyContent: 'flex-start'": () => 'justify-start',
  "justifyContent: 'flex-end'": () => 'justify-end',
  "justifyContent: 'space-between'": () => 'justify-between',
  "justifyContent: 'space-around'": () => 'justify-around',
  "justifyContent: 'space-evenly'": () => 'justify-evenly',

  // Flex
  "flexWrap: 'wrap'": () => 'flex-wrap',
  "flexWrap: 'nowrap'": () => 'flex-nowrap',
  "flexGrow: 1": () => 'grow',
  "flexShrink: 0": () => 'shrink-0',
  "flex: 1": () => 'flex-1',

  // Position
  "position: 'relative'": () => 'relative',
  "position: 'absolute'": () => 'absolute',
  "position: 'fixed'": () => 'fixed',
  "position: 'sticky'": () => 'sticky',

  // Overflow
  "overflow: 'hidden'": () => 'overflow-hidden',
  "overflow: 'auto'": () => 'overflow-auto',
  "overflow: 'scroll'": () => 'overflow-scroll',
  "overflow: 'visible'": () => 'overflow-visible',
  "overflowY: 'auto'": () => 'overflow-y-auto',
  "overflowX: 'auto'": () => 'overflow-x-auto',
  "overflowX: 'hidden'": () => 'overflow-x-hidden',
  "overflowY: 'hidden'": () => 'overflow-y-hidden',

  // Text
  "textAlign: 'center'": () => 'text-center',
  "textAlign: 'left'": () => 'text-left',
  "textAlign: 'right'": () => 'text-right',
  "textDecoration: 'none'": () => 'no-underline',
  "textDecoration: 'underline'": () => 'underline',
  "textTransform: 'none'": () => 'normal-case',
  "textTransform: 'uppercase'": () => 'uppercase',
  "textTransform: 'lowercase'": () => 'lowercase',
  "textTransform: 'capitalize'": () => 'capitalize',
  "whiteSpace: 'nowrap'": () => 'whitespace-nowrap',
  "whiteSpace: 'pre-wrap'": () => 'whitespace-pre-wrap',
  "textOverflow: 'ellipsis'": () => 'text-ellipsis',
  "wordBreak: 'break-all'": () => 'break-all',
  "wordBreak: 'break-word'": () => 'break-words',

  // Width/Height
  "width: '100%'": () => 'w-full',
  "height: '100%'": () => 'h-full',
  "width: 'auto'": () => 'w-auto',
  "height: 'auto'": () => 'h-auto',
  "minWidth: 0": () => 'min-w-0',
  "minHeight: 0": () => 'min-h-0',
  "width: '100vw'": () => 'w-screen',
  "height: '100vh'": () => 'h-screen',
  "maxWidth: '100%'": () => 'max-w-full',

  // Cursor
  "cursor: 'pointer'": () => 'cursor-pointer',
  "cursor: 'default'": () => 'cursor-default',
  "cursor: 'grab'": () => 'cursor-grab',
  "cursor: 'not-allowed'": () => 'cursor-not-allowed',

  // Border
  "borderRadius: 1": () => 'rounded',
  "borderRadius: 2": () => 'rounded-lg',
  "borderRadius: '50%'": () => 'rounded-full',
  "border: 'none'": () => 'border-0',

  // Visibility
  "visibility: 'hidden'": () => 'invisible',
  "visibility: 'visible'": () => 'visible',
  "opacity: 0": () => 'opacity-0',
  "opacity: 1": () => 'opacity-100',
  "pointerEvents: 'none'": () => 'pointer-events-none',

  // Colors — MUI palette → Tailwind
  "bgcolor: 'background.paper'": () => 'bg-white dark:bg-gray-900',
  "bgcolor: 'background.default'": () => 'bg-gray-50 dark:bg-gray-950',
  "bgcolor: 'primary.main'": () => 'bg-blue-600',
  "bgcolor: 'primary.light'": () => 'bg-blue-100 dark:bg-blue-900/30',
  "bgcolor: 'primary.dark'": () => 'bg-blue-800',
  "bgcolor: 'secondary.main'": () => 'bg-indigo-600',
  "bgcolor: 'secondary.light'": () => 'bg-indigo-100 dark:bg-indigo-900/30',
  "bgcolor: 'secondary.dark'": () => 'bg-indigo-800',
  "bgcolor: 'success.main'": () => 'bg-green-600',
  "bgcolor: 'success.light'": () => 'bg-green-100 dark:bg-green-900/30',
  "bgcolor: 'error.main'": () => 'bg-red-600',
  "bgcolor: 'error.light'": () => 'bg-red-100 dark:bg-red-900/30',
  "bgcolor: 'warning.main'": () => 'bg-amber-600',
  "bgcolor: 'warning.light'": () => 'bg-amber-100 dark:bg-amber-900/30',
  "bgcolor: 'info.main'": () => 'bg-sky-600',
  "bgcolor: 'info.light'": () => 'bg-sky-100 dark:bg-sky-900/30',
  "bgcolor: 'grey.50'": () => 'bg-gray-50 dark:bg-gray-800',
  "bgcolor: 'grey.100'": () => 'bg-gray-100 dark:bg-gray-800',
  "bgcolor: 'grey.200'": () => 'bg-gray-200 dark:bg-gray-700',
  "bgcolor: 'action.hover'": () => 'bg-gray-100 dark:bg-gray-800',
  "bgcolor: 'action.selected'": () => 'bg-blue-50 dark:bg-blue-900/20',
  "color: 'primary.main'": () => 'text-blue-600',
  "color: 'primary.contrastText'": () => 'text-white',
  "color: 'secondary.main'": () => 'text-indigo-600',
  "color: 'secondary.contrastText'": () => 'text-white',
  "color: 'success.main'": () => 'text-green-600',
  "color: 'success.contrastText'": () => 'text-white',
  "color: 'error.main'": () => 'text-red-600',
  "color: 'error.contrastText'": () => 'text-white',
  "color: 'warning.main'": () => 'text-amber-600',
  "color: 'warning.contrastText'": () => 'text-gray-900',
  "color: 'info.main'": () => 'text-sky-600',
  "color: 'info.contrastText'": () => 'text-white',
  "color: 'text.primary'": () => 'text-gray-900 dark:text-gray-100',
  "color: 'text.secondary'": () => 'text-gray-500 dark:text-gray-400',
  "color: 'text.disabled'": () => 'text-gray-400 dark:text-gray-600',
  "borderColor: 'divider'": () => 'border-gray-200 dark:border-gray-700',
  "borderColor: 'primary.main'": () => 'border-blue-600',

  // Width/Height additional
  "minWidth: 'max-content'": () => 'min-w-max',
  "maxWidth: 'sm'": () => 'max-w-sm',
  "maxWidth: 'md'": () => 'max-w-md',
  "maxWidth: 'lg'": () => 'max-w-lg',
  "maxWidth: 'xl'": () => 'max-w-xl',
  "maxWidth: 'none'": () => 'max-w-none',

  // Transitions
  "transition: 'all 0.2s'": () => 'transition-all duration-200',
  "transition: 'all 0.3s'": () => 'transition-all duration-300',

  // Transform
  "transform: 'scale(0.8)'": () => 'scale-[0.8]',
  "transform: 'rotate(3deg)'": () => 'rotate-3',

  // Border width
  "border: '1px solid'": () => 'border border-solid',
  "borderBottom: 'none'": () => 'border-b-0',
  "borderTop: 'none'": () => 'border-t-0',

  // Cursor additional
  "cursor: 'help'": () => 'cursor-help',
  "cursor: 'grabbing'": () => 'cursor-grabbing',
  "cursor: 'move'": () => 'cursor-move',
  "cursor: 'text'": () => 'cursor-text',

  // Line height
  "lineHeight: 1": () => 'leading-none',
  "lineHeight: 1.2": () => 'leading-tight',
  "lineHeight: 1.5": () => 'leading-normal',
  "lineHeight: 2": () => 'leading-loose',

  // User select
  "userSelect: 'none'": () => 'select-none',
};

// Numeric property patterns
const NUMERIC_PROPS: Record<string, (v: number) => string> = {
  'p': (v) => `p-${muiSpacingToTw(v)}`,
  'px': (v) => `px-${muiSpacingToTw(v)}`,
  'py': (v) => `py-${muiSpacingToTw(v)}`,
  'pt': (v) => `pt-${muiSpacingToTw(v)}`,
  'pb': (v) => `pb-${muiSpacingToTw(v)}`,
  'pl': (v) => `pl-${muiSpacingToTw(v)}`,
  'pr': (v) => `pr-${muiSpacingToTw(v)}`,
  'm': (v) => `m-${muiSpacingToTw(v)}`,
  'mx': (v) => `mx-${muiSpacingToTw(v)}`,
  'my': (v) => `my-${muiSpacingToTw(v)}`,
  'mt': (v) => `mt-${muiSpacingToTw(v)}`,
  'mb': (v) => `mb-${muiSpacingToTw(v)}`,
  'ml': (v) => `ml-${muiSpacingToTw(v)}`,
  'mr': (v) => `mr-${muiSpacingToTw(v)}`,
  'gap': (v) => `gap-${muiSpacingToTw(v)}`,
  'top': (v) => `top-${muiSpacingToTw(v)}`,
  'bottom': (v) => `bottom-${muiSpacingToTw(v)}`,
  'left': (v) => `left-${muiSpacingToTw(v)}`,
  'right': (v) => `right-${muiSpacingToTw(v)}`,
  'flexGrow': (v) => v === 1 ? 'grow' : `grow-[${v}]`,
  'flexShrink': (v) => v === 0 ? 'shrink-0' : `shrink-[${v}]`,
  'zIndex': (v) => `z-[${v}]`,
  'borderRadius': (v) => {
    const map: Record<number, string> = { 0: 'rounded-none', 1: 'rounded', 1.5: 'rounded-md', 2: 'rounded-lg', 3: 'rounded-xl', 4: 'rounded-2xl' };
    return map[v] || `rounded-[${v * 8}px]`;
  },
  'minWidth': (v) => `min-w-[${v}px]`,
  'maxWidth': (v) => `max-w-[${v}px]`,
  'minHeight': (v) => `min-h-[${v}px]`,
  'maxHeight': (v) => `max-h-[${v}px]`,
  'width': (v) => `w-[${v}px]`,
  'height': (v) => `h-[${v}px]`,
  'opacity': (v) => `opacity-[${v}]`,
};

// Font size mapping (px → Tailwind)
const FONT_SIZES: Record<string, string> = {
  "'10px'": 'text-[10px]',
  "'11px'": 'text-[11px]',
  "'12px'": 'text-xs',
  "'13px'": 'text-[13px]',
  "'14px'": 'text-sm',
  "'16px'": 'text-base',
  "'18px'": 'text-lg',
  "'20px'": 'text-xl',
  "'24px'": 'text-2xl',
  "'30px'": 'text-3xl',
  "'36px'": 'text-4xl',
  "'0.625rem'": 'text-[0.625rem]',
  "'0.7rem'": 'text-[0.7rem]',
  "'0.75rem'": 'text-xs',
  "'0.8rem'": 'text-[0.8rem]',
  "'0.85rem'": 'text-[0.85rem]',
  "'0.875rem'": 'text-sm',
  "'1rem'": 'text-base',
  "'1.125rem'": 'text-lg',
  "'1.25rem'": 'text-xl',
  "'1.5rem'": 'text-2xl',
  "'2rem'": 'text-[2rem]',
  "'2.5rem'": 'text-[2.5rem]',
  "'3rem'": 'text-5xl',
};

// Font weight mapping
const FONT_WEIGHTS: Record<string, string> = {
  '300': 'font-light',
  '400': 'font-normal',
  '500': 'font-medium',
  '600': 'font-semibold',
  '700': 'font-bold',
  '800': 'font-extrabold',
  "'bold'": 'font-bold',
  "'normal'": 'font-normal',
};

// ─── Simple sx Parser ─────────────────────────────────────────────────

/**
 * Attempts to parse a simple sx={{ ... }} into Tailwind classes.
 * Returns null if the sx object is too complex (dynamic values, nested, etc.)
 */
function parseSxToTailwind(sxContent: string): { classes: string; unconverted: string[] } | null {
  const classes: string[] = [];
  const unconverted: string[] = [];

  // Remove surrounding braces if present
  let inner = sxContent.trim();
  if (inner.startsWith('{') && inner.endsWith('}')) {
    inner = inner.slice(1, -1).trim();
  }

  // Quick bail: if it contains dynamic expressions, template literals, ternaries, function calls
  if (
    inner.includes('${') ||
    inner.includes('(') && !inner.includes("'") ||
    inner.includes('...') ||
    inner.includes('?') ||
    inner.includes('&&') ||
    inner.includes('||') ||
    inner.match(/\[.*\]:/) // computed keys
  ) {
    return null;
  }

  // Split by commas (simple approach — won't work for nested objects)
  // This handles: display: 'flex', alignItems: 'center', gap: 2
  const propRegex = /(\w+)\s*:\s*([^,}]+)/g;
  let match;

  while ((match = propRegex.exec(inner)) !== null) {
    const prop = match[1].trim();
    const rawValue = match[2].trim();

    // Check static mapping first
    const staticKey = `${prop}: ${rawValue}`;
    if (SX_TO_TAILWIND[staticKey]) {
      const twClass = SX_TO_TAILWIND[staticKey](rawValue);
      if (twClass) classes.push(twClass);
      continue;
    }

    // Check numeric props
    if (NUMERIC_PROPS[prop]) {
      const numVal = parseFloat(rawValue);
      if (!isNaN(numVal)) {
        classes.push(NUMERIC_PROPS[prop](numVal));
        continue;
      }
    }

    // Font size
    if (prop === 'fontSize') {
      if (FONT_SIZES[rawValue]) {
        classes.push(FONT_SIZES[rawValue]);
        continue;
      }
    }

    // Font weight
    if (prop === 'fontWeight') {
      if (FONT_WEIGHTS[rawValue]) {
        classes.push(FONT_WEIGHTS[rawValue]);
        continue;
      }
    }

    // If we can't convert, track it
    unconverted.push(`${prop}: ${rawValue}`);
  }

  if (classes.length === 0 && unconverted.length > 0) {
    return null; // Nothing converted
  }

  return { classes: classes.join(' '), unconverted };
}

// ─── File Processor ───────────────────────────────────────────────────

interface SxResult {
  converted: number;
  skipped: number;
  todoAdded: number;
}

function processSxProps(filePath: string, content: string): { newContent: string; result: SxResult } {
  const result: SxResult = { converted: 0, skipped: 0, todoAdded: 0 };

  // Match sx={{ ... }} — simple single-level objects only
  // This is intentionally conservative: only matches sx={{ key: value, ... }}
  // Skips sx with variables, nested objects, arrays, etc.
  const sxRegex = /sx=\{\{([^{}]*)\}\}/g;

  const newContent = content.replace(sxRegex, (fullMatch, innerContent: string) => {
    const parsed = parseSxToTailwind(innerContent);

    if (!parsed) {
      result.skipped++;
      return fullMatch; // Leave as-is
    }

    if (parsed.unconverted.length > 0) {
      // Partial conversion: convert what we can, leave the rest in style
      const styleProps = parsed.unconverted.join(', ');
      result.todoAdded++;
      return `className="${parsed.classes}" /* TODO: migrate remaining sx: ${styleProps} */`;
    }

    // Full conversion
    result.converted++;
    return `className="${parsed.classes}"`;
  });

  return { newContent, result };
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

// ─── Main ─────────────────────────────────────────────────────────────

function main() {
  console.log(`\n🎨 Converting sx= props to Tailwind className${DRY_RUN ? ' (DRY RUN)' : ''}\n`);

  const searchDirs = [
    path.join(ROOT, 'products/yappc/frontend'),
  ];

  const extensions = ['.tsx', '.jsx'];
  let totalFiles = 0;
  let totalConverted = 0;
  let totalSkipped = 0;
  let totalTodo = 0;

  for (const dir of searchDirs) {
    const files = findFiles(dir, extensions);

    for (const filePath of files) {
      const content = fs.readFileSync(filePath, 'utf-8');

      // Quick check — skip files without sx=
      if (!content.includes('sx={')) continue;

      totalFiles++;
      const { newContent, result } = processSxProps(filePath, content);

      if (result.converted > 0 || result.todoAdded > 0) {
        const relPath = path.relative(ROOT, filePath);
        console.log(
          `📝 ${relPath}: ${result.converted} converted, ` +
          `${result.todoAdded} partial, ${result.skipped} skipped`
        );

        if (!DRY_RUN) {
          fs.writeFileSync(filePath, newContent, 'utf-8');
        }
      }

      totalConverted += result.converted;
      totalSkipped += result.skipped;
      totalTodo += result.todoAdded;
    }
  }

  console.log(`\n✅ Summary:`);
  console.log(`   Files with sx= props: ${totalFiles}`);
  console.log(`   Fully converted: ${totalConverted}`);
  console.log(`   Partially converted (with TODO): ${totalTodo}`);
  console.log(`   Skipped (complex/dynamic): ${totalSkipped}`);
  if (DRY_RUN) {
    console.log(`   ⚠️  DRY RUN — no files were actually modified`);
  }
}

main();
