import type { ReactNode } from 'react';

export function AppThemeProvider({ children }: { children: ReactNode }) {
	return <>{children}</>;
}

export function ThemeToggleButton() {
	return null;
}

export function ThemeStatusIndicator() {
	return null;
}

export function useTheme() {
	return {} as unknown;
}

export default AppThemeProvider;
