/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.port;

import com.ghatana.appplatform.governance.DataRetentionPolicyService.RetentionAction;
import com.ghatana.appplatform.governance.DataRetentionPolicyService.RetentionPolicy;

import java.util.List;

/**
 * Repository port for retention policy persistence.
 *
 * @doc.type interface
 * @doc.purpose Abstracts retention policy storage from domain service
 * @doc.layer product
 * @doc.pattern Port
 */
public interface RetentionPolicyStore {

    RetentionPolicy insertPolicy(String policyId, String assetPattern, int retentionDays,
                                 RetentionAction action, String regulatoryBasis) throws Exception;

    RetentionPolicy loadPolicy(String policyId) throws Exception;

    List<RetentionPolicy> fetchAllPolicies() throws Exception;

    long countActivePolicies() throws Exception;

    RetentionPolicy deactivatePolicy(String policyId) throws Exception;

    List<AssetPolicyMatch> matchAssets() throws Exception;

    void applyPolicy(String policyId, String assetId) throws Exception;

    record AssetPolicyMatch(String assetId, String assetName, String policyId) {}
}
