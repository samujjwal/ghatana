package com.ghatana.digitalmarketing.application.localization;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.localization.CurrencyConfig;
import com.ghatana.digitalmarketing.domain.localization.TimeZoneConfig;
import com.ghatana.digitalmarketing.domain.localization.ConsentRuleConfig;
import com.ghatana.digitalmarketing.domain.localization.BrandLegalReviewConfig;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service interface for managing localization configurations.
 *
 * @doc.type class
 * @doc.purpose Defines localization config management contract (P3-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface LocalizationConfigService {

    Promise<CurrencyConfig> getCurrencyConfig(String regionCode);

    Promise<TimeZoneConfig> getTimeZoneConfig(String regionCode);

    Promise<ConsentRuleConfig> getConsentRuleConfig(String regionCode);

    Promise<BrandLegalReviewConfig> getBrandLegalReviewConfig(String regionCode);

    Promise<CurrencyConfig> saveCurrencyConfig(CurrencyConfig config);

    Promise<TimeZoneConfig> saveTimeZoneConfig(TimeZoneConfig config);

    Promise<ConsentRuleConfig> saveConsentRuleConfig(ConsentRuleConfig config);

    Promise<BrandLegalReviewConfig> saveBrandLegalReviewConfig(BrandLegalReviewConfig config);
}
