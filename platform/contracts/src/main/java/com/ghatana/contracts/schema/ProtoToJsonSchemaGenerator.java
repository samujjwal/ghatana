/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL. Use is subject to the terms of a separate
 * license agreement between you and Ghatana Platforms, Inc. You may not
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, in whole or in part, except as expressly
 * permitted under the applicable written license agreement.
 *
 * Unauthorized use, reproduction, or distribution of this software, or any
 * portion of it, may result in severe civil and criminal penalties, and
 * will be prosecuted to the maximum extent possible under the law.
 */
package com.ghatana.contracts.schema;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.ExtensionRegistry;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Proto → JSON Schema (draft-07) generator.
 *
 * <p>- Mirrors Protobuf messages to JSON Schemas (one file per message, + optional bundle). -
 * Encodes: scalars/enums/messages, repeated, map<,>, oneof, well-known types. - Extracts
 * leading/trailing/detached comments from SourceCodeInfo into "description".
 *
 * <p>Example usage:
 *
 * <pre>
 * // With default configuration
 * ProtoToJsonSchemaGenerator generator = new ProtoToJsonSchemaGenerator();
 *
 * // With custom package prefixes
 * ProtoToJsonSchemaGenerator customGenerator = ProtoToJsonSchemaGenerator.builder()
 *     .withPackagePrefixes(List.of("com.example.", "com.acme."))
 *     .build();
 * </pre>
 *
 * <p>CLI:
 *
 * <pre>
 * java com.ghatana.contracts.schema.ProtoToJsonSchemaGenerator \
 *   --descriptorSet=build/descriptors/contracts.desc \
 *   --outDir=build/jsonschema \
 *   --messages=ghatana.contracts.v1.EventRecord,ghatana.contracts.v1.EventType \
 *   --bundleName=bundle.schema.json
 * </pre>
 */
public final class ProtoToJsonSchemaGenerator {

    /** Builder for {@link ProtoToJsonSchemaGenerator} to configure package prefixes. */
    public static final class Builder {
        private List<String> packagePrefixes;

        private Builder() {
            this.packagePrefixes = new ArrayList<>(DEFAULT_PACKAGE_PREFIXES);
        }

        /**
         * Sets the package prefixes to use for enum resolution.
         *
         * @param prefixes The list of package prefixes
         * @return This builder instance for method chaining
         */
        public Builder withPackagePrefixes(List<String> prefixes) {
            this.packagePrefixes =
                    new ArrayList<>(prefixes != null ? prefixes : Collections.emptyList());
            return this;
        }

        /**
         * Adds a package prefix to the list of prefixes.
         *
         * @param prefix The package prefix to add
         * @return This builder instance for method chaining
         */
        public Builder addPackagePrefix(String prefix) {
            if (prefix != null) {
                this.packagePrefixes.add(prefix);
            }
            return this;
        }

        /**
         * Builds a new instance of ProtoToJsonSchemaGenerator with the configured settings.
         *
         * @return A new ProtoToJsonSchemaGenerator instance
         */
        public ProtoToJsonSchemaGenerator build() {
            return new ProtoToJsonSchemaGenerator(packagePrefixes);
        }
    }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.parse(args);
        new ProtoToJsonSchemaGenerator().run(cfg);
    }

    private final ObjectMapper mapper;

    private static final List<String> DEFAULT_PACKAGE_PREFIXES =
            List.of("", "test.", "com.example.", "com.ghatana.contracts.");

    private final List<String> packagePrefixes;

    /** Creates a new instance with default package prefixes. */
    public ProtoToJsonSchemaGenerator() {
        this(DEFAULT_PACKAGE_PREFIXES);
    }

    /**
     * Creates a new instance with custom package prefixes.
     *
     * @param packagePrefixes List of package prefixes to use when resolving enum types. If null or
     *     empty, the default prefixes will be used.
     */
    public ProtoToJsonSchemaGenerator(List<String> packagePrefixes) {
        this.packagePrefixes =
                packagePrefixes != null && !packagePrefixes.isEmpty()
                        ? List.copyOf(packagePrefixes) // Defensive copy
                        : DEFAULT_PACKAGE_PREFIXES;

        this.mapper =
                new ObjectMapper(new JsonFactory()).enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Creates a new builder for configuring and creating a ProtoToJsonSchemaGenerator.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public void run(Config cfg) throws IOException {
        FileDescriptorSet fds = readDescriptorSet(cfg.descriptorSet());
        Registry reg = Registry.from(fds);

        Set<String> targets = cfg.messages().isEmpty() ? reg.topLevelMessageFQNs() : cfg.messages();

        Files.createDirectories(cfg.outDir());

        // Optional bundle with $defs
        ObjectNode bundle = mapper.createObjectNode();
        bundle.put("$schema", "http://json-schema.org/draft-07/schema#");
        String bundleName = cfg.bundleName();
        if (cfg.bundleName() != null && !cfg.bundleName().isBlank()) {
            bundle.put(
                    "$id",
                    "urn:ghatana:schema:bundle:" + cfg.bundleName().replaceAll("\\.json$", ""));
        }
        ObjectNode defs = bundle.putObject("$defs");

        // Generate per-message schemas
        for (String fqn : targets) {
            ObjectNode schema = buildMessageSchema(bundleName, reg, fqn, defs, new HashSet<>());
            schema.put("$schema", "http://json-schema.org/draft-07/schema#");
            schema.put("$id", idFor(reg, fqn));
            schema.put("title", simpleName(fqn));

            // attach message doc (if any)
            reg.messageDoc(fqn).ifPresent(doc -> schema.put("description", doc));

            write(cfg.outDir().resolve(simpleName(fqn) + ".schema.json"), schema);
        }

        // Emit bundle if requested
        if (cfg.bundleName() != null && !cfg.bundleName().isBlank()) {
            addBundleTopLevelRefs(bundle, targets);
            write(cfg.outDir().resolve(cfg.bundleName()), bundle);
        }
    }

    // ---------- Descriptor IO ----------

    private static FileDescriptorSet readDescriptorSet(Path descriptorSet) throws IOException {
        try (InputStream in = Files.newInputStream(descriptorSet)) {
            return FileDescriptorSet.parseFrom(in, ExtensionRegistry.getEmptyRegistry());
        }
    }

    // ---------- Schema building ----------

    private ObjectNode type(String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        return node;
    }

    private ObjectNode refTo(String fqn, String bundleName) {
        ObjectNode ref = mapper.createObjectNode();
        ref.put("$ref", bundleName + "#/$defs/" + fqn);
        return ref;
    }

    ObjectNode buildMessageSchema(
            String bundleName, Registry reg, String fqn, ObjectNode defs, Set<String> visiting) {
        if (visiting.contains(fqn)) return refTo(fqn, bundleName);
        visiting.add(fqn);

        if (defs.has(fqn)) {
            visiting.remove(fqn);
            return refTo(fqn, bundleName);
        }

        // Check for well-known types first, before looking up in registry
        ObjectNode known = wellKnownMessageSchema(fqn);
        if (known != null) {
            // For well-known types, we can inline the schema directly
            // instead of adding it to definitions and returning a reference
            visiting.remove(fqn);
            return known;
        }

        // Not a well-known type, look up in registry
        DescriptorProto msg = reg.message(fqn);
        if (msg == null) throw new IllegalArgumentException("Unknown message: " + fqn);

        ObjectNode def = mapper.createObjectNode();
        def.put("type", "object");
        def.put("additionalProperties", false);

        // Add message documentation if available
        reg.messageDoc(fqn).ifPresent(doc -> def.put("description", doc));

        // Add message options if they exist
        if (msg.hasOptions()) {
            MessageOptions options = msg.getOptions();
            ObjectNode optionsNode = mapper.createObjectNode();

            // Add standard options
            if (options.hasDeprecated()) {
                def.put("deprecated", options.getDeprecated());
                optionsNode.put("deprecated", options.getDeprecated());
            }
            if (options.hasMapEntry()) {
                optionsNode.put("mapEntry", options.getMapEntry());
            }
            if (options.hasNoStandardDescriptorAccessor()) {
                optionsNode.put(
                        "noStandardDescriptorAccessor", options.getNoStandardDescriptorAccessor());
            }

            // Add any custom options
            if (options.getUnknownFields().asMap().size() > 0) {
                ObjectNode customOptions = mapper.createObjectNode();
                options.getUnknownFields()
                        .asMap()
                        .forEach(
                                (fieldNum, field) -> {
                                    customOptions.put("field_" + fieldNum, field.toString());
                                });
                optionsNode.set("customOptions", customOptions);
            }

            // Add options to the schema
            if (optionsNode.size() > 0) {
                def.set("x-protobuf-options", optionsNode);
            }
        }

        ObjectNode props = def.putObject("properties");
        ArrayNode required = mapper.createArrayNode();

        // For oneof → oneOf
        Map<Integer, List<FieldDescriptorProto>> oneofGroups = new HashMap<>();

        for (FieldDescriptorProto f : msg.getFieldList()) {
            boolean isMap = isMapField(reg, f);
            String jsonName = jsonNameOf(f);

            ObjectNode fieldSchema;
            if (isMap) {
                // Map<K,V> → object(additionalProperties: schema(V))
                String entryFqn = normalizedTypeName(reg, f);
                DescriptorProto entry = reg.message(entryFqn);
                FieldDescriptorProto valField = entry.getField(1); // key=0, value=1
                FieldDescriptorProto keyField = entry.getField(0); // key=0, value=1
                ObjectNode keySchema = fieldToSchema(reg, keyField, defs, visiting);
                JsonNode keyType = keySchema.get("type");
                keyType = keyType == null ? keySchema.get("$ref") : keyType;
                ObjectNode valSchema = fieldToSchema(reg, valField, defs, visiting);
                JsonNode valType = valSchema.get("type");
                valType = valType == null ? valSchema.get("$ref") : valType;
                fieldSchema = mapper.createObjectNode();
                fieldSchema.put("type", "object");
                fieldSchema.set("key", keyType);
                fieldSchema.set("value", valType);
                //                fieldSchema.set("additionalProperties", valSchema);
            } else {
                fieldSchema = fieldToSchema(reg, f, defs, visiting);
            }

            // Attach field doc (if any)
            reg.fieldDoc(fqn, f.getNumber()).ifPresent(doc -> fieldSchema.put("description", doc));

            props.set(jsonName, fieldSchema);

            // Add required fields to the required array
            if (f.getLabel() == FieldDescriptorProto.Label.LABEL_REQUIRED) {
                required.add(jsonName);
            }

            if (f.hasOneofIndex()) {
                oneofGroups.computeIfAbsent(f.getOneofIndex(), k -> new ArrayList<>()).add(f);
            }
        }

        if (required.size() > 0) def.set("required", required);

        if (!oneofGroups.isEmpty()) {
            ArrayNode oneOf = def.putArray("oneOf");
            for (List<FieldDescriptorProto> alts : oneofGroups.values()) {
                for (FieldDescriptorProto alt : alts) {
                    ObjectNode obj = mapper.createObjectNode();
                    ArrayNode req = obj.putArray("required");
                    req.add(jsonNameOf(alt));
                    oneOf.add(obj);
                }
            }
        }

        defs.set(fqn, def);
        visiting.remove(fqn);
        return refTo(fqn, bundleName);
    }

    ObjectNode fieldToSchema(
            Registry reg, FieldDescriptorProto f, ObjectNode defs, Set<String> visiting) {
        // Handle map fields first
        if (isMapField(reg, f)) {
            String mapEntryType = normalizedTypeName(reg, f);
            DescriptorProto entry = reg.message(mapEntryType);
            if (entry == null) {
                throw new IllegalArgumentException("Map entry type not found: " + mapEntryType);
            }

            // Get key and value fields from the map entry
            FieldDescriptorProto keyField = null;
            FieldDescriptorProto valueField = null;
            for (FieldDescriptorProto field : entry.getFieldList()) {
                if (field.getName().equals("key")) {
                    keyField = field;
                } else if (field.getName().equals("value")) {
                    valueField = field;
                }
            }

            if (keyField == null || valueField == null) {
                throw new IllegalArgumentException(
                        "Invalid map entry type: missing 'key' or 'value' field");
            }

            // Create the schema for the map using standard JSON Schema representation
            ObjectNode mapSchema = type("object");
            if ("google.protobuf.Struct".equals(normalizedTypeName(reg, valueField))) {
                ObjectNode anyObject = mapper.createObjectNode();
                anyObject.put("type", "object");
                anyObject.put("additionalProperties", true);
                mapSchema.set("additionalProperties", anyObject);
            } else {
                mapSchema.set(
                        "additionalProperties", fieldToSchema(reg, valueField, defs, visiting));
            }
            if (f.hasJsonName()) {
                mapSchema.put("$comment", "JSON name: " + f.getJsonName());
            }
            return mapSchema;
        }

        ObjectNode base;

        // Handle scalar types and message types
        switch (f.getType()) {
            case TYPE_DOUBLE:
            case TYPE_FLOAT:
                base = type("number");
                if (f.getType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
                    base.put("format", "float");
                } else {
                    base.put("format", "double");
                }
                break;

            case TYPE_INT32:
                base = type("integer");
                if (f.hasDefaultValue()) {
                    base.put("default", Integer.parseInt(f.getDefaultValue()));
                }
                break;

            case TYPE_SINT32:
            case TYPE_SFIXED32:
                base = type("integer").put("format", "int32");
                if (f.hasDefaultValue()) {
                    base.put("default", Integer.parseInt(f.getDefaultValue()));
                }
                break;

            case TYPE_INT64:
            case TYPE_SINT64:
            case TYPE_SFIXED64:
            case TYPE_UINT64:
            case TYPE_FIXED64:
                base = type("integer").put("format", "int64");
                break;

            case TYPE_UINT32:
            case TYPE_FIXED32:
                base = type("integer").put("format", "int32");
                break;

            case TYPE_BOOL:
                base = type("boolean");
                break;

            case TYPE_STRING:
                base = type("string");
                break;

            case TYPE_BYTES:
                base = type("string").put("format", "byte");
                break;

            case TYPE_ENUM:
                {
                    String enumFqn = normalizedTypeName(reg, f);
                    if (!defs.has(enumFqn)) {
                        ObjectNode enumSchema = buildEnumSchema(reg, enumFqn);
                        defs.set(enumFqn, enumSchema);
                    }
                    return refTo(enumFqn, "");
                }

            case TYPE_MESSAGE:
                {
                    String msgFqn = normalizedTypeName(reg, f);
                    // Handle well-known types
                    if (msgFqn.startsWith("google.protobuf.")) {
                        String wellKnownType = msgFqn.substring("google.protobuf.".length());
                        switch (wellKnownType) {
                            case "Timestamp":
                                return type("string").put("format", "date-time");
                            case "Duration":
                                return type("string")
                                        .put("pattern", "^\\d+(\\.\\d+)?s$")
                                        .put("format", "int64");
                            case "FieldMask":
                                return type("string").put("pattern", "^[\\w,]+$");
                            case "Struct":
                                return type("object");
                            case "Value":
                                return type("object").put("additionalProperties", true);
                            case "ListValue":
                                ObjectNode listSchema = type("array");
                                listSchema.set(
                                        "items", type("object").put("additionalProperties", true));
                                return listSchema;
                            case "NullValue":
                                return type("null");
                            case "Any":
                                ObjectNode anySchema = type("object");
                                anySchema.put("additionalProperties", true);
                                anySchema.set(
                                        "properties",
                                        mapper.createObjectNode().set("@type", type("string")));
                                anySchema.set("required", mapper.createArrayNode().add("@type"));
                                return anySchema;
                        }
                    }

                    base = buildMessageSchema("", reg, msgFqn, defs, visiting);
                }
                break;

            default:
                throw new IllegalStateException("Unhandled field type: " + f.getType());
        }

        // proto3 optional → nullable
        if (f.getProto3Optional()) {
            base = nullable(base);
        }

        // Handle field documentation
        Optional<String> fieldDocOpt =
                reg.fieldDoc(f.getExtendee() != null ? f.getExtendee() : "", f.getNumber());
        if (fieldDocOpt.isPresent()) {
            String fieldDoc = fieldDocOpt.get();
            if (!fieldDoc.isEmpty()) {
                base.put("description", fieldDoc);
            }
        }

        // Handle field options and metadata
        if (f.hasJsonName()) {
            // Add JSON name as a comment and as the title
            base.put("$comment", "JSON name: " + f.getJsonName());
            base.put("title", f.getJsonName());
        }

        // Handle default values
        if (f.hasDefaultValue() && !f.getDefaultValue().isEmpty()) {
            String defaultValue = f.getDefaultValue();
            switch (f.getType()) {
                case TYPE_BOOL:
                    base.put("default", Boolean.parseBoolean(defaultValue));
                    break;
                case TYPE_STRING:
                    base.put("default", defaultValue);
                    break;
                case TYPE_BYTES:
                    base.put("default", defaultValue);
                    break;
                case TYPE_ENUM:
                    // Default value for enums is the string name
                    base.put("default", defaultValue);
                    break;
                case TYPE_FLOAT:
                case TYPE_DOUBLE:
                    base.put("default", Double.parseDouble(defaultValue));
                    break;
                case TYPE_INT32:
                case TYPE_SINT32:
                case TYPE_SFIXED32:
                case TYPE_UINT32:
                case TYPE_FIXED32:
                    base.put("default", Integer.parseInt(defaultValue));
                    break;
                case TYPE_INT64:
                case TYPE_SINT64:
                case TYPE_SFIXED64:
                case TYPE_UINT64:
                case TYPE_FIXED64:
                    base.put("default", Long.parseLong(defaultValue));
                    break;
            }
        }

        // Handle field options
        if (f.hasOptions()) {
            FieldOptions options = f.getOptions();
            if (options.hasDeprecated()) {
                base.put("deprecated", options.getDeprecated());
            }
        }

        // repeated → array
        if (f.getLabel() == FieldDescriptorProto.Label.LABEL_REPEATED) {
            ObjectNode arr = type("array");
            arr.set("items", base);
            return arr;
        }

        return base;
    }

    private boolean isMapField(Registry reg, FieldDescriptorProto f) {
        if (f.getType() != FieldDescriptorProto.Type.TYPE_MESSAGE) return false;
        String target = normalizedTypeName(reg, f);
        DescriptorProto entry = reg.message(target);
        return entry != null && entry.getOptions().getMapEntry();
    }

    static String normalizedTypeName(Registry reg, FieldDescriptorProto f) {
        String raw = f.getTypeName();
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Field type name cannot be null or empty");
        }

        // Remove leading dot if present
        String typeName = raw.startsWith(".") ? raw.substring(1) : raw;

        // Handle well-known types
        if (typeName.startsWith("google.protobuf.")) {
            return typeName; // Keep well-known types as is
        }

        // If the type is already fully qualified, return it as is
        if (typeName.contains(".")) {
            return typeName;
        }

        // Get the package from the registry
        String pkg = "";
        if (f.hasExtendee()) {
            // For nested types, get package from parent message
            String parentType = f.getExtendee();
            if (parentType.contains(".")) {
                pkg = parentType.substring(0, parentType.lastIndexOf('.'));
            }
        } else {
            // For top-level types, get the base package
            pkg = reg.pkgByFqn.getOrDefault("", "");
        }

        // Construct fully qualified name
        String fullName = pkg.isEmpty() ? typeName : pkg + "." + typeName;

        // For enum types
        if (f.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
            if (reg.enumsByFqn.containsKey(fullName)) {
                return fullName;
            }
            // Try with the simple name as fallback
            if (reg.enumsByFqn.containsKey(typeName)) {
                return typeName;
            }
            return fullName; // Return the constructed name even if not found
        }

        // For message types
        if (f.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
            // Special handling for map entries
            if (typeName.endsWith("Entry") && typeName.contains("Map")) {
                return typeName;
            }

            if (reg.msgsByFqn.containsKey(fullName)) {
                return fullName;
            }
            // Try with the simple name as fallback
            if (reg.msgsByFqn.containsKey(typeName)) {
                return typeName;
            }
            return fullName; // Return the constructed name even if not found
        }

        // For all other types, just return the type name as is
        return typeName;
    }

    /** Exception thrown when there is an error during schema generation. */
    public static class SchemaGenerationException extends RuntimeException {
        public SchemaGenerationException(String message) {
            super(message);
        }

        public SchemaGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Exception thrown when an enum cannot be resolved. */
    public static class EnumResolutionException extends SchemaGenerationException {
        private final String enumName;
        private final List<String> attemptedPrefixes;

        public EnumResolutionException(String enumName, List<String> attemptedPrefixes) {
            super(
                    String.format(
                            "Could not resolve enum '%s'. Attempted prefixes: %s",
                            enumName, attemptedPrefixes));
            this.enumName = enumName;
            this.attemptedPrefixes = List.copyOf(attemptedPrefixes);
        }

        public String getEnumName() {
            return enumName;
        }

        public List<String> getAttemptedPrefixes() {
            return attemptedPrefixes;
        }
    }

    /**
     * Builds a JSON Schema for a Protocol Buffers enum type.
     *
     * @param reg The registry containing enum definitions
     * @param enumFqn Fully qualified name of the enum (e.g., "package.EnumName" or
     *     "ParentMessage.EnumName")
     * @return ObjectNode containing the JSON Schema for the enum
     * @throws IllegalArgumentException if the enum cannot be found in the registry
     */
    // Add VisibleForTesting annotation from ghatana.annotations.VisibleForTesting
    ObjectNode buildEnumSchema(Registry reg, String enumFqn) {
        // Try to find the enum by its fully qualified name with default common prefixes
        EnumDescriptorProto enumDescriptor = findEnumDescriptor(reg, enumFqn, List.of());

        if (enumDescriptor == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "Enum '%s' not found. Available enums: %s",
                            enumFqn, String.join(", ", reg.enumsByFqn.keySet())));
        }

        // Extract the simple name for the title
        String simpleName =
                enumFqn.contains(".") ? enumFqn.substring(enumFqn.lastIndexOf('.') + 1) : enumFqn;

        // Build the base schema
        ObjectNode schema =
                mapper.createObjectNode().put("type", "string").put("title", simpleName);

        // Add enum values
        ArrayNode enumValues = schema.putArray("enum");
        enumDescriptor.getValueList().stream()
                .map(EnumValueDescriptorProto::getName)
                .forEach(enumValues::add);

        // Add documentation if available
        reg.enumDoc(enumFqn)
                .filter(desc -> !desc.isEmpty())
                .ifPresent(desc -> schema.put("description", desc));

        // Add deprecation status if marked as deprecated
        if (enumDescriptor.hasOptions() && enumDescriptor.getOptions().hasDeprecated()) {
            schema.put("deprecated", enumDescriptor.getOptions().getDeprecated());
        }

        return schema;
    }

    /**
     * Finds an enum descriptor by its fully qualified name, trying different package prefixes if
     * needed.
     *
     * @param reg The registry containing the proto definitions
     * @param enumFqn The fully qualified name of the enum
     * @param commonPrefixes Optional list of package prefixes to try
     * @return The found enum descriptor
     * @throws EnumResolutionException if the enum cannot be found
     */
    EnumDescriptorProto findEnumDescriptor(
            Registry reg, String enumFqn, List<String> commonPrefixes) {
        Objects.requireNonNull(reg, "Registry cannot be null");
        Objects.requireNonNull(enumFqn, "Enum FQN cannot be null");

        try {
            // Try exact match first
            EnumDescriptorProto enumDesc = reg.enumType(enumFqn);
            if (enumDesc != null) {
                return enumDesc;
            }

            // Try simple name for top-level enums
            String simpleName =
                    enumFqn.contains(".")
                            ? enumFqn.substring(enumFqn.lastIndexOf('.') + 1)
                            : enumFqn;

            enumDesc = reg.enumType(simpleName);
            if (enumDesc != null) {
                return enumDesc;
            }

            // Handle nested enums (e.g., "ParentMessage.EnumName")
            if (enumFqn.contains(".")) {
                String[] parts = enumFqn.split("\\.");
                if (parts.length < 2) {
                    throw new EnumResolutionException(enumFqn, List.of("<none>"));
                }

                String enumName = parts[parts.length - 1];
                String parentPath = String.join(".", Arrays.copyOf(parts, parts.length - 1));

                // Try to find in parent message
                DescriptorProto parentMsg = reg.message(parentPath);
                if (parentMsg != null) {
                    EnumDescriptorProto nested = findNestedEnum(parentMsg, enumName);
                    if (nested != null) {
                        return nested;
                    }
                }

                // Try with common package prefixes if not found
                List<String> prefixes =
                        commonPrefixes != null && !commonPrefixes.isEmpty()
                                ? commonPrefixes
                                : DEFAULT_PACKAGE_PREFIXES;

                List<String> attemptedPrefixes = new ArrayList<>();

                for (String prefix : prefixes) {
                    String fullPath = prefix.isEmpty() ? parentPath : (prefix + parentPath);
                    attemptedPrefixes.add(prefix);

                    parentMsg = reg.message(fullPath);
                    if (parentMsg != null) {
                        EnumDescriptorProto nested = findNestedEnum(parentMsg, enumName);
                        if (nested != null) {
                            return nested;
                        }
                    }
                }

                // If we get here, we couldn't find the enum
                throw new EnumResolutionException(enumFqn, attemptedPrefixes);
            }

            throw new EnumResolutionException(enumFqn, List.of("<none>"));
        } catch (Exception e) {
            if (e instanceof EnumResolutionException) {
                throw e;
            }
            throw new SchemaGenerationException("Error resolving enum: " + enumFqn, e);
        }
    }

    /**
     * Recursively searches for an enum by name within a message and its nested messages.
     *
     * @param parent The parent message descriptor to search within
     * @param enumName The name of the enum to find
     * @return The found enum descriptor, or null if not found
     */
    private EnumDescriptorProto findNestedEnum(DescriptorProto parent, String enumName) {
        // Check direct nested enums first
        return parent.getEnumTypeList().stream()
                .filter(e -> e.getName().equals(enumName))
                .findFirst()
                .orElseGet(
                        () -> {
                            // If not found, recursively check nested messages
                            return parent.getNestedTypeList().stream()
                                    .map(nested -> findNestedEnum(nested, enumName))
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(null);
                        });
    }

    private static ObjectNode nullable(ObjectNode schema) {
        ObjectMapper m = new ObjectMapper();
        ObjectNode n = m.createObjectNode();
        ArrayNode anyOf = m.createArrayNode();
        anyOf.add(m.createObjectNode().put("type", "null"));
        anyOf.add(schema);
        n.set("anyOf", anyOf);
        return n;
    }

    private static String idFor(Registry reg, String fqn) {
        String pkg = reg.packageOf(fqn).orElse("contracts");
        return "urn:ghatana:schema:" + pkg + ":" + simpleName(fqn);
    }

    private static String simpleName(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }

    private static String jsonNameOf(FieldDescriptorProto f) {
        if (f.hasJsonName()) return f.getJsonName();
        // snake_case → lowerCamelCase
        String[] parts = f.getName().split("_");
        StringBuilder b = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            b.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1));
        }
        return b.toString();
    }

    // Well-known types
    private ObjectNode wellKnownMessageSchema(String fqn) {
        // Handle both fully qualified and unqualified names
        String simpleName = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;

        // Check for well-known types by both simple and fully qualified names
        if (simpleName.equals("Timestamp") || fqn.equals("google.protobuf.Timestamp")) {
            return type("string").put("format", "date-time");
        }

        // Handle other well-known types by simple name
        switch (simpleName) {
            case "Duration":
                return type("string").put("pattern", "^-?\\d+(\\.\\d+)?s$");
            case "StringValue":
                return type("string");
            case "Int32Value":
            case "UInt32Value":
                return type("integer");
            case "Int64Value":
            case "UInt64Value":
                return type("string").put("format", "int64");
            case "BoolValue":
                return type("boolean");
            case "BytesValue":
                return type("string").put("format", "byte");
            case "Struct":
                {
                    ObjectNode st = type("object");
                    st.put("additionalProperties", true);
                    return st;
                }
            case "Value":
                {
                    ObjectNode any = mapper.createObjectNode();
                    ArrayNode union = any.putArray("anyOf");
                    union.add(type("null"));
                    union.add(type("boolean"));
                    union.add(type("number"));
                    union.add(type("string"));

                    // Add array type
                    ObjectNode arr = type("array");
                    arr.set("items", mapper.createObjectNode());
                    union.add(arr);

                    // Add object type
                    ObjectNode obj = type("object");
                    obj.put("additionalProperties", true);
                    union.add(obj);

                    return any;
                }
            case "Empty":
                {
                    ObjectNode empty = type("object");
                    empty.set("properties", mapper.createObjectNode());
                    empty.put("additionalProperties", false);
                    return empty;
                }
            case "ListValue":
                {
                    ObjectNode arr = type("array");
                    arr.set("items", wellKnownMessageSchema("Value"));
                    return arr;
                }
            case "Any":
                {
                    // For Any type, we'll just allow any valid JSON
                    ObjectNode any = mapper.createObjectNode();
                    ArrayNode types = any.putArray("type");
                    types.add("object");
                    types.add("array");
                    types.add("string");
                    types.add("number");
                    types.add("integer");
                    types.add("boolean");
                    types.add("null");
                    any.put("additionalProperties", true);
                    return any;
                }
            default:
                return null;
        }
    }

    private void addBundleTopLevelRefs(ObjectNode bundle, Set<String> topLevelFqns) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode props = root.putObject("properties");
        for (String fqn : topLevelFqns) {
            props.set(simpleName(fqn), refTo(fqn, ""));
        }
        root.put("additionalProperties", false);
        bundle.set("components", root);
    }

    private void write(Path path, ObjectNode node) {
        try (BufferedOutputStream out =
                new BufferedOutputStream(
                        Files.newOutputStream(
                                path,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING))) {
            mapper.writeValue(out, node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- Registry: messages/enums + doc indexing ----------

    static final class Registry {
        private final Map<String, DescriptorProto> msgsByFqn;
        private final Map<String, EnumDescriptorProto> enumsByFqn;
        private final Map<String, String> pkgByFqn;

        private final Map<String, String> messageDoc; // fqn → doc
        private final Map<String, String> enumDoc; // enum fqn → doc
        private final Map<String, String>
                fieldDocByMsgAndNumber; // msg fqn + "#" + fieldNumber → doc

        private Registry(
                Map<String, DescriptorProto> msgsByFqn,
                Map<String, EnumDescriptorProto> enumsByFqn,
                Map<String, String> pkgByFqn,
                Map<String, String> messageDoc,
                Map<String, String> enumDoc,
                Map<String, String> fieldDocByMsgAndNumber) {
            this.msgsByFqn = msgsByFqn;
            this.enumsByFqn = enumsByFqn;
            this.pkgByFqn = pkgByFqn;
            this.messageDoc = messageDoc;
            this.enumDoc = enumDoc;
            this.fieldDocByMsgAndNumber = fieldDocByMsgAndNumber;
        }

        static Registry from(FileDescriptorSet fds) {
            Map<String, DescriptorProto> msgs = new LinkedHashMap<>();
            Map<String, EnumDescriptorProto> enums = new LinkedHashMap<>();
            Map<String, String> pkgOf = new LinkedHashMap<>();
            Map<String, String> msgDoc = new HashMap<>();
            Map<String, String> enDoc = new HashMap<>();
            Map<String, String> fldDoc = new HashMap<>();

            for (FileDescriptorProto fd : fds.getFileList()) {
                final String pkg = fd.hasPackage() ? fd.getPackage() : "";
                // Build a path → comment map for this file
                Map<String, String> docByPath = buildDocByPath(fd.getSourceCodeInfo());

                // Top-level messages
                for (int mi = 0; mi < fd.getMessageTypeCount(); mi++) {
                    DescriptorProto d = fd.getMessageType(mi);
                    String msgFqn = join(pkg, d.getName());
                    indexMessage(
                            pkg,
                            d,
                            msgs,
                            pkgOf,
                            msgDoc,
                            enDoc,
                            fldDoc,
                            docByPath,
                            list(4, mi),
                            d.getName());
                }

                // Top-level enums
                for (int ei = 0; ei < fd.getEnumTypeCount(); ei++) {
                    EnumDescriptorProto e = fd.getEnumType(ei);
                    String enumFqn = join(pkg, e.getName());
                    enums.put(enumFqn, e);
                    pkgOf.put(enumFqn, pkg);
                    putIfNotBlank(enDoc, enumFqn, docByPath.get(key(list(5, ei))));
                }
            }
            return new Registry(msgs, enums, pkgOf, msgDoc, enDoc, fldDoc);
        }

        private static void indexMessage(
                String pkg,
                DescriptorProto d,
                Map<String, DescriptorProto> msgs,
                Map<String, String> pkgOf,
                Map<String, String> msgDoc,
                Map<String, String> enumDoc,
                Map<String, String> fieldDoc,
                Map<String, String> docByPath,
                List<Integer> path,
                String pathName // relative name chain (A.B.C)
                ) {
            String msgFqn = join(pkg, pathName);
            msgs.put(msgFqn, d);
            pkgOf.put(msgFqn, pkg);

            // message doc
            putIfNotBlank(msgDoc, msgFqn, docByPath.get(key(path)));

            // fields doc
            for (int fi = 0; fi < d.getFieldCount(); fi++) {
                FieldDescriptorProto f = d.getField(fi);
                putIfNotBlank(
                        fieldDoc,
                        msgFqn + "#" + f.getNumber(),
                        docByPath.get(key(extend(path, 2, fi))));
            }

            // nested messages
            for (int ni = 0; ni < d.getNestedTypeCount(); ni++) {
                DescriptorProto nested = d.getNestedType(ni);
                indexMessage(
                        pkg,
                        nested,
                        msgs,
                        pkgOf,
                        msgDoc,
                        enumDoc,
                        fieldDoc,
                        docByPath,
                        extend(path, 3, ni),
                        pathName + "." + nested.getName());
            }

            // nested enums
            for (int eni = 0; eni < d.getEnumTypeCount(); eni++) {
                EnumDescriptorProto en = d.getEnumType(eni);
                String enFqn = join(pkg, pathName + "." + en.getName());
                // store doc path: [ ... , 4, enumIndex ]
                putIfNotBlank(enumDoc, enFqn, docByPath.get(key(extend(path, 4, eni))));
            }
        }

        private static Map<String, String> buildDocByPath(SourceCodeInfo sci) {
            Map<String, String> map = new HashMap<>();
            if (sci == null) return map;
            for (SourceCodeInfo.Location loc : sci.getLocationList()) {
                String doc = extractDoc(loc);
                if (doc != null && !doc.isBlank()) {
                    map.put(key(loc.getPathList()), doc.trim());
                }
            }
            return map;
        }

        private static String extractDoc(SourceCodeInfo.Location loc) {
            StringBuilder b = new StringBuilder();
            for (String dc : loc.getLeadingDetachedCommentsList()) {
                if (!dc.isBlank()) b.append(dc.trim()).append("\n");
            }
            if (loc.hasLeadingComments()) b.append(loc.getLeadingComments().trim()).append("\n");
            if (loc.hasTrailingComments()) b.append(loc.getTrailingComments().trim());
            String s = b.toString().trim();
            return s.isEmpty() ? null : s;
        }

        private static String key(List<Integer> path) {
            return path.stream().map(String::valueOf).collect(Collectors.joining("."));
        }

        private static String key(int... ints) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ints.length; i++) {
                if (i > 0) sb.append('.');
                sb.append(ints[i]);
            }
            return sb.toString();
        }

        private static List<Integer> list(int... ints) {
            List<Integer> l = new ArrayList<>(ints.length);
            for (int i : ints) l.add(i);
            return l;
        }

        private static List<Integer> extend(List<Integer> base, int fieldNum, int index) {
            List<Integer> l = new ArrayList<>(base.size() + 2);
            l.addAll(base);
            l.add(fieldNum);
            l.add(index);
            return l;
        }

        Set<String> topLevelMessageFQNs() {
            // top-level messages → those whose pathName has no '.' after package
            return msgsByFqn.keySet().stream()
                    .filter(
                            fqn -> {
                                String pkg = pkgByFqn.get(fqn);
                                String rest =
                                        (pkg == null || pkg.isEmpty())
                                                ? fqn
                                                : fqn.substring(pkg.length() + 1);
                                return !rest.contains(".");
                            })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        DescriptorProto message(String fqn) {
            return msgsByFqn.get(fqn);
        }

        EnumDescriptorProto enumType(String fqn) {
            return enumsByFqn.get(fqn);
        }

        Optional<String> packageOf(String fqn) {
            return Optional.ofNullable(pkgByFqn.get(fqn));
        }

        Optional<String> messageDoc(String fqn) {
            return Optional.ofNullable(messageDoc.get(fqn)).filter(s -> !s.isBlank());
        }

        Optional<String> enumDoc(String fqn) {
            return Optional.ofNullable(enumDoc.get(fqn)).filter(s -> !s.isBlank());
        }

        Optional<String> fieldDoc(String msgFqn, int fieldNumber) {
            return Optional.ofNullable(fieldDocByMsgAndNumber.get(msgFqn + "#" + fieldNumber))
                    .filter(s -> !s.isBlank());
        }

        private static String join(String pkg, String name) {
            return (pkg == null || pkg.isEmpty()) ? name : (pkg + "." + name);
        }

        private static void putIfNotBlank(Map<String, String> map, String key, String value) {
            if (value != null && !value.isBlank()) map.put(key, value.trim());
        }
    }

    // ---------- CLI config ----------
    public static final class Config {
        private final Path descriptorSet;
        private final Path outDir;
        private final Set<String> messages;
        private final String bundleName;

        private Config(Path descriptorSet, Path outDir, Set<String> messages, String bundleName) {
            this.descriptorSet = descriptorSet;
            this.outDir = outDir;
            this.messages = messages;
            this.bundleName = bundleName;
        }

        public Path descriptorSet() {
            return descriptorSet;
        }

        public Path outDir() {
            return outDir;
        }

        public Set<String> messages() {
            return messages;
        }

        public String bundleName() {
            return bundleName;
        }

        static Config parse(String[] args) {
            Map<String, String> kv = new HashMap<>();
            Pattern p = Pattern.compile("^--([^=]+)=(.*)$");
            for (String a : args) {
                var m = p.matcher(a);
                if (m.matches()) kv.put(m.group(1), m.group(2));
            }
            Path desc =
                    Path.of(
                            Objects.requireNonNull(
                                    kv.get("descriptorSet"), "--descriptorSet is required"));
            Path out = Path.of(Objects.requireNonNull(kv.get("outDir"), "--outDir is required"));
            String msgs = kv.getOrDefault("messages", "");
            String bundle = kv.getOrDefault("bundleName", "bundle.schema.json");

            Set<String> set =
                    Arrays.stream(msgs.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toCollection(LinkedHashSet::new));

            return new Config(desc, out, set, bundle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(descriptorSet, outDir, messages, bundleName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Config config = (Config) o;
            return descriptorSet.equals(config.descriptorSet)
                    && outDir.equals(config.outDir)
                    && messages.equals(config.messages)
                    && bundleName.equals(config.bundleName);
        }

        @Override
        public String toString() {
            return "Config{"
                    + "descriptorSet="
                    + descriptorSet
                    + ", outDir="
                    + outDir
                    + ", messages="
                    + messages
                    + ", bundleName='"
                    + bundleName
                    + '\''
                    + '}';
        }
    }
}
