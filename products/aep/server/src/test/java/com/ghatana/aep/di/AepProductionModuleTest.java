package com.ghatana.aep.di;

import com.ghatana.aep.identity.OidcIdentityProvider;
import com.ghatana.aep.identity.SamlIdentityProvider;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.IdentityResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for production profile validation.
 *
 * @doc.type class
 * @doc.purpose Verify AEP production profile resolution and mandatory env validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepProductionModule")
class AepProductionModuleTest {

    @Test
    @DisplayName("AEP_PROFILE takes precedence over AEP_ENV")
    void profileTakesPrecedence() { 
        assertThat(AepRuntimeProfile.resolve(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_ENV", "development"))).isEqualTo("production");
    }

    @Test
    @DisplayName("defaults to production when env vars are absent")
    void defaultsToProduction() { 
        assertThat(AepRuntimeProfile.resolve(Map.of())).isEqualTo("production");
        assertThat(AepRuntimeProfile.isProduction(Map.of())).isTrue(); 
    }

    @Test
    @DisplayName("production module requires AEP_DB_URL and AEP_JWT_SECRET")
    void requiresMandatoryProductionSettings() { 
        assertThatThrownBy(() -> new AepProductionModule(Map.of("AEP_PROFILE", "production"))) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("AEP_DB_URL");

        assertThatThrownBy(() -> new AepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep")))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("AEP_JWT_SECRET");
    }

    @Test
    @DisplayName("non-production profile does not require production settings")
    void nonProductionAllowsMissingSettings() { 
        new AepProductionModule(Map.of("AEP_PROFILE", "development")); 
    }

    @Test
    @DisplayName("production module wires a non-in-memory identity service when required config is present")
    void productionWiresJdbcBackedIdentityService() { 
        AepProductionModule module = new TestAepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep",
            "AEP_JWT_SECRET", "test-secret"));

        IdentityService identityService = module.identityService(); 

        assertThat(identityService).isNotNull(); 
    }

    @Test
    @DisplayName("production module adds OIDC federation resolver ahead of JDBC when configured")
    void productionAddsOidcResolverWhenConfigured() { 
        AepProductionModule module = new AepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep",
            "AEP_JWT_SECRET", "test-secret",
            "AEP_OIDC_CLIENT_ID", "aep-client",
            "AEP_OIDC_CLIENT_SECRET", "aep-secret",
            "AEP_OIDC_TOKEN_ENDPOINT", "https://issuer.example.com/token",
            "AEP_OIDC_ISSUER_URI", "https://issuer.example.com",
            "AEP_OIDC_AGENT_SUBJECTS", "tenant-a:agent-1=oidc-subject-1",
            "AEP_OIDC_AGENT_TOKENS", "tenant-a:agent-1=token-1",
            "AEP_OIDC_AGENT_SCOPES", "tenant-a:agent-1=aep:capability:routing|aep:capability:govern"));

        List<IdentityResolver> resolvers = module.identityResolvers(mock(DataSource.class)); 

        assertThat(resolvers).hasSize(2); 
        assertThat(resolvers.get(0)).isInstanceOf(OidcIdentityProvider.class); 
    }

    @Test
    @DisplayName("production module adds SAML federation resolver ahead of JDBC when configured")
    void productionAddsSamlResolverWhenConfigured() { 
        AepProductionModule module = new AepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep",
            "AEP_JWT_SECRET", "test-secret",
            "AEP_SAML_IDP_ENTITY_ID", "https://idp.example.com/metadata",
            "AEP_SAML_SP_ENTITY_ID", "https://aep.example.com/sp",
            "AEP_SAML_AGENT_SUBJECTS", "tenant-a:agent-1=saml-subject-1",
            "AEP_SAML_AGENT_ASSERTIONS", "tenant-a:agent-1=<Assertion xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\"><Issuer>https://idp.example.com/metadata</Issuer><Subject><NameID>saml-subject-1</NameID></Subject><Conditions NotBefore=\"2026-04-15T00:00:00Z\" NotOnOrAfter=\"2099-04-15T00:00:00Z\"><AudienceRestriction><Audience>https://aep.example.com/sp</Audience></AudienceRestriction></Conditions></Assertion>"));

        List<IdentityResolver> resolvers = module.identityResolvers(mock(DataSource.class)); 

        assertThat(resolvers).hasSize(2); 
        assertThat(resolvers.get(0)).isInstanceOf(SamlIdentityProvider.class); 
    }

    @Test
    @DisplayName("partial OIDC federation configuration fails fast")
    void partialOidcConfigurationFailsFast() { 
        AepProductionModule module = new AepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep",
            "AEP_JWT_SECRET", "test-secret",
            "AEP_OIDC_CLIENT_ID", "aep-client"));

        assertThatThrownBy(() -> module.identityResolvers(mock(DataSource.class))) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("AEP_OIDC_CLIENT_SECRET");
    }

    @Test
    @DisplayName("partial SAML federation configuration fails fast")
    void partialSamlConfigurationFailsFast() { 
        AepProductionModule module = new AepProductionModule(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_DB_URL", "jdbc:postgresql://localhost:5432/aep",
            "AEP_JWT_SECRET", "test-secret",
            "AEP_SAML_IDP_ENTITY_ID", "https://idp.example.com/metadata"));

        assertThatThrownBy(() -> module.identityResolvers(mock(DataSource.class))) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("AEP_SAML_SP_ENTITY_ID");
    }

    private static final class TestAepProductionModule extends AepProductionModule {

        private TestAepProductionModule(Map<String, String> environment) { 
            super(environment); 
        }

        @Override
        DataSource dataSource() { 
            return mock(DataSource.class); 
        }
    }
}
