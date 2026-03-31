package com.ghatana.finance.ai;

import java.time.Instant;

/**
 * Fraud detection result.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for fraud detection results
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @doc.description Represents the result of a fraud detection analysis.
 * @doc.usage This class is used to transfer the results of a fraud detection analysis between different components of the system.
 */
public class FraudDetectionResult {
    private final String tradeId;
    private final String accountId;
    private final boolean suspicious;
    private final String fraudType;
    private final double confidence;
    private final boolean skipped;

    private FraudDetectionResult(String tradeId, String accountId, boolean suspicious,
                                 String fraudType, double confidence, boolean skipped) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.suspicious = suspicious;
        this.fraudType = fraudType;
        this.confidence = confidence;
        this.skipped = skipped;
    }

    public static FraudDetectionResult skip() {
        return new FraudDetectionResult(null, null, false, null, 0.0, true);
    }

    public static FraudDetectionResult clean(String tradeId, String accountId) {
        return new FraudDetectionResult(tradeId, accountId, false, null, 1.0, false);
    }

    public static FraudDetectionResult suspicious(String tradeId, String accountId,
                                                  String fraudType, double confidence) {
        return new FraudDetectionResult(tradeId, accountId, true, fraudType, confidence, false);
    }

    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public boolean isSuspicious() { return suspicious; }
    public String getFraudType() { return fraudType; }
    public double getConfidence() { return confidence; }
    public boolean isSkipped() { return skipped; }
}
