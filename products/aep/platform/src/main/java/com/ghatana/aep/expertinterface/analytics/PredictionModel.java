package com.ghatana.aep.expertinterface.analytics;

/**
 * Prediction model identifier.
 * 
 * @doc.type class
 * @doc.purpose Prediction model
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class PredictionModel {
    private String modelId;
    private String modelType;
    private double accuracy;
    
    public PredictionModel() {
    }
    
    public PredictionModel(String modelId) {
        this.modelId = modelId;
    }
    
    public PredictionModel(String modelId, String modelType, double accuracy) {
        this.modelId = modelId;
        this.modelType = modelType;
        this.accuracy = accuracy;
    }
    
    public String getModelId() { 
        return modelId; 
    }
    
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    
    public String getModelType() {
        return modelType;
    }
    
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
    
    public double getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
}
