import { ReactNode, useEffect } from "react";
import { useAtom } from "jotai";
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

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme] = useAtom(themeAtom);

    // Apply theme to the document root for Tailwind + tokens.css
    useEffect(() => {
        if (typeof window === "undefined") return;

        const root = document.documentElement;

        // Persist logical preference for future loads
        window.localStorage.setItem('software-org:theme', theme);

        // Resolve "system" using OS preference
        let effective: "light" | "dark";
        if (theme === "system") {
            const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
            effective = prefersDark ? "dark" : "light";
        } else {
            effective = theme;
        }

        // Manage html classes explicitly so both Tailwind and tokens.css behave:
        // - 'dark' enables Tailwind dark: and html.dark rules in tokens.css
        // - 'light' prevents @media (prefers-color-scheme: dark) :root:not(.light)
        //   from forcing dark tokens when the user has chosen light.
        root.classList.remove("light", "dark");
        root.classList.add(effective);

        // Expose logical preference for any consumers
        root.setAttribute("data-theme", theme);
    }, [theme]);

    // No external theme provider: rely on tokens.css + Tailwind
    return <>{children}</>;
}

export default ThemeProvider;
