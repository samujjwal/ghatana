package com.ghatana.refactorer.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**

 * @doc.type class

 * @doc.purpose Handles process exec test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ProcessExecTest {

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void run_echo_success() throws Exception {
        Path tmp = Files.createTempDirectory("pexec");
        var res =
                ProcessExec.run(
                        tmp, Duration.ofSeconds(5), List.of("sh", "-lc", "echo hello"), Map.of());
        assertThat(res.exitCode()).isZero();
        assertThat(res.out().trim()).isEqualTo("hello");
        assertThat(res.err()).isEmpty();
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void run_timeout() throws Exception {
        Path tmp = Files.createTempDirectory("pexec");
        var res =
                ProcessExec.run(
                        tmp, Duration.ofMillis(100), List.of("sh", "-lc", "sleep 2"), Map.of());
        assertThat(res.exitCode()).isEqualTo(-1);
        assertThat(res.err()).contains("Timed out");
    }
}
