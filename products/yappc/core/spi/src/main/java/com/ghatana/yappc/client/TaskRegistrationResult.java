package com.ghatana.yappc.client;

/**
 * Result of task registration.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles task registration result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class TaskRegistrationResult {
    
    private final String taskId;
    private final boolean success;
    private final String message;
    
    private TaskRegistrationResult(String taskId, boolean success, String message) {
        this.taskId = taskId;
        this.success = success;
        this.message = message;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public static TaskRegistrationResult success(String taskId) {
        return new TaskRegistrationResult(taskId, true, "Task registered successfully");
    }
    
    public static TaskRegistrationResult failure(String taskId, String message) {
        return new TaskRegistrationResult(taskId, false, message);
    }
}
