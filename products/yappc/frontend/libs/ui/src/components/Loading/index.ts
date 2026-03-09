/**
 * Loading Components Module
 * 
 * Production-grade loading indicators and skeletons
 * 
 * @module ui/components/Loading
 * @doc.type module
 * @doc.purpose Loading state indicators
 * @doc.layer ui
 */

// Spinner components
export { Spinner, InlineSpinner, LoadingButton } from './Spinner';
export type {
  SpinnerProps,
  SpinnerSize,
  SpinnerVariant,
  InlineSpinnerProps,
  LoadingButtonProps,
} from './Spinner';

// Skeleton components
export {
  Skeleton,
  SkeletonCard,
  SkeletonTable,
  SkeletonList,
} from './Skeleton';
export type {
  SkeletonProps,
  SkeletonVariant,
  SkeletonAnimation,
  SkeletonCardProps,
  SkeletonTableProps,
  SkeletonListProps,
} from './Skeleton';

// ============================================================================
// Usage Examples
// ============================================================================

/**
 * @example Basic Spinner
 * 
 * import { Spinner } from '@ghatana/yappc-ui';
 * 
 * function LoadingState() {
 *   return <Spinner size="lg" centered />;
 * }
 */

/**
 * @example Loading Button
 * 
 * import { LoadingButton } from '@ghatana/yappc-ui';
 * 
 * function SaveButton() {
 *   const [saving, setSaving] = useState(false);
 *   
 *   const handleSave = async () => {
 *     setSaving(true);
 *     await saveData();
 *     setSaving(false);
 *   };
 *   
 *   return (
 *     <LoadingButton
 *       loading={saving}
 *       loadingText="Saving..."
 *       onClick={handleSave}
 *     >
 *       Save
 *     </LoadingButton>
 *   );
 * }
 */

/**
 * @example Skeleton Loading
 * 
 * import { SkeletonCard } from '@ghatana/yappc-ui';
 * 
 * function LoadingCard() {
 *   return <SkeletonCard showAvatar lines={3} />;
 * }
 */

/**
 * @example Skeleton Table
 * 
 * import { SkeletonTable } from '@ghatana/yappc-ui';
 * 
 * function LoadingTable() {
 *   return <SkeletonTable rows={5} columns={4} showHeader />;
 * }
 */
