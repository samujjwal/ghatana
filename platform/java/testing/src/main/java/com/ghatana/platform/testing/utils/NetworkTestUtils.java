package com.ghatana.platform.testing.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/**
 * Shared network helpers for integration tests that need ephemeral ports
 * and deterministic server-readiness waits.
 *
 * @doc.type class
 * @doc.purpose Reusable network test utilities for ephemeral port allocation and readiness probing
 * @doc.layer platform
 * @doc.pattern Utils
 */
public final class NetworkTestUtils {

    private NetworkTestUtils() {
    }

    public static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static void waitForTcpPortOpen(String host, int port, long maxWaitMillis)
            throws IOException, InterruptedException {
        Instant deadline = Instant.now().plusMillis(maxWaitMillis);
        while (Instant.now().isBefore(deadline)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 250);
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        long waitedMillis = Duration.between(deadline.minusMillis(maxWaitMillis), Instant.now()).toMillis();
        throw new IOException("Port " + host + ":" + port + " did not open within " + waitedMillis + "ms");
    }
}

