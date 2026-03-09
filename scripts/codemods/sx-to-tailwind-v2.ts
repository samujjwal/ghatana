#!/usr/bin/env tsx
/**
 * Codemod v2: Convert MUI sx= props to Tailwind className (Enhanced)
 *
 * Handles:
 * - Multi-line sx blocks
 * - String CSS values (color: '#hex', backgroundColor: '#hex')
 * - Long-form spacing (marginTop, paddingLeft, etc.)
 * - fontSize as numbers/strings
 * - fontFamily mapping
 * - Pseudo-selectors (&:hover, &:last-child, etc.)
 * - Responsive breakpoints ({ xs: ..., md: ... })
 * - Simple ternary expressions → clsx()
 * - Existing TODO comments from prior partial conversions
 *
 * Usage: node --experimental-strip-types scripts/codemods/sx-to-tailwind-v2.ts [--dry-run] [--target=path]
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const DRY_RUN = process.argv.includes('--dry-run');
const TARGET_ARG = process.argv.find((a) => a.startsWith('--target='));
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '../..');

// ─── Spacing utilities ────────────────────────────────────────────────

function muiSpacingToTw(value: number | string): string {
  if (typeof value === 'string') {
    // Handle 'auto'
    if (value === 'auto') return 'auto';
    // Handle string px/rem values
    return `[${value}]`;
  }
  const twValue = value * 2; // MUI 1 = 8px, TW 2 = 8px
  if (twValue === 0) return '0';
  if (twValue === 0.5) return '0.5';
  if (twValue === 1) return '1';
  if (twValue === 1.5) return '1.5';
  if (Number.isInteger(twValue) && twValue <= 96) return String(twValue);
  return `[${value * 8}px]`;
}

// ─── Property converters ──────────────────────────────────────────────

type Converter = (value: string) => string | null;

/** Convert a raw CSS value (might be quoted string, number, etc.) */
function unquote(v: string): string {
  const t = v.trim();
  if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith('"') && t.endsWith('"'))) {
    return t.slice(1, -1);
  }
  return t;
}

function isNumeric(v: string): boolean {
  return !isNaN(parseFloat(v.trim())) && isFinite(Number(v.trim()));
}

// ─── fontSize mapping ─────────────────────────────────────────────────

const FONT_SIZE_PX: Record<number, string> = {
  10: 'text-[10px]',
  11: 'text-[11px]',
  12: 'text-xs',
  13: 'text-[13px]',
  14: 'text-sm',
  16: 'text-base',
  18: 'text-lg',
  20: 'text-xl',
  24: 'text-2xl',
  28: 'text-[28px]',
  30: 'text-3xl',
  32: 'text-[32px]',
  36: 'text-4xl',
  40: 'text-[40px]',
  48: 'text-5xl',
  56: 'text-[56px]',
  64: 'text-[64px]',
};

const FONT_SIZE_REM: Record<string, string> = {
  '0.625rem': 'text-[0.625rem]',
  '0.7rem': 'text-[0.7rem]',
  '0.75rem': 'text-xs',
  '0.8rem': 'text-[0.8rem]',
  '0.85rem': 'text-[0.85rem]',
  '0.875rem': 'text-sm',
  '1rem': 'text-base',
  '1.125rem': 'text-lg',
  '1.25rem': 'text-xl',
  '1.5rem': 'text-2xl',
  '2rem': 'text-[2rem]',
  '2.5rem': 'text-[2.5rem]',
  '3rem': 'text-5xl',
  '3.75rem': 'text-6xl',
};

// ─── Property → Tailwind class converters ─────────────────────────────

const PROP_CONVERTERS: Record<string, Converter> = {
  // ─── Display ────────────────────────────────────────────────────────
  display: (v) => {
    const map: Record<string, string> = {
      flex: 'flex',
      block: 'block',
      'inline-flex': 'inline-flex',
      inline: 'inline',
      'inline-block': 'inline-block',
      grid: 'grid',
      none: 'hidden',
      contents: 'contents',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Flex ───────────────────────────────────────────────────────────
  flexDirection: (v) => {
    const map: Record<string, string> = {
      column: 'flex-col',
      row: 'flex-row',
      'column-reverse': 'flex-col-reverse',
      'row-reverse': 'flex-row-reverse',
    };
    return map[unquote(v)] ?? null;
  },
  alignItems: (v) => {
    const map: Record<string, string> = {
      center: 'items-center',
      'flex-start': 'items-start',
      'flex-end': 'items-end',
      stretch: 'items-stretch',
      baseline: 'items-baseline',
    };
    return map[unquote(v)] ?? null;
  },
  justifyContent: (v) => {
    const map: Record<string, string> = {
      center: 'justify-center',
      'flex-start': 'justify-start',
      'flex-end': 'justify-end',
      'space-between': 'justify-between',
      'space-around': 'justify-around',
      'space-evenly': 'justify-evenly',
    };
    return map[unquote(v)] ?? null;
  },
  flexWrap: (v) => {
    const map: Record<string, string> = { wrap: 'flex-wrap', nowrap: 'flex-nowrap' };
    return map[unquote(v)] ?? null;
  },
  flexGrow: (v) => (v.trim() === '1' ? 'grow' : `grow-[${v.trim()}]`),
  flexShrink: (v) => (v.trim() === '0' ? 'shrink-0' : `shrink-[${v.trim()}]`),
  flex: (v) => (v.trim() === '1' ? 'flex-1' : null),
  alignSelf: (v) => {
    const map: Record<string, string> = {
      center: 'self-center',
      'flex-start': 'self-start',
      'flex-end': 'self-end',
      stretch: 'self-stretch',
      auto: 'self-auto',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Position ───────────────────────────────────────────────────────
  position: (v) => {
    const map: Record<string, string> = {
      relative: 'relative',
      absolute: 'absolute',
      fixed: 'fixed',
      sticky: 'sticky',
      static: 'static',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Spacing: MUI shorthand ────────────────────────────────────────
  p: (v) => {
    if (isNumeric(v)) return `p-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `p-[${u}]`;
    return null;
  },
  px: (v) => (isNumeric(v) ? `px-${muiSpacingToTw(parseFloat(v))}` : null),
  py: (v) => (isNumeric(v) ? `py-${muiSpacingToTw(parseFloat(v))}` : null),
  pt: (v) => (isNumeric(v) ? `pt-${muiSpacingToTw(parseFloat(v))}` : null),
  pb: (v) => (isNumeric(v) ? `pb-${muiSpacingToTw(parseFloat(v))}` : null),
  pl: (v) => (isNumeric(v) ? `pl-${muiSpacingToTw(parseFloat(v))}` : null),
  pr: (v) => (isNumeric(v) ? `pr-${muiSpacingToTw(parseFloat(v))}` : null),
  m: (v) => (isNumeric(v) ? `m-${muiSpacingToTw(parseFloat(v))}` : null),
  mx: (v) => {
    if (unquote(v) === 'auto') return 'mx-auto';
    return isNumeric(v) ? `mx-${muiSpacingToTw(parseFloat(v))}` : null;
  },
  my: (v) => (isNumeric(v) ? `my-${muiSpacingToTw(parseFloat(v))}` : null),
  mt: (v) => {
    if (unquote(v) === 'auto') return 'mt-auto';
    return isNumeric(v) ? `mt-${muiSpacingToTw(parseFloat(v))}` : null;
  },
  mb: (v) => (isNumeric(v) ? `mb-${muiSpacingToTw(parseFloat(v))}` : null),
  ml: (v) => {
    if (unquote(v) === 'auto') return 'ml-auto';
    return isNumeric(v) ? `ml-${muiSpacingToTw(parseFloat(v))}` : null;
  },
  mr: (v) => {
    if (unquote(v) === 'auto') return 'mr-auto';
    return isNumeric(v) ? `mr-${muiSpacingToTw(parseFloat(v))}` : null;
  },

  // ─── Spacing: long-form ────────────────────────────────────────────
  padding: (v) => {
    if (isNumeric(v)) return `p-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px')) return `p-[${u}]`;
    if (u.endsWith('rem')) return `p-[${u}]`;
    return null;
  },
  paddingTop: (v) => {
    if (isNumeric(v)) return `pt-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `pt-[${u}]`;
    return null;
  },
  paddingBottom: (v) => {
    if (isNumeric(v)) return `pb-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `pb-[${u}]`;
    return null;
  },
  paddingLeft: (v) => {
    if (isNumeric(v)) return `pl-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `pl-[${u}]`;
    return null;
  },
  paddingRight: (v) => {
    if (isNumeric(v)) return `pr-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `pr-[${u}]`;
    return null;
  },
  margin: (v) => {
    if (isNumeric(v)) return `m-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u === '0 auto') return 'mx-auto';
    if (u.endsWith('px') || u.endsWith('rem')) return `m-[${u}]`;
    return null;
  },
  marginTop: (v) => {
    if (isNumeric(v)) return `mt-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u === 'auto') return 'mt-auto';
    if (u.endsWith('px') || u.endsWith('rem')) return `mt-[${u}]`;
    return null;
  },
  marginBottom: (v) => {
    if (isNumeric(v)) return `mb-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `mb-[${u}]`;
    return null;
  },
  marginLeft: (v) => {
    if (isNumeric(v)) return `ml-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u === 'auto') return 'ml-auto';
    if (u.endsWith('px') || u.endsWith('rem')) return `ml-[${u}]`;
    return null;
  },
  marginRight: (v) => {
    if (isNumeric(v)) return `mr-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u === 'auto') return 'mr-auto';
    if (u.endsWith('px') || u.endsWith('rem')) return `mr-[${u}]`;
    return null;
  },

  // ─── Gap ────────────────────────────────────────────────────────────
  gap: (v) => {
    if (isNumeric(v)) return `gap-${muiSpacingToTw(parseFloat(v))}`;
    const u = unquote(v);
    if (u.endsWith('px')) return `gap-[${u}]`;
    return null;
  },
  rowGap: (v) => (isNumeric(v) ? `gap-y-${muiSpacingToTw(parseFloat(v))}` : null),
  columnGap: (v) => (isNumeric(v) ? `gap-x-${muiSpacingToTw(parseFloat(v))}` : null),

  // ─── Width / Height ────────────────────────────────────────────────
  width: (v) => {
    const u = unquote(v);
    const map: Record<string, string> = {
      '100%': 'w-full',
      auto: 'w-auto',
      '100vw': 'w-screen',
      'fit-content': 'w-fit',
      'max-content': 'w-max',
      'min-content': 'w-min',
    };
    if (map[u]) return map[u];
    if (isNumeric(v)) return `w-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%') || u.endsWith('vw') || u.includes('calc'))
      return `w-[${u}]`;
    return null;
  },
  height: (v) => {
    const u = unquote(v);
    const map: Record<string, string> = {
      '100%': 'h-full',
      auto: 'h-auto',
      '100vh': 'h-screen',
      'fit-content': 'h-fit',
    };
    if (map[u]) return map[u];
    if (isNumeric(v)) return `h-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%') || u.endsWith('vh') || u.includes('calc'))
      return `h-[${u}]`;
    return null;
  },
  minWidth: (v) => {
    const u = unquote(v);
    if (u === '0' || v.trim() === '0') return 'min-w-0';
    if (u === 'max-content') return 'min-w-max';
    if (u === '100%') return 'min-w-full';
    if (isNumeric(v)) return `min-w-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `min-w-[${u}]`;
    return null;
  },
  maxWidth: (v) => {
    const u = unquote(v);
    const map: Record<string, string> = {
      '100%': 'max-w-full',
      none: 'max-w-none',
      sm: 'max-w-sm',
      md: 'max-w-md',
      lg: 'max-w-lg',
      xl: 'max-w-xl',
    };
    if (map[u]) return map[u];
    if (isNumeric(v)) return `max-w-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `max-w-[${u}]`;
    return null;
  },
  minHeight: (v) => {
    const u = unquote(v);
    if (u === '0' || v.trim() === '0') return 'min-h-0';
    if (u === '100%') return 'min-h-full';
    if (u === '100vh') return 'min-h-screen';
    if (isNumeric(v)) return `min-h-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%') || u.includes('calc'))
      return `min-h-[${u}]`;
    return null;
  },
  maxHeight: (v) => {
    const u = unquote(v);
    if (u === '100%') return 'max-h-full';
    if (u === '100vh') return 'max-h-screen';
    if (u === 'none') return 'max-h-none';
    if (isNumeric(v)) return `max-h-[${v.trim()}px]`;
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%') || u.includes('calc'))
      return `max-h-[${u}]`;
    return null;
  },

  // ─── Position offsets ──────────────────────────────────────────────
  top: (v) => {
    if (isNumeric(v)) return `top-[${v.trim()}px]`;
    const u = unquote(v);
    if (u === '0') return 'top-0';
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `top-[${u}]`;
    return null;
  },
  bottom: (v) => {
    if (isNumeric(v)) return `bottom-[${v.trim()}px]`;
    const u = unquote(v);
    if (u === '0') return 'bottom-0';
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `bottom-[${u}]`;
    return null;
  },
  left: (v) => {
    if (isNumeric(v)) return `left-[${v.trim()}px]`;
    const u = unquote(v);
    if (u === '0') return 'left-0';
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `left-[${u}]`;
    return null;
  },
  right: (v) => {
    if (isNumeric(v)) return `right-[${v.trim()}px]`;
    const u = unquote(v);
    if (u === '0') return 'right-0';
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('%')) return `right-[${u}]`;
    return null;
  },

  // ─── z-index ───────────────────────────────────────────────────────
  zIndex: (v) => {
    if (isNumeric(v)) {
      const n = parseInt(v.trim());
      const map: Record<number, string> = { 0: 'z-0', 10: 'z-10', 20: 'z-20', 30: 'z-30', 40: 'z-40', 50: 'z-50' };
      return map[n] ?? `z-[${n}]`;
    }
    return null;
  },

  // ─── Overflow ──────────────────────────────────────────────────────
  overflow: (v) => {
    const map: Record<string, string> = {
      hidden: 'overflow-hidden',
      auto: 'overflow-auto',
      scroll: 'overflow-scroll',
      visible: 'overflow-visible',
    };
    return map[unquote(v)] ?? null;
  },
  overflowX: (v) => {
    const map: Record<string, string> = {
      hidden: 'overflow-x-hidden',
      auto: 'overflow-x-auto',
      scroll: 'overflow-x-scroll',
    };
    return map[unquote(v)] ?? null;
  },
  overflowY: (v) => {
    const map: Record<string, string> = {
      hidden: 'overflow-y-hidden',
      auto: 'overflow-y-auto',
      scroll: 'overflow-y-scroll',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Text ──────────────────────────────────────────────────────────
  textAlign: (v) => {
    const map: Record<string, string> = {
      center: 'text-center',
      left: 'text-left',
      right: 'text-right',
      justify: 'text-justify',
    };
    return map[unquote(v)] ?? null;
  },
  textDecoration: (v) => {
    const map: Record<string, string> = {
      none: 'no-underline',
      underline: 'underline',
      'line-through': 'line-through',
    };
    return map[unquote(v)] ?? null;
  },
  textTransform: (v) => {
    const map: Record<string, string> = {
      none: 'normal-case',
      uppercase: 'uppercase',
      lowercase: 'lowercase',
      capitalize: 'capitalize',
    };
    return map[unquote(v)] ?? null;
  },
  whiteSpace: (v) => {
    const map: Record<string, string> = {
      nowrap: 'whitespace-nowrap',
      'pre-wrap': 'whitespace-pre-wrap',
      pre: 'whitespace-pre',
      normal: 'whitespace-normal',
    };
    return map[unquote(v)] ?? null;
  },
  textOverflow: (v) => (unquote(v) === 'ellipsis' ? 'text-ellipsis' : null),
  wordBreak: (v) => {
    const map: Record<string, string> = { 'break-all': 'break-all', 'break-word': 'break-words' };
    return map[unquote(v)] ?? null;
  },
  lineHeight: (v) => {
    if (isNumeric(v)) {
      const n = parseFloat(v);
      const map: Record<number, string> = { 1: 'leading-none', 1.2: 'leading-tight', 1.25: 'leading-tight', 1.5: 'leading-normal', 1.6: 'leading-relaxed', 1.75: 'leading-relaxed', 2: 'leading-loose' };
      return map[n] ?? `leading-[${n}]`;
    }
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('rem')) return `leading-[${u}]`;
    return null;
  },
  letterSpacing: (v) => {
    const u = unquote(v);
    if (u.endsWith('px') || u.endsWith('em') || u.endsWith('rem')) return `tracking-[${u}]`;
    return null;
  },

  // ─── Font ──────────────────────────────────────────────────────────
  fontSize: (v) => {
    if (isNumeric(v)) {
      const n = parseFloat(v);
      return FONT_SIZE_PX[n] ?? `text-[${n}px]`;
    }
    const u = unquote(v);
    if (FONT_SIZE_REM[u]) return FONT_SIZE_REM[u];
    if (u.endsWith('px') || u.endsWith('rem') || u.endsWith('em')) return `text-[${u}]`;
    return null;
  },
  fontWeight: (v) => {
    const map: Record<string, string> = {
      '100': 'font-thin',
      '200': 'font-extralight',
      '300': 'font-light',
      '400': 'font-normal',
      '500': 'font-medium',
      '600': 'font-semibold',
      '700': 'font-bold',
      '800': 'font-extrabold',
      '900': 'font-black',
      bold: 'font-bold',
      normal: 'font-normal',
    };
    return map[unquote(v)] ?? (isNumeric(v) ? map[v.trim()] : null) ?? null;
  },
  fontFamily: (v) => {
    const u = unquote(v);
    if (u === 'monospace' || u.includes('monospace')) return 'font-mono';
    if (u === 'serif') return 'font-serif';
    if (u === 'sans-serif') return 'font-sans';
    return null;
  },
  fontStyle: (v) => {
    const map: Record<string, string> = { italic: 'italic', normal: 'not-italic' };
    return map[unquote(v)] ?? null;
  },

  // ─── Color ─────────────────────────────────────────────────────────
  color: (v) => {
    const u = unquote(v);
    // MUI palette tokens
    const palette: Record<string, string> = {
      'primary.main': 'text-blue-600',
      'primary.contrastText': 'text-white',
      'primary.light': 'text-blue-400',
      'primary.dark': 'text-blue-800',
      'secondary.main': 'text-indigo-600',
      'secondary.contrastText': 'text-white',
      'success.main': 'text-green-600',
      'success.contrastText': 'text-white',
      'error.main': 'text-red-600',
      'error.contrastText': 'text-white',
      'warning.main': 'text-amber-600',
      'warning.contrastText': 'text-gray-900',
      'info.main': 'text-sky-600',
      'info.contrastText': 'text-white',
      'text.primary': 'text-gray-900 dark:text-gray-100',
      'text.secondary': 'text-gray-500 dark:text-gray-400',
      'text.disabled': 'text-gray-400 dark:text-gray-600',
    };
    if (palette[u]) return palette[u];
    if (u === 'inherit') return 'text-inherit';
    if (u === 'white') return 'text-white';
    if (u === 'black') return 'text-black';
    if (u === 'transparent') return 'text-transparent';
    if (u === 'currentColor') return 'text-current';
    if (u === 'gold') return 'text-[gold]';
    // Hex colors
    if (u.match(/^#[0-9a-fA-F]{3,8}$/)) return `text-[${u}]`;
    // rgba colors
    if (u.startsWith('rgba(') || u.startsWith('rgb(')) return `text-[${u.replace(/\s+/g, '_')}]`;
    return null;
  },
  bgcolor: (v) => {
    const u = unquote(v);
    const palette: Record<string, string> = {
      'background.paper': 'bg-white dark:bg-gray-900',
      'background.default': 'bg-gray-50 dark:bg-gray-950',
      'primary.main': 'bg-blue-600',
      'primary.light': 'bg-blue-100 dark:bg-blue-900/30',
      'primary.dark': 'bg-blue-800',
      'secondary.main': 'bg-indigo-600',
      'secondary.light': 'bg-indigo-100 dark:bg-indigo-900/30',
      'success.main': 'bg-green-600',
      'success.light': 'bg-green-100 dark:bg-green-900/30',
      'error.main': 'bg-red-600',
      'error.light': 'bg-red-100 dark:bg-red-900/30',
      'warning.main': 'bg-amber-600',
      'warning.light': 'bg-amber-100 dark:bg-amber-900/30',
      'info.main': 'bg-sky-600',
      'info.light': 'bg-sky-100 dark:bg-sky-900/30',
      'grey.50': 'bg-gray-50 dark:bg-gray-800',
      'grey.100': 'bg-gray-100 dark:bg-gray-800',
      'grey.200': 'bg-gray-200 dark:bg-gray-700',
      'action.hover': 'bg-gray-100 dark:bg-gray-800',
      'action.selected': 'bg-blue-50 dark:bg-blue-900/20',
    };
    if (palette[u]) return palette[u];
    if (u === 'transparent') return 'bg-transparent';
    if (u === 'white') return 'bg-white';
    if (u === 'black') return 'bg-black';
    if (u.match(/^#[0-9a-fA-F]{3,8}$/)) return `bg-[${u}]`;
    if (u.startsWith('rgba(') || u.startsWith('rgb(')) return `bg-[${u.replace(/\s+/g, '_')}]`;
    return null;
  },
  backgroundColor: (v) => {
    const u = unquote(v);
    if (u === 'transparent') return 'bg-transparent';
    if (u === 'white') return 'bg-white';
    if (u === 'black') return 'bg-black';
    if (u.match(/^#[0-9a-fA-F]{3,8}$/)) return `bg-[${u}]`;
    if (u.startsWith('rgba(') || u.startsWith('rgb(')) return `bg-[${u.replace(/\s+/g, '_')}]`;
    // MUI palette
    const palette: Record<string, string> = {
      'background.paper': 'bg-white dark:bg-gray-900',
      'background.default': 'bg-gray-50 dark:bg-gray-950',
      'primary.main': 'bg-blue-600',
      'primary.light': 'bg-blue-100',
      'success.main': 'bg-green-600',
      'error.main': 'bg-red-600',
      'warning.main': 'bg-amber-600',
    };
    return palette[u] ?? null;
  },

  // ─── Border ────────────────────────────────────────────────────────
  border: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'border-0';
    if (u === '1px solid') return 'border border-solid';
    if (isNumeric(v)) {
      const n = parseInt(v.trim());
      return n === 0 ? 'border-0' : n === 1 ? 'border' : `border-[${n}px]`;
    }
    // '1px solid #ccc' etc
    const m = u.match(/^(\d+)px solid (.+)$/);
    if (m) return `border border-solid border-[${m[2]}]`;
    // '1px solid divider' etc
    if (u.includes('solid')) return `border-[${u.replace(/\s+/g, '_')}]`;
    return null;
  },
  borderBottom: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'border-b-0';
    if (u === '1px solid') return 'border-b border-solid';
    const m = u.match(/^(\d+)px solid (.+)$/);
    if (m) return `border-b border-solid border-b-[${m[2].replace(/\s+/g, '_')}]`;
    return null;
  },
  borderTop: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'border-t-0';
    if (u === '1px solid') return 'border-t border-solid';
    return null;
  },
  borderLeft: (v) => {
    const u = unquote(v);
    if (u === 'none' || v.trim() === '0') return 'border-l-0';
    return null;
  },
  borderRight: (v) => {
    const u = unquote(v);
    if (u === 'none' || v.trim() === '0') return 'border-r-0';
    if (isNumeric(v)) return parseInt(v.trim()) === 1 ? 'border-r' : `border-r-[${v.trim()}px]`;
    return null;
  },
  borderRadius: (v) => {
    if (isNumeric(v)) {
      const n = parseFloat(v.trim());
      const map: Record<number, string> = {
        0: 'rounded-none',
        0.5: 'rounded-sm',
        1: 'rounded',
        1.5: 'rounded-md',
        2: 'rounded-lg',
        3: 'rounded-xl',
        4: 'rounded-2xl',
      };
      return map[n] ?? `rounded-[${n * 8}px]`;
    }
    const u = unquote(v);
    if (u === '50%') return 'rounded-full';
    if (u.endsWith('px') || u.endsWith('rem')) return `rounded-[${u}]`;
    return null;
  },
  borderColor: (v) => {
    const u = unquote(v);
    const map: Record<string, string> = {
      divider: 'border-gray-200 dark:border-gray-700',
      'primary.main': 'border-blue-600',
      'error.main': 'border-red-600',
      'success.main': 'border-green-600',
      'warning.main': 'border-amber-600',
      transparent: 'border-transparent',
    };
    if (map[u]) return map[u];
    if (u.match(/^#[0-9a-fA-F]{3,8}$/)) return `border-[${u}]`;
    if (u.startsWith('rgba(') || u.startsWith('rgb(')) return `border-[${u.replace(/\s+/g, '_')}]`;
    return null;
  },

  // ─── Cursor ────────────────────────────────────────────────────────
  cursor: (v) => {
    const map: Record<string, string> = {
      pointer: 'cursor-pointer',
      default: 'cursor-default',
      grab: 'cursor-grab',
      grabbing: 'cursor-grabbing',
      'not-allowed': 'cursor-not-allowed',
      help: 'cursor-help',
      move: 'cursor-move',
      text: 'cursor-text',
      none: 'cursor-none',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Opacity ───────────────────────────────────────────────────────
  opacity: (v) => {
    if (!isNumeric(v)) return null;
    const n = parseFloat(v.trim());
    if (n === 0) return 'opacity-0';
    if (n === 1) return 'opacity-100';
    if (n >= 0 && n <= 1) return `opacity-[${n}]`;
    return `opacity-[${n}]`;
  },

  // ─── Visibility / Pointer ─────────────────────────────────────────
  visibility: (v) => {
    const map: Record<string, string> = { hidden: 'invisible', visible: 'visible' };
    return map[unquote(v)] ?? null;
  },
  pointerEvents: (v) => {
    const map: Record<string, string> = { none: 'pointer-events-none', auto: 'pointer-events-auto' };
    return map[unquote(v)] ?? null;
  },
  userSelect: (v) => {
    const map: Record<string, string> = { none: 'select-none', all: 'select-all', text: 'select-text', auto: 'select-auto' };
    return map[unquote(v)] ?? null;
  },

  // ─── Transition / Animation ────────────────────────────────────────
  transition: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'transition-none';
    // 'all 0.2s' or 'all 0.3s ease' → transition-all duration-Xms
    const m = u.match(/^all\s+([\d.]+)s(?:\s+\w+)?$/);
    if (m) return `transition-all duration-${Math.round(parseFloat(m[1]) * 1000)}`;
    // Arbitrary
    if (u.includes('width') || u.includes('height') || u.includes('transform') || u.includes('opacity'))
      return `transition-all duration-300`;
    return null;
  },

  // ─── Box sizing ────────────────────────────────────────────────────
  boxSizing: (v) => (unquote(v) === 'border-box' ? 'box-border' : unquote(v) === 'content-box' ? 'box-content' : null),

  // ─── Backdrop filter ──────────────────────────────────────────────
  backdropFilter: (v) => {
    const u = unquote(v);
    const m = u.match(/^blur\((\d+)px\)$/);
    if (m) return `backdrop-blur-[${m[1]}px]`;
    if (u === 'none') return 'backdrop-blur-none';
    return null;
  },

  // ─── Filter ───────────────────────────────────────────────────────
  filter: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'filter-none';
    const m = u.match(/^blur\((\d+)px\)$/);
    if (m) return `blur-[${m[1]}px]`;
    return null;
  },

  // ─── Transform ────────────────────────────────────────────────────
  transform: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'transform-none';
    const rot = u.match(/^rotate\((-?\d+)deg\)$/);
    if (rot) return `rotate-[${rot[1]}deg]`;
    const sc = u.match(/^scale\(([\d.]+)\)$/);
    if (sc) return `scale-[${sc[1]}]`;
    const tx = u.match(/^translateY\((-?\d+)px\)$/);
    if (tx) return `translate-y-[${tx[1]}px]`;
    const txx = u.match(/^translateX\((-?\d+)px\)$/);
    if (txx) return `translate-x-[${txx[1]}px]`;
    return null;
  },

  // ─── Object fit ────────────────────────────────────────────────────
  objectFit: (v) => {
    const map: Record<string, string> = {
      cover: 'object-cover',
      contain: 'object-contain',
      fill: 'object-fill',
      none: 'object-none',
    };
    return map[unquote(v)] ?? null;
  },

  // ─── Box shadow ────────────────────────────────────────────────────
  boxShadow: (v) => {
    const u = unquote(v);
    if (u === 'none') return 'shadow-none';
    if (isNumeric(v)) {
      const n = parseInt(v.trim());
      const map: Record<number, string> = { 0: 'shadow-none', 1: 'shadow-sm', 2: 'shadow', 3: 'shadow-md', 4: 'shadow-lg', 6: 'shadow-xl', 8: 'shadow-2xl' };
      return map[n] ?? `shadow-[${u}]`;
    }
    return null;
  },

  // ─── Grid ──────────────────────────────────────────────────────────
  gridTemplateColumns: (v) => {
    const u = unquote(v);
    // 'repeat(3, 1fr)' → grid-cols-3
    const m = u.match(/^repeat\((\d+),\s*1fr\)$/);
    if (m) return `grid-cols-${m[1]}`;
    if (u === '1fr') return 'grid-cols-1';
    if (u === '1fr 1fr') return 'grid-cols-2';
    if (u === '1fr 1fr 1fr') return 'grid-cols-3';
    return null;
  },
  gridTemplateRows: (v) => {
    const u = unquote(v);
    const m = u.match(/^repeat\((\d+),\s*1fr\)$/);
    if (m) return `grid-rows-${m[1]}`;
    return null;
  },
  gridColumn: (v) => {
    const u = unquote(v);
    if (u === 'span 2') return 'col-span-2';
    if (u === 'span 3') return 'col-span-3';
    return null;
  },
};

// ─── Advanced multi-line parser ───────────────────────────────────────

interface ParseResult {
  classes: string[];
  unconverted: string[];
  styleProps: string[];
  hasDynamic: boolean;
  hasPseudo: boolean;
  hasNested: boolean;
}

/**
 * Extract balanced braces content from str starting at pos
 */
function extractBalanced(str: string, pos: number): { content: string; end: number } | null {
  if (str[pos] !== '{') return null;
  let depth = 0;
  let i = pos;
  while (i < str.length) {
    if (str[i] === '{') depth++;
    else if (str[i] === '}') {
      depth--;
      if (depth === 0) return { content: str.slice(pos + 1, i), end: i };
    }
    i++;
  }
  return null;
}

/**
 * Tokenize the inner content of an sx object, handling nested braces.
 * Returns array of { key, value, isNested }
 */
function tokenizeSxInner(content: string): Array<{ key: string; value: string; isNested: boolean }> {
  const tokens: Array<{ key: string; value: string; isNested: boolean }> = [];
  // Regex to match key: value pairs, handling nested braces
  let remaining = content.trim();

  while (remaining.length > 0) {
    // Skip whitespace and commas
    remaining = remaining.replace(/^[\s,]+/, '');
    if (!remaining) break;

    // Match key (may be quoted for pseudo-selectors)
    const keyMatch = remaining.match(/^(?:'([^']+)'|"([^"]+)"|(\w+))\s*:\s*/);
    if (!keyMatch) break;

    const key = keyMatch[1] ?? keyMatch[2] ?? keyMatch[3];
    remaining = remaining.slice(keyMatch[0].length);

    // Value: could be a nested object, string, number, or expression
    if (remaining.startsWith('{')) {
      const balanced = extractBalanced(remaining, 0);
      if (balanced) {
        tokens.push({ key, value: `{${balanced.content}}`, isNested: true });
        remaining = remaining.slice(balanced.end + 1);
      } else {
        break; // Unbalanced braces
      }
    } else if (remaining.startsWith('(')) {
      // Function/callback — extract balanced parens
      let depth = 0;
      let i = 0;
      while (i < remaining.length) {
        if (remaining[i] === '(') depth++;
        else if (remaining[i] === ')') {
          depth--;
          if (depth === 0) {
            i++;
            // May continue with => and more
            const arrowMatch = remaining.slice(i).match(/^\s*=>\s*/);
            if (arrowMatch) {
              i += arrowMatch[0].length;
              // Get the return value
              if (remaining[i] === '{') {
                const b = extractBalanced(remaining, i);
                if (b) {
                  tokens.push({ key, value: remaining.slice(0, b.end + 1), isNested: true });
                  remaining = remaining.slice(b.end + 1);
                } else break;
              } else {
                // Simple expression
                const valEnd = remaining.indexOf(',', i);
                const val = valEnd === -1 ? remaining.slice(i) : remaining.slice(i, valEnd);
                tokens.push({ key, value: remaining.slice(0, i) + val.trim(), isNested: true });
                remaining = valEnd === -1 ? '' : remaining.slice(valEnd + 1);
              }
            } else {
              tokens.push({ key, value: remaining.slice(0, i), isNested: true });
              remaining = remaining.slice(i);
            }
            break;
          }
        }
        i++;
      }
      if (depth !== 0) break;
    } else {
      // Simple value: read until next comma or end, but be careful of strings with commas
      let i = 0;
      let inString: string | null = null;
      while (i < remaining.length) {
        const ch = remaining[i];
        if (inString) {
          if (ch === inString) inString = null;
        } else {
          if (ch === "'" || ch === '"') inString = ch;
          else if (ch === ',' || ch === '}') break;
        }
        i++;
      }
      const value = remaining.slice(0, i).trim();
      tokens.push({ key, value, isNested: false });
      remaining = remaining.slice(i);
    }
  }

  return tokens;
}

/**
 * Parse a pseudo-selector block into Tailwind variant classes
 */
function parsePseudoBlock(selector: string, innerContent: string): string[] {
  const pseudoMap: Record<string, string> = {
    '&:hover': 'hover',
    '&:focus': 'focus',
    '&:active': 'active',
    '&:disabled': 'disabled',
    '&:first-child': 'first',
    '&:last-child': 'last',
    '&:focus-visible': 'focus-visible',
    '&:focus-within': 'focus-within',
    '&::before': 'before',
    '&::after': 'after',
    '&::placeholder': 'placeholder',
  };

  const prefix = pseudoMap[selector];
  if (!prefix) return []; // Can't translate

  const classes: string[] = [];
  const tokens = tokenizeSxInner(innerContent);

  for (const token of tokens) {
    if (token.isNested) continue;
    const converter = PROP_CONVERTERS[token.key];
    if (converter) {
      const tw = converter(token.value);
      if (tw) {
        // Apply prefix to each class
        tw.split(' ').forEach((c) => classes.push(`${prefix}:${c}`));
      }
    }
  }

  return classes;
}

function parseSxObject(sxContent: string): ParseResult {
  const result: ParseResult = {
    classes: [],
    unconverted: [],
    styleProps: [],
    hasDynamic: false,
    hasPseudo: false,
    hasNested: false,
  };

  const tokens = tokenizeSxInner(sxContent);

  for (const token of tokens) {
    // Pseudo-selectors
    if (token.key.startsWith('&')) {
      result.hasPseudo = true;
      if (token.isNested) {
        const inner = token.value.startsWith('{') ? token.value.slice(1, -1) : token.value;
        // Check for dynamic/conditional pseudo
        if (inner.includes('?') || inner.includes('!') || inner.includes('&&') || inner.includes('||')) {
          result.unconverted.push(`${token.key}: ${token.value}`);
          result.hasDynamic = true;
          continue;
        }
        const pseudoClasses = parsePseudoBlock(token.key, inner);
        if (pseudoClasses.length > 0) {
          result.classes.push(...pseudoClasses);
        } else {
          result.unconverted.push(`'${token.key}': ${token.value}`);
        }
      }
      continue;
    }

    // @keyframes - skip, too complex
    if (token.key.startsWith('@')) {
      result.hasNested = true;
      result.unconverted.push(`${token.key}: ${token.value}`);
      continue;
    }

    // MUI nested selectors (& .MuiXxx)
    if (token.key.includes('.') && token.key.startsWith('&')) {
      result.hasNested = true;
      result.unconverted.push(`'${token.key}': ${token.value}`);
      continue;
    }

    // Dynamic value? (ternary, variable, function call, template literal)
    const isDynamic = (
      token.value.includes('?') ||
      token.value.includes('&&') ||
      token.value.includes('||') ||
      token.value.includes('${') ||
      token.value.includes('`') ||
      // Variable references (not quoted strings, not numbers)
      (!token.value.startsWith("'") && !token.value.startsWith('"') && !isNumeric(token.value) && /[a-zA-Z_$]/.test(token.value[0]) && !['true', 'false'].includes(token.value.trim()))
    );

    // Function calls as values (e.g., color: getStatusColor('healthy'))
    // These are tokenized with isNested: true but should be style props
    if (token.isNested && !token.value.startsWith('{') && !token.key.startsWith('&') && !token.key.startsWith('@')) {
      result.hasDynamic = true;
      result.styleProps.push(`${token.key}: ${token.value}`);
      continue;
    }

    if (!token.isNested && isDynamic) {
      result.hasDynamic = true;
      result.styleProps.push(`${token.key}: ${token.value}`);
      continue;
    }

    // Nested object (responsive breakpoints like { xs: ..., md: ... })
    if (token.isNested && token.value.startsWith('{')) {
      const inner = token.value.slice(1, -1).trim();
      // Try parsing as responsive breakpoints
      const bpTokens = tokenizeSxInner(inner);
      const bpMap: Record<string, string> = { xs: '', sm: 'sm:', md: 'md:', lg: 'lg:', xl: 'xl:', '2xl': '2xl:' };
      let allConverted = true;
      const bpClasses: string[] = [];

      for (const bp of bpTokens) {
        if (bpMap[bp.key] !== undefined) {
          const converter = PROP_CONVERTERS[token.key];
          if (converter) {
            const tw = converter(bp.value);
            if (tw) {
              const prefix = bpMap[bp.key];
              tw.split(' ').forEach((c) => bpClasses.push(prefix ? `${prefix}${c}` : c));
            } else {
              allConverted = false;
            }
          } else {
            allConverted = false;
          }
        } else {
          allConverted = false;
        }
      }

      if (allConverted && bpClasses.length > 0) {
        result.classes.push(...bpClasses);
      } else {
        result.unconverted.push(`${token.key}: ${token.value}`);
        result.hasNested = true;
      }
      continue;
    }

    // Standard property conversion
    const converter = PROP_CONVERTERS[token.key];
    if (converter) {
      const tw = converter(token.value);
      if (tw) {
        result.classes.push(...tw.split(' '));
      } else {
        result.unconverted.push(`${token.key}: ${token.value}`);
      }
    } else {
      result.unconverted.push(`${token.key}: ${token.value}`);
    }
  }

  return result;
}

// ─── File Processor ───────────────────────────────────────────────────

interface ProcessResult {
  fullyConverted: number;
  partialConverted: number;
  skipped: number;
  withStyle: number;
}

function processFile(filePath: string, content: string): { newContent: string; result: ProcessResult } {
  const result: ProcessResult = { fullyConverted: 0, partialConverted: 0, skipped: 0, withStyle: 0 };

  // Find sx={{ ... }} using balanced brace matching
  let newContent = '';
  let i = 0;

  while (i < content.length) {
    // Look for 'sx={'
    const sxPos = content.indexOf('sx={', i);
    if (sxPos === -1) {
      newContent += content.slice(i);
      break;
    }

    // Append everything before sx=
    newContent += content.slice(i, sxPos);

    // Check: next char after sx={ should be {
    const afterEq = sxPos + 4; // position after 'sx={'
    if (content[afterEq] !== '{') {
      // sx={variable} or sx={fn()} — not a literal object
      newContent += 'sx={';
      i = afterEq;
      result.skipped++;
      continue;
    }

    // Extract balanced inner object
    const balanced = extractBalanced(content, afterEq);
    if (!balanced) {
      newContent += 'sx={';
      i = afterEq;
      result.skipped++;
      continue;
    }

    const innerContent = balanced.content;
    const fullEnd = balanced.end + 1; // After inner }
    // Now we need to also consume the outer }
    if (content[fullEnd] !== '}') {
      newContent += 'sx={';
      i = afterEq;
      result.skipped++;
      continue;
    }

    // Parse the sx object
    const parsed = parseSxObject(innerContent);

    if (parsed.classes.length === 0 && parsed.styleProps.length === 0) {
      // Nothing converted at all — keep original
      newContent += content.slice(sxPos, fullEnd + 1);
      i = fullEnd + 1;
      result.skipped++;
      continue;
    }

    const classStr = parsed.classes.join(' ');

    // Check if element already has className
    // Look backwards to see if there's a className= before this sx=
    const beforeSx = newContent.slice(Math.max(0, newContent.length - 200));
    const hasExistingClassName = /className=("[^"]*"|{[^}]*})\s*$/.test(beforeSx);

    if (parsed.unconverted.length === 0 && parsed.styleProps.length === 0) {
      // Fully converted
      if (hasExistingClassName) {
        // Merge into existing className
        // Replace trailing className="..." with merged version
        newContent = newContent.replace(
          /className="([^"]*)"\s*$/,
          `className="$1 ${classStr}"`
        );
      } else {
        newContent += `className="${classStr}"`;
      }
      result.fullyConverted++;
    } else if (parsed.styleProps.length > 0) {
      // Has dynamic props → convert static to className, dynamic to style=
      const styleEntries = parsed.styleProps
        .map((s) => {
          const colonIdx = s.indexOf(':');
          const key = s.slice(0, colonIdx).trim();
          const val = s.slice(colonIdx + 1).trim();
          return `${key}: ${val}`;
        })
        .join(', ');

      if (classStr) {
        if (hasExistingClassName) {
          newContent = newContent.replace(
            /className="([^"]*)"\s*$/,
            `className="$1 ${classStr}"`
          );
          newContent += ` style={{ ${styleEntries} }}`;
        } else {
          newContent += `className="${classStr}" style={{ ${styleEntries} }}`;
        }
      } else {
        // Only dynamic props, no static classes
        newContent += `style={{ ${styleEntries} }}`;
      }

      if (parsed.unconverted.length > 0) {
        newContent += ` /* TODO: migrate remaining: ${parsed.unconverted.join('; ')} */`;
      }
      result.withStyle++;
    } else {
      // Partial — some unconverted
      if (hasExistingClassName) {
        newContent = newContent.replace(
          /className="([^"]*)"\s*$/,
          `className="$1 ${classStr}"`
        );
      } else {
        newContent += `className="${classStr}"`;
      }
      newContent += ` /* TODO: migrate remaining sx: ${parsed.unconverted.join('; ')} */`;
      result.partialConverted++;
    }

    i = fullEnd + 1;
  }

  return { newContent, result };
}

// ─── Also clean TODO comments from prior partial runs ─────────────────

function cleanPriorTodoComments(content: string): { newContent: string; fixed: number } {
  let fixed = 0;

  // Pattern: className="classes" /* TODO: migrate remaining sx: prop: val, prop: val */
  // Try to convert the remaining props
  const newContent = content.replace(
    /className="([^"]*)"\s*\/\*\s*TODO:\s*migrate remaining sx:\s*([^*]+)\*\//g,
    (match, existingClasses: string, todoProps: string) => {
      const additionalClasses: string[] = [];
      const stillUnconverted: string[] = [];

      // Parse the TODO props
      const propsStr = todoProps.trim();
      const propRegex = /(\w+)\s*:\s*([^,]+)/g;
      let m;
      while ((m = propRegex.exec(propsStr)) !== null) {
        const prop = m[1].trim();
        const val = m[2].trim();
        const converter = PROP_CONVERTERS[prop];
        if (converter) {
          const tw = converter(val);
          if (tw) {
            additionalClasses.push(...tw.split(' '));
            continue;
          }
        }
        stillUnconverted.push(`${prop}: ${val}`);
      }

      if (additionalClasses.length > 0) {
        fixed++;
        const mergedClasses = `${existingClasses} ${additionalClasses.join(' ')}`.trim();
        if (stillUnconverted.length > 0) {
          return `className="${mergedClasses}" /* TODO: migrate remaining sx: ${stillUnconverted.join(', ')} */`;
        }
        return `className="${mergedClasses}"`;
      }

      return match; // Nothing new to convert
    }
  );

  return { newContent, fixed };
}

// ─── File discovery ───────────────────────────────────────────────────

function findFiles(dir: string, extensions: string[]): string[] {
  const results: string[] = [];
  function walk(d: string) {
    if (!fs.existsSync(d)) return;
    const entries = fs.readdirSync(d, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(d, entry.name);
      if (entry.isDirectory()) {
        if (['node_modules', 'dist', 'build', '.git', '.next', '.turbo'].includes(entry.name)) continue;
        walk(fullPath);
      } else if (extensions.some((ext) => entry.name.endsWith(ext))) {
        results.push(fullPath);
      }
    }
  }
  walk(dir);
  return results;
}

// ─── Main ─────────────────────────────────────────────────────────────

function main() {
  console.log(`\n🎨 sx-to-tailwind v2 ${DRY_RUN ? '(DRY RUN)' : ''}\n`);

  const targetDir = TARGET_ARG
    ? path.resolve(ROOT, TARGET_ARG.split('=')[1])
    : path.join(ROOT, 'products/yappc/frontend');

  console.log(`Target: ${path.relative(ROOT, targetDir)}\n`);

  const files = findFiles(targetDir, ['.tsx', '.jsx']);
  let totalFull = 0;
  let totalPartial = 0;
  let totalSkipped = 0;
  let totalStyle = 0;
  let totalTodoFixed = 0;
  let filesModified = 0;

  for (const filePath of files) {
    let content = fs.readFileSync(filePath, 'utf-8');
    const relPath = path.relative(ROOT, filePath);

    // Skip files without sx=
    if (!content.includes('sx=') && !content.includes('TODO: migrate remaining sx')) continue;

    // First: clean up prior TODO comments
    const { newContent: afterTodo, fixed: todoFixed } = cleanPriorTodoComments(content);
    if (todoFixed > 0) {
      content = afterTodo;
      totalTodoFixed += todoFixed;
    }

    // Then: process remaining sx= props
    const { newContent, result } = processFile(filePath, content);

    if (result.fullyConverted > 0 || result.partialConverted > 0 || result.withStyle > 0 || todoFixed > 0) {
      console.log(
        `📝 ${relPath}: full=${result.fullyConverted} partial=${result.partialConverted} ` +
        `style=${result.withStyle} skipped=${result.skipped} todoFixed=${todoFixed}`
      );
      filesModified++;
      if (!DRY_RUN) {
        fs.writeFileSync(filePath, newContent, 'utf-8');
      }
    }

    totalFull += result.fullyConverted;
    totalPartial += result.partialConverted;
    totalSkipped += result.skipped;
    totalStyle += result.withStyle;
  }

  console.log(`\n✅ Summary:`);
  console.log(`   Files modified: ${filesModified}`);
  console.log(`   Fully converted: ${totalFull}`);
  console.log(`   Partial (with TODO): ${totalPartial}`);
  console.log(`   Dynamic → style=: ${totalStyle}`);
  console.log(`   Prior TODOs fixed: ${totalTodoFixed}`);
  console.log(`   Skipped (complex): ${totalSkipped}`);
  if (DRY_RUN) {
    console.log(`   ⚠️  DRY RUN — no files modified`);
  }
}

main();
