package com.ghatana.pipeline.registry.model;

/**
 * Convenience alias for {@link PipelineRegistration} used by the AEP server module.
 *
 * <p>This class inherits all fields and behavior from PipelineRegistration and
 * exists to provide a shorter name for pipeline CRUD operations in the HTTP layer.
 *
 * @doc.type class
 * @doc.purpose Pipeline type alias for server HTTP layer
 * @doc.layer product
 * @doc.pattern DomainModel
 */
public class Pipeline extends PipelineRegistration {
}
