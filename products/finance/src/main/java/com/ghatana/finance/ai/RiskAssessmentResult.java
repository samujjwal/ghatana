package com.ghatana.finance.ai;

/**
 * Risk assessment result.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for risk assessment results
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class RiskAssessmentResult {
    private final String portfolioId;
    private final double var95;
    private final double var99;
    private final double expectedShortfall;
    private final double beta;
    private final double concentrationRisk;
    private final double liquidityRisk;
    private final double creditRisk;
    private final double overallRiskScore;
    private final double confidence;
    private final boolean skipped;

    private RiskAssessmentResult(String portfolioId, double var95, double var99, double expectedShortfall,
                                 double beta, double concentrationRisk, double liquidityRisk, double creditRisk,
                                 double overallRiskScore, double confidence, boolean skipped) {
        this.portfolioId = portfolioId;
        this.var95 = var95;
        this.var99 = var99;
        this.expectedShortfall = expectedShortfall;
        this.beta = beta;
        this.concentrationRisk = concentrationRisk;
        this.liquidityRisk = liquidityRisk;
        this.creditRisk = creditRisk;
        this.overallRiskScore = overallRiskScore;
        this.confidence = confidence;
        this.skipped = skipped;
    }

    public static RiskAssessmentResult skip() {
        return new RiskAssessmentResult(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    public static RiskAssessmentResult create(String portfolioId, double var95, double var99,
                                              double expectedShortfall, double beta, double concentrationRisk,
                                              double liquidityRisk, double creditRisk, double overallRiskScore,
                                              double confidence) {
        return new RiskAssessmentResult(portfolioId, var95, var99, expectedShortfall, beta,
            concentrationRisk, liquidityRisk, creditRisk, overallRiskScore, confidence, false);
    }

    public String getPortfolioId() { return portfolioId; }
    public double getVar95() { return var95; }
    public double getVar99() { return var99; }
    public double getExpectedShortfall() { return expectedShortfall; }
    public double getBeta() { return beta; }
    public double getConcentrationRisk() { return concentrationRisk; }
    public double getLiquidityRisk() { return liquidityRisk; }
    public double getCreditRisk() { return creditRisk; }
    public double getOverallRiskScore() { return overallRiskScore; }
    public double getConfidence() { return confidence; }
    public boolean isSkipped() { return skipped; }
}
