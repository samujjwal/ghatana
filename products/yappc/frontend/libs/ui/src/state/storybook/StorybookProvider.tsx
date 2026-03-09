import { ThemeProvider } from '@ghatana/yappc-ui';
import { Provider, useAtom } from 'jotai';
import React from 'react';

import { storybookThemeAtom } from './storybookStore';

export interface StorybookProviderProps {
  children: React.ReactNode;
  theme?: 'light' | 'dark';
}

/**
 * Wrap Storybook stories with Jotai + Theme providers so components can reuse
 * the same global atoms as the main application.
 */
export const StorybookProvider: React.FC<StorybookProviderProps> = ({
  children,
  theme: initialTheme = 'light',
}) => {
  return (
    <Provider>
      <ThemeProviderWithState initialTheme={initialTheme}>
        {children}
      </ThemeProviderWithState>
    </Provider>
  );
};

interface ThemeProviderWithStateProps {
  children: React.ReactNode;
  initialTheme: 'light' | 'dark';
}

const ThemeProviderWithState: React.FC<ThemeProviderWithStateProps> = ({
  children,
  initialTheme,
}) => {
  const [theme, setTheme] = useAtom(storybookThemeAtom);

  React.useEffect(() => {
    setTheme(initialTheme);
  }, [initialTheme, setTheme]);

  return <ThemeProvider defaultMode={theme}>{children}</ThemeProvider>;
};
