import * as React from 'react';
import { cn } from '@ghatana/utils';
import { lightColors, darkColors, spacing, componentRadius } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { Text, type TextProps } from './Text';

export interface CodeProps extends Omit<TextProps, 'as'> {
  block?: boolean;
  language?: string;
}

export const Code = React.forwardRef<HTMLElement, CodeProps>((props, ref) => {
  const { block = false, language, children, className, style, ...rest } = props;
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;

  if (block) {
    const raw = (spacing as Record<string, unknown>)['4'];
    const blockPadding = typeof raw === 'number' ? raw : Number(raw) || 16;
    return (
      <pre
        ref={ref as React.Ref<HTMLPreElement>}
        className={cn('gh-code-block', className)}
        style={{
          margin: 0,
          padding: `${blockPadding}px`,
          background: surface.background.paper,
          borderRadius: componentRadius.panel,
          border: `1px solid ${surface.border}`,
          overflowX: 'auto',
          fontFamily: 'Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
          fontSize: '0.875rem',
          lineHeight: 1.6,
          ...style,
        }}
        data-language={language}
        {...rest}
      >
        <code>{children}</code>
      </pre>
    );
  }

  return (
    <Text
      ref={ref as React.Ref<HTMLElement>}
      as="code"
      variant="code"
      className={cn('gh-code-inline', className)}
      style={{
        backgroundColor: isDark ? 'rgba(15, 23, 42, 0.45)' : 'rgba(15, 23, 42, 0.08)',
        borderRadius: componentRadius.badge,
        padding: '0 0.25em',
        fontFamily: 'Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
        fontSize: '0.875em',
        ...style,
      }}
      {...rest}
    >
      {children}
    </Text>
  );
});

Code.displayName = 'Code';
