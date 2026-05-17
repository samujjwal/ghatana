package com.ghatana.yappc.services.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies GitLabSourceProvider correctly encodes project IDs and file paths
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@DisplayName("GitLabSourceProvider URL Encoding Tests")
class GitLabSourceProviderUrlEncodingTest {

    private final GitLabSourceProvider provider = new GitLabSourceProvider();

    @Test
    @DisplayName("encodeProjectId encodes slash as %2F for namespace/project")
    void encodeProjectIdEncodesSlash() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeProjectId", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "my-group/my-project");
        assertThat(encoded).isEqualTo("my-group%2Fmy-project");
    }

    @Test
    @DisplayName("encodeProjectId encodes nested group paths")
    void encodeProjectIdEncodesNestedGroups() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeProjectId", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "org/sub-group/service");
        assertThat(encoded).isEqualTo("org%2Fsub-group%2Fservice");
    }

    @Test
    @DisplayName("encodeFilePath joins path segments with %2F between encoded segments")
    void encodeFilePathJoinsSegmentsWithEncodedSlash() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeFilePath", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "src/main/App.ts");
        // Each segment is URL-encoded, slashes between segments are %2F
        assertThat(encoded).isEqualTo("src%2Fmain%2FApp.ts");
    }

    @Test
    @DisplayName("encodeFilePath encodes special chars in segment names")
    void encodeFilePathEncodesSpecialCharsInSegments() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeFilePath", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "src/my component/index.tsx");
        // Java URLEncoder encodes spaces as '+' while preserving path separators as %2F
        assertThat(encoded).contains("+");
        assertThat(encoded).contains("%2F");
    }

    @Test
    @DisplayName("normalizeRepo strips gitlab.com prefix and .git suffix")
    void normalizeRepoStripsGitlabPrefixAndGitSuffix() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("normalizeRepo", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(null, "https://gitlab.com/my-org/my-repo.git"))
            .isEqualTo("my-org/my-repo");
        assertThat((String) method.invoke(null, "git@gitlab.com:my-org/my-repo.git"))
            .isEqualTo("my-org/my-repo");
        assertThat((String) method.invoke(null, "my-org/my-repo"))
            .isEqualTo("my-org/my-repo");
    }
}
