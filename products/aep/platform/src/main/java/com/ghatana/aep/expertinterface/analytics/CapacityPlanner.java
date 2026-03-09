package com.ghatana.aep.expertinterface.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Capacity planning service for resource optimization and scaling recommendations.
 * 
 * @doc.type class
 * @doc.purpose Capacity planning and resource optimization
 * @doc.layer analytics
 */
public class CapacityPlanner {
    private static final Logger log = LoggerFactory.getLogger(CapacityPlanner.class);
    
    /**
     * Plans capacity based on current and projected resource utilization.
     * 
     * @param data capacity planning data
     * @return capacity plan with recommendations
     */
    public CapacityPlan planCapacity(CapacityPlanningData data) {
        Objects.requireNonNull(data, "Capacity planning data is required");
        
        log.debug("Planning capacity for: {}", data.getResourceType());
        
        // Analyze current utilization
        ResourceUtilization currentUtilization = analyzeCurrentUtilization(data);
        
        // Forecast future utilization
        ResourceForecast forecast = forecastUtilization(data, currentUtilization);
        
        // Calculate required capacity
        CapacityRequirement requirement = calculateRequiredCapacity(data, forecast);
        
        // Generate scaling recommendations
        List<ScalingRecommendation> recommendations = generateScalingRecommendations(
            data, currentUtilization, forecast, requirement
        );
        
        // Calculate cost implications
        CostAnalysis costAnalysis = analyzeCosts(data, requirement, recommendations);
        
        CapacityPlan plan = new CapacityPlan(
            data.getResourceType(),
            currentUtilization,
            forecast,
            requirement,
            recommendations,
            costAnalysis,
            Instant.now()
        );
        
        log.info("Capacity plan generated: resource={}, recommendations={}", 
            data.getResourceType(), recommendations.size());
        
        return plan;
    }
    
    private ResourceUtilization analyzeCurrentUtilization(CapacityPlanningData data) {
        double cpuUtilization = data.getCurrentCpuUsage();
        double memoryUtilization = data.getCurrentMemoryUsage();
        double storageUtilization = data.getCurrentStorageUsage();
        double networkUtilization = data.getCurrentNetworkUsage();
        
        // Calculate peak utilization
        double peakCpu = data.getPeakCpuUsage();
        double peakMemory = data.getPeakMemoryUsage();
        double peakStorage = data.getPeakStorageUsage();
        double peakNetwork = data.getPeakNetworkUsage();
        
        // Calculate average utilization
        double avgCpu = (cpuUtilization + peakCpu) / 2;
        double avgMemory = (memoryUtilization + peakMemory) / 2;
        double avgStorage = (storageUtilization + peakStorage) / 2;
        double avgNetwork = (networkUtilization + peakNetwork) / 2;
        
        // Determine utilization level
        UtilizationLevel level = determineUtilizationLevel(avgCpu, avgMemory, avgStorage, avgNetwork);
        
        return new ResourceUtilization(
            cpuUtilization, memoryUtilization, storageUtilization, networkUtilization,
            peakCpu, peakMemory, peakStorage, peakNetwork,
            avgCpu, avgMemory, avgStorage, avgNetwork,
            level
        );
    }
    
    private ResourceForecast forecastUtilization(CapacityPlanningData data, ResourceUtilization current) {
        // Simple linear growth model
        double growthRate = data.getGrowthRate();
        int forecastDays = data.getForecastDays();
        
        // Forecast CPU
        double forecastCpu = current.getAvgCpu() * (1 + (growthRate * forecastDays / 365.0));
        
        // Forecast Memory
        double forecastMemory = current.getAvgMemory() * (1 + (growthRate * forecastDays / 365.0));
        
        // Forecast Storage (typically grows faster)
        double forecastStorage = current.getAvgStorage() * (1 + (growthRate * 1.5 * forecastDays / 365.0));
        
        // Forecast Network
        double forecastNetwork = current.getAvgNetwork() * (1 + (growthRate * forecastDays / 365.0));
        
        // Calculate when capacity will be exhausted
        int daysUntilCpuExhausted = calculateDaysUntilExhausted(current.getAvgCpu(), growthRate, 0.9);
        int daysUntilMemoryExhausted = calculateDaysUntilExhausted(current.getAvgMemory(), growthRate, 0.9);
        int daysUntilStorageExhausted = calculateDaysUntilExhausted(current.getAvgStorage(), growthRate * 1.5, 0.9);
        
        return new ResourceForecast(
            forecastCpu, forecastMemory, forecastStorage, forecastNetwork,
            daysUntilCpuExhausted, daysUntilMemoryExhausted, daysUntilStorageExhausted,
            forecastDays
        );
    }
    
    private int calculateDaysUntilExhausted(double currentUtilization, double growthRate, double threshold) {
        if (currentUtilization >= threshold) {
            return 0;
        }
        if (growthRate <= 0) {
            return Integer.MAX_VALUE;
        }
        
        // Calculate days until utilization reaches threshold
        double remainingCapacity = threshold - currentUtilization;
        double dailyGrowth = growthRate / 365.0;
        
        return (int) Math.ceil(remainingCapacity / (currentUtilization * dailyGrowth));
    }
    
    private CapacityRequirement calculateRequiredCapacity(CapacityPlanningData data, ResourceForecast forecast) {
        // Add buffer for safety (20% overhead)
        double bufferMultiplier = 1.2;
        
        double requiredCpu = Math.ceil(forecast.getForecastCpu() * bufferMultiplier * 100) / 100;
        double requiredMemory = Math.ceil(forecast.getForecastMemory() * bufferMultiplier * 100) / 100;
        double requiredStorage = Math.ceil(forecast.getForecastStorage() * bufferMultiplier * 100) / 100;
        double requiredNetwork = Math.ceil(forecast.getForecastNetwork() * bufferMultiplier * 100) / 100;
        
        // Calculate additional capacity needed
        double additionalCpu = Math.max(0, requiredCpu - data.getCurrentCpuCapacity());
        double additionalMemory = Math.max(0, requiredMemory - data.getCurrentMemoryCapacity());
        double additionalStorage = Math.max(0, requiredStorage - data.getCurrentStorageCapacity());
        double additionalNetwork = Math.max(0, requiredNetwork - data.getCurrentNetworkCapacity());
        
        return new CapacityRequirement(
            requiredCpu, requiredMemory, requiredStorage, requiredNetwork,
            additionalCpu, additionalMemory, additionalStorage, additionalNetwork
        );
    }
    
    private List<ScalingRecommendation> generateScalingRecommendations(
            CapacityPlanningData data, ResourceUtilization current, 
            ResourceForecast forecast, CapacityRequirement requirement) {
        
        List<ScalingRecommendation> recommendations = new ArrayList<>();
        
        // CPU scaling recommendations
        if (requirement.getAdditionalCpu() > 0 || current.getLevel() == UtilizationLevel.CRITICAL) {
            recommendations.add(new ScalingRecommendation(
                "CPU",
                current.getAvgCpu() > 0.8 ? "IMMEDIATE" : "PLANNED",
                "Scale CPU capacity",
                String.format("Add %.2f CPU cores to handle projected load", requirement.getAdditionalCpu()),
                calculatePriority(current.getAvgCpu(), forecast.getDaysUntilCpuExhausted())
            ));
        }
        
        // Memory scaling recommendations
        if (requirement.getAdditionalMemory() > 0 || current.getAvgMemory() > 0.8) {
            recommendations.add(new ScalingRecommendation(
                "MEMORY",
                current.getAvgMemory() > 0.8 ? "IMMEDIATE" : "PLANNED",
                "Scale memory capacity",
                String.format("Add %.2f GB memory to handle projected load", requirement.getAdditionalMemory()),
                calculatePriority(current.getAvgMemory(), forecast.getDaysUntilMemoryExhausted())
            ));
        }
        
        // Storage scaling recommendations
        if (requirement.getAdditionalStorage() > 0 || current.getAvgStorage() > 0.7) {
            recommendations.add(new ScalingRecommendation(
                "STORAGE",
                current.getAvgStorage() > 0.8 ? "IMMEDIATE" : "PLANNED",
                "Scale storage capacity",
                String.format("Add %.2f GB storage to handle projected load", requirement.getAdditionalStorage()),
                calculatePriority(current.getAvgStorage(), forecast.getDaysUntilStorageExhausted())
            ));
        }
        
        // Optimization recommendations
        if (current.getLevel() == UtilizationLevel.LOW) {
            recommendations.add(new ScalingRecommendation(
                "OPTIMIZATION",
                "PLANNED",
                "Optimize resource allocation",
                "Current utilization is low. Consider downsizing to reduce costs.",
                3
            ));
        }
        
        return recommendations;
    }
    
    private int calculatePriority(double utilization, int daysUntilExhausted) {
        if (utilization > 0.9 || daysUntilExhausted < 7) return 1; // Critical
        if (utilization > 0.8 || daysUntilExhausted < 30) return 2; // High
        if (utilization > 0.7 || daysUntilExhausted < 90) return 3; // Medium
        return 4; // Low
    }
    
    private CostAnalysis analyzeCosts(CapacityPlanningData data, CapacityRequirement requirement,
                                     List<ScalingRecommendation> recommendations) {
        
        // Simple cost model (can be enhanced with actual pricing)
        double cpuCostPerCore = 50.0; // per month
        double memoryCostPerGb = 10.0; // per month
        double storageCostPerGb = 0.1; // per month
        
        double currentCost = (data.getCurrentCpuCapacity() * cpuCostPerCore) +
                            (data.getCurrentMemoryCapacity() * memoryCostPerGb) +
                            (data.getCurrentStorageCapacity() * storageCostPerGb);
        
        double projectedCost = (requirement.getRequiredCpu() * cpuCostPerCore) +
                              (requirement.getRequiredMemory() * memoryCostPerGb) +
                              (requirement.getRequiredStorage() * storageCostPerGb);
        
        double additionalCost = projectedCost - currentCost;
        double costIncrease = currentCost > 0 ? (additionalCost / currentCost) * 100 : 0;
        
        return new CostAnalysis(currentCost, projectedCost, additionalCost, costIncrease);
    }
    
    private UtilizationLevel determineUtilizationLevel(double cpu, double memory, double storage, double network) {
        double maxUtilization = Math.max(Math.max(cpu, memory), Math.max(storage, network));
        
        if (maxUtilization >= 0.9) return UtilizationLevel.CRITICAL;
        if (maxUtilization >= 0.75) return UtilizationLevel.HIGH;
        if (maxUtilization >= 0.5) return UtilizationLevel.MEDIUM;
        if (maxUtilization >= 0.25) return UtilizationLevel.LOW;
        return UtilizationLevel.MINIMAL;
    }
    
    // Inner classes for data structures
    
    public enum UtilizationLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public static class CapacityPlanningData {
        private final String resourceType;
        private final double currentCpuUsage;
        private final double currentMemoryUsage;
        private final double currentStorageUsage;
        private final double currentNetworkUsage;
        private final double peakCpuUsage;
        private final double peakMemoryUsage;
        private final double peakStorageUsage;
        private final double peakNetworkUsage;
        private final double currentCpuCapacity;
        private final double currentMemoryCapacity;
        private final double currentStorageCapacity;
        private final double currentNetworkCapacity;
        private final double growthRate;
        private final int forecastDays;
        
        public CapacityPlanningData(String resourceType, double currentCpuUsage, double currentMemoryUsage,
                                   double currentStorageUsage, double currentNetworkUsage,
                                   double peakCpuUsage, double peakMemoryUsage, double peakStorageUsage,
                                   double peakNetworkUsage, double currentCpuCapacity, double currentMemoryCapacity,
                                   double currentStorageCapacity, double currentNetworkCapacity,
                                   double growthRate, int forecastDays) {
            this.resourceType = resourceType;
            this.currentCpuUsage = currentCpuUsage;
            this.currentMemoryUsage = currentMemoryUsage;
            this.currentStorageUsage = currentStorageUsage;
            this.currentNetworkUsage = currentNetworkUsage;
            this.peakCpuUsage = peakCpuUsage;
            this.peakMemoryUsage = peakMemoryUsage;
            this.peakStorageUsage = peakStorageUsage;
            this.peakNetworkUsage = peakNetworkUsage;
            this.currentCpuCapacity = currentCpuCapacity;
            this.currentMemoryCapacity = currentMemoryCapacity;
            this.currentStorageCapacity = currentStorageCapacity;
            this.currentNetworkCapacity = currentNetworkCapacity;
            this.growthRate = growthRate;
            this.forecastDays = forecastDays;
        }
        
        public String getResourceType() { return resourceType; }
        public double getCurrentCpuUsage() { return currentCpuUsage; }
        public double getCurrentMemoryUsage() { return currentMemoryUsage; }
        public double getCurrentStorageUsage() { return currentStorageUsage; }
        public double getCurrentNetworkUsage() { return currentNetworkUsage; }
        public double getPeakCpuUsage() { return peakCpuUsage; }
        public double getPeakMemoryUsage() { return peakMemoryUsage; }
        public double getPeakStorageUsage() { return peakStorageUsage; }
        public double getPeakNetworkUsage() { return peakNetworkUsage; }
        public double getCurrentCpuCapacity() { return currentCpuCapacity; }
        public double getCurrentMemoryCapacity() { return currentMemoryCapacity; }
        public double getCurrentStorageCapacity() { return currentStorageCapacity; }
        public double getCurrentNetworkCapacity() { return currentNetworkCapacity; }
        public double getGrowthRate() { return growthRate; }
        public int getForecastDays() { return forecastDays; }
    }
    
    public static class ResourceUtilization {
        private final double currentCpu, currentMemory, currentStorage, currentNetwork;
        private final double peakCpu, peakMemory, peakStorage, peakNetwork;
        private final double avgCpu, avgMemory, avgStorage, avgNetwork;
        private final UtilizationLevel level;
        
        public ResourceUtilization(double currentCpu, double currentMemory, double currentStorage, double currentNetwork,
                                  double peakCpu, double peakMemory, double peakStorage, double peakNetwork,
                                  double avgCpu, double avgMemory, double avgStorage, double avgNetwork,
                                  UtilizationLevel level) {
            this.currentCpu = currentCpu;
            this.currentMemory = currentMemory;
            this.currentStorage = currentStorage;
            this.currentNetwork = currentNetwork;
            this.peakCpu = peakCpu;
            this.peakMemory = peakMemory;
            this.peakStorage = peakStorage;
            this.peakNetwork = peakNetwork;
            this.avgCpu = avgCpu;
            this.avgMemory = avgMemory;
            this.avgStorage = avgStorage;
            this.avgNetwork = avgNetwork;
            this.level = level;
        }
        
        public double getAvgCpu() { return avgCpu; }
        public double getAvgMemory() { return avgMemory; }
        public double getAvgStorage() { return avgStorage; }
        public double getAvgNetwork() { return avgNetwork; }
        public UtilizationLevel getLevel() { return level; }
    }
    
    public static class ResourceForecast {
        private final double forecastCpu, forecastMemory, forecastStorage, forecastNetwork;
        private final int daysUntilCpuExhausted, daysUntilMemoryExhausted, daysUntilStorageExhausted;
        private final int forecastDays;
        
        public ResourceForecast(double forecastCpu, double forecastMemory, double forecastStorage, double forecastNetwork,
                               int daysUntilCpuExhausted, int daysUntilMemoryExhausted, int daysUntilStorageExhausted,
                               int forecastDays) {
            this.forecastCpu = forecastCpu;
            this.forecastMemory = forecastMemory;
            this.forecastStorage = forecastStorage;
            this.forecastNetwork = forecastNetwork;
            this.daysUntilCpuExhausted = daysUntilCpuExhausted;
            this.daysUntilMemoryExhausted = daysUntilMemoryExhausted;
            this.daysUntilStorageExhausted = daysUntilStorageExhausted;
            this.forecastDays = forecastDays;
        }
        
        public double getForecastCpu() { return forecastCpu; }
        public double getForecastMemory() { return forecastMemory; }
        public double getForecastStorage() { return forecastStorage; }
        public double getForecastNetwork() { return forecastNetwork; }
        public int getDaysUntilCpuExhausted() { return daysUntilCpuExhausted; }
        public int getDaysUntilMemoryExhausted() { return daysUntilMemoryExhausted; }
        public int getDaysUntilStorageExhausted() { return daysUntilStorageExhausted; }
    }
    
    public static class CapacityRequirement {
        private final double requiredCpu, requiredMemory, requiredStorage, requiredNetwork;
        private final double additionalCpu, additionalMemory, additionalStorage, additionalNetwork;
        
        public CapacityRequirement(double requiredCpu, double requiredMemory, double requiredStorage, double requiredNetwork,
                                  double additionalCpu, double additionalMemory, double additionalStorage, double additionalNetwork) {
            this.requiredCpu = requiredCpu;
            this.requiredMemory = requiredMemory;
            this.requiredStorage = requiredStorage;
            this.requiredNetwork = requiredNetwork;
            this.additionalCpu = additionalCpu;
            this.additionalMemory = additionalMemory;
            this.additionalStorage = additionalStorage;
            this.additionalNetwork = additionalNetwork;
        }
        
        public double getRequiredCpu() { return requiredCpu; }
        public double getRequiredMemory() { return requiredMemory; }
        public double getRequiredStorage() { return requiredStorage; }
        public double getAdditionalCpu() { return additionalCpu; }
        public double getAdditionalMemory() { return additionalMemory; }
        public double getAdditionalStorage() { return additionalStorage; }
    }
    
    public static class ScalingRecommendation {
        private final String resourceType;
        private final String urgency;
        private final String action;
        private final String description;
        private final int priority;
        
        public ScalingRecommendation(String resourceType, String urgency, String action, String description, int priority) {
            this.resourceType = resourceType;
            this.urgency = urgency;
            this.action = action;
            this.description = description;
            this.priority = priority;
        }
        
        public String getResourceType() { return resourceType; }
        public String getUrgency() { return urgency; }
        public String getAction() { return action; }
        public String getDescription() { return description; }
        public int getPriority() { return priority; }
    }
    
    public static class CostAnalysis {
        private final double currentCost;
        private final double projectedCost;
        private final double additionalCost;
        private final double costIncrease;
        
        public CostAnalysis(double currentCost, double projectedCost, double additionalCost, double costIncrease) {
            this.currentCost = currentCost;
            this.projectedCost = projectedCost;
            this.additionalCost = additionalCost;
            this.costIncrease = costIncrease;
        }
        
        public double getCurrentCost() { return currentCost; }
        public double getProjectedCost() { return projectedCost; }
        public double getAdditionalCost() { return additionalCost; }
        public double getCostIncrease() { return costIncrease; }
    }
    
    public static class CapacityPlan {
        private final String resourceType;
        private final ResourceUtilization currentUtilization;
        private final ResourceForecast forecast;
        private final CapacityRequirement requirement;
        private final List<ScalingRecommendation> recommendations;
        private final CostAnalysis costAnalysis;
        private final Instant createdAt;
        
        public CapacityPlan(String resourceType, ResourceUtilization currentUtilization, ResourceForecast forecast,
                           CapacityRequirement requirement, List<ScalingRecommendation> recommendations,
                           CostAnalysis costAnalysis, Instant createdAt) {
            this.resourceType = resourceType;
            this.currentUtilization = currentUtilization;
            this.forecast = forecast;
            this.requirement = requirement;
            this.recommendations = recommendations;
            this.costAnalysis = costAnalysis;
            this.createdAt = createdAt;
        }
        
        public String getResourceType() { return resourceType; }
        public ResourceUtilization getCurrentUtilization() { return currentUtilization; }
        public ResourceForecast getForecast() { return forecast; }
        public CapacityRequirement getRequirement() { return requirement; }
        public List<ScalingRecommendation> getRecommendations() { return recommendations; }
        public CostAnalysis getCostAnalysis() { return costAnalysis; }
        public Instant getCreatedAt() { return createdAt; }
    }
}
