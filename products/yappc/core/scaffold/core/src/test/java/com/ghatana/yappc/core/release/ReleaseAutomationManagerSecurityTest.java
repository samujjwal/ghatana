package com.ghatana.yappc.core.release;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Security-focused tests for {@link ReleaseAutomationManager}.
 *
 * <p>Covers three security boundaries implemented in {@code GitOperations}:
 * <ol>
 *   <li><b>Remote allowlist</b> — {@code validateRemote()} must block URLs not in // GH-90000
 *       {@code config/security/allowed-git-remotes.yaml}.</li>
 *   <li><b>Commit message sanitization</b> — {@code validateCommitMessage()} must reject // GH-90000
 *       messages with null bytes or non-printable control characters.</li>
 *   <li><b>Version string sanitization</b> — {@code sanitizeVersionString()} must reject // GH-90000
 *       version strings containing shell-special characters.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Security tests for ReleaseAutomationManager.GitOperations security boundaries
 * @doc.layer product
 * @doc.pattern SecurityTest
 */
@DisplayName("ReleaseAutomationManager Security Tests [GH-90000]")
class ReleaseAutomationManagerSecurityTest {

    @TempDir
    Path projectRoot;

    /** The inner {@code GitOperations} instance extracted via reflection. */
    private Object gitOps;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        ReleaseAutomationManager manager = new ReleaseAutomationManager(projectRoot); // GH-90000
        Field gitOpsField = ReleaseAutomationManager.class.getDeclaredField("gitOps [GH-90000]");
        gitOpsField.setAccessible(true); // GH-90000
        gitOps = gitOpsField.get(manager); // GH-90000
    }

    // =========================================================================
    // 1. validateRemote — remote URL allowlist (10.3.2 / 4.2.3) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("validateRemote — remote URL allowlist [GH-90000]")
    class ValidateRemote {

        private void writeAllowlist(String... prefixes) throws Exception { // GH-90000
            Path dir = projectRoot.resolve("config/security [GH-90000]");
            Files.createDirectories(dir); // GH-90000
            StringBuilder yaml = new StringBuilder("allowedPrefixes:\n [GH-90000]");
            for (String prefix : prefixes) { // GH-90000
                yaml.append("  - \"").append(prefix).append("\"\n"); // GH-90000
            }
            Files.writeString(dir.resolve("allowed-git-remotes.yaml [GH-90000]"), yaml.toString());
        }

        private void invokeValidateRemote(String url) throws Throwable { // GH-90000
            Method method = gitOps.getClass().getDeclaredMethod("validateRemote", String.class); // GH-90000
            method.setAccessible(true); // GH-90000
            try {
                method.invoke(gitOps, url); // GH-90000
            } catch (InvocationTargetException e) { // GH-90000
                throw e.getCause(); // GH-90000
            }
        }

        @Test
        @DisplayName("Allowed prefix — GitHub ghatana-technologies SSH — passes [GH-90000]")
        void allowedGitHubSsh_passes() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatCode(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Allowed prefix — GitHub ghatana-technologies HTTPS — passes [GH-90000]")
        void allowedGitHubHttps_passes() throws Throwable { // GH-90000
            writeAllowlist("https://github.com/ghatana-technologies/ [GH-90000]");
            assertThatCode(() -> invokeValidateRemote("https://github.com/ghatana-technologies/yappc.git [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Prefix match is case-insensitive [GH-90000]")
        void prefixMatchIsCaseInsensitive() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatCode(() -> invokeValidateRemote("GIT@GITHUB.COM:ghatana-technologies/yappc.git [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("URL not in allowlist — throws SecurityException [GH-90000]")
        void disallowedUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:unknown-org/malicious-repo.git [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("not in the allowed-git-remotes allowlist [GH-90000]");
        }

        @Test
        @DisplayName("Third-party public GitHub URL — blocked [GH-90000]")
        void thirdPartyGitHub_blocked() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatThrownBy(() -> invokeValidateRemote("https://github.com/attacker/exfiltrate.git [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Blank URL — throws SecurityException [GH-90000]")
        void blankUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatThrownBy(() -> invokeValidateRemote(" [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        @DisplayName("Null URL — throws SecurityException [GH-90000]")
        void nullUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/ [GH-90000]");
            assertThatThrownBy(() -> invokeValidateRemote(null)) // GH-90000
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Missing allowlist file — throws SecurityException (security misconfiguration) [GH-90000]")
        void missingAllowlistFile_throwsSecurityException() throws Throwable { // GH-90000
            // No file created — allowlist is absent
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("allowed-git-remotes.yaml [GH-90000]");
        }
    }

    // =========================================================================
    // 2. validateCommitMessage — argument injection prevention
    // =========================================================================

    @Nested
    @DisplayName("validateCommitMessage — commit message sanitization [GH-90000]")
    class ValidateCommitMessage {

        private void invokeValidateCommitMessage(String msg) throws Throwable { // GH-90000
            Method method = gitOps.getClass().getDeclaredMethod("validateCommitMessage", String.class); // GH-90000
            method.setAccessible(true); // GH-90000
            try {
                method.invoke(null, msg); // static method // GH-90000
            } catch (InvocationTargetException e) { // GH-90000
                throw e.getCause(); // GH-90000
            }
        }

        @Test
        @DisplayName("Valid conventional commit — passes [GH-90000]")
        void validConventionalCommit_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeValidateCommitMessage( // GH-90000
                    "chore: release version 2.3.1\n\nFeatures:\n- New operator API"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Null message — throws SecurityException [GH-90000]")
        void nullMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage(null)) // GH-90000
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Blank message — throws SecurityException [GH-90000]")
        void blankMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage("    [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Message exceeding 2000 chars — throws SecurityException [GH-90000]")
        void oversizedMessage_throwsSecurityException() throws Throwable { // GH-90000
            String huge = "a".repeat(2001); // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage(huge)) // GH-90000
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("maximum length [GH-90000]");
        }

        @Test
        @DisplayName("Null byte in message — throws SecurityException (injection guard) [GH-90000]")
        void nullByteInMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage("chore: release\0 --exec=malicious [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("control character [GH-90000]");
        }

        @ParameterizedTest(name = "control char 0x{0} rejected") // GH-90000
        @ValueSource(strings = {"01", "06", "1B", "7F"}) // GH-90000
        @DisplayName("Non-printable control characters — all rejected [GH-90000]")
        void controlCharacters_throwsSecurityException(String hexCode) throws Throwable { // GH-90000
            char controlChar = (char) Integer.parseInt(hexCode, 16); // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage("chore: release " + controlChar)) // GH-90000
                    .isInstanceOf(SecurityException.class); // GH-90000
        }
    }

    // =========================================================================
    // 3. sanitizeVersionString — version injection prevention
    // =========================================================================

    @Nested
    @DisplayName("sanitizeVersionString — version string sanitization [GH-90000]")
    class SanitizeVersionString {

        private String invokeSanitize(String version) throws Throwable { // GH-90000
            Method method = gitOps.getClass().getDeclaredMethod("sanitizeVersionString", String.class); // GH-90000
            method.setAccessible(true); // GH-90000
            try {
                return (String) method.invoke(null, version); // static method // GH-90000
            } catch (InvocationTargetException e) { // GH-90000
                throw e.getCause(); // GH-90000
            }
        }

        @Test
        @DisplayName("Valid semver — passes through unchanged [GH-90000]")
        void validSemver_passesThroughUnchanged() throws Throwable { // GH-90000
            assertThatCode(() -> { // GH-90000
                String result = invokeSanitize("2.3.1 [GH-90000]");
                assert "2.3.1".equals(result); // GH-90000
            }).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("SemVer with pre-release label — passes [GH-90000]")
        void semverWithPreRelease_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeSanitize("1.0.0-SNAPSHOT [GH-90000]")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SemVer with build metadata — passes [GH-90000]")
        void semverWithBuildMetadata_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeSanitize("1.0.0+20240101 [GH-90000]")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Shell semicolon injection — throws SecurityException [GH-90000]")
        void shellSemicolon_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0; rm -rf / [GH-90000]"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("unsafe characters [GH-90000]");
        }

        @Test
        @DisplayName("Backtick command substitution — throws SecurityException [GH-90000]")
        void backtickSubstitution_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0`id` [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Dollar-sign variable expansion — throws SecurityException [GH-90000]")
        void dollarSignExpansion_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0$(ls) [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Pipe character — throws SecurityException [GH-90000]")
        void pipeCharacter_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0|tee /etc/passwd [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Space in version — throws SecurityException [GH-90000]")
        void spaceInVersion_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0 RELEASE [GH-90000]"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }
    }
}
