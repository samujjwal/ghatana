import React, { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

type ThemeContextValue = {
	theme: ThemeMode;
	resolvedTheme: ResolvedTheme;
	setTheme: (theme: ThemeMode) => void;
	toggleTheme: () => void;
	isDarkMode: boolean;
	isLightMode: boolean;
	systemTheme: ResolvedTheme;
};

const STORAGE_KEY = 'theme';
const MEDIA_QUERY = '(prefers-color-scheme: dark)';

const ThemeContext = createContext<ThemeContextValue | null>(null);

function getSystemTheme(): ResolvedTheme {
	if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
		return 'light';
	}

	return window.matchMedia(MEDIA_QUERY).matches ? 'dark' : 'light';
}

function resolveTheme(theme: ThemeMode, systemTheme: ResolvedTheme): ResolvedTheme {
	return theme === 'system' ? systemTheme : theme;
}

function readStoredTheme(): ThemeMode {
	if (typeof window === 'undefined') {
		return 'system';
	}

	const storedTheme = window.localStorage.getItem(STORAGE_KEY);
	if (storedTheme === 'light' || storedTheme === 'dark' || storedTheme === 'system') {
		return storedTheme;
	}

	return 'system';
}

function applyResolvedTheme(resolvedTheme: ResolvedTheme): void {
	if (typeof document === 'undefined') {
		return;
	}

	const root = document.documentElement;
	root.classList.toggle('dark', resolvedTheme === 'dark');
	root.dataset.theme = resolvedTheme;
	root.style.colorScheme = resolvedTheme;
}

export function AppThemeProvider({ children }: { children: ReactNode }) {
	const [theme, setThemeState] = useState<ThemeMode>(() => readStoredTheme());
	const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(() => getSystemTheme());

	const resolvedTheme = useMemo(
		() => resolveTheme(theme, systemTheme),
		[theme, systemTheme]
	);

	useEffect(() => {
		applyResolvedTheme(resolvedTheme);
		if (typeof window !== 'undefined') {
			window.localStorage.setItem(STORAGE_KEY, theme);
		}
	}, [resolvedTheme, theme]);

	useEffect(() => {
		if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
			return;
		}

		const mediaQuery = window.matchMedia(MEDIA_QUERY);
		const onChange = (event: MediaQueryListEvent) => {
			setSystemTheme(event.matches ? 'dark' : 'light');
		};

		if (typeof mediaQuery.addEventListener === 'function') {
			mediaQuery.addEventListener('change', onChange);
			return () => mediaQuery.removeEventListener('change', onChange);
		}

		mediaQuery.addListener(onChange);
		return () => mediaQuery.removeListener(onChange);
	}, []);

	const value = useMemo<ThemeContextValue>(
		() => ({
			theme,
			resolvedTheme,
			systemTheme,
			setTheme: setThemeState,
			toggleTheme: () => {
				setThemeState((currentTheme) => {
					const currentResolved = resolveTheme(currentTheme, systemTheme);
					return currentResolved === 'dark' ? 'light' : 'dark';
				});
			},
			isDarkMode: resolvedTheme === 'dark',
			isLightMode: resolvedTheme === 'light',
		}),
		[resolvedTheme, systemTheme, theme]
	);

	return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
	return <AppThemeProvider>{children}</AppThemeProvider>;
}

export function ThemeToggleButton() {
	return null;
}

export function ThemeStatusIndicator() {
	return null;
}

function useThemeContext(): ThemeContextValue {
	const context = useContext(ThemeContext);
	if (!context) {
		throw new Error('Theme hooks must be used within AppThemeProvider');
	}

	return context;
}

export function useTheme() {
	return useThemeContext();
}

export function useThemeMode(): ThemeMode {
	return useThemeContext().theme;
}

export function useResolvedTheme(): ResolvedTheme {
	return useThemeContext().resolvedTheme;
}

export function useThemeToggle(): () => void {
	return useThemeContext().toggleTheme;
}

export function useSystemTheme(): ResolvedTheme {
	return useThemeContext().systemTheme;
}

export function useIsDarkMode(): boolean {
	return useThemeContext().isDarkMode;
}

export function useIsLightMode(): boolean {
	return useThemeContext().isLightMode;
}

export default AppThemeProvider;
