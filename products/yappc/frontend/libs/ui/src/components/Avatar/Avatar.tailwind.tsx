import { forwardRef, useState } from 'react';

import { cn } from '../../utils/cn';

import type { ImgHTMLAttributes } from 'react';

/**
 * Avatar size variants
 */
export type AvatarSize = 'small' | 'medium' | 'large' | 'xlarge';

/**
 * Avatar shape variants
 */
export type AvatarVariant = 'circular' | 'rounded' | 'square';

/**
 * Avatar color variants mapped to design tokens
 */
export type AvatarColor = 
  | 'primary' 
  | 'secondary' 
  | 'error' 
  | 'warning' 
  | 'info' 
  | 'success' 
  | 'default';

/**
 * Props for the Avatar component
 */
export interface AvatarProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'size'> {
  /**
   * Avatar size
   * @default 'medium'
   */
  size?: AvatarSize;
  
  /**
   * Avatar variant (shape)
   * @default 'circular'
   */
  variant?: AvatarVariant;
  
  /**
   * Fallback text (initials) - required for accessibility
   */
  alt: string;
  
  /**
   * Image source URL
   */
  src?: string;
  
  /**
   * Background color for fallback (when no image)
   * @default 'info'
   */
  color?: AvatarColor;
  
  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * Avatar component for displaying user profile images with fallback initials.
 * 
 * Features:
 * - Displays image or fallback initials
 * - 4 size variants: small (32px), medium (40px), large (56px), xlarge (80px)
 * - 3 shape variants: circular, rounded, square
 * - 7 color variants for fallback background
 * - Automatic initials generation from alt text
 * - Image loading states with smooth transition
 * - Full accessibility support
 * 
 * @example
 * ```tsx
 * // With image
 * <Avatar src="/avatar.jpg" alt="John Doe" size="medium" />
 * 
 * // With initials fallback
 * <Avatar alt="Jane Smith" size="large" variant="rounded" color="primary" />
 * 
 * // Custom styling
 * <Avatar alt="JS" className="border-2 border-primary-500" />
 * ```
 */
export const Avatar = forwardRef<HTMLDivElement, AvatarProps>(
  (
    {
      size = 'medium',
      variant = 'circular',
      alt,
      src,
      color = 'info',
      className,
      ...props
    },
    ref
  ) => {
    const [imageError, setImageError] = useState(false);
    const [imageLoaded, setImageLoaded] = useState(false);

    // Size classes mapping
    const sizeClasses: Record<AvatarSize, string> = {
      small: 'w-8 h-8 text-xs',     // 32px, 12px font
      medium: 'w-10 h-10 text-base', // 40px, 16px font
      large: 'w-14 h-14 text-xl',   // 56px, 20px font
      xlarge: 'w-20 h-20 text-3xl', // 80px, 30px font
    };

    // Variant (shape) classes
    const variantClasses: Record<AvatarVariant, string> = {
      circular: 'rounded-full',
      rounded: 'rounded-lg',  // 8px border radius
      square: 'rounded',      // 4px border radius
    };

    // Color classes for fallback background
    const colorClasses: Record<AvatarColor, string> = {
      primary: 'bg-primary-500 text-white',
      secondary: 'bg-secondary-500 text-white',
      error: 'bg-error-500 text-white',
      warning: 'bg-warning-500 text-white',
      info: 'bg-info-500 text-white',
      success: 'bg-success-500 text-white',
      default: 'bg-grey-400 text-white',
    };

    /**
     * Extract initials from the alt text
     * - Takes first letter of first and last word if multiple words
     * - Takes first two letters if single word
     */
    const getInitials = (name?: string): string => {
      // Guard against undefined/null and empty strings
      const safeName = (name ?? '').trim();
      if (safeName.length === 0) return '';

      const parts = safeName.split(/\s+/);
      if (parts.length >= 2) {
        return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
      }
      return safeName.slice(0, 2).toUpperCase();
    };

    const handleImageLoad = () => {
      setImageLoaded(true);
    };

    const handleImageError = () => {
      setImageError(true);
      setImageLoaded(false);
    };

    return (
      <div
        ref={ref}
        className={cn(
          // Base styles
          'inline-flex items-center justify-center flex-shrink-0 select-none overflow-hidden font-medium',
          // Size
          sizeClasses[size],
          // Shape variant
          variantClasses[variant],
          // Background color (only visible when showing initials)
          colorClasses[color],
          // Custom classes
          className
        )}
        role="img"
        aria-label={alt}
      >
        {src && !imageError ? (
          <>
            <img
              src={src}
              alt={alt}
              className={cn(
                'w-full h-full object-cover transition-opacity duration-200',
                imageLoaded ? 'opacity-100' : 'opacity-0'
              )}
              onLoad={handleImageLoad}
              onError={handleImageError}
              {...props}
            />
            {/* Show initials while image is loading */}
            {!imageLoaded && <span className="absolute">{getInitials(alt)}</span>}
          </>
        ) : (
          <span>{getInitials(alt)}</span>
        )}
      </div>
    );
  }
);

Avatar.displayName = 'Avatar';

/**
 * Props for the AvatarGroup component
 */
export interface AvatarGroupProps {
  /**
   * Avatar components to display
   */
  children: React.ReactNode;
  
  /**
   * Maximum number of avatars to show before displaying "+N"
   * @default 5
   */
  max?: number;
  
  /**
   * Spacing variant for avatar overlap
   * @default 'medium'
   */
  spacing?: 'small' | 'medium' | 'large';
  
  /**
   * Additional CSS classes
   */
  className?: string;
}

/**
 * AvatarGroup component for displaying multiple avatars with overlap effect.
 * 
 * Features:
 * - Overlapping avatar layout
 * - Configurable maximum visible avatars
 * - Shows "+N" badge for remaining avatars
 * - Three spacing variants
 * - White border around each avatar for separation
 * 
 * @example
 * ```tsx
 * <AvatarGroup max={3} spacing="medium">
 *   <Avatar alt="User 1" src="/avatar1.jpg" />
 *   <Avatar alt="User 2" src="/avatar2.jpg" />
 *   <Avatar alt="User 3" src="/avatar3.jpg" />
 *   <Avatar alt="User 4" src="/avatar4.jpg" />
 * </AvatarGroup>
 * // Shows: [Avatar1] [Avatar2] [Avatar3] [+1]
 * ```
 */
export function AvatarGroup({
  children,
  max = 5,
  spacing = 'medium',
  className,
}: AvatarGroupProps) {
  const childArray = Array.isArray(children) ? children : [children];
  const visibleChildren = childArray.slice(0, max);
  const remaining = childArray.length - max;

  // Spacing classes for overlap amount
  const spacingClasses: Record<string, string> = {
    small: '-ml-1',   // 4px overlap
    medium: '-ml-2',  // 8px overlap
    large: '-ml-3',   // 12px overlap
  };

  return (
    <div className={cn('flex items-center', className)}>
      {visibleChildren.map((child, index) => (
        <div
          key={index}
          className={cn(
            'border-2 border-white',
            index > 0 && spacingClasses[spacing]
          )}
        >
          {child}
        </div>
      ))}
      {remaining > 0 && (
        <div className={cn('border-2 border-white', spacingClasses[spacing])}>
          <Avatar alt={`+${remaining}`} size="medium" color="default" />
        </div>
      )}
    </div>
  );
}
