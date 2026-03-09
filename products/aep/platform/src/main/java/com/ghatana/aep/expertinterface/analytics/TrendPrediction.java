package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents a prediction of future metric trends based on historical data analysis.
 * 
 * <p>This class encapsulates the predicted value for a metric, enabling expert interfaces
 * to display forecasted trends and support proactive decision-making.
 *
 * @doc.type class
 * @doc.purpose Encapsulates trend prediction values for metrics forecasting
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class TrendPrediction {
    private double predictedValue;
    public double getPredictedValue() { return predictedValue; }
}
