package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SamlIdentityProvider}.
 *
 * @doc.type class
 * @doc.purpose Verify SAML-backed federated agent identity resolution
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SamlIdentityProvider")
class SamlIdentityProviderTest extends EventloopTestBase {

    @Test
    @DisplayName("resolve returns federated agent identity when the configured assertion matches")
    void resolveReturnsFederatedAgentIdentity() { // GH-90000
        SamlIdentityProvider provider = new SamlIdentityProvider( // GH-90000
            "https://idp.example.com/metadata",
            "https://aep.example.com/sp",
            List.of(new SamlIdentityProvider.FederatedAgentRegistration( // GH-90000
                "tenant-a",
                "agent-1",
                "saml-subject-1",
                encode(assertionXml("saml-subject-1", "https://idp.example.com/metadata", "https://aep.example.com/sp", "2099-04-15T00:00:00Z")), // GH-90000
                Set.of("aep:capability:routing"))));

        Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isPresent(); // GH-90000
        assertThat(identity.orElseThrow().tenantId()).isEqualTo("tenant-a");
        assertThat(identity.orElseThrow().agentId()).isEqualTo("agent-1");
        assertThat(identity.orElseThrow().spiffeId()) // GH-90000
            .isEqualTo("https://idp.example.com/metadata/subject/saml-subject-1");
        assertThat(identity.orElseThrow().scopes()) // GH-90000
            .contains("aep:execute", "aep:capability:routing"); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty when the SAML subject does not match the registration")
    void resolveReturnsEmptyWhenSubjectDoesNotMatch() { // GH-90000
        SamlIdentityProvider provider = new SamlIdentityProvider( // GH-90000
            "https://idp.example.com/metadata",
            "https://aep.example.com/sp",
            List.of(new SamlIdentityProvider.FederatedAgentRegistration( // GH-90000
                "tenant-a",
                "agent-1",
                "expected-subject",
                assertionXml("unexpected-subject", "https://idp.example.com/metadata", "https://aep.example.com/sp", "2099-04-15T00:00:00Z"), // GH-90000
                Set.of()))); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty when the SAML assertion is expired")
    void resolveReturnsEmptyWhenAssertionExpired() { // GH-90000
        SamlIdentityProvider provider = new SamlIdentityProvider( // GH-90000
            "https://idp.example.com/metadata",
            "https://aep.example.com/sp",
            List.of(new SamlIdentityProvider.FederatedAgentRegistration( // GH-90000
                "tenant-a",
                "agent-1",
                "saml-subject-1",
                assertionXml("saml-subject-1", "https://idp.example.com/metadata", "https://aep.example.com/sp", "2020-04-15T00:00:00Z"), // GH-90000
                Set.of()))); // GH-90000

        Optional<AgentIdentity> identity = runPromise(() -> provider.resolve("tenant-a", "agent-1")); // GH-90000

        assertThat(identity).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("supports and resolve return negative results for unregistered agents")
    void unregisteredAgentsAreNotSupported() { // GH-90000
        SamlIdentityProvider provider = new SamlIdentityProvider( // GH-90000
            "https://idp.example.com/metadata",
            "https://aep.example.com/sp",
            List.of()); // GH-90000

        assertThat(provider.supports("tenant-a", "agent-1")).isFalse(); // GH-90000
        assertThat(runPromise(() -> provider.resolve("tenant-a", "agent-1"))).isEmpty(); // GH-90000
    }

    private static String assertionXml(String subject, String issuer, String audience, String notOnOrAfter) { // GH-90000
        return "<Assertion xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
            + "<Issuer>" + issuer + "</Issuer>"
            + "<Subject><NameID>" + subject + "</NameID></Subject>"
            + "<Conditions NotBefore=\"2026-04-15T00:00:00Z\" NotOnOrAfter=\"" + notOnOrAfter + "\">"
            + "<AudienceRestriction><Audience>" + audience + "</Audience></AudienceRestriction>"
            + "</Conditions>"
            + "</Assertion>";
    }

    private static String encode(String value) { // GH-90000
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); // GH-90000
    }
}