package com.ghatana.phr.security;

import com.ghatana.kernel.security.SecurityContext;

/**
 * Component for SecurityContextHolder
 *
 * @doc.type class
 * @doc.purpose Component for SecurityContextHolder
 * @doc.layer product
 * @doc.pattern Service
 */
public class SecurityContextHolder {
    private static final ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();

    public static void setContext(SecurityContext context) {
        contextHolder.set(context);
    }

    public static SecurityContext getContext() {
        return contextHolder.get();
    }

    public static void clearContext() {
        contextHolder.remove();
    }
}
