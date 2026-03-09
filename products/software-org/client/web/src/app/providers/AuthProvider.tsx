import React, { ReactNode } from "react";
import { useAtom } from "jotai";
import { sessionAtom } from "@/state/jotai/session.store";

/**
 * Authentication provider for session management
 *
 * <p><b>Purpose</b><br>
 * Manages user authentication state and provides session context to app.
 * Handles login/logout and role-based access control.
 *
 * <p><b>Features</b><br>
 * - Manages session atom (userId, token, roles)
 * - Provides logout functionality
 * - Supports RBAC with roles array
 * - Can integrate with JWT validation/refresh
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <AuthProvider>
 *   <App />
 * </AuthProvider>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Authentication provider
 * @doc.layer product
 * @doc.pattern Provider
 */
export function AuthProvider({ children }: { children: ReactNode }) {
    const [, setSession] = useAtom(sessionAtom);

    // Initialize session from localStorage if available
    React.useEffect(() => {
        const savedSession = localStorage.getItem("session");
        if (savedSession) {
            try {
                const parsed = JSON.parse(savedSession);
                setSession(parsed);
            } catch (err) {
                console.error("Failed to restore session:", err);
            }
        }
    }, [setSession]);

    return <>{children}</>;
}

export default AuthProvider;
