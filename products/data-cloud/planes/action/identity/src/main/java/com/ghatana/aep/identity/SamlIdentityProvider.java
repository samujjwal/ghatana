package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.spi.IdentityResolver;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * SAML-backed {@link IdentityResolver} for federated AEP agent identities.
 *
 * <p>Each federated agent is registered with an expected SAML subject and assertion.
 * Resolution succeeds only when the assertion issuer, audience, subject, and validity
 * window match the configured federation contract.
 *
 * @doc.type class
 * @doc.purpose Resolve AEP agent identities via SAML-backed federated service principals
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class SamlIdentityProvider implements IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(SamlIdentityProvider.class);

    private final String identityProviderEntityId;
    private final String serviceProviderEntityId;
    private final Map<String, FederatedAgentRegistration> registrations;

    public SamlIdentityProvider(
            @NotNull String identityProviderEntityId,
            @NotNull String serviceProviderEntityId,
            @NotNull List<FederatedAgentRegistration> registrations) {
        this.identityProviderEntityId = Objects.requireNonNull(identityProviderEntityId, "identityProviderEntityId");
        this.serviceProviderEntityId = Objects.requireNonNull(serviceProviderEntityId, "serviceProviderEntityId");
        Objects.requireNonNull(registrations, "registrations");

        LinkedHashMap<String, FederatedAgentRegistration> byKey = new LinkedHashMap<>();
        for (FederatedAgentRegistration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            byKey.put(key(registration.tenantId(), registration.agentId()), registration);
        }
        this.registrations = Map.copyOf(byKey);
    }

    @Override
    public boolean supports(String tenantId, String agentId) {
        return registrations.containsKey(key(tenantId, agentId));
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        FederatedAgentRegistration registration = registrations.get(key(tenantId, agentId));
        if (registration == null) {
            return Promise.of(Optional.empty());
        }

        return Promise.of(resolveIdentity(registration));
    }

    private Optional<AgentIdentity> resolveIdentity(FederatedAgentRegistration registration) {
        try {
            SamlAssertion assertion = parseAssertion(registration.assertion());
            if (!identityProviderEntityId.equals(assertion.issuer())) {
                log.warn(
                    "SAML issuer mismatch for tenant={} agent={}: expected={}, actual={}",
                    registration.tenantId(),
                    registration.agentId(),
                    identityProviderEntityId,
                    assertion.issuer());
                return Optional.empty();
            }
            if (assertion.audience() != null && !serviceProviderEntityId.equals(assertion.audience())) {
                log.warn(
                    "SAML audience mismatch for tenant={} agent={}: expected={}, actual={}",
                    registration.tenantId(),
                    registration.agentId(),
                    serviceProviderEntityId,
                    assertion.audience());
                return Optional.empty();
            }
            if (!registration.subject().equals(assertion.subject())) {
                log.warn(
                    "SAML subject mismatch for tenant={} agent={}: expected={}, actual={}",
                    registration.tenantId(),
                    registration.agentId(),
                    registration.subject(),
                    assertion.subject());
                return Optional.empty();
            }

            Instant now = Instant.now();
            if (assertion.notBefore() != null && now.isBefore(assertion.notBefore())) {
                log.warn("SAML assertion is not yet valid for tenant={} agent={}", registration.tenantId(), registration.agentId());
                return Optional.empty();
            }
            if (assertion.notOnOrAfter() != null && !now.isBefore(assertion.notOnOrAfter())) {
                log.warn("SAML assertion expired for tenant={} agent={}", registration.tenantId(), registration.agentId());
                return Optional.empty();
            }

            LinkedHashSet<String> scopes = new LinkedHashSet<>();
            scopes.add("aep:execute");
            scopes.addAll(registration.scopes());

            return Optional.of(new AgentIdentity(
                registration.tenantId(),
                registration.agentId(),
                identityProviderEntityId + "/subject/" + assertion.subject(),
                Set.copyOf(scopes),
                now));
        } catch (Exception e) {
            log.warn(
                "Failed to resolve SAML identity for tenant={} agent={}: {}",
                registration.tenantId(),
                registration.agentId(),
                e.getMessage());
            return Optional.empty();
        }
    }

    private static SamlAssertion parseAssertion(String rawAssertion) throws Exception {
        String xml = normalizeAssertionXml(rawAssertion);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element root = document.getDocumentElement();

        String issuer = textOfFirst(root, "Issuer");
        String subject = textOfFirst(root, "NameID");
        String audience = textOfFirst(root, "Audience");

        Element conditions = firstElement(root, "Conditions");
        Instant notBefore = parseInstant(conditions != null ? conditions.getAttribute("NotBefore") : null);
        Instant notOnOrAfter = parseInstant(conditions != null ? conditions.getAttribute("NotOnOrAfter") : null);

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("SAML assertion issuer is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("SAML assertion subject is required");
        }

        return new SamlAssertion(issuer, subject, audience, notBefore, notOnOrAfter);
    }

    private static String normalizeAssertionXml(String rawAssertion) {
        Objects.requireNonNull(rawAssertion, "rawAssertion");
        String trimmed = rawAssertion.trim();
        if (trimmed.startsWith("<")) {
            return trimmed;
        }
        return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8).trim();
    }

    private static Element firstElement(Element root, String localName) {
        NodeList elements = root.getElementsByTagNameNS("*", localName);
        if (elements.getLength() == 0) {
            return null;
        }
        return (Element) elements.item(0);
    }

    private static String textOfFirst(Element root, String localName) {
        Element element = firstElement(root, localName);
        return element != null ? element.getTextContent().trim() : null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String key(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }

    private record SamlAssertion(
            String issuer,
            String subject,
            String audience,
            Instant notBefore,
            Instant notOnOrAfter) {
    }

    /**
     * SAML federation registration for a tenant-scoped AEP agent.
     */
    public record FederatedAgentRegistration(
            String tenantId,
            String agentId,
            String subject,
            String assertion,
            Set<String> scopes) {

        public FederatedAgentRegistration {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(assertion, "assertion");
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        }
    }
}