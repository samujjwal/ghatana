declare module '@ghatana/yappc-shared-ui-core/utils/safePalette' {
  import type { Theme } from '@mui/material/styles';
  export function resolveMuiColor(
    theme: Theme,
    key?: string,
    fallback?: string
  ): string;
  export function getPaletteMain(
    theme: Theme,
    key?: string,
    fallback?: string
  ): string;
  export default function resolveThemeColor(
    theme: Theme,
    key?: string,
    fallback?: string
  ): string;
}
