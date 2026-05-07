package com.ghatana.digitalmarketing.domain.localization;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Brand/legal review configuration for a locale/region.
 *
 * @doc.type class
 * @doc.purpose Brand/legal review configuration with locale-specific requirements (P3-005)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class BrandLegalReviewConfig {

    private final String regionCode;
    private final String reviewFramework;
    private final boolean legalReviewRequired;
    private final boolean brandReviewRequired;
    private final List<String> prohibitedPhrases;
    private final List<String> requiredDisclaimers;
    private final Map<String, String> industrySpecificRules;
    private final int reviewTurnaroundDays;
    private final String approvalAuthority;

    private BrandLegalReviewConfig(Builder builder) {
        this.regionCode = Objects.requireNonNull(builder.regionCode, "regionCode must not be null");
        this.reviewFramework = Objects.requireNonNull(builder.reviewFramework, "reviewFramework must not be null");
        this.legalReviewRequired = builder.legalReviewRequired;
        this.brandReviewRequired = builder.brandReviewRequired;
        this.prohibitedPhrases = List.copyOf(builder.prohibitedPhrases);
        this.requiredDisclaimers = List.copyOf(builder.requiredDisclaimers);
        this.industrySpecificRules = Map.copyOf(builder.industrySpecificRules);
        this.reviewTurnaroundDays = builder.reviewTurnaroundDays;
        this.approvalAuthority = builder.approvalAuthority;
    }

    public String getRegionCode() { return regionCode; }
    public String getReviewFramework() { return reviewFramework; }
    public boolean isLegalReviewRequired() { return legalReviewRequired; }
    public boolean isBrandReviewRequired() { return brandReviewRequired; }
    public List<String> getProhibitedPhrases() { return prohibitedPhrases; }
    public List<String> getRequiredDisclaimers() { return requiredDisclaimers; }
    public Map<String, String> getIndustrySpecificRules() { return industrySpecificRules; }
    public int getReviewTurnaroundDays() { return reviewTurnaroundDays; }
    public String getApprovalAuthority() { return approvalAuthority; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrandLegalReviewConfig)) return false;
        return regionCode.equals(((BrandLegalReviewConfig) o).regionCode) && reviewFramework.equals(((BrandLegalReviewConfig) o).reviewFramework);
    }

    @Override
    public int hashCode() { return regionCode.hashCode() ^ reviewFramework.hashCode(); }

    @Override
    public String toString() {
        return "BrandLegalReviewConfig{regionCode='" + regionCode + "', framework='" + reviewFramework + "'}";
    }

    public Builder toBuilder() {
        return new Builder()
            .regionCode(regionCode)
            .reviewFramework(reviewFramework)
            .legalReviewRequired(legalReviewRequired)
            .brandReviewRequired(brandReviewRequired)
            .prohibitedPhrases(prohibitedPhrases)
            .requiredDisclaimers(requiredDisclaimers)
            .industrySpecificRules(industrySpecificRules)
            .reviewTurnaroundDays(reviewTurnaroundDays)
            .approvalAuthority(approvalAuthority);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String regionCode;
        private String reviewFramework;
        private boolean legalReviewRequired;
        private boolean brandReviewRequired;
        private List<String> prohibitedPhrases = List.of();
        private List<String> requiredDisclaimers = List.of();
        private Map<String, String> industrySpecificRules = Map.of();
        private int reviewTurnaroundDays = 3;
        private String approvalAuthority;

        public Builder regionCode(String v) { this.regionCode = v; return this; }
        public Builder reviewFramework(String v) { this.reviewFramework = v; return this; }
        public Builder legalReviewRequired(boolean v) { this.legalReviewRequired = v; return this; }
        public Builder brandReviewRequired(boolean v) { this.brandReviewRequired = v; return this; }
        public Builder prohibitedPhrases(List<String> v) { this.prohibitedPhrases = v; return this; }
        public Builder requiredDisclaimers(List<String> v) { this.requiredDisclaimers = v; return this; }
        public Builder industrySpecificRules(Map<String, String> v) { this.industrySpecificRules = v; return this; }
        public Builder reviewTurnaroundDays(int v) { this.reviewTurnaroundDays = v; return this; }
        public Builder approvalAuthority(String v) { this.approvalAuthority = v; return this; }

        public BrandLegalReviewConfig build() {
            if (regionCode == null || regionCode.isBlank()) throw new IllegalArgumentException("regionCode must not be blank");
            if (reviewFramework == null || reviewFramework.isBlank()) throw new IllegalArgumentException("reviewFramework must not be blank");
            return new BrandLegalReviewConfig(this);
        }
    }
}
