import { Card as BaseCard, CardContent, CardHeader, CardActions, CardMedia } from '@ghatana/ui';
import React from 'react';

import { useAccessibility } from '../../hooks';
import { borderRadius, elevationLevels } from '../../tokens';
import { getA11yProps } from '../../utils/accessibility';

/**
 * Props for the enhanced Card component.
 * Extends standard HTML div attributes with design-system shape, elevation, and interaction controls.
 */
export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Card shape variant
   */
  shape?: 'rounded' | 'square' | 'soft';
  
  /**
   * Card elevation (shadow)
   */
  elevation?: 0 | 1 | 2 | 4 | 8;
  
  /**
   * Card variant
   */
  variant?: 'outlined' | 'elevation' | 'elevated' | 'subtle';
  
  /**
   * Card hover effect
   */
  hover?: boolean;
  
  /**
   * ARIA label for accessibility
   */
  'aria-label'?: string;
  
  /**
   * ID of element that labels this card
   */
  'aria-labelledby'?: string;
  
  /**
   * ID of element that describes this card
   */
  'aria-describedby'?: string;
  
  /**
   * Whether the card has an interactive role
   */
  interactive?: boolean;
}

/** Map shape prop to Tailwind border-radius */
const shapeClasses: Record<string, string> = {
  rounded: 'rounded-lg',   // ~8px, matches borderRadius.md
  soft: 'rounded-xl',      // ~12px, matches borderRadius.lg
  square: 'rounded-sm',    // ~2px, matches borderRadius.xs
};

/** Map elevation prop to Tailwind shadow */
const elevationClasses: Record<number, string> = {
  0: 'shadow-none',
  1: 'shadow-sm',
  2: 'shadow',
  4: 'shadow-md',
  8: 'shadow-lg',
};

/**
 * Card component for containing content and actions
 */
export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  (props, ref) => {
    const { 
      children, 
      shape = 'rounded',
      elevation = 2,
      variant = 'elevation',
      hover = false,
      interactive = false,
      className,
      ...rest 
    } = props;
    
    // Extract accessibility props
    const { a11yProps, rest: otherProps } = getA11yProps(rest);
    
    // Use accessibility hook for audit
    const { ref: a11yRef } = useAccessibility<HTMLDivElement>({
      componentName: 'Card',
      devOnly: true,
      logResults: true,
    });

    const setRefs = React.useCallback(
      (element: HTMLDivElement | null) => {
        a11yRef.current = element;

        if (!ref) {
          return;
        }

        if (typeof ref === 'function') {
          ref(element);
        } else {
          (ref as React.MutableRefObject<HTMLDivElement | null>).current = element;
        }
      },
      [a11yRef, ref]
    );
    
    // Add appropriate role for interactive cards
    const roleProps = interactive ? { role: 'button', tabIndex: 0 } : {};

    // Map variant to BaseCard variant
    const baseVariant = variant === 'elevation' ? 'elevated' : variant === 'outlined' ? 'outlined' : variant as 'elevated' | 'outlined' | 'subtle';

    // Compose Tailwind classes
    const cardClassName = [
      shapeClasses[shape] || shapeClasses.rounded,
      variant === 'elevation' ? elevationClasses[elevation] || elevationClasses[2] : '',
      'transition-all duration-300 ease-in-out',
      hover ? 'hover:-translate-y-1 hover:shadow-lg' : '',
      interactive ? 'cursor-pointer focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2' : '',
      className,
    ].filter(Boolean).join(' ');
    
    return (
      <BaseCard
        ref={setRefs}
        variant={baseVariant}
        elevation={variant === 'elevation' ? elevation : 0}
        hover={hover}
        interactive={interactive}
        className={cardClassName}
        {...otherProps}
        {...roleProps}
        {...a11yProps}
      >
        {children}
      </BaseCard>
    );
  }
);

Card.displayName = 'Card';

// Export MUI Card subcomponents
export { CardContent, CardHeader, CardActions, CardMedia };
