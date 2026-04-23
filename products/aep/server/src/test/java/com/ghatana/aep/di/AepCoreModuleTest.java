package com.ghatana.aep.di;

import com.ghatana.aep.identity.OidcIdentityProvider;
import com.ghatana.aep.identity.SamlIdentityProvider;
import com.ghatana.identity.spi.IdentityResolver;
import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.toolruntime.ToolSandbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepCoreModule} tool-sandbox wiring.
 *
 * @doc.type class
 * @doc.purpose Verify AEP binds a policy-gated tool sandbox with fail-closed execution behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepCoreModule")
@ExtendWith(MockitoExtension.class) // GH-90000
class AepCoreModuleTest extends EventloopTestBase {

    @Mock
    private PolicyAsCodeEngine policyEngine;

    private final AepCoreModule module = new AepCoreModule(); // GH-90000

    @Test
    @DisplayName("tool sandbox fails closed after an allowed policy decision")
    void toolSandboxFailsClosedWithoutConcreteExecutionSandbox() { // GH-90000
        when(policyEngine.evaluate(eq("tenant-1"), eq("tool_execution_policy"), any()))
            .thenReturn(io.activej.promise.Promise.of(PolicyEvalResult.allow("tool_execution_policy")));

        ToolSandbox toolSandbox = module.toolSandbox(policyEngine); // GH-90000

        IllegalStateException thrown = assertThrows(IllegalStateException.class, // GH-90000
            () -> runPromise(() -> toolSandbox.execute("tenant-1", "agent-1", "delete-file", Map.of()))); // GH-90000

        assertThat(thrown.getMessage()).contains("no concrete execution sandbox is configured");
    }

    @Test
    @DisplayName("core module prepends OIDC federation resolver ahead of the in-memory fallback when configured")
    void identityResolversPreferOidcWhenConfigured() { // GH-90000
        TestAepCoreModule testModule = new TestAepCoreModule(Map.of( // GH-90000
            "AEP_OIDC_CLIENT_ID", "aep-client",
            "AEP_OIDC_CLIENT_SECRET", "aep-secret",
            "AEP_OIDC_TOKEN_ENDPOINT", "https://issuer.example.com/token",
            "AEP_OIDC_ISSUER_URI", "https://issuer.example.com",
            "AEP_OIDC_AGENT_SUBJECTS", "tenant-a:agent-1=oidc-subject-1",
            "AEP_OIDC_AGENT_TOKENS", "tenant-a:agent-1=token-1"));

        List<IdentityResolver> resolvers = testModule.identityResolvers(testModule.environment(), null); // GH-90000

        assertThat(resolvers).hasSize(2); // GH-90000
        assertThat(resolvers.get(0)).isInstanceOf(OidcIdentityProvider.class); // GH-90000
        assertThat(resolvers.get(1)).isInstanceOf(InMemoryIdentityResolver.class); // GH-90000
    }

    @Test
    @DisplayName("core module prepends SAML federation resolver ahead of the in-memory fallback when configured")
    void identityResolversPreferSamlWhenConfigured() { // GH-90000
        TestAepCoreModule testModule = new TestAepCoreModule(Map.of( // GH-90000
            "AEP_SAML_IDP_ENTITY_ID", "https://idp.example.com/metadata",
            "AEP_SAML_SP_ENTITY_ID", "https://aep.example.com/sp",
            "AEP_SAML_AGENT_SUBJECTS", "tenant-a:agent-1=saml-subject-1",
            "AEP_SAML_AGENT_ASSERTIONS", "tenant-a:agent-1=<Assertion xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\"><Issuer>https://idp.example.com/metadata</Issuer><Subject><NameID>saml-subject-1</NameID></Subject><Conditions NotBefore=\"2026-04-15T00:00:00Z\" NotOnOrAfter=\"2099-04-15T00:00:00Z\"><AudienceRestriction><Audience>https://aep.example.com/sp</Audience></AudienceRestriction></Conditions></Assertion>"));

        List<IdentityResolver> resolvers = testModule.identityResolvers(testModule.environment(), null); // GH-90000

        assertThat(resolvers).hasSize(2); // GH-90000
        assertThat(resolvers.get(0)).isInstanceOf(SamlIdentityProvider.class); // GH-90000
        assertThat(resolvers.get(1)).isInstanceOf(InMemoryIdentityResolver.class); // GH-90000
    }

    private static final class TestAepCoreModule extends AepCoreModule {
        private final Map<String, String> environment;

        private TestAepCoreModule(Map<String, String> environment) { // GH-90000
            this.environment = environment;
        }

        @Override
        protected Map<String, String> environment() { // GH-90000
            return environment;
        }

        @Override
        DataSource dataSource() { // GH-90000
            return null;
        }
    }
}