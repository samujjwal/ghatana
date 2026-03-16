/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Domain Event Meta Annotation
 */
package com.ghatana.yappc.api.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation on concrete {@link DomainEvent} subclasses to declare their
 * canonical type string and schema version.
 *
 * <p>When present, the {@link DomainEventRegistry} reads {@link #eventType()} and
 * {@link #schemaVersion()} from this annotation rather than inferring them from the class name
 * and the default value.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @DomainEventMetaInfo(eventType = "ProjectCreated", schemaVersion = 2)
 * public final class ProjectCreatedEvent extends DomainEvent {
 *     // ...
 * }
 * }</pre>
 *
 * @doc.type annotation
 * @doc.purpose Annotates DomainEvent subclasses with canonical type name and schema version
 * @doc.layer domain
 * @doc.pattern EventSourced
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainEventMetaInfo {

    /**
     * Canonical event type string used as the key in the {@link DomainEventRegistry}.
     * If left blank (default), the registry infers the type from the class simple name
     * by stripping the {@code "Event"} suffix (e.g. {@code "ProjectCreatedEvent"} →
     * {@code "ProjectCreated"}).
     *
     * @return canonical event type string, or {@code ""} to use inference
     */
    String eventType() default "";

    /**
     * Schema version for this event type. Must be ≥ 1.
     * Increment this value whenever the event's payload structure changes in a breaking way.
     *
     * @return schema version ≥ 1
     */
    int schemaVersion() default 1;
}
