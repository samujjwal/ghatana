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
@DisplayName("ReleaseAutomationManager Security Tests")
class ReleaseAutomationManagerSecurityTest {

    @TempDir
    Path projectRoot;

    /** The inner {@code GitOperations} instance extracted via reflection. */
    private Object gitOps;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        ReleaseAutomationManager manager = new ReleaseAutomationManager(projectRoot); // GH-90000
        Field gitOpsField = ReleaseAutomationManager.class.getDeclaredField("gitOps");
        gitOpsField.setAccessible(true); // GH-90000
        gitOps = gitOpsField.get(manager); // GH-90000
    }

    // =========================================================================
    // 1. validateRemote — remote URL allowlist (10.3.2 / 4.2.3) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("validateRemote — remote URL allowlist")
    class ValidateRemote {

        private void writeAllowlist(String... prefixes) throws Exception { // GH-90000
            Path dir = projectRoot.resolve("config/security");
            Files.createDirectories(dir); // GH-90000
            StringBuilder yaml = new StringBuilder("allowedPrefixes:\n");
            for (String prefix : prefixes) { // GH-90000
                yaml.append("  - \"").append(prefix).append("\"\n"); // GH-90000
            }
            Files.writeString(dir.resolve("allowed-git-remotes.yaml"), yaml.toString());
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
        @DisplayName("Allowed prefix — GitHub ghatana-technologies SSH — passes")
        void allowedGitHubSsh_passes() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Allowed prefix — GitHub ghatana-technologies HTTPS — passes")
        void allowedGitHubHttps_passes() throws Throwable { // GH-90000
            writeAllowlist("https://github.com/ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("https://github.com/ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Prefix match is case-insensitive")
        void prefixMatchIsCaseInsensitive() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("GIT@GITHUB.COM:ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("URL not in allowlist — throws SecurityException")
        void disallowedUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:unknown-org/malicious-repo.git"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("not in the allowed-git-remotes allowlist");
        }

        @Test
        @DisplayName("Third-party public GitHub URL — blocked")
        void thirdPartyGitHub_blocked() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote("https://github.com/attacker/exfiltrate.git"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Blank URL — throws SecurityException")
        void blankUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote(""))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("Null URL — throws SecurityException")
        void nullUrl_throwsSecurityException() throws Throwable { // GH-90000
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote(null)) // GH-90000
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Missing allowlist file — throws SecurityException (security misconfiguration)")
        void missingAllowlistFile_throwsSecurityException() throws Throwable { // GH-90000
            // No file created — allowlist is absent
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("allowed-git-remotes.yaml");
        }
    }

    // =========================================================================
    // 2. validateCommitMessage — argument injection prevention
    // =========================================================================

    @Nested
    @DisplayName("validateCommitMessage — commit message sanitization")
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
        @DisplayName("Valid conventional commit — passes")
        void validConventionalCommit_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeValidateCommitMessage( // GH-90000
                    "chore: release version 2.3.1\n\nFeatures:\n- New operator API"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("Null message — throws SecurityException")
        void nullMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage(null)) // GH-90000
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Blank message — throws SecurityException")
        void blankMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage("   "))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Message exceeding 2000 chars — throws SecurityException")
        void oversizedMessage_throwsSecurityException() throws Throwable { // GH-90000
            String huge = "a".repeat(2001); // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage(huge)) // GH-90000
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("maximum length");
        }

        @Test
        @DisplayName("Null byte in message — throws SecurityException (injection guard)")
        void nullByteInMessage_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeValidateCommitMessage("chore: release\0 --exec=malicious"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("control character");
        }

        @ParameterizedTest(name = "control char 0x{0} rejected") // GH-90000
        @ValueSource(strings = {"01", "06", "1B", "7F"}) // GH-90000
        @DisplayName("Non-printable control characters — all rejected")
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
    @DisplayName("sanitizeVersionString — version string sanitization")
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
        @DisplayName("Valid semver — passes through unchanged")
        void validSemver_passesThroughUnchanged() throws Throwable { // GH-90000
            assertThatCode(() -> { // GH-90000
                String result = invokeSanitize("2.3.1");
                assert "2.3.1".equals(result); // GH-90000
            }).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("SemVer with pre-release label — passes")
        void semverWithPreRelease_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeSanitize("1.0.0-SNAPSHOT")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SemVer with build metadata — passes")
        void semverWithBuildMetadata_passes() throws Throwable { // GH-90000
            assertThatCode(() -> invokeSanitize("1.0.0+20240101")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Shell semicolon injection — throws SecurityException")
        void shellSemicolon_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0; rm -rf /"))
                    .isInstanceOf(SecurityException.class) // GH-90000
                    .hasMessageContaining("unsafe characters");
        }

        @Test
        @DisplayName("Backtick command substitution — throws SecurityException")
        void backtickSubstitution_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0`id`"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Dollar-sign variable expansion — throws SecurityException")
        void dollarSignExpansion_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0$(ls)"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Pipe character — throws SecurityException")
        void pipeCharacter_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0|tee /etc/passwd"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }

        @Test
        @DisplayName("Space in version — throws SecurityException")
        void spaceInVersion_throwsSecurityException() throws Throwable { // GH-90000
            assertThatThrownBy(() -> invokeSanitize("1.0.0 RELEASE"))
                    .isInstanceOf(SecurityException.class); // GH-90000
        }
    }
}
