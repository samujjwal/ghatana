/**
 * yappc-product-theme
 *
 * YAPPC-specific theme layer on top of the shared @ghatana platform.
 *
 * What lives here:
 * - YAPPC lifecycle phase color presets (8-phase visual semantics)
 * - MUI bridge adapter (connects @ghatana/theme to MUI ThemeProvider)
 *
 * What does NOT live here:
 * - Generic token primitives → use @ghatana/tokens
 * - Theme runtime / CSS variables → use @ghatana/theme
 * - Generic design-system components → use @ghatana/design-system
 *
 * @doc.type module
 * @doc.purpose YAPPC product theme presets
 * @doc.layer product
 * @doc.pattern Tokens
 */

export {
  PHASE_THEMES,
  getPhaseTheme,
  getPhaseLabel,
  getPhaseIcon,
  type LifecyclePhase,
  type PhaseTheme,
} from './lifecycle-presets';

export { MuiThemeConnector } from './mui-bridge';
