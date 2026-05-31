package com.ghatana.yappc.kernel;

import java.util.ArrayList;
import java.util.List;

/**
 * G10-006: Product Generation Mode
 *
 * Provides generation modes for Product scaffolding, including a mode that
 * emits "delete duplicate legacy path" tasks instead of deprecation tasks.
 *
 * @doc.type class
 * @doc.purpose Configure Product generation modes
 * @doc.layer integration
 * @doc.pattern Configuration
 */
public class ProductGenerationMode {

    public enum Mode {
        /**
         * Standard mode: emits deprecation tasks for legacy paths
         */
        STANDARD,
        
        /**
         * Cleanup mode: emits delete tasks for duplicate legacy paths
         */
        CLEANUP,
        
        /**
         * Preview mode: generates without writing files
         */
        PREVIEW
    }

    private Mode mode;
    private List<GenerationTask> tasks;

    public ProductGenerationMode() {
        this.mode = Mode.STANDARD;
        this.tasks = new ArrayList<>();
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<GenerationTask> getTasks() { return tasks; }
    public void setTasks(List<GenerationTask> tasks) { this.tasks = tasks; }

    /**
     * Add a generation task based on current mode
     */
    public void addTask(String type, String path, String description) {
        GenerationTask task = new GenerationTask();
        task.setType(type);
        task.setPath(path);
        task.setDescription(description);
        
        if (mode == Mode.CLEANUP && type.equals("deprecation")) {
            task.setType("delete");
            task.setDescription("Delete duplicate legacy path: " + path);
        }
        
        tasks.add(task);
    }

    /**
     * Generate task report
     */
    public String generateTaskReport() {
        StringBuilder report = new StringBuilder();
        report.append("Product Generation Task Report\n");
        report.append("===========================\n");
        report.append("Mode: ").append(mode).append("\n");
        report.append("Total Tasks: ").append(tasks.size()).append("\n\n");
        
        for (GenerationTask task : tasks) {
            report.append("- [").append(task.getType()).append("] ")
                  .append(task.getPath())
                  .append(": ").append(task.getDescription())
                  .append("\n");
        }
        
        return report.toString();
    }

    /**
     * Generation Task model
     */
    public static class GenerationTask {
        private String type;
        private String path;
        private String description;
        private String priority;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
}
