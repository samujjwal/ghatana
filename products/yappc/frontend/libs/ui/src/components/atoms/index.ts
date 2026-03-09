/**
 * Atoms — Basic Building Blocks
 *
 * Atoms are the smallest, indivisible UI elements.
 * They cannot be broken down further without losing their meaning.
 *
 * @packageDocumentation
 */

// Re-export atoms from parent directory for backward compatibility
// This allows gradual migration while maintaining existing imports

export { Button } from '../Button';
export type { ButtonProps } from '../Button';

export { Input } from '../Input';
export type { InputProps } from '../Input';

export { Checkbox } from '../Checkbox';
export type { CheckboxProps } from '../Checkbox';

export { Radio } from '../Radio';
export type { RadioProps } from '../Radio';

export { Switch } from '../Switch';
export type { SwitchProps } from '../Switch';

export { Slider } from '../Slider';
export type { SliderProps } from '../Slider';

export { Badge } from '../Badge';
export type { BadgeProps } from '../Badge';

export { Chip } from '../Chip';
export type { ChipProps} from '../Chip';

export { Avatar } from '../Avatar';
export type { AvatarProps } from '../Avatar';

export { Divider } from '../Divider';
export type { DividerProps } from '../Divider';

export { Spinner } from '../Spinner';
export type { SpinnerProps } from '../Spinner';

export { Progress } from '../Progress';
export type { ProgressProps } from '../Progress';

export { Rating } from '../Rating';
export type { RatingProps } from '../Rating';

export { Tooltip } from '../Tooltip';
export type { TooltipProps } from '../Tooltip';

/**
 * Atom components are the foundational building blocks of the UI.
 * Use them to construct more complex molecules and organisms.
 *
 * @example Basic Usage
 * ```tsx
 * import { Button, Badge, Avatar } from '@ghatana/yappc-shared-ui-core/atoms';
 *
 * function UserProfile() {
 *   return (
 *     <div>
 *       <Avatar src="/avatar.jpg" alt="User" />
 *       <Button variant="contained">
 *         Messages <Badge>3</Badge>
 *       </Button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @example With Tokens
 * ```tsx
 * import { Button } from '@ghatana/yappc-shared-ui-core/atoms';
 * import { Button } from '@ghatana/ui';
 *
 * function ThemedButton() {
 *   return (
 *     <Button className="bg-blue-600 rounded-lg">
 *       Click Me
 *     </Button>
 *   );
 * }
 * ```
 */
