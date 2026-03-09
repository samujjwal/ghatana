package com.ghatana.orchestrator;

import java.util.ArrayList;
import java.util.List;

/**
 * PipelineRunResult represents the result of a single pipeline run.
 * It contains the timestamp, status, error message (if any), and a list of steps executed.
 * Each step records the agent name and its output.
 */
public class PipelineRunResult {
    /** Timestamp of when the pipeline run started (ISO-8601 format). */
    public String timestamp; 
    /** Status of the run: SUCCESS or FAILED. */
    public String status;    
    /** Error message if the run failed, otherwise null. */
    public String error;     
    /** List of steps executed in this run. */
    public List<Step> steps = new ArrayList<>();

    /**
     * A step executed in the pipeline, capturing the agent's name and its output.
     */
    public static class Step {
        /** The name/ID of the agent for this step. */
        public String name;
        /** Output produced by the agent for this step. */
        public String output;

        public Step() {
        }

        public Step(String name, String output) {
            this.name = name;
            this.output = output;
        }
    }
}