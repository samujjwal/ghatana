/**
 * ConfidenceBadge — displays a numeric confidence score with color coding.
 *
 * Re-exports platform ConfidenceBadge from @ghatana/design-system.
 * Color tiers: Green (≥ 0.9) high, Yellow (≥ 0.7) acceptable, Red (< 0.7) low.
 *
 * @doc.type component
 * @doc.purpose Visually communicate confidence scores from learned policies
 * @doc.layer frontend
 * @deprecated-warning Use @ghatana/design-system ConfidenceBadge directly
 */
export { ConfidenceBadge } from '@ghatana/design-system';
export type { ConfidenceBadgeProps } from '@ghatana/design-system';
