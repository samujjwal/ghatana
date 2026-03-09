package com.ghatana.aep.expertinterface.analytics;

import java.util.List;
import java.util.ArrayList;

/**
 * Time series forecast result.
 * 
 * @doc.type class
 * @doc.purpose Forecast result
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class TimeSeriesForecast {
    private List<Double> predictions;
    
    public TimeSeriesForecast() {
        this.predictions = new ArrayList<>();
    }
    
    public TimeSeriesForecast(List<Double> predictions) {
        this.predictions = new ArrayList<>(predictions);
    }
    
    public List<Double> getPredictions() { 
        return predictions; 
    }
    
    public void setPredictions(List<Double> predictions) {
        this.predictions = new ArrayList<>(predictions);
    }
    
    public void addPrediction(double prediction) {
        this.predictions.add(prediction);
    }
}
