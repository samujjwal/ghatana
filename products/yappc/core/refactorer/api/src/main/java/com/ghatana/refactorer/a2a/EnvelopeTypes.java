package com.ghatana.refactorer.a2a;

/**
 * Constants for A2A envelope types. Defines the standard message types for agent-to-agent
 * communication.
 
 * @doc.type class
 * @doc.purpose Handles envelope types operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class EnvelopeTypes {

    private EnvelopeTypes() {
        // Constants class
    }

    /** Request to execute a Polyfix task */
    public static final String TASK_REQUEST = "polyfix.task.request";

    /** Result of a Polyfix task execution */
    public static final String TASK_RESULT = "polyfix.task.result";

    /** Progress update for a running task */
    public static final String TASK_PROGRESS = "polyfix.task.progress";

    /** Error during task execution */
    public static final String TASK_ERROR = "polyfix.task.error";

    /** Agent capabilities announcement */
    public static final String CAPABILITIES = "polyfix.capabilities";

    /** Heartbeat/keepalive message */
    public static final String HEARTBEAT = "polyfix.heartbeat";

    /** Connection acknowledgment */
    public static final String ACK = "polyfix.ack";

    /** Graceful disconnect notification */
    public static final String DISCONNECT = "polyfix.disconnect";
}
