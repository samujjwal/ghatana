package com.ghatana.digitalmarketing.application.localization;

import com.ghatana.digitalmarketing.domain.localization.BrandLegalReviewConfig;
import com.ghatana.digitalmarketing.domain.localization.ConsentRuleConfig;
import com.ghatana.digitalmarketing.domain.localization.CurrencyConfig;
import com.ghatana.digitalmarketing.domain.localization.TimeZoneConfig;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of {@link LocalizationConfigService}.
 *
 * <p>Currently uses in-memory storage for development. Production implementation
 * should use PostgreSQL with proper schema and connection pooling.</p>
 *
 * @doc.type class
 * @doc.purpose Manages localization configurations with in-memory storage (P3-005)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class LocalizationConfigServiceImpl implements LocalizationConfigService {

    private final Map<String, CurrencyConfig> currencyConfigs = new ConcurrentHashMap<>();
    private final Map<String, TimeZoneConfig> timeZoneConfigs = new ConcurrentHashMap<>();
    private final Map<String, ConsentRuleConfig> consentRuleConfigs = new ConcurrentHashMap<>();
    private final Map<String, BrandLegalReviewConfig> brandLegalReviewConfigs = new ConcurrentHashMap<>();

    @Override
    public Promise<CurrencyConfig> getCurrencyConfig(String regionCode) {
        Objects.requireNonNull(regionCode, "regionCode must not be null");
        return Promise.of(Optional.ofNullable(currencyConfigs.get(regionCode))
            .orElseGet(() -> getDefaultCurrencyConfig(regionCode)));
    }

    @Override
    public Promise<TimeZoneConfig> getTimeZoneConfig(String regionCode) {
        Objects.requireNonNull(regionCode, "regionCode must not be null");
        return Promise.of(Optional.ofNullable(timeZoneConfigs.get(regionCode))
            .orElseGet(() -> getDefaultTimeZoneConfig(regionCode)));
    }

    @Override
    public Promise<ConsentRuleConfig> getConsentRuleConfig(String regionCode) {
        Objects.requireNonNull(regionCode, "regionCode must not be null");
        return Promise.of(Optional.ofNullable(consentRuleConfigs.get(regionCode))
            .orElseGet(() -> getDefaultConsentRuleConfig(regionCode)));
    }

    @Override
    public Promise<BrandLegalReviewConfig> getBrandLegalReviewConfig(String regionCode) {
        Objects.requireNonNull(regionCode, "regionCode must not be null");
        return Promise.of(Optional.ofNullable(brandLegalReviewConfigs.get(regionCode))
            .orElseGet(() -> getDefaultBrandLegalReviewConfig(regionCode)));
    }

    @Override
    public Promise<CurrencyConfig> saveCurrencyConfig(CurrencyConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        currencyConfigs.put(config.getLocaleCode(), config);
        return Promise.of(config);
    }

    @Override
    public Promise<TimeZoneConfig> saveTimeZoneConfig(TimeZoneConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        timeZoneConfigs.put(config.getRegionCode(), config);
        return Promise.of(config);
    }

    @Override
    public Promise<ConsentRuleConfig> saveConsentRuleConfig(ConsentRuleConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        consentRuleConfigs.put(config.getRegionCode(), config);
        return Promise.of(config);
    }

    @Override
    public Promise<BrandLegalReviewConfig> saveBrandLegalReviewConfig(BrandLegalReviewConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        brandLegalReviewConfigs.put(config.getRegionCode(), config);
        return Promise.of(config);
    }

    private CurrencyConfig getDefaultCurrencyConfig(String regionCode) {
        return CurrencyConfig.builder()
            .currencyCode("USD")
            .localeCode("en-US")
            .symbol("$")
            .decimalPlaces(2)
            .decimalSeparator(".")
            .thousandsSeparator(",")
            .symbolPosition("before")
            .build();
    }

    private TimeZoneConfig getDefaultTimeZoneConfig(String regionCode) {
        return TimeZoneConfig.builder()
            .regionCode(regionCode)
            .timeZoneId("UTC")
            .displayName("Coordinated Universal Time")
            .utcOffset("+00:00")
            .observesDST(false)
            .build();
    }

    private ConsentRuleConfig getDefaultConsentRuleConfig(String regionCode) {
        return ConsentRuleConfig.builder()
            .regionCode(regionCode)
            .consentFramework("GDPR")
            .explicitConsentRequired(true)
            .ageVerificationRequired(false)
            .minimumAge(18)
            .build();
    }

    private BrandLegalReviewConfig getDefaultBrandLegalReviewConfig(String regionCode) {
        return BrandLegalReviewConfig.builder()
            .regionCode(regionCode)
            .reviewFramework("Standard")
            .legalReviewRequired(true)
            .brandReviewRequired(true)
            .reviewTurnaroundDays(3)
            .build();
    }
}
