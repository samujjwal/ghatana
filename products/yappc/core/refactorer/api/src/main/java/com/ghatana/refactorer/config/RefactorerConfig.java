package com.ghatana.refactorer.config;

/**
 * Minimal configuration holder for the Refactorer service.
 *
 * This lightweight class is intentionally small: it provides the methods
 * referenced by RefactorerHttpServer (http().host(), http().port()). The full
 * project may provide a richer config object via DI; this shim ensures
 * compile-time availability while the project migrations continue.
 
 * @doc.type class
 * @doc.purpose Handles refactorer config operations
 * @doc.layer core
 * @doc.pattern Configuration
*/
public final class RefactorerConfig {

    private final HttpConfig http;

    public RefactorerConfig(HttpConfig http) {
        this.http = http;
    }

    /**
     * Returns HTTP configuration used to bind the server.
     */
    public HttpConfig http() {
        return http;
    }

    /**
     * Simple HTTP configuration container.
     */
    public static final class HttpConfig {

        private final String host;
        private final int port;

        public HttpConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }
    }

    /**
     * Convenience factory for a default configuration used in simple tests or
     * when the DI system doesn't provide a RefactorerConfig instance.
     */
    public static RefactorerConfig defaultConfig() {
        return new RefactorerConfig(new HttpConfig("0.0.0.0", 8080));
    }
}
