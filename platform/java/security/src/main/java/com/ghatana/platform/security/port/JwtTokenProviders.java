package com.ghatana.platform.security.port;

import com.ghatana.platform.security.jwt.JwtKeyManager;

/**
 * @doc.type class
 * @doc.purpose Canonical factory entry points for JWT token provider ports
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class JwtTokenProviders {

    private JwtTokenProviders() {
    }

    /**
     * Create the canonical JWT provider backed by a shared secret.
     *
     * @param secretKey secret used to sign and verify tokens
     * @param validityInMilliseconds token validity window in milliseconds
     * @return canonical JWT provider exposed as the port type
     */
    public static JwtTokenProvider fromSharedSecret(String secretKey, long validityInMilliseconds) {
        return new com.ghatana.platform.security.jwt.JwtTokenProvider(secretKey, validityInMilliseconds);
    }

    /**
     * Create the canonical JWT provider backed by a rotating key manager.
     *
     * @param keyManager key manager responsible for active signing and verification keys
     * @param validityInMilliseconds token validity window in milliseconds
     * @return canonical JWT provider exposed as the port type
     */
    public static JwtTokenProvider fromKeyManager(JwtKeyManager keyManager, long validityInMilliseconds) {
        return new com.ghatana.platform.security.jwt.JwtTokenProvider(keyManager, validityInMilliseconds);
    }
}