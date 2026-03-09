package com.ghatana.platform.types;

/**
 * Event payload format enumeration with MIME type mappings.
 *
 * <p><b>Documentation Metadata:</b>
 * <ul>
 *   <li><b>Type:</b> Enumeration</li>
 *   <li><b>Layer:</b> Core/Types</li>
 *   <li><b>Purpose:</b> Type-safe content format specification for event payload serialization</li>
 *   <li><b>Patterns:</b> Type-Safe Enum, MIME Type Mapping</li>
 * </ul>
 *
 * <p><b>Technical Documentation:</b><br>
 * Defines supported content formats for event payloads in EventCloud, with standard MIME type
 * mappings. Enables content-aware serialization, deserialization, and routing. Provides
 * bidirectional mapping between ContentType enums and MIME type strings for HTTP headers and
 * protocol compliance.
 *
 * <p><b>User/API Documentation:</b><br>
 * ContentType is used throughout EventCloud for format negotiation and payload handling:
 * <ul>
 *   <li>Event submission: Specify payload format in ingestion API</li>
 *   <li>Serialization: Select codec based on content type</li>
 *   <li>Content negotiation: HTTP Accept/Content-Type headers</li>
 *   <li>Storage encoding: Persist payload format with event</li>
 *   <li>Cross-format routing: Support multiple formats in same pipeline</li>
 * </ul>
 *
 * <p><b>Architecture Role</b><br>
 * Key component in EventCloud's format flexibility:
 * <ul>
 *   <li><b>Ingestion API:</b> Required field specifying event payload format</li>
 *   <li><b>Codec Selection:</b> Routes to appropriate serialization codec</li>
 *   <li><b>Event Storage:</b> Persisted with event metadata for deserialization</li>
 *   <li><b>Protocol Headers:</b> Used in HTTP, gRPC, Kafka headers</li>
 *   <li><b>Format Transformation:</b> Enables cross-format pipeline operations</li>
 * </ul>
 *
 * <p><b>Design Decisions:</b>
 * <ul>
 *   <li><b>MIME type standard:</b> Uses IANA-registered MIME types for interoperability</li>
 *   <li><b>Fixed set:</b> Predefined formats prevent arbitrary format proliferation</li>
 *   <li><b>Bidirectional mapping:</b> Support conversion to/from MIME strings</li>
 *   <li><b>Null-safe parsing:</b> fromMimeType returns null for unrecognized types</li>
 *   <li><b>Binary support:</b> Includes generic binary format for unknown payloads</li>
 * </ul>
 *
 * <p><b>Supported Formats:</b>
 * <ul>
 *   <li><b>JSON:</b> application/json - Human-readable structured data</li>
 *   <li><b>Protobuf:</b> application/protobuf - Compact binary format</li>
 *   <li><b>Avro:</b> application/avro - Self-describing binary format</li>
 *   <li><b>Text:</b> text/plain - Plain text payloads</li>
 *   <li><b>Binary:</b> application/octet-stream - Generic binary data</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Create event with JSON payload
 * Event event = Event.builder()
 *     .contentType(ContentType.JSON)
 *     .payload(jsonBytes)
 *     .build();
 *
 * // Get MIME type for HTTP header
 * String mimeType = ContentType.JSON.getMimeType();  // "application/json"
 *
 * // Parse from Content-Type header
 * String headerValue = "application/protobuf";
 * ContentType format = ContentType.fromMimeType(headerValue);  // ContentType.PROTOBUF
 *
 * // Serialize based on content type
 * Codec codec = codecRegistry.getCodec(event.getContentType());
 * byte[] serialized = codec.encode(payload);
 * }</pre>
 *
 * <p><b>Thread Safety:</b> Enum instances are thread-safe singletons.
 * <p><b>Lifecycle:</b> Specified at event creation, immutable throughout event lifetime.
 * <p><b>Performance:</b> O(1) MIME type lookup via EnumMap.
 * <p><b>Dependencies:</b> None.
 * <p><b>Integration Points:</b> Used in event model, HTTP headers, Kafka headers, codec selection,
 * and format conversion pipelines.
 *
 * @see Enum
 * @doc.type enum
 * @doc.purpose Type-safe content format specification with MIME type mappings
 * @doc.layer core
 * @doc.pattern Type-Safe Enum, MIME Type Mapping
 * @doc.test-hints "MIME type parsing", "case insensitivity", "unknown format handling",
 *     "all constants", "enum ordering"
 */
public enum ContentType {
    /**
     * JSON (application/json).
     *
     * <p><b>Purpose:</b> Human-readable structured data format commonly used for APIs and
     * configurations.
     * <p><b>Use Case:</b> Default format for most EventCloud events, easy debugging and logs.
     */
    JSON("application/json"),

    /**
     * Protocol Buffers (application/protobuf).
     *
     * <p><b>Purpose:</b> Compact binary serialization format developed by Google.
     * <p><b>Use Case:</b> High-performance, bandwidth-optimized event transmission.
     */
    PROTOBUF("application/protobuf"),

    /**
     * Apache Avro (application/avro).
     *
     * <p><b>Purpose:</b> Self-describing binary format with schema evolution support.
     * <p><b>Use Case:</b> Schema-driven event processing with version compatibility.
     */
    AVRO("application/avro"),

    /**
     * Plain text (text/plain).
     *
     * <p><b>Purpose:</b> Unformatted text payload.
     * <p><b>Use Case:</b> Log lines, CSV data, or other text-based events.
     */
    TEXT("text/plain"),

    /**
     * Generic binary data (application/octet-stream).
     *
     * <p><b>Purpose:</b> Unknown or proprietary binary format.
     * <p><b>Use Case:</b> Legacy systems, custom binary formats, or unidentified payloads.
     */
    BINARY("application/octet-stream");

    private final String mimeType;

    /**
     * Create ContentType with MIME type.
     *
     * @param mimeType the standard MIME type string
     */
    ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Get the MIME type string.
     *
     * <p><b>Method Metadata:</b>
     * <ul>
     *   <li><b>Visibility:</b> public</li>
     *   <li><b>Purpose:</b> Retrieve MIME type for HTTP headers and content negotiation</li>
     * </ul>
     *
     * <p><b>Usage Examples:</b>
     * <pre>{@code
     * String mimeType = ContentType.JSON.getMimeType();  // "application/json"
     * response.setContentType(mimeType);
     * }</pre>
     *
     * @return the MIME type string (e.g., "application/json")
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Parse ContentType from MIME type string.
     *
     * <p><b>Method Metadata:</b>
     * <ul>
     *   <li><b>Visibility:</b> public static</li>
     *   <li><b>Purpose:</b> Bidirectional conversion from MIME type to ContentType</li>
     * </ul>
     *
     * <p><b>Technical Documentation:</b><br>
     * Converts MIME type string to corresponding ContentType enumeration. Case-insensitive
     * comparison for HTTP header robustness. Returns null if MIME type is unrecognized.
     *
     * <p><b>Usage Examples:</b>
     * <pre>{@code
     * // From HTTP header
     * String contentTypeHeader = request.getHeader("Content-Type");
     * ContentType format = ContentType.fromMimeType(contentTypeHeader);
     *
     * // Case-insensitive
     * ContentType ct1 = ContentType.fromMimeType("APPLICATION/JSON");
     * ContentType ct2 = ContentType.fromMimeType("application/json");
     * // Both return JSON
     *
     * // Unknown type
     * ContentType unknown = ContentType.fromMimeType("application/xml");
     * // Returns null
     * }</pre>
     *
     * @param mimeType MIME type string to parse; can be null
     * @return ContentType enum if recognized, null if mimeType is null or unrecognized
     */
    public static ContentType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        for (ContentType ct : values()) {
            if (ct.mimeType.equalsIgnoreCase(mimeType)) {
                return ct;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return mimeType;
    }
}
