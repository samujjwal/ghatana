/**
 * Phase theme re-export — canonical source is `@yappc/product-theme`.
 * Import from this barrel to avoid direct product-lib coupling in app code.
 *
 * @doc.type utility
 * @doc.purpose Re-exports YAPPC lifecycle phase theme tokens
 * @doc.layer product
 * @doc.pattern Re-export
 */
export {
  PHASE_THEMES,
  getPhaseTheme,
  getPhaseLabel,
  getPhaseIcon,
  type LifecyclePhase,
  type PhaseTheme,
} from '@yappc/product-theme';
