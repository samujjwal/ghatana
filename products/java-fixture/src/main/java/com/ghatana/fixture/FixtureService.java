package com.ghatana.fixture;

/**
 * Java fixture service for polyglot product validation.
 *
 * <p>This service provides a simple implementation to validate that the Java
 * adapter can successfully build, test, and package a Java product surface.</p>
 *
 * @doc.type class
 * @doc.purpose Fixture service for Java adapter conformance validation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FixtureService {

    /**
     * Processes a fixture request and returns a response.
     *
     * @param request the fixture request
     * @return the fixture response
     */
    public FixtureResponse process(FixtureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return new FixtureResponse(
            request.id(),
            "processed",
            "Java fixture service processed request successfully"
        );
    }

    /**
     * Fixture request record.
     */
    public record FixtureRequest(
        String id,
        String payload
    ) {}

    /**
     * Fixture response record.
     */
    public record FixtureResponse(
        String id,
        String status,
        String message
    ) {}
}
