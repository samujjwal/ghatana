package com.ghatana.pattern.codegen.model;

import com.ghatana.pattern.api.codegen.GeneratedTypeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates schema metadata required for code generation.
 */
public final class MappedEventSchema {
    private final GeneratedTypeKey key;
    private final String packageName;
    private final String simpleClassName;
    private final String qualifiedClassName;
    private final String jsonSchema;
    private final List<FieldDefinition> headerFields;
    private final List<FieldDefinition> payloadFields;
    private final List<FieldDefinition> derivedFields;
    private final List<FieldDefinition> allFields;

    public MappedEventSchema(GeneratedTypeKey key,
                             String packageName,
                             String simpleClassName,
                             String jsonSchema,
                             List<FieldDefinition> headerFields,
                             List<FieldDefinition> payloadFields,
                             List<FieldDefinition> derivedFields) {
        this.key = Objects.requireNonNull(key, "key");
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.simpleClassName = Objects.requireNonNull(simpleClassName, "simpleClassName");
        this.qualifiedClassName = packageName + "." + simpleClassName;
        this.jsonSchema = Objects.requireNonNull(jsonSchema, "jsonSchema");
        this.headerFields = List.copyOf(headerFields == null ? List.of() : headerFields);
        this.payloadFields = List.copyOf(payloadFields == null ? List.of() : payloadFields);
        this.derivedFields = List.copyOf(derivedFields == null ? List.of() : derivedFields);

        List<FieldDefinition> combined = new ArrayList<>(this.headerFields.size() +
                this.payloadFields.size() + this.derivedFields.size());
        combined.addAll(this.headerFields);
        combined.addAll(this.payloadFields);
        combined.addAll(this.derivedFields);
        this.allFields = Collections.unmodifiableList(combined);
    }

    public GeneratedTypeKey key() {
        return key;
    }

    public String packageName() {
        return packageName;
    }

    public String simpleClassName() {
        return simpleClassName;
    }

    public String qualifiedClassName() {
        return qualifiedClassName;
    }

    public String jsonSchema() {
        return jsonSchema;
    }

    public List<FieldDefinition> headerFields() {
        return headerFields;
    }

    public List<FieldDefinition> payloadFields() {
        return payloadFields;
    }

    public List<FieldDefinition> derivedFields() {
        return derivedFields;
    }

    public List<FieldDefinition> allFields() {
        return allFields;
    }
}
