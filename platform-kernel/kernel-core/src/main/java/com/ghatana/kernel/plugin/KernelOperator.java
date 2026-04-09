package com.ghatana.kernel.plugin;

/**
 * Kernel operator interface.
 *
 * <p>Operators provide typed data processing capabilities for kernel workflows.
 * Each operator declares its accepted input types and produced output types so the
 * kernel can validate pipeline compatibility at registration time.</p>
 *
 * @doc.type interface
 * @doc.purpose Data processing capability unit for kernel workflow pipelines
 * @doc.layer kernel
 * @doc.pattern Plugin
 */
public interface KernelOperator {

    /**
     * Get the operator identifier.
     */
    String getOperatorId();

    /**
     * Get the operator version.
     */
    String getVersion();

    /**
     * Get the operator description.
     */
    String getDescription();

    /**
     * Get the input types this operator accepts.
     */
    String[] getInputTypes();

    /**
     * Get the output types this operator produces.
     */
    String[] getOutputTypes();

    /**
     * Initialize the operator.
     */
    void initialize(PluginContext context);

    /**
     * Process data through the operator.
     *
     * @param input the input data
     * @return the processed output data
     */
    Object process(Object input);

    /**
     * Start the operator.
     */
    void start();

    /**
     * Stop the operator.
     */
    void stop();

    /**
     * Shutdown the operator.
     */
    void shutdown();
}
