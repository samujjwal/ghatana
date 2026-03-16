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
 *   <li><b>Remote allowlist</b> — {@code validateRemote()} must block URLs not in
 *       {@code config/security/allowed-git-remotes.yaml}.</li>
 *   <li><b>Commit message sanitization</b> — {@code validateCommitMessage()} must reject
 *       messages with null bytes or non-printable control characters.</li>
 *   <li><b>Version string sanitization</b> — {@code sanitizeVersionString()} must reject
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
    void setUp() throws Exception {
        ReleaseAutomationManager manager = new ReleaseAutomationManager(projectRoot);
        Field gitOpsField = ReleaseAutomationManager.class.getDeclaredField("gitOps");
        gitOpsField.setAccessible(true);
        gitOps = gitOpsField.get(manager);
    }

    // =========================================================================
    // 1. validateRemote — remote URL allowlist (10.3.2 / 4.2.3)
    // =========================================================================

    @Nested
    @DisplayName("validateRemote — remote URL allowlist")
    class ValidateRemote {

        private void writeAllowlist(String... prefixes) throws Exception {
            Path dir = projectRoot.resolve("config/security");
            Files.createDirectories(dir);
            StringBuilder yaml = new StringBuilder("allowedPrefixes:\n");
            for (String prefix : prefixes) {
                yaml.append("  - \"").append(prefix).append("\"\n");
            }
            Files.writeString(dir.resolve("allowed-git-remotes.yaml"), yaml.toString());
        }

        private void invokeValidateRemote(String url) throws Throwable {
            Method method = gitOps.getClass().getDeclaredMethod("validateRemote", String.class);
            method.setAccessible(true);
            try {
                method.invoke(gitOps, url);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Test
        @DisplayName("Allowed prefix — GitHub ghatana-technologies SSH — passes")
        void allowedGitHubSsh_passes() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Allowed prefix — GitHub ghatana-technologies HTTPS — passes")
        void allowedGitHubHttps_passes() throws Throwable {
            writeAllowlist("https://github.com/ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("https://github.com/ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Prefix match is case-insensitive")
        void prefixMatchIsCaseInsensitive() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatCode(() -> invokeValidateRemote("GIT@GITHUB.COM:ghatana-technologies/yappc.git"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("URL not in allowlist — throws SecurityException")
        void disallowedUrl_throwsSecurityException() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:unknown-org/malicious-repo.git"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("not in the allowed-git-remotes allowlist");
        }

        @Test
        @DisplayName("Third-party public GitHub URL — blocked")
        void thirdPartyGitHub_blocked() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote("https://github.com/attacker/exfiltrate.git"))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Blank URL — throws SecurityException")
        void blankUrl_throwsSecurityException() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote(""))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("Null URL — throws SecurityException")
        void nullUrl_throwsSecurityException() throws Throwable {
            writeAllowlist("git@github.com:ghatana-technologies/");
            assertThatThrownBy(() -> invokeValidateRemote(null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Missing allowlist file — throws SecurityException (security misconfiguration)")
        void missingAllowlistFile_throwsSecurityException() throws Throwable {
            // No file created — allowlist is absent
            assertThatThrownBy(() -> invokeValidateRemote("git@github.com:ghatana-technologies/yappc.git"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("allowed-git-remotes.yaml");
        }
    }

    // =========================================================================
    // 2. validateCommitMessage — argument injection prevention
    // =========================================================================

    @Nested
    @DisplayName("validateCommitMessage — commit message sanitization")
    class ValidateCommitMessage {

        private void invokeValidateCommitMessage(String msg) throws Throwable {
            Method method = gitOps.getClass().getDeclaredMethod("validateCommitMessage", String.class);
            method.setAccessible(true);
            try {
                method.invoke(null, msg); // static method
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Test
        @DisplayName("Valid conventional commit — passes")
        void validConventionalCommit_passes() throws Throwable {
            assertThatCode(() -> invokeValidateCommitMessage(
                    "chore: release version 2.3.1\n\nFeatures:\n- New operator API"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Null message — throws SecurityException")
        void nullMessage_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeValidateCommitMessage(null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Blank message — throws SecurityException")
        void blankMessage_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeValidateCommitMessage("   "))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Message exceeding 2000 chars — throws SecurityException")
        void oversizedMessage_throwsSecurityException() throws Throwable {
            String huge = "a".repeat(2001);
            assertThatThrownBy(() -> invokeValidateCommitMessage(huge))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("maximum length");
        }

        @Test
        @DisplayName("Null byte in message — throws SecurityException (injection guard)")
        void nullByteInMessage_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeValidateCommitMessage("chore: release\0 --exec=malicious"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("control character");
        }

        @ParameterizedTest(name = "control char 0x{0} rejected")
        @ValueSource(strings = {"01", "06", "1B", "7F"})
        @DisplayName("Non-printable control characters — all rejected")
        void controlCharacters_throwsSecurityException(String hexCode) throws Throwable {
            char controlChar = (char) Integer.parseInt(hexCode, 16);
            assertThatThrownBy(() -> invokeValidateCommitMessage("chore: release " + controlChar))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // =========================================================================
    // 3. sanitizeVersionString — version injection prevention
    // =========================================================================

    @Nested
    @DisplayName("sanitizeVersionString — version string sanitization")
    class SanitizeVersionString {

        private String invokeSanitize(String version) throws Throwable {
            Method method = gitOps.getClass().getDeclaredMethod("sanitizeVersionString", String.class);
            method.setAccessible(true);
            try {
                return (String) method.invoke(null, version); // static method
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Test
        @DisplayName("Valid semver — passes through unchanged")
        void validSemver_passesThroughUnchanged() throws Throwable {
            assertThatCode(() -> {
                String result = invokeSanitize("2.3.1");
                assert "2.3.1".equals(result);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SemVer with pre-release label — passes")
        void semverWithPreRelease_passes() throws Throwable {
            assertThatCode(() -> invokeSanitize("1.0.0-SNAPSHOT")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SemVer with build metadata — passes")
        void semverWithBuildMetadata_passes() throws Throwable {
            assertThatCode(() -> invokeSanitize("1.0.0+20240101")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Shell semicolon injection — throws SecurityException")
        void shellSemicolon_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeSanitize("1.0.0; rm -rf /"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("unsafe characters");
        }

        @Test
        @DisplayName("Backtick command substitution — throws SecurityException")
        void backtickSubstitution_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeSanitize("1.0.0`id`"))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Dollar-sign variable expansion — throws SecurityException")
        void dollarSignExpansion_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeSanitize("1.0.0$(ls)"))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Pipe character — throws SecurityException")
        void pipeCharacter_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeSanitize("1.0.0|tee /etc/passwd"))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("Space in version — throws SecurityException")
        void spaceInVersion_throwsSecurityException() throws Throwable {
            assertThatThrownBy(() -> invokeSanitize("1.0.0 RELEASE"))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
