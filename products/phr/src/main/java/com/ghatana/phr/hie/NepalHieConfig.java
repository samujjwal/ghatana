package com.ghatana.phr.hie;

import java.time.Duration;

/**
 * @doc.type record
 * @doc.purpose Runtime configuration for Nepal HIE connectivity and HL7 submission metadata
 * @doc.layer product
 * @doc.pattern Configuration
 */
public record NepalHieConfig(
    String endpoint,
    String sendingApplication,
    String sendingFacility,
    String receivingApplication,
    String receivingFacility,
    String bearerToken,
    Duration requestTimeout
) {

    public static NepalHieConfig fromEnvironment() {
        return new NepalHieConfig(
            read("PHR_NEPAL_HIE_ENDPOINT", ""),
            read("PHR_NEPAL_HIE_SENDING_APPLICATION", "GHATANA-PHR"),
            read("PHR_NEPAL_HIE_SENDING_FACILITY", ""),
            read("PHR_NEPAL_HIE_RECEIVING_APPLICATION", ""),
            read("PHR_NEPAL_HIE_RECEIVING_FACILITY", ""),
            read("PHR_NEPAL_HIE_BEARER_TOKEN", ""),
            Duration.ofSeconds(Long.parseLong(read("PHR_NEPAL_HIE_TIMEOUT_SECONDS", "15")))
        );
    }

    private static String read(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        return property != null && !property.isBlank() ? property : defaultValue;
    }
}
