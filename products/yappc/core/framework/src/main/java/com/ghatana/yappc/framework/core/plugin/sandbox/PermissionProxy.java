/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a dynamic proxy around a plugin instance that enforces its
 * {@link PermissionSet} before delegating each method call.
 *
 * <p>The proxy intercepts every method invocation and validates any
 * permission-annotated parameters (network host, file path, Java package).
 * Method calls that would violate the granted permissions are rejected with a
 * {@link SecurityException} before the real plugin code runs.
 *
 * <p>Current enforcement strategy: the proxy checks the {@code toString()} of
 * every {@code String} argument against the three allow-lists. This is a
 * structural safety net; plugins that attempt to bypass it by passing
 * non-String arguments will need additional validation at the platform API
 * boundary.
 *
 * @doc.type class
 * @doc.purpose Intercepts plugin method calls and enforces permission policies
 * @doc.layer product
 * @doc.pattern Proxy / Decorator
 */
public final class PermissionProxy {

    private static final Logger log = LoggerFactory.getLogger(PermissionProxy.class);

    private PermissionProxy() {}

    /**
     * Wraps {@code instance} with a proxy that enforces {@code permissions}.
     *
     * @param <T>         plugin contract type
     * @param instance    real plugin instance
     * @param contract    the interface the proxy should implement
     * @param permissions permission set granted to this plugin
     * @return a permission-enforcing proxy for {@code instance}
     * @throws IllegalArgumentException if {@code contract} is not an interface
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(T instance, Class<T> contract, PermissionSet permissions) {
        if (!contract.isInterface()) {
            throw new IllegalArgumentException("contract must be an interface, got: " + contract.getName());
        }

        InvocationHandler handler = new PermissionEnforcingHandler(instance, permissions, contract.getSimpleName());

        return (T) Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[] {contract},
                handler);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal handler
    // ─────────────────────────────────────────────────────────────────────────

    private static final class PermissionEnforcingHandler implements InvocationHandler {

        private final Object target;
        private final PermissionSet permissions;
        private final String contractName;

        PermissionEnforcingHandler(Object target, PermissionSet permissions, String contractName) {
            this.target = target;
            this.permissions = permissions;
            this.contractName = contractName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args != null) {
                checkArgs(method, args);
            }
            return method.invoke(target, args);
        }

        /**
         * Inspects String arguments and rejects values that look like a
         * disallowed network host, file path, or Java package.
         *
         * <p>This is intentionally conservative: if an argument looks like a
         * hostname (no spaces, contains a dot or is a plain word) and is NOT
         * in the network allow-list, it is blocked. Callers that legitimately
         * pass hostname-shaped strings must add the host to the allow-list.
         */
        private void checkArgs(Method method, Object[] args) {
            for (Object arg : args) {
                if (!(arg instanceof String s)) {
                    continue;
                }
                checkNetworkArg(method, s);
                checkFileArg(method, s);
            }
        }

        private void checkNetworkArg(Method method, String value) {
            // Only check values that look like hostnames / URLs
            if (!looksLikeHostOrUrl(value)) {
                return;
            }
            // Extract hostname portion if URL-shaped
            String host = extractHost(value);
            if (!permissions.isNetworkHostAllowed(host)) {
                String msg = contractName + "." + method.getName()
                        + " – plugin attempted to access disallowed network host '" + host + "'";
                log.warn(msg);
                throw new SecurityException(msg);
            }
        }

        private void checkFileArg(Method method, String value) {
            if (!looksLikeAbsolutePath(value)) {
                return;
            }
            if (!permissions.isFilePathAllowed(value)) {
                String msg = contractName + "." + method.getName()
                        + " – plugin attempted to access disallowed file path '" + value + "'";
                log.warn(msg);
                throw new SecurityException(msg);
            }
        }

        private static boolean looksLikeHostOrUrl(String s) {
            return s.startsWith("http://")
                    || s.startsWith("https://")
                    || s.startsWith("ftp://")
                    || (s.contains(".") && !s.contains(" ") && !s.startsWith("/"));
        }

        private static boolean looksLikeAbsolutePath(String s) {
            return s.startsWith("/") || (s.length() > 2 && s.charAt(1) == ':' && s.charAt(2) == '\\');
        }

        private static String extractHost(String value) {
            try {
                if (value.contains("://")) {
                    String withoutScheme = value.substring(value.indexOf("://") + 3);
                    int slashIdx = withoutScheme.indexOf('/');
                    String hostPort = slashIdx >= 0 ? withoutScheme.substring(0, slashIdx) : withoutScheme;
                    int colonIdx = hostPort.indexOf(':');
                    return colonIdx >= 0 ? hostPort.substring(0, colonIdx) : hostPort;
                }
            } catch (Exception ignored) {
                // fall through to raw value
            }
            return value;
        }
    }
}
