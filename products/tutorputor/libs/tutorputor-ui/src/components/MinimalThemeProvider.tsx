import React from 'react';
import { ThemeProvider } from '@ghatana/theme';

export interface MinimalThemeProviderProps {
    children: React.ReactNode;
    storageKey?: string;
}

/**
 * Theme provider wrapper that uses @ghatana/theme for context
 * but removes inline styles that would override Tailwind CSS.
 *
 * This allows the `useTheme` hook to work while preventing the provider
 * from injecting inline styles that break Tailwind's dark-mode class strategy.
 *
 * @example
 * ```tsx
 * <MinimalThemeProvider storageKey="my-product-theme">
 *   <App />
 * </MinimalThemeProvider>
 * ```
 */
export function MinimalThemeProvider({
    children,
    storageKey = 'tutorputor-theme',
}: MinimalThemeProviderProps): React.ReactElement {
    React.useEffect(() => {
        // Remove inline styles that ThemeProvider adds to document.documentElement
        const root = document.documentElement;
        root.style.colorScheme = '';
        root.style.backgroundColor = '';
        root.style.color = '';

        // Remove all CSS custom properties set by the ThemeProvider —
        // they are already defined in our index.css/globals.css.
        const styleProps = Array.from(root.style);
        for (const prop of styleProps) {
            if (prop.startsWith('--gh-color')) {
                root.style.removeProperty(prop);
            }
        }
    });

    return (
        <ThemeProvider
            defaultTheme="system"
            storageKey={storageKey}
            enableStorage={true}
            enableSystem={true}
            disableTransition={true}
        >
            {children}
        </ThemeProvider>
    );
}
