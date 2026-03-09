package com.ghatana.core.agent;

import com.ghatana.virtualorg.v1.AgentPerformanceProto;
import com.ghatana.virtualorg.v1.AgentStateProto;
import com.ghatana.virtualorg.v1.ToolProto;
import com.ghatana.virtualorg.v1.AgentProto;
import com.ghatana.virtualorg.v1.TaskRequestProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.TaskStatusProto;
import com.ghatana.virtualorg.v1.TaskPriorityProto;
import com.ghatana.virtualorg.v1.TaskTypeProto;
import com.ghatana.virtualorg.v1.AgentMetricsProto;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VirtualOrgAgent {
    private static final Logger logger = LoggerFactory.getLogger(VirtualOrgAgent.class);
    
    private final String agentId;
    private final String name;
    private final String description;
    private final List<ToolProto> tools;
    
    private AgentStateProto state = AgentStateProto.newBuilder()
            .setState(AgentStateProto.State.AGENT_STATE_IDLE)
            .setStatusMessage("Initialized")
            .build();
    
    private AgentPerformanceProto performance = AgentPerformanceProto.newBuilder()
            .setTasksProcessed(0)
            .setAverageProcessingTimeMs(0)
            .setErrorCount(0)
            .build();
    
    public VirtualOrgAgent(String agentId, String name, String description, List<ToolProto> tools) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.tools = tools;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public List<ToolProto> getTools() {
        return tools;
    }
    
    public AgentStateProto getState() {
        return state;
    }
    
    public AgentPerformanceProto getPerformance() {
        return performance;
    }
    
    public Promise<Void> start() {
        logger.info("Starting agent: {}", name);
        this.state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_IDLE)
                .setStatusMessage("Running")
                .build();
        return Promise.complete();
    }
    
    public Promise<Void> stop() {
        logger.info("Stopping agent: {}", name);
        this.state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_TERMINATED)
                .setStatusMessage("Stopped")
                .build();
        return Promise.complete();
    }
    
    public Promise<String> process(String input) {
        logger.info("Processing input: {}", input);
        return Promise.of("Processed: " + input);
    }
    
    public boolean isHealthy() {
        return state.getState() != AgentStateProto.State.AGENT_STATE_ERROR && 
               state.getState() != AgentStateProto.State.AGENT_STATE_TERMINATED;
    }
    
    public void updatePerformance(long processingTimeMs, boolean success) {
        long newTasksProcessed = performance.getTasksProcessed() + 1;
        long newErrorCount = success ? performance.getErrorCount() : performance.getErrorCount() + 1;
        double newAvgTime = (performance.getAverageProcessingTimeMs() * performance.getTasksProcessed() + processingTimeMs) / newTasksProcessed;
        
        this.performance = AgentPerformanceProto.newBuilder(performance)
                .setTasksProcessed(newTasksProcessed)
                .setAverageProcessingTimeMs(newAvgTime)
                .setErrorCount(newErrorCount)
                .build();
    }
}
