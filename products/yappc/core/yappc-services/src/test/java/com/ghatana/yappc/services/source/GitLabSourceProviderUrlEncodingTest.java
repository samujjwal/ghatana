package com.ghatana.yappc.services.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.source.SourceLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("prefetch eligibility rejects vendor/build/binary paths")
    void prefetchEligibilityRejectsIneligiblePaths() throws Exception {
        Method method = GitLabSourceProvider.class.getDeclaredMethod("isEligibleBlobPath", String.class);
        method.setAccessible(true);

        assertThat((Boolean) method.invoke(null, "src/main/App.ts")).isTrue();
        assertThat((Boolean) method.invoke(null, "vendor/lib/index.js")).isFalse();
        assertThat((Boolean) method.invoke(null, "build/output/app.js")).isFalse();
        assertThat((Boolean) method.invoke(null, "assets/icon.png")).isFalse();
    }

    @Test
    @DisplayName("fails closed with PARTIAL_SNAPSHOT_REJECTED when total size limit is exceeded")
    void failsClosedWhenTotalSizeLimitExceeded() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        SourceCredentialResolver credentialResolver = mock(SourceCredentialResolver.class);

        GitLabSourceProvider provider = new GitLabSourceProvider(
            httpClient,
            new ObjectMapper(),
            credentialResolver,
            1024,
            10
        );

        when(credentialResolver.resolve(any(), anyString(), anyString(), anyString(), anyString())).thenReturn("test-token");
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(
            "{\"id\":\"abc123\"}",
            "["
                + "{\"path\":\"src/one.ts\",\"type\":\"blob\",\"size\":8},"
                + "{\"path\":\"src/two.ts\",\"type\":\"blob\",\"size\":8}"
                + "]",
            "{\"content\":\"MTIzNDU2Nzg=\",\"encoding\":\"base64\"}",
            "{\"content\":\"YWJjZGVmZ2g=\",\"encoding\":\"base64\"}"
        );

        SourceLocator locator = SourceLocator.builder()
            .provider("gitlab")
            .repoId("my-group/my-project")
            .ref("main")
            .tenantId("t1")
            .workspaceId("ws1")
            .projectId("p1")
            .build();

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("t1", "ws1", "p1", "user1");

        io.activej.promise.Promise<com.ghatana.yappc.domain.source.RepositorySnapshot> result = provider.resolve(locator, scope);
        assertThat(result.isException()).isTrue();
        assertThat(result.getException())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PARTIAL_SNAPSHOT_REJECTED");
    }
}
