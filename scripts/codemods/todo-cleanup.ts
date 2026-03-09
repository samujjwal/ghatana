#!/usr/bin/env node
/**
 * todo-cleanup.ts — Remove TODO comments left by prior sx→Tailwind codemods.
 *
 * These TODOs have the form:
 *   /* TODO: migrate remaining sx: prop: val, prop: val * /
 *   /* TODO: migrate remaining: prop: val; prop: val * /
 *
 * For each TODO, the script:
 *   1. Parses the CSS properties listed in the comment.
 *   2. Converts convertible properties to Tailwind classes (appended to className).
 *   3. Converts dynamic/complex properties to inline style={{}} props.
 *   4. Removes the TODO comment.
 *
 * Usage:
 *   node --experimental-strip-types scripts/codemods/todo-cleanup.ts [--dry-run] [--target=path]
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ── CLI flags ────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const targetArg = args.find(a => a.startsWith('--target='));
const TARGET = targetArg ? targetArg.replace('--target=', '') : null;

// ── Counters ─────────────────────────────────────────────────────────────────
let totalFiles = 0;
let modifiedFiles = 0;
let todosRemoved = 0;
let classesAdded = 0;
let stylesAdded = 0;
let todosKept = 0;

// ── MUI Palette → Tailwind Color Map ────────────────────────────────────────
const MUI_COLOR_MAP: Record<string, string> = {
  // Action colors
  'action.active': 'gray-600',
  'action.hover': 'gray-100',
  'action.selected': 'gray-200',
  'action.disabled': 'gray-400',
  'action.disabledBackground': 'gray-200',
  // Text colors
  'text.primary': 'gray-900',
  'text.secondary': 'gray-600',
  'text.tertiary': 'gray-500',
  'text.disabled': 'gray-400',
  'text.inverse': 'white',
  // Grey palette
  'grey.50': 'gray-50',
  'grey.100': 'gray-100',
  'grey.200': 'gray-200',
  'grey.300': 'gray-300',
  'grey.400': 'gray-400',
  'grey.500': 'gray-500',
  'grey.600': 'gray-600',
  'grey.700': 'gray-700',
  'grey.800': 'gray-800',
  'grey.900': 'gray-900',
  // Primary
  'primary.main': 'blue-600',
  'primary.light': 'blue-400',
  'primary.dark': 'blue-800',
  'primary.50': 'blue-50',
  'primary.100': 'blue-100',
  // Secondary
  'secondary.main': 'purple-600',
  'secondary.light': 'purple-400',
  'secondary.dark': 'purple-800',
  // Error
  'error.main': 'red-600',
  'error.light': 'red-400',
  'error.dark': 'red-800',
  // Warning
  'warning.main': 'amber-600',
  'warning.light': 'amber-400',
  // Success
  'success.main': 'green-600',
  'success.light': 'green-400',
  // Info
  'info.main': 'sky-600',
  'info.light': 'sky-400',
  'info.50': 'sky-50',
  // Background
  'background.paper': 'white',
  'background.default': 'gray-50',
  // Divider
  'divider': 'gray-200',
};

// ── Canvas Token Constants → Tailwind ────────────────────────────────────────
const SPACING_MAP: Record<string, string> = {
  'SPACING.XXS': '0.5',
  'SPACING.XS': '1',
  'SPACING.SM': '2',
  'SPACING.MD': '4',
  'SPACING.LG': '6',
  'SPACING.XL': '8',
  'SPACING.XXL': '12',
};

// ── Single-Property Converters ───────────────────────────────────────────────
// Returns [tailwindClass | null, styleProperty | null]
type ConvertResult = { tw: string | null; style: string | null };

function convertProperty(prop: string, value: string): ConvertResult {
  const trimProp = prop.trim();
  const trimVal = value.trim().replace(/^['"]|['"]$/g, ''); // strip quotes

  // ── Border shorthand (borderBottom: 1, borderTop: 1, etc.) ─────────────
  if (trimProp === 'borderBottom' && (trimVal === '1' || trimVal === '1px')) {
    return { tw: 'border-b', style: null };
  }
  if (trimProp === 'borderTop' && (trimVal === '1' || trimVal === '1px')) {
    return { tw: 'border-t', style: null };
  }
  if (trimProp === 'borderLeft') {
    if (trimVal === '1' || trimVal === '1px') return { tw: 'border-l', style: null };
    if (trimVal === '3' || trimVal === '3px') return { tw: 'border-l-[3px]', style: null };
    const borderLeftMatch = trimVal.match(/^(\d+)px\s+solid\s+(.+)$/);
    if (borderLeftMatch) {
      return { tw: null, style: `borderLeft: '${trimVal}'` };
    }
    if (trimVal === '4px solid' || trimVal.match(/^\d+$/)) {
      return { tw: null, style: `borderLeft: '${trimVal}px solid'` };
    }
    return { tw: null, style: `borderLeft: '${trimVal}'` };
  }
  if (trimProp === 'borderRight' && (trimVal === '1' || trimVal === '1px')) {
    return { tw: 'border-r', style: null };
  }

  // ── Border style ───────────────────────────────────────────────────────
  if (trimProp === 'borderStyle') {
    if (trimVal === 'dashed') return { tw: 'border-dashed', style: null };
    if (trimVal === 'solid') return { tw: 'border-solid', style: null };
    if (trimVal === 'dotted') return { tw: 'border-dotted', style: null };
    return { tw: null, style: `borderStyle: '${trimVal}'` };
  }

  // ── Border radius ──────────────────────────────────────────────────────
  if (trimProp === 'borderRadius' || trimProp === 'borderTopLeftRadius' || trimProp === 'borderTopRightRadius' || trimProp === 'borderBottomLeftRadius' || trimProp === 'borderBottomRightRadius') {
    if (trimVal.startsWith('var(')) {
      return { tw: null, style: `${trimProp}: '${trimVal}'` };
    }
    const radiusMap: Record<string, string> = {
      '0': 'rounded-none', '2': 'rounded-sm', '4': 'rounded',
      '6': 'rounded-md', '8': 'rounded-lg', '12': 'rounded-xl',
      '16': 'rounded-2xl', '9999': 'rounded-full',
    };
    if (trimProp === 'borderRadius') {
      if (radiusMap[trimVal]) return { tw: radiusMap[trimVal], style: null };
      return { tw: `rounded-[${trimVal}px]`, style: null };
    }
    return { tw: null, style: `${trimProp}: ${isNaN(Number(trimVal)) ? `'${trimVal}'` : trimVal}` };
  }

  // ── Alignment ──────────────────────────────────────────────────────────
  if (trimProp === 'verticalAlign') {
    const vaMap: Record<string, string> = {
      'middle': 'align-middle', 'top': 'align-top', 'bottom': 'align-bottom',
      'baseline': 'align-baseline', 'text-top': 'align-text-top',
    };
    if (vaMap[trimVal]) return { tw: vaMap[trimVal], style: null };
    return { tw: null, style: `verticalAlign: '${trimVal}'` };
  }

  // ── Cursor ─────────────────────────────────────────────────────────────
  if (trimProp === 'cursor') {
    const cursorVals = ['pointer', 'crosshair', 'col-resize', 'move', 'text', 'grab', 'grabbing', 'default', 'not-allowed', 'wait', 'help', 'zoom-in', 'zoom-out', 'none'];
    if (cursorVals.includes(trimVal)) return { tw: `cursor-${trimVal}`, style: null };
    return { tw: null, style: `cursor: '${trimVal}'` };
  }

  // ── Inset ──────────────────────────────────────────────────────────────
  if (trimProp === 'inset') {
    if (trimVal === '0') return { tw: 'inset-0', style: null };
    return { tw: `inset-[${trimVal}${isNaN(Number(trimVal)) ? '' : 'px'}]`, style: null };
  }

  // ── Layout sizing ──────────────────────────────────────────────────────
  if (trimProp === 'maxHeight') {
    return { tw: `max-h-[${trimVal}]`, style: null };
  }
  if (trimProp === 'minWidth') {
    if (trimVal === 'auto') return { tw: 'min-w-0', style: null };
    return { tw: `min-w-[${trimVal}]`, style: null };
  }
  if (trimProp === 'maxWidth') {
    return { tw: `max-w-[${trimVal}]`, style: null };
  }
  if (trimProp === 'width' && !isVariableOrDynamic(value)) {
    return { tw: `w-[${trimVal}]`, style: null };
  }
  if (trimProp === 'height' && !isVariableOrDynamic(value)) {
    return { tw: `h-[${trimVal}]`, style: null };
  }

  // ── Padding/Margin with theme spacing (numeric = *8px in MUI) ──────────
  if (trimProp === 'paddingX' || trimProp === 'px') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `px-${s}`, style: null };
  }
  if (trimProp === 'paddingY' || trimProp === 'py') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `py-${s}`, style: null };
  }
  if (trimProp === 'p' && !isVariableOrDynamic(value)) {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `p-${s}`, style: null };
  }
  if (trimProp === 'pt') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `pt-${s}`, style: null };
  }
  if (trimProp === 'pb') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `pb-${s}`, style: null };
  }
  if (trimProp === 'pl') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `pl-${s}`, style: null };
  }
  if (trimProp === 'pr') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `pr-${s}`, style: null };
  }
  if (trimProp === 'mx' || trimProp === 'marginX') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `mx-${s}`, style: null };
  }
  if (trimProp === 'my' || trimProp === 'marginY') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `my-${s}`, style: null };
  }
  if (trimProp === 'mt') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `mt-${s}`, style: null };
  }
  if (trimProp === 'mb') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `mb-${s}`, style: null };
  }
  if (trimProp === 'ml') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `ml-${s}`, style: null };
  }
  if (trimProp === 'mr') {
    const s = spacingToTw(trimVal);
    if (s) return { tw: `mr-${s}`, style: null };
  }

  // ── Gap ────────────────────────────────────────────────────────────────
  if (trimProp === 'gap') {
    // Handle SPACING constants
    if (SPACING_MAP[trimVal]) return { tw: `gap-${SPACING_MAP[trimVal]}`, style: null };
    const s = spacingToTw(trimVal);
    if (s) return { tw: `gap-${s}`, style: null };
  }

  // ── Letter spacing ────────────────────────────────────────────────────
  if (trimProp === 'letterSpacing') {
    if (trimVal === '0.5') return { tw: 'tracking-wider', style: null };
    if (trimVal === '1') return { tw: 'tracking-widest', style: null };
    return { tw: `tracking-[${trimVal}px]`, style: null };
  }

  // ── Opacity ────────────────────────────────────────────────────────────
  if (trimProp === 'opacity' && !isVariableOrDynamic(value)) {
    const n = parseFloat(trimVal);
    if (!isNaN(n)) return { tw: `opacity-[${n}]`, style: null };
  }

  // ── Font ───────────────────────────────────────────────────────────────
  if (trimProp === 'fontWeight') {
    const fwMap: Record<string, string> = {
      '100': 'font-thin', '200': 'font-extralight', '300': 'font-light',
      '400': 'font-normal', 'normal': 'font-normal', '500': 'font-medium',
      'medium': 'font-medium', '600': 'font-semibold', 'semibold': 'font-semibold',
      '700': 'font-bold', 'bold': 'font-bold', '800': 'font-extrabold',
      '900': 'font-black',
    };
    if (fwMap[trimVal]) return { tw: fwMap[trimVal], style: null };
    // Handle canvas token constants
    if (trimVal === 'FONT_WEIGHT.SEMIBOLD') return { tw: 'font-semibold', style: null };
    if (trimVal === 'FONT_WEIGHT.BOLD') return { tw: 'font-bold', style: null };
    if (trimVal === 'FONT_WEIGHT.MEDIUM') return { tw: 'font-medium', style: null };
    if (trimVal === 'FONT_WEIGHT.NORMAL') return { tw: 'font-normal', style: null };
  }
  if (trimProp === 'fontSize') {
    // Canvas token constants
    if (trimVal === 'TYPOGRAPHY.XS') return { tw: 'text-xs', style: null };
    if (trimVal === 'TYPOGRAPHY.SM') return { tw: 'text-sm', style: null };
    if (trimVal === 'TYPOGRAPHY.BASE') return { tw: 'text-base', style: null };
    if (trimVal === 'TYPOGRAPHY.LG') return { tw: 'text-lg', style: null };
    if (trimVal === 'TYPOGRAPHY.XL') return { tw: 'text-xl', style: null };
    // Numeric px values
    const pxMatch = trimVal.match(/^(\d+(?:\.\d+)?)(px)?$/);
    if (pxMatch) {
      const n = parseFloat(pxMatch[1]);
      const sizeMap: Record<number, string> = {
        10: 'text-[10px]', 11: 'text-[11px]', 12: 'text-xs', 14: 'text-sm',
        16: 'text-base', 18: 'text-lg', 20: 'text-xl', 24: 'text-2xl',
      };
      return { tw: sizeMap[n] || `text-[${n}px]`, style: null };
    }
    // rem
    const remMatch = trimVal.match(/^(\d+(?:\.\d+)?)rem$/);
    if (remMatch) return { tw: `text-[${remMatch[1]}rem]`, style: null };
  }
  if (trimProp === 'fontFamily') {
    return { tw: null, style: `fontFamily: '${trimVal}'` };
  }

  // ── Colors via MUI palette ─────────────────────────────────────────────
  if (trimProp === 'color' || trimProp === 'textColor') {
    if (MUI_COLOR_MAP[trimVal]) return { tw: `text-${MUI_COLOR_MAP[trimVal]}`, style: null };
    // Hex colors
    const hexMatch = trimVal.match(/^#([0-9a-fA-F]{3,8})$/);
    if (hexMatch) return { tw: `text-[${trimVal}]`, style: null };
    // Canvas COLORS constants
    if (trimVal.startsWith('COLORS.')) return { tw: null, style: `color: ${trimVal}` };
    // Dynamic / variable
    if (isVariableOrDynamic(value)) return { tw: null, style: `color: ${value.trim()}` };
    return { tw: null, style: `color: '${trimVal}'` };
  }
  if (trimProp === 'bgcolor' || trimProp === 'backgroundColor' || trimProp === 'background') {
    // MUI palette
    if (MUI_COLOR_MAP[trimVal]) {
      return { tw: `bg-${MUI_COLOR_MAP[trimVal]}`, style: null };
    }
    // Hex colors
    const hexMatch = trimVal.match(/^#([0-9a-fA-F]{3,8})$/);
    if (hexMatch) return { tw: `bg-[${trimVal}]`, style: null };
    // Canvas COLORS constants
    if (trimVal.startsWith('COLORS.')) return { tw: null, style: `backgroundColor: ${trimVal}` };
    // rgba
    if (trimVal.startsWith('rgba(') || trimVal.startsWith('rgb(')) {
      return { tw: null, style: `backgroundColor: '${trimVal}'` };
    }
    // linear-gradient
    if (trimVal.startsWith('linear-gradient(') || trimVal.startsWith('radial-gradient(')) {
      return { tw: null, style: `background: '${trimVal}'` };
    }
    // Dynamic / variable
    if (isVariableOrDynamic(value)) return { tw: null, style: `backgroundColor: ${value.trim()}` };
    return { tw: null, style: `backgroundColor: '${trimVal}'` };
  }
  if (trimProp === 'borderColor') {
    if (MUI_COLOR_MAP[trimVal]) return { tw: `border-${MUI_COLOR_MAP[trimVal]}`, style: null };
    const hexMatch = trimVal.match(/^#([0-9a-fA-F]{3,8})$/);
    if (hexMatch) return { tw: `border-[${trimVal}]`, style: null };
    if (isVariableOrDynamic(value)) return { tw: null, style: `borderColor: ${value.trim()}` };
    return { tw: null, style: `borderColor: '${trimVal}'` };
  }

  // ── Transform ──────────────────────────────────────────────────────────
  if (trimProp === 'transform') {
    return { tw: null, style: `transform: '${trimVal}'` };
  }
  if (trimProp === 'transformOrigin') {
    return { tw: null, style: `transformOrigin: '${trimVal}'` };
  }

  // ── Transition ─────────────────────────────────────────────────────────
  if (trimProp === 'transition') {
    return { tw: null, style: `transition: '${trimVal}'` };
  }

  // ── Box shadow ─────────────────────────────────────────────────────────
  if (trimProp === 'boxShadow') {
    if (isVariableOrDynamic(value)) return { tw: null, style: `boxShadow: ${value.trim()}` };
    return { tw: null, style: `boxShadow: '${trimVal}'` };
  }

  // ── Backdrop filter ────────────────────────────────────────────────────
  if (trimProp === 'backdropFilter') {
    const blurMatch = trimVal.match(/^blur\((\d+)px\)$/);
    if (blurMatch) return { tw: `backdrop-blur-[${blurMatch[1]}px]`, style: null };
    return { tw: null, style: `backdropFilter: '${trimVal}'` };
  }

  // ── Z-index ────────────────────────────────────────────────────────────
  if (trimProp === 'zIndex') {
    if (isVariableOrDynamic(value)) return { tw: null, style: `zIndex: ${value.trim()}` };
    return { tw: `z-[${trimVal}]`, style: null };
  }

  // ── Overflow ───────────────────────────────────────────────────────────
  if (trimProp === 'overflow') {
    const overflows = ['hidden', 'auto', 'scroll', 'visible', 'clip'];
    if (overflows.includes(trimVal)) return { tw: `overflow-${trimVal}`, style: null };
  }
  if (trimProp === 'overflowY') {
    const overflows = ['hidden', 'auto', 'scroll', 'visible'];
    if (overflows.includes(trimVal)) return { tw: `overflow-y-${trimVal}`, style: null };
  }
  if (trimProp === 'overflowX') {
    const overflows = ['hidden', 'auto', 'scroll', 'visible'];
    if (overflows.includes(trimVal)) return { tw: `overflow-x-${trimVal}`, style: null };
  }

  // ── Display ────────────────────────────────────────────────────────────
  if (trimProp === 'display') {
    const displayMap: Record<string, string> = {
      'flex': 'flex', 'block': 'block', 'inline': 'inline',
      'inline-flex': 'inline-flex', 'inline-block': 'inline-block',
      'grid': 'grid', 'none': 'hidden', 'contents': 'contents',
      '-webkit-box': 'line-clamp-2',
    };
    if (displayMap[trimVal]) return { tw: displayMap[trimVal], style: null };
  }

  // ── Flex ───────────────────────────────────────────────────────────────
  if (trimProp === 'flex') {
    return { tw: null, style: `flex: '${trimVal}'` };
  }
  if (trimProp === 'flexShrink' || trimProp === 'shrink') {
    if (trimVal === '0') return { tw: 'shrink-0', style: null };
    return { tw: `shrink-[${trimVal}]`, style: null };
  }
  if (trimProp === 'flexGrow' || trimProp === 'grow') {
    if (trimVal === '0') return { tw: 'grow-0', style: null };
    if (trimVal === '1') return { tw: 'grow', style: null };
    return { tw: `grow-[${trimVal}]`, style: null };
  }
  if (trimProp === 'justifyContent') {
    const jcMap: Record<string, string> = {
      'center': 'justify-center', 'flex-start': 'justify-start',
      'flex-end': 'justify-end', 'space-between': 'justify-between',
      'space-around': 'justify-around', 'space-evenly': 'justify-evenly',
    };
    if (jcMap[trimVal]) return { tw: jcMap[trimVal], style: null };
  }
  if (trimProp === 'alignItems') {
    const aiMap: Record<string, string> = {
      'center': 'items-center', 'flex-start': 'items-start',
      'flex-end': 'items-end', 'stretch': 'items-stretch',
      'baseline': 'items-baseline',
    };
    if (aiMap[trimVal]) return { tw: aiMap[trimVal], style: null };
  }

  // ── Position ───────────────────────────────────────────────────────────
  if (trimProp === 'position') {
    const posMap: Record<string, string> = {
      'relative': 'relative', 'absolute': 'absolute', 'fixed': 'fixed',
      'sticky': 'sticky', 'static': 'static',
    };
    if (posMap[trimVal]) return { tw: posMap[trimVal], style: null };
  }

  // ── Pointer events ────────────────────────────────────────────────────
  if (trimProp === 'pointerEvents') {
    if (trimVal === 'none') return { tw: 'pointer-events-none', style: null };
    if (trimVal === 'auto') return { tw: 'pointer-events-auto', style: null };
  }

  // ── User select ────────────────────────────────────────────────────────
  if (trimProp === 'userSelect') {
    if (trimVal === 'none') return { tw: 'select-none', style: null };
    if (trimVal === 'text') return { tw: 'select-text', style: null };
    if (trimVal === 'all') return { tw: 'select-all', style: null };
  }

  // ── White space ────────────────────────────────────────────────────────
  if (trimProp === 'whiteSpace') {
    if (trimVal === 'nowrap') return { tw: 'whitespace-nowrap', style: null };
    if (trimVal === 'pre-wrap') return { tw: 'whitespace-pre-wrap', style: null };
    if (trimVal === 'pre') return { tw: 'whitespace-pre', style: null };
  }

  // ── Text overflow ──────────────────────────────────────────────────────
  if (trimProp === 'textOverflow') {
    if (trimVal === 'ellipsis') return { tw: 'text-ellipsis', style: null };
  }
  if (trimProp === 'textTransform') {
    if (trimVal === 'uppercase') return { tw: 'uppercase', style: null };
    if (trimVal === 'lowercase') return { tw: 'lowercase', style: null };
    if (trimVal === 'capitalize') return { tw: 'capitalize', style: null };
    if (trimVal === 'none') return { tw: 'normal-case', style: null };
  }
  if (trimProp === 'textAlign') {
    if (['left', 'center', 'right', 'justify'].includes(trimVal)) {
      return { tw: `text-${trimVal}`, style: null };
    }
  }

  // ── WebKit line clamp ──────────────────────────────────────────────────
  if (trimProp === 'WebkitLineClamp') {
    return { tw: `line-clamp-${trimVal}`, style: null };
  }
  if (trimProp === 'WebkitBoxOrient') {
    // line-clamp already handles this
    return { tw: null, style: null };
  }

  // ── Animation ──────────────────────────────────────────────────────────
  if (trimProp === 'animation') {
    return { tw: null, style: `animation: '${trimVal}'` };
  }

  // ── Misc ───────────────────────────────────────────────────────────────
  if (trimProp === 'typography') {
    if (trimVal === 'caption') return { tw: 'text-xs', style: null };
    if (trimVal === 'body2') return { tw: 'text-sm', style: null };
    if (trimVal === 'body1') return { tw: 'text-base', style: null };
    if (trimVal === 'h6') return { tw: 'text-lg font-medium', style: null };
  }

  // ── backgroundSize, backgroundImage ────────────────────────────────────
  if (trimProp === 'backgroundSize') {
    return { tw: null, style: `backgroundSize: '${trimVal}'` };
  }
  if (trimProp === 'backgroundImage') {
    return { tw: null, style: `backgroundImage: '${trimVal}'` };
  }

  // ── Canvas token SPACING constants for spacing props ───────────────────
  // Handle SPACING.XS etc. in p, m, gap, etc.
  if (SPACING_MAP[trimVal]) {
    const spacingProps: Record<string, string> = {
      'p': 'p', 'px': 'px', 'py': 'py', 'pt': 'pt', 'pb': 'pb', 'pl': 'pl', 'pr': 'pr',
      'm': 'm', 'mx': 'mx', 'my': 'my', 'mt': 'mt', 'mb': 'mb', 'ml': 'ml', 'mr': 'mr',
      'gap': 'gap',
    };
    if (spacingProps[trimProp]) {
      return { tw: `${spacingProps[trimProp]}-${SPACING_MAP[trimVal]}`, style: null };
    }
  }

  // ── Canvas COLORS constants ────────────────────────────────────────────
  if (trimVal.startsWith('COLORS.')) {
    // Map common COLORS constants to Tailwind
    const colorConstMap: Record<string, { text: string; bg: string; border: string }> = {
      'COLORS.TEXT_PRIMARY': { text: 'text-gray-900', bg: 'bg-gray-900', border: 'border-gray-900' },
      'COLORS.TEXT_SECONDARY': { text: 'text-gray-500', bg: 'bg-gray-500', border: 'border-gray-500' },
      'COLORS.TEXT_DISABLED': { text: 'text-gray-400', bg: 'bg-gray-400', border: 'border-gray-400' },
      'COLORS.TEXT_INVERSE': { text: 'text-white', bg: 'bg-white', border: 'border-white' },
      'COLORS.NEUTRAL_100': { text: 'text-gray-100', bg: 'bg-gray-100', border: 'border-gray-100' },
      'COLORS.NEUTRAL_200': { text: 'text-gray-200', bg: 'bg-gray-200', border: 'border-gray-200' },
      'COLORS.BORDER_LIGHT': { text: 'text-gray-200', bg: 'bg-gray-200', border: 'border-gray-200' },
      'COLORS.PANEL_BG_LIGHT': { text: 'text-white', bg: 'bg-white', border: 'border-white' },
      'COLORS.SELECTION_BG': { text: 'text-blue-100', bg: 'bg-blue-100', border: 'border-blue-100' },
      'COLORS.SUCCESS': { text: 'text-green-600', bg: 'bg-green-600', border: 'border-green-600' },
      'COLORS.ERROR': { text: 'text-red-600', bg: 'bg-red-600', border: 'border-red-600' },
      'COLORS.WARNING': { text: 'text-amber-600', bg: 'bg-amber-600', border: 'border-amber-600' },
    };
    const c = colorConstMap[trimVal];
    if (c) {
      if (trimProp === 'color') return { tw: c.text, style: null };
      if (trimProp === 'bgcolor' || trimProp === 'backgroundColor') return { tw: c.bg, style: null };
      if (trimProp === 'borderColor') return { tw: c.border, style: null };
    }
    // Fall through to style for unknown COLORS constants
    const cssProp = trimProp === 'bgcolor' ? 'backgroundColor' : trimProp;
    return { tw: null, style: `${cssProp}: ${trimVal}` };
  }

  // ── Canvas TYPOGRAPHY constants for fontSize ───────────────────────────
  if (trimVal.startsWith('TYPOGRAPHY.')) {
    if (trimProp === 'fontSize') {
      const typMap: Record<string, string> = {
        'TYPOGRAPHY.XS': 'text-xs', 'TYPOGRAPHY.SM': 'text-sm',
        'TYPOGRAPHY.BASE': 'text-base', 'TYPOGRAPHY.LG': 'text-lg',
        'TYPOGRAPHY.XL': 'text-xl',
      };
      if (typMap[trimVal]) return { tw: typMap[trimVal], style: null };
    }
  }

  // ── Canvas RADIUS constants ────────────────────────────────────────────
  if (trimVal.startsWith('RADIUS.')) {
    if (trimProp === 'borderRadius') {
      const radMap: Record<string, string> = {
        'RADIUS.SM': 'rounded-sm', 'RADIUS.MD': 'rounded-md',
        'RADIUS.LG': 'rounded-lg', 'RADIUS.XL': 'rounded-xl',
        'RADIUS.FULL': 'rounded-full', 'RADIUS.NONE': 'rounded-none',
      };
      if (radMap[trimVal]) return { tw: radMap[trimVal], style: null };
    }
  }

  // ── Canvas SHADOWS constants ───────────────────────────────────────────
  if (trimVal.startsWith('SHADOWS.')) {
    if (trimProp === 'boxShadow') {
      const shadowMap: Record<string, string> = {
        'SHADOWS.SM': 'shadow-sm', 'SHADOWS.MD': 'shadow-md',
        'SHADOWS.LG': 'shadow-lg', 'SHADOWS.XL': 'shadow-xl',
        'SHADOWS.NONE': 'shadow-none',
      };
      if (shadowMap[trimVal]) return { tw: shadowMap[trimVal], style: null };
    }
  }

  // ── Fallback: if it's a known CSS property with a static value ─────────
  // Return as style prop
  if (!isVariableOrDynamic(value)) {
    const cssProp = trimProp === 'bgcolor' ? 'backgroundColor' : trimProp;
    return { tw: null, style: `${cssProp}: '${trimVal}'` };
  }

  // Dynamic value → style prop
  const cssProp = trimProp === 'bgcolor' ? 'backgroundColor' : trimProp;
  return { tw: null, style: `${cssProp}: ${value.trim()}` };
}

// ── Helper: MUI spacing number → Tailwind class suffix ──────────────────────
function spacingToTw(val: string): string | null {
  // MUI spacing: 1 = 8px, 0.5 = 4px, 2 = 16px, etc.
  // Tailwind: 1 = 0.25rem (4px), 2 = 0.5rem (8px), etc.
  // So MUI spacing n → Tailwind spacing n*2
  const n = parseFloat(val);
  if (isNaN(n)) {
    // Check SPACING constants
    if (SPACING_MAP[val]) return SPACING_MAP[val];
    return null;
  }
  // MUI spacing to Tailwind: 0.5→1, 1→2, 1.5→3, 2→4, 3→6, 4→8, ...
  const tw = n * 2;
  if (tw === 0) return '0';
  if (tw % 1 === 0) return String(tw);
  return `[${n * 8}px]`;
}

// ── Helper: Detect dynamic/variable values ───────────────────────────────────
function isVariableOrDynamic(value: string): boolean {
  const v = value.trim();
  // Variable references, function calls, ternaries, template literals
  return /^[A-Z_]/.test(v) ||    // Constant/variable (COLORS.PRIMARY, etc.)
         v.includes('(') ||       // Function call (alpha(), theme.fn(), etc.)
         v.includes('?') ||       // Ternary
         v.includes('`') ||       // Template literal
         v.includes('${') ||      // Template expression
         /\b(theme|props|state|config|data|item|event)\b/.test(v); // common dynamic refs
}

// ══════════════════════════════════════════════════════════════════════════════
// MAIN PROCESSING
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Parse properties from a TODO comment body.
 * Handles: "borderBottom: 1, color: '#fff'" 
 * and: "borderBottom: 1; color: '#fff'"
 * and: "'& .MuiChip-icon': { color: '#fff' }"
 * and multiline
 */
function parseTodoProperties(body: string): Array<{ prop: string; value: string; isPseudo: boolean }> {
  const results: Array<{ prop: string; value: string; isPseudo: boolean }> = [];
  
  // Check for pseudo-selector / nested selector patterns
  // These start with '&' or '& .' - we'll handle them separately
  const pseudoMatch = body.match(/^['"]?(&[^'"]*?)['"]?\s*:\s*\{/);
  if (pseudoMatch) {
    // This is a pseudo-selector block - mark as pseudo
    results.push({ prop: body.trim(), value: '', isPseudo: true });
    return results;
  }

  // Check for @keyframes pattern
  if (body.includes('@keyframes')) {
    results.push({ prop: body.trim(), value: '', isPseudo: true });
    return results;
  }

  // Split by comma or semicolon, but not inside quotes or parens
  const parts = smartSplit(body);
  
  for (const part of parts) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    
    // Check for nested selector: '& .MuiXxx': { ... }
    if (trimmed.startsWith("'&") || trimmed.startsWith('"&') || trimmed.startsWith('&')) {
      results.push({ prop: trimmed, value: '', isPseudo: true });
      continue;
    }

    // Normal property: key: value
    const colonIdx = trimmed.indexOf(':');
    if (colonIdx === -1) continue;
    
    const prop = trimmed.substring(0, colonIdx).trim().replace(/^['"]|['"]$/g, '');
    const value = trimmed.substring(colonIdx + 1).trim();
    
    if (prop && value) {
      results.push({ prop, value, isPseudo: false });
    }
  }
  
  return results;
}

/**
 * Smart split by comma/semicolon, respecting quotes and parens.
 */
function smartSplit(s: string): string[] {
  const results: string[] = [];
  let current = '';
  let depth = 0;
  let inSingleQuote = false;
  let inDoubleQuote = false;
  
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    
    if (ch === "'" && !inDoubleQuote) { inSingleQuote = !inSingleQuote; current += ch; continue; }
    if (ch === '"' && !inSingleQuote) { inDoubleQuote = !inDoubleQuote; current += ch; continue; }
    if (inSingleQuote || inDoubleQuote) { current += ch; continue; }
    
    if (ch === '(' || ch === '{' || ch === '[') { depth++; current += ch; continue; }
    if (ch === ')' || ch === '}' || ch === ']') { depth--; current += ch; continue; }
    
    if (depth === 0 && (ch === ',' || ch === ';')) {
      if (current.trim()) results.push(current.trim());
      current = '';
      continue;
    }
    
    current += ch;
  }
  
  if (current.trim()) results.push(current.trim());
  return results;
}

/**
 * Process a single file: find all TODO comments and resolve them.
 */
function processFile(filePath: string): boolean {
  let content = fs.readFileSync(filePath, 'utf-8');
  const original = content;
  
  // Pattern to match TODO comments - both variants
  // Variant 1: /* TODO: migrate remaining sx: ... */
  // Variant 2: /* TODO: migrate remaining: ... */
  // These can span multiple lines
  const todoRegex = /\/\*\s*TODO:\s*migrate\s+remaining(?:\s+sx)?:\s*([\s\S]*?)\*\//g;
  
  let match: RegExpExecArray | null;
  const replacements: Array<{ start: number; end: number; todoBody: string }> = [];
  
  while ((match = todoRegex.exec(content)) !== null) {
    replacements.push({
      start: match.index,
      end: match.index + match[0].length,
      todoBody: match[1],
    });
  }
  
  if (replacements.length === 0) return false;
  
  // Process replacements in reverse order (so indices stay valid)
  for (let i = replacements.length - 1; i >= 0; i--) {
    const { start, end, todoBody } = replacements[i];
    const todoComment = content.substring(start, end);
    
    // Parse properties from the TODO body
    const properties = parseTodoProperties(todoBody);
    
    // Check if ALL properties are pseudo/nested selectors or complex
    const allPseudo = properties.length > 0 && properties.every(p => p.isPseudo);
    
    // Collect Tailwind classes and style props
    const twClasses: string[] = [];
    const styleProps: string[] = [];
    let hasUnresolvable = false;
    
    for (const { prop, value, isPseudo } of properties) {
      if (isPseudo) {
        // Try to handle simple pseudo patterns
        const simplePseudo = tryConvertPseudo(prop, value);
        if (simplePseudo.tw) twClasses.push(simplePseudo.tw);
        else if (simplePseudo.style) styleProps.push(simplePseudo.style);
        else hasUnresolvable = true;
        continue;
      }
      
      const result = convertProperty(prop, value);
      if (result.tw) twClasses.push(result.tw);
      if (result.style) styleProps.push(result.style);
    }
    
    // If we couldn't resolve anything meaningful, keep the TODO (but mark as unresolved)
    if (twClasses.length === 0 && styleProps.length === 0 && hasUnresolvable) {
      todosKept++;
      continue;
    }
    
    // Find the line containing the TODO comment
    const lineStart = content.lastIndexOf('\n', start) + 1;
    const lineEnd = content.indexOf('\n', end);
    const line = content.substring(lineStart, lineEnd === -1 ? content.length : lineEnd);
    
    // Determine what to do:
    // 1. Add Tailwind classes to existing className
    // 2. Add/merge style props
    // 3. Remove the TODO comment
    
    let newContent = content.substring(0, start) + content.substring(end);
    
    // Clean up any trailing/leading whitespace from removing the comment
    // Handle case: className="..." /* TODO */ >  →  className="..." >
    newContent = newContent.substring(0, start).replace(/\s+$/, ' ') + 
                 newContent.substring(start).replace(/^\s+/, '');
    
    // Now prepend Tailwind classes to className if any
    if (twClasses.length > 0) {
      const classesToAdd = twClasses.join(' ');
      
      // Find className in the same element (look backwards from the TODO position)
      const beforeTodo = newContent.substring(Math.max(0, start - 2000), start);
      const classNameMatch = beforeTodo.match(/className="([^"]*)"(?=[^"]*$)/);
      
      if (classNameMatch) {
        const existingClasses = classNameMatch[1];
        const newClasses = `${existingClasses} ${classesToAdd}`;
        const classNameStart = beforeTodo.lastIndexOf(classNameMatch[0]);
        const absoluteStart = Math.max(0, start - 2000) + classNameStart;
        
        newContent = newContent.substring(0, absoluteStart) +
                    `className="${newClasses}"` +
                    newContent.substring(absoluteStart + classNameMatch[0].length);
        
        classesAdded += twClasses.length;
      }
    }
    
    // Add style props if any
    if (styleProps.length > 0) {
      const styleStr = styleProps.join(', ');
      
      // Check if there's already a style={{}} prop
      const afterTodoRemoval = newContent;
      const searchStart = Math.max(0, start - 2000);
      const searchRegion = afterTodoRemoval.substring(searchStart, start + 500);
      
      const existingStyleMatch = searchRegion.match(/style=\{\{([^}]*)\}\}/);
      
      if (existingStyleMatch) {
        // Merge into existing style
        const existingStyle = existingStyleMatch[1].trim();
        const merged = existingStyle ? `${existingStyle}, ${styleStr}` : styleStr;
        const styleStart = searchStart + searchRegion.indexOf(existingStyleMatch[0]);
        newContent = afterTodoRemoval.substring(0, styleStart) +
                    `style={{ ${merged} }}` +
                    afterTodoRemoval.substring(styleStart + existingStyleMatch[0].length);
      } else {
        // Add style prop after className
        const beforePos = newContent.substring(Math.max(0, start - 2000), start);
        const classNamePos = beforePos.lastIndexOf('className="');
        if (classNamePos >= 0) {
          // Find end of className value
          const absClassNameStart = Math.max(0, start - 2000) + classNamePos;
          const afterClassName = newContent.substring(absClassNameStart);
          const endQuote = afterClassName.indexOf('"', 11); // skip className="
          if (endQuote >= 0) {
            const insertPos = absClassNameStart + endQuote + 1;
            newContent = newContent.substring(0, insertPos) +
                        ` style={{ ${styleStr} }}` +
                        newContent.substring(insertPos);
          }
        }
      }
      
      stylesAdded += styleProps.length;
    }
    
    content = newContent;
    todosRemoved++;
  }
  
  if (content !== original) {
    if (!DRY_RUN) {
      fs.writeFileSync(filePath, content, 'utf-8');
    }
    return true;
  }
  
  return false;
}

/**
 * Try to convert simple pseudo-selector patterns to Tailwind.
 */
function tryConvertPseudo(prop: string, _value: string): ConvertResult {
  // Handle patterns like: '&:hover': { backgroundColor: 'action.hover' }
  // Handle patterns like: '& .MuiChip-icon': { color: '#fff' }
  // These are in the raw prop string from the TODO body
  
  // For now, we can't reliably parse these from the truncated TODO comments
  // Just mark as needing manual review
  return { tw: null, style: null };
}

// ── File walker ──────────────────────────────────────────────────────────────
function walkDir(dir: string, extensions: string[]): string[] {
  const files: string[] = [];
  
  if (!fs.existsSync(dir)) return files;
  
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'build' || entry.name === 'dist') continue;
    
    if (entry.isDirectory()) {
      files.push(...walkDir(fullPath, extensions));
    } else if (entry.isFile() && extensions.some(ext => entry.name.endsWith(ext))) {
      files.push(fullPath);
    }
  }
  
  return files;
}

// ── Main ─────────────────────────────────────────────────────────────────────
function main() {
  const rootDir = path.resolve(__dirname, '../..');
  const frontendDir = path.join(rootDir, 'products/yappc/frontend');
  
  let targetDir = frontendDir;
  if (TARGET) {
    targetDir = path.resolve(rootDir, TARGET);
  }
  
  console.log(`\n🔧 TODO Comment Cleanup Codemod`);
  console.log(`   Target: ${targetDir}`);
  console.log(`   Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log('');
  
  const files = walkDir(targetDir, ['.tsx', '.ts']);
  
  for (const file of files) {
    totalFiles++;
    const content = fs.readFileSync(file, 'utf-8');
    if (!content.includes('TODO: migrate remaining')) continue;
    
    const modified = processFile(file);
    if (modified) {
      modifiedFiles++;
      const relPath = path.relative(rootDir, file);
      console.log(`  ✅ ${relPath}`);
    }
  }
  
  console.log(`\n══════════════════════════════════════`);
  console.log(`  Files scanned:    ${totalFiles}`);
  console.log(`  Files modified:   ${modifiedFiles}`);
  console.log(`  TODOs removed:    ${todosRemoved}`);
  console.log(`  Classes added:    ${classesAdded}`);
  console.log(`  Styles added:     ${stylesAdded}`);
  console.log(`  TODOs kept:       ${todosKept}`);
  console.log(`══════════════════════════════════════\n`);
}

main();
