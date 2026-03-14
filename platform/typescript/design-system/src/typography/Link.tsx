import * as React from 'react';
import { cn } from '@ghatana/utils';
import { Text, type TextProps } from './Text';
export interface LinkProps extends Omit<TextProps, 'as'> {
  href?: string;
  target?: string;
  rel?: string;
  underline?: 'always' | 'hover' | 'none';
}

export const Link = React.forwardRef<HTMLAnchorElement, LinkProps>((props, ref) => {
  const {
    href,
    target,
    rel,
    underline = 'hover',
    className,
    children,
    tone = 'primary',
    ...rest
  } = props;

  const relAttr = target === '_blank' ? rel ?? 'noopener noreferrer' : rel;

  const [isHover, setIsHover] = React.useState(false);

  const textDecoration = underline === 'always' ? 'underline' : underline === 'hover' && isHover ? 'underline' : 'none';

  const {
    style: styleProp,
    onMouseEnter: onMouseEnterProp,
    onMouseLeave: onMouseLeaveProp,
    variant: variantProp,
    ...restProps
  } = rest;

  if (href) {
    return (
      <a
        ref={ref}
        href={href}
        target={target}
        rel={relAttr}
        className={cn('gh-link', className)}
        style={{ textDecoration, cursor: 'pointer', ...(styleProp as Record<string, unknown>) }}
        onMouseEnter={(event) => {
          setIsHover(true);
          onMouseEnterProp?.(event as React.MouseEvent<HTMLElement>);
        }}
        onMouseLeave={(event) => {
          setIsHover(false);
          onMouseLeaveProp?.(event as React.MouseEvent<HTMLElement>);
        }}
        {...(restProps as Record<string, unknown>)}
      >
        {children}
      </a>
    );
  }

  return (
    <Text
      ref={ref as React.Ref<HTMLElement>}
      variant={variantProp ?? 'body'}
      tone={tone}
      className={className}
      style={{ textDecoration, cursor: 'pointer', ...(styleProp as Record<string, unknown>) }}
      onMouseEnter={(event) => {
        setIsHover(true);
        onMouseEnterProp?.(event as React.MouseEvent<HTMLElement>);
      }}
      onMouseLeave={(event) => {
        setIsHover(false);
        onMouseLeaveProp?.(event as React.MouseEvent<HTMLElement>);
      }}
      {...(restProps as Record<string, unknown>)}
    >
      {children}
    </Text>
  );
});

Link.displayName = 'Link';
