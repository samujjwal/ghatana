/**
 * Root Layout Route
 * 
 * Provides the base layout for the entire application with proper HTML structure,
 * accessibility landmarks, and unified theme support.
 * 
 * @doc.type route-layout
 * @doc.purpose Application root with theme, accessibility, and context providers
 * @doc.layer infrastructure
 */

import { useEffect, Suspense } from "react";
import { useNavigation, useLocation } from "react-router";

import { RouteErrorBoundary } from "../components/route/ErrorBoundary";
import { RouteProgressBar } from "../components/route/RouteProgressBar";
import { ShortcutProvider } from "../contexts/ShortcutContext";
import { PersonaProvider } from "../context/PersonaContext";
import { HydrateFallback } from "../components/route/HydrateFallback";
import { AppThemeProvider } from "../theme";

/**
 * Root layout component
 */
export function Layout({ children }: { children: React.ReactNode }) {
    const navigation = useNavigation();
    const isLoading = navigation.state === "loading";

    const location = useLocation();

    useEffect(() => {
        // NOTE: Add any global initialization logic here
    }, [location.key]);

    return (
        <div id="root-layout" style={{ minHeight: "100vh" }}>
            {/* Skip to main content link for accessibility */}
            <a
                href="#main-content"
                className="sr-only"
                style={{
                    position: "absolute",
                    left: "-10000px",
                    top: "auto",
                    width: "1px",
                    height: "1px",
                    overflow: "hidden"
                }}
                onFocus={(e) => {
                    e.currentTarget.style.position = "static";
                    e.currentTarget.style.left = "auto";
                    e.currentTarget.style.width = "auto";
                    e.currentTarget.style.height = "auto";
                    e.currentTarget.style.overflow = "visible";
                }}
                onBlur={(e) => {
                    e.currentTarget.style.position = "absolute";
                    e.currentTarget.style.left = "-10000px";
                    e.currentTarget.style.width = "1px";
                    e.currentTarget.style.height = "1px";
                    e.currentTarget.style.overflow = "hidden";
                }}
            >
                Skip to main content
            </a>

            {/* Route transition progress bar */}
            <RouteProgressBar isLoading={isLoading} />

            <main id="main-content" role="main">
                <AppThemeProvider defaultTheme="system">
                    <PersonaProvider>
                        <ShortcutProvider>
                            <Suspense fallback={<HydrateFallback />}>
                                {children}
                            </Suspense>
                        </ShortcutProvider>
                    </PersonaProvider>
                </AppThemeProvider>
            </main>

            <style>
                {`
              @keyframes loading-bar {
                0% { transform: translateX(-100%); }
                100% { transform: translateX(100vw); }
              }
            `}
            </style>
        </div>
    );
}
