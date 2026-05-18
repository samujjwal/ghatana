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
    void encodeFilePathJoinsSegments() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeFilePath", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "src/components/Button.tsx");
        assertThat(encoded).isEqualTo("src%2Fcomponents%2FButton.tsx");
    }

    @Test
    @DisplayName("encodeFilePath encodes special chars in segment names")
    void encodeFilePathEncodesSpecialChars() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("encodeFilePath", String.class);
        method.setAccessible(true);
        String encoded = (String) method.invoke(null, "src/[test]/file.ts");
        assertThat(encoded).isEqualTo("src%2F%5Btest%5D%2Ffile.ts");
    }

    @Test
    @DisplayName("normalizeRepo strips gitlab.com prefix and .git suffix")
    void normalizeRepoStripsPrefix() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("normalizeRepo", String.class);
        method.setAccessible(true);
        String normalized = (String) method.invoke(null, "https://gitlab.com/my-group/my-project.git");
        assertThat(normalized).isEqualTo("my-group/my-project");
    }
}
