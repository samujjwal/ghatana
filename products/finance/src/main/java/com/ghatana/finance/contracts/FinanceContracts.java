/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.contracts;

import com.ghatana.kernel.contracts.KernelContract;
import java.util.List;
import java.util.Map;

/**
 * Component for FinanceContracts
 *
 * @doc.type class
 * @doc.purpose Component for FinanceContracts
 * @doc.layer product
 * @doc.pattern Registry
 */
public class FinanceContracts {

    public static final KernelContract TRANSACTION_API = GenericDomainContract.builder()
        .contractId("finance.api.transaction")
        .name("Transaction API")
        .version("1.0")
        .family(KernelContract.ContractFamily.API)
        .metadata(Map.of(
            "version", "1.0",
            "http_methods", "POST,GET,PUT",
            "path", "/api/v1/transactions",
            "authentication", "oauth2",
            "request_schema", "TransactionRequest",
            "response_schema", "TransactionResponse",
            "rate_limiting", "100/minute"
        ))
        .build();

    public static final KernelContract TRANSACTION_SCHEMA = GenericDomainContract.builder()
        .contractId("finance.schema.transaction")
        .name("Transaction Schema")
        .version("1.0")
        .family(KernelContract.ContractFamily.SCHEMA)
        .metadata(Map.of(
            "schema_type", "JSON",
            "fields", "id,amount,currency,timestamp,status",
            "compatibility_mode", "BACKWARD",
            "required_fields", "id,amount,currency",
            "validation_rules", "amount>0,currency in [USD,EUR,GBP]"
        ))
        .build();

    public static final KernelContract FRAUD_DETECTION_AUTONOMOUS = GenericDomainContract.builder()
        .contractId("finance.autonomous.fraud-detection")
        .name("Fraud Detection Autonomy Contract")
        .version("1.0")
        .family(KernelContract.ContractFamily.AUTONOMY)
        .metadata(Map.of(
            "agent_type", "fraud_detection",
            "autonomy_level", "MEDIUM",
            "human_review", "conditional",
            "review_criteria", "fraud_score>0.8",
            "ai_governed", "true",
            "model_approval", "required",
            "explainability", "true",
            "explanation_format", "feature_importance",
            "decision_logging", "true",
            "capabilities", "detect_fraud,assess_risk,analyze_transaction"
        ))
        .build();

    public static final KernelContract TRANSACTION_ANALYTICS = GenericDomainContract.builder()
        .contractId("finance.analytics.transactions")
        .name("Transaction Analytics")
        .version("1.0")
        .family(KernelContract.ContractFamily.ANALYTICS)
        .metadata(Map.of(
            "metric_types", "counter,gauge,histogram",
            "aggregation_methods", "sum,avg,percentile",
            "collection_frequency", "real-time",
            "sampling_rate", "100%",
            "real_time", "true",
            "latency_sla", "100ms",
            "retention", "7years",
            "regulatory", "true",
            "compliance_requirements", "SOX,PCI-DSS"
        ))
        .build();

    public static final KernelContract REGULATORY_REPORTING_API = GenericDomainContract.builder()
        .contractId("finance.api.regulatory-reporting")
        .name("Regulatory Reporting Workflow API")
        .version("1.0")
        .family(KernelContract.ContractFamily.API)
        .metadata(Map.of(
            "path", "/api/v1/regulatory-reports/submissions",
            "http_methods", "POST,GET",
            "authentication", "oauth2",
            "workflow", "maker-checker",
            "report_types", "MiFIDII,EMIR,SFTR,SEBON",
            "acknowledgement", "ACK,NACK,resubmit<=3"
        ))
        .build();

    public static final KernelContract FRAUD_EXPLAINABILITY_SCHEMA = GenericDomainContract.builder()
        .contractId("finance.schema.fraud-explanation")
        .name("Fraud Decision Explainability Schema")
        .version("1.0")
        .family(KernelContract.ContractFamily.SCHEMA)
        .metadata(Map.of(
            "schema_type", "JSON",
            "fields", "summary,primaryReason,topFactors",
            "top_factor_fields", "key,contribution,rationale",
            "compatibility_mode", "FORWARD",
            "required_fields", "summary,primaryReason,topFactors"
        ))
        .build();

    public static final KernelContract FRAUD_PERFORMANCE_BASELINE = GenericDomainContract.builder()
        .contractId("finance.analytics.fraud-performance-baseline")
        .name("Fraud Inference Performance Baseline")
        .version("1.0")
        .family(KernelContract.ContractFamily.ANALYTICS)
        .metadata(Map.of(
            "metric_types", "counter,histogram,trend",
            "scenario_types", "sustained_load,p99,error_rate,trend",
            "latency_sla", "150ms",
            "error_rate_limit", "0.1%",
            "resource_budget", "cpu<80%,memory<2048MB"
        ))
        .build();

    public static List<KernelContract> getAllContracts() {
        return List.of(
            TRANSACTION_API,
            TRANSACTION_SCHEMA,
            FRAUD_DETECTION_AUTONOMOUS,
            TRANSACTION_ANALYTICS,
            REGULATORY_REPORTING_API,
            FRAUD_EXPLAINABILITY_SCHEMA,
            FRAUD_PERFORMANCE_BASELINE
        );
    }
}
