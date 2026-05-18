package com.ghatana.yappc.services.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.source.SourceLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Verifies GitHubSourceProvider uses commit pinning and SourceCredentialResolver
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubSourceProvider Commit Pinning Tests")
class GitHubSourceProviderCommitPinnedTest extends EventloopTestBase {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private SourceCredentialResolver credentialResolver;

    private GitHubSourceProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new GitHubSourceProvider(httpClient, new ObjectMapper(), credentialResolver);
        lenient().when(credentialResolver.resolve(any(), anyString(), anyString(), anyString(), anyString())).thenReturn("test-token");
        lenient().when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(httpResponse);
        lenient().when(httpResponse.statusCode()).thenReturn(200);
    }

    @Test
    @DisplayName("uses SourceCredentialResolver to resolve token, not inline env lookup")
    void usesCredentialResolver() throws Exception {
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("my-org/my-repo")
            .ref("main")
            .credentialRef("MY_GH_TOKEN")
            .tenantId("t1")
            .workspaceId("ws1")
            .projectId("p1")
            .build();

        when(httpResponse.body()).thenReturn(
            "{\"sha\": \"abc123\"}",
            "{\"truncated\": false, \"tree\": []}",
            "{\"content\": \"\", \"encoding\": \"base64\"}"
        );

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("t1", "ws1", "p1", "user1");
        runPromise(() -> provider.resolve(locator, scope));

        ArgumentCaptor<SourceLocator> locatorCaptor = ArgumentCaptor.forClass(SourceLocator.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        verify(credentialResolver).resolve(locatorCaptor.capture(), providerCaptor.capture(), anyString(), anyString(), anyString());

        assertThat(locatorCaptor.getValue().repoId()).isEqualTo("my-org/my-repo");
        assertThat(providerCaptor.getValue()).isEqualTo("github");
    }

    @Test
    @DisplayName("Authorization header is set using resolved credential token")
    void setsAuthorizationHeader() throws Exception {
        when(credentialResolver.resolve(any(), anyString(), anyString(), anyString(), anyString())).thenReturn("ghp_testtoken");
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("my-org/my-repo")
            .ref("main")
            .tenantId("t1")
            .workspaceId("ws1")
            .projectId("p1")
            .build();

        when(httpResponse.body()).thenReturn(
            "{\"sha\": \"abc123\"}",
            "{\"truncated\": false, \"tree\": []}",
            "{\"content\": \"\", \"encoding\": \"base64\"}"
        );

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("t1", "ws1", "p1", "user1");
        runPromise(() -> provider.resolve(locator, scope));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, atLeastOnce()).send(requestCaptor.capture(), any());

        boolean hasAuthHeader = requestCaptor.getAllValues().stream()
            .anyMatch(req -> req.headers().firstValue("Authorization")
                .map(v -> v.equals("Bearer ghp_testtoken"))
                .orElse(false));
        assertThat(hasAuthHeader).isTrue();
    }

    @Test
    @DisplayName("prefetch eligibility rejects vendor/build/binary paths")
    void prefetchEligibilityRejectsIneligiblePaths() throws Exception {
        java.lang.reflect.Method method = GitHubSourceProvider.class
            .getDeclaredMethod("isEligibleBlobPath", String.class);
        method.setAccessible(true);

        assertThat((Boolean) method.invoke(null, "src/main/App.ts")).isTrue();
        assertThat((Boolean) method.invoke(null, "node_modules/react/index.js")).isFalse();
        assertThat((Boolean) method.invoke(null, "dist/app.bundle.js")).isFalse();
        assertThat((Boolean) method.invoke(null, "assets/logo.png")).isFalse();
    }

    @Test
    @DisplayName("fails closed with PARTIAL_SNAPSHOT_REJECTED when total size limit is exceeded")
    void failsClosedWhenTotalSizeLimitExceeded() throws Exception {
        GitHubSourceProvider limitedProvider = new GitHubSourceProvider(
            httpClient,
            new ObjectMapper(),
            credentialResolver,
            1024,
            10
        );

        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("my-org/my-repo")
            .ref("main")
            .tenantId("t1")
            .workspaceId("ws1")
            .projectId("p1")
            .build();

        when(httpResponse.body()).thenReturn(
            "{\"sha\": \"abc123\"}",
            "{\"truncated\": false, \"tree\": ["
                + "{\"path\":\"src/one.ts\",\"type\":\"blob\",\"sha\":\"blob1\",\"size\":8},"
                + "{\"path\":\"src/two.ts\",\"type\":\"blob\",\"sha\":\"blob2\",\"size\":8}"
                + "]}",
            "{\"content\":\"MTIzNDU2Nzg=\",\"encoding\":\"base64\"}",
            "{\"content\":\"YWJjZGVmZ2g=\",\"encoding\":\"base64\"}"
        );

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext("t1", "ws1", "p1", "user1");

        assertThatThrownBy(() -> runPromise(() -> limitedProvider.resolve(locator, scope)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PARTIAL_SNAPSHOT_REJECTED");
    }
}
