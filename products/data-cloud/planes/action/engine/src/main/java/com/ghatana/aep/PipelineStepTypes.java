package com.ghatana.aep;

/**
 * Canonical step type names used by the lightweight AEP pipeline executor.
 *
 * @doc.type class
 * @doc.purpose Central constants for AEP pipeline step type names
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PipelineStepTypes {

    public static final String REGISTER_PATTERN = "register_pattern";
    public static final String LOG = "log";

    private PipelineStepTypes() {
    }
}
