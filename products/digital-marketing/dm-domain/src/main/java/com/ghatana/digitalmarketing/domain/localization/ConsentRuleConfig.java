package com.ghatana.digitalmarketing.domain.localization;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Consent rule configuration for a locale/region.
 *
 * @doc.type class
 * @doc.purpose Consent rule configuration with locale-specific requirements (P3-005)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class ConsentRuleConfig {

    private final String regionCode;
    private final String consentFramework;
    private final boolean explicitConsentRequired;
    private final boolean ageVerificationRequired;
    private final int minimumAge;
    private final Map<String, String> consentCategories;
    private final String consentTextTemplate;
    private final Instant effectiveDate;
    private final String regulatoryAuthority;

    private ConsentRuleConfig(Builder builder) {
        this.regionCode = Objects.requireNonNull(builder.regionCode, "regionCode must not be null");
        this.consentFramework = Objects.requireNonNull(builder.consentFramework, "consentFramework must not be null");
        this.explicitConsentRequired = builder.explicitConsentRequired;
        this.ageVerificationRequired = builder.ageVerificationRequired;
        this.minimumAge = builder.minimumAge;
        this.consentCategories = Map.copyOf(builder.consentCategories);
        this.consentTextTemplate = builder.consentTextTemplate;
        this.effectiveDate = builder.effectiveDate;
        this.regulatoryAuthority = builder.regulatoryAuthority;
    }

    public String getRegionCode() { return regionCode; }
    public String getConsentFramework() { return consentFramework; }
    public boolean isExplicitConsentRequired() { return explicitConsentRequired; }
    public boolean isAgeVerificationRequired() { return ageVerificationRequired; }
    public int getMinimumAge() { return minimumAge; }
    public Map<String, String> getConsentCategories() { return consentCategories; }
    public String getConsentTextTemplate() { return consentTextTemplate; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public String getRegulatoryAuthority() { return regulatoryAuthority; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsentRuleConfig)) return false;
        return regionCode.equals(((ConsentRuleConfig) o).regionCode) && consentFramework.equals(((ConsentRuleConfig) o).consentFramework);
    }

    @Override
    public int hashCode() { return regionCode.hashCode() ^ consentFramework.hashCode(); }

    @Override
    public String toString() {
        return "ConsentRuleConfig{regionCode='" + regionCode + "', framework='" + consentFramework + "'}";
    }

    public Builder toBuilder() {
        return new Builder()
            .regionCode(regionCode)
            .consentFramework(consentFramework)
            .explicitConsentRequired(explicitConsentRequired)
            .ageVerificationRequired(ageVerificationRequired)
            .minimumAge(minimumAge)
            .consentCategories(consentCategories)
            .consentTextTemplate(consentTextTemplate)
            .effectiveDate(effectiveDate)
            .regulatoryAuthority(regulatoryAuthority);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String regionCode;
        private String consentFramework;
        private boolean explicitConsentRequired;
        private boolean ageVerificationRequired;
        private int minimumAge = 18;
        private Map<String, String> consentCategories = Map.of();
        private String consentTextTemplate;
        private Instant effectiveDate;
        private String regulatoryAuthority;

        public Builder regionCode(String v) { this.regionCode = v; return this; }
        public Builder consentFramework(String v) { this.consentFramework = v; return this; }
        public Builder explicitConsentRequired(boolean v) { this.explicitConsentRequired = v; return this; }
        public Builder ageVerificationRequired(boolean v) { this.ageVerificationRequired = v; return this; }
        public Builder minimumAge(int v) { this.minimumAge = v; return this; }
        public Builder consentCategories(Map<String, String> v) { this.consentCategories = v; return this; }
        public Builder consentTextTemplate(String v) { this.consentTextTemplate = v; return this; }
        public Builder effectiveDate(Instant v) { this.effectiveDate = v; return this; }
        public Builder regulatoryAuthority(String v) { this.regulatoryAuthority = v; return this; }

        public ConsentRuleConfig build() {
            if (regionCode == null || regionCode.isBlank()) throw new IllegalArgumentException("regionCode must not be blank");
            if (consentFramework == null || consentFramework.isBlank()) throw new IllegalArgumentException("consentFramework must not be blank");
            return new ConsentRuleConfig(this);
        }
    }
}
