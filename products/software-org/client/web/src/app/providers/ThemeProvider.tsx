import { ReactNode, useEffect } from "react";
import { useAtom } from "jotai";
import {
    ThemeProvider as PlatformThemeProvider,
    useTheme as usePlatformTheme,
} from "@ghatana/theme";
import { themeAtom } from "@/state/jotai/session.store";

/**
 * Theme provider for dark/light mode support
 *
 * <p><b>Purpose</b><br>
 * Applies theme preference to document root and provides theme context.
 * Wraps @ghatana/theme ThemeProvider for UI component support.
 * Persists theme choice to localStorage.
 *
 * <p><b>Features</b><br>
 * - Syncs theme to Tailwind dark mode (dark:* classes)
 * - Provides theme context via @ghatana/theme ThemeProvider
 * - Reads from localStorage on mount
 * - Respects system preference with "system" option
 * - Automatically updates document data-theme attribute
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <ThemeProvider>
 *   <App />
 * </ThemeProvider>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Theme provider with dark mode support
 * @doc.layer product
 * @doc.pattern Provider
 */

function ThemeBridge({
    children,
    theme,
}: {
    children: ReactNode;
    theme: "light" | "dark" | "system";
}) {
    const { setTheme } = usePlatformTheme();

    useEffect(() => {
        if (typeof window === "undefined") return;
        setTheme(theme);
        document.documentElement.setAttribute("data-theme", theme);
    }, [setTheme, theme]);

    return <>{children}</>;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme] = useAtom(themeAtom);

    return (
        <PlatformThemeProvider attribute="class" storageKey="software-org:theme">
            <ThemeBridge theme={theme}>{children}</ThemeBridge>
        </PlatformThemeProvider>
    );
}

export default ThemeProvider;
