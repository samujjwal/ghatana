/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.DynamicDataMaskingService.MaskingRule;

/**
 * Repository port for masking rule persistence.
 *
 * @doc.type interface
 * @doc.purpose Abstracts masking rule storage from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface MaskingRuleStore {

    MaskingRule fetchRule(String fieldPattern, String classificationLevel) throws Exception;

    void upsertRule(MaskingRule rule) throws Exception;
}
