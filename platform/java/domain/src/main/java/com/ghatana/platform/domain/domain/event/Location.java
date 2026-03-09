package com.ghatana.platform.domain.domain.event;

import java.util.Objects;

/**
 * Immutable value object representing a geographic location with coordinates.
 * 
 * <p>
 * Encapsulates geographic coordinates and optional metadata for events and entities.
 * Provides a type-safe, validated container for location information used throughout
 * the event processing pipeline. Immutability ensures safe sharing across concurrent
 * event processing streams.
 * </p>
 *
 * <h2>Location Components</h2>
 * <dl>
 *   <dt><b>latitude, longitude</b> (required)</dt>
 *   <dd>WGS84 decimal degree coordinates. Latitude range: [-90, 90], Longitude range: [-180, 180]</dd>
 *
 *   <dt><b>altitude</b> (optional)</dt>
 *   <dd>Height above sea level in meters. Used for 3D geospatial analysis and altitude-based routing.</dd>
 *
 *   <dt><b>accuracy</b> (optional)</dt>
 *   <dd>Location precision in meters. Indicates confidence bounds for GPS/GNSS measurements.</dd>
 *
 *   <dt><b>name</b> (optional)</dt>
 *   <dd>Human-readable location identifier. Examples: "NYC Data Center", "Chicago Office", "User GPS"</dd>
 * </dl>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Used by event enrichment and correlation engines:
 * <ul>
 *   <li><b>Event Enrichment</b>: Added to events from location-aware sources (mobile, IoT, GPS trackers)</li>
 *   <li><b>Geospatial Correlation</b>: Enables location-based event grouping and proximity analysis</li>
 *   <li><b>Root Cause Analysis</b>: Geographic proximity helps identify infrastructure failures</li>
 *   <li><b>SLA Tracking</b>: Location-based SLA rules for multi-region deployments</li>
 *   <li><b>Reporting & Dashboards</b>: Visualize event distribution across geographic regions</li>
 * </ul>
 * </p>
 *
 * <h2>Creation & Usage</h2>
 * <pre>{@code
 * // Simple creation with coordinates only
 * Location nyc = Location.of(40.7128, -74.0060);  // NYC
 *
 * // Builder with all optional fields
 * Location enhanced = Location.builder()
 *     .withCoordinates(40.7128, -74.0060)
 *     .withAltitude(10.5)  // sea level
 *     .withAccuracy(5.0)   // ±5 meters precision
 *     .withName("NYC HQ")
 *     .build();
 *
 * // Validation on build
 * try {
 *   Location invalid = Location.of(91.0, 0);  // Latitude out of range
 * } catch (IllegalStateException e) {
 *   // Latitude must be between -90 and 90 degrees
 * }
 *
 * // Immutable value semantics
 * Location loc1 = Location.of(40.7128, -74.0060);
 * Location loc2 = Location.of(40.7128, -74.0060);
 * assert loc1.equals(loc2);  // True - value equality, not identity
 * }</pre>
 *
 * <h2>Immutability & Thread Safety</h2>
 * <p>
 * All fields are final and private. No setters. Once constructed, Location instances
 * are guaranteed immutable and safe to share across concurrent threads without
 * synchronization. Equality and hashcode respect all fields including optional fields.
 * </p>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>Latitude: Must be in range [-90, 90] (equator ±90°)</li>
 *   <li>Longitude: Must be in range [-180, 180] (prime meridian ±180°)</li>
 *   <li>Altitude: No validation - can be negative (below sea level) or null</li>
 *   <li>Accuracy: No validation - unit is meters, can be null</li>
 *   <li>Name: No validation - can be null or empty string</li>
 * </ul>
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose geographic location representation with validation
 * @doc.pattern immutable value object (final class, private final fields, no setters)
 * @doc.test-hints verify coordinate validation, test builder pattern, validate immutability, check equality semantics
 *
 * @see com.ghatana.platform.domain.domain.event.Event (events may contain location)
 * @see com.ghatana.platform.domain.domain.event.EventContext (enrichment context for location data)
 */
public final class Location {
    private final double latitude;
    private final double longitude;
    private final Double altitude;
    private final Double accuracy;
    private final String name;

    private Location(Builder builder) {
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.altitude = builder.altitude;
        this.accuracy = builder.accuracy;
        this.name = builder.name;
    }

    /**
     * Gets the latitude in decimal degrees.
     *
     * @return The latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Gets the longitude in decimal degrees.
     *
     * @return The longitude
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Gets the altitude in meters above sea level, if available.
     *
     * @return The altitude in meters, or null if not available
     */
    public Double getAltitude() {
        return altitude;
    }

    /**
     * Gets the accuracy of the location in meters, if available.
     *
     * @return The accuracy in meters, or null if not available
     */
    public Double getAccuracy() {
        return accuracy;
    }

    /**
     * Gets the name or description of this location, if available.
     *
     * @return The name, or null if not available
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Location location = (Location) o;
        return Double.compare(location.latitude, latitude) == 0 &&
               Double.compare(location.longitude, longitude) == 0 &&
               Objects.equals(altitude, location.altitude) &&
               Objects.equals(accuracy, location.accuracy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, altitude, accuracy);
    }

    @Override
    public String toString() {
        return "Location{" 
               + "latitude=" + latitude 
               + ", longitude=" + longitude 
               + (altitude != null ? ", altitude=" + altitude : "") 
               + (accuracy != null ? ", accuracy=" + accuracy : "") 
               + (name != null ? ", name='" + name + '\'' : "") 
               + '}';
    
    }

    /**
     * Creates a new builder for constructing Location objects.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new Location with the specified coordinates.
     *
     * @param latitude  The latitude in decimal degrees
     * @param longitude The longitude in decimal degrees
     * @return A new Location instance
     */
    public static Location of(double latitude, double longitude) {
        return builder().withCoordinates(latitude, longitude).build();
    }

    /**
     * Builder for creating Location instances.
     */
    public static final class Builder {
        private double latitude;
        private double longitude;
        private Double altitude;
        private Double accuracy;
        private String name;

        private Builder() { }

        /**
         * Sets the latitude and longitude coordinates.
         *
         * @param latitude  The latitude in decimal degrees
         * @param longitude The longitude in decimal degrees
         * @return This builder
         */
        public Builder withCoordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            return this;
        }

        /**
         * Sets the altitude.
         *
         * @param altitude The altitude in meters above sea level
         * @return This builder
         */
        public Builder withAltitude(double altitude) {
            this.altitude = altitude;
            return this;
        }

        /**
         * Sets the accuracy.
         *
         * @param accuracy The accuracy in meters
         * @return This builder
         */
        public Builder withAccuracy(double accuracy) {
            this.accuracy = accuracy;
            return this;
        }

        /**
         * Sets the name or description.
         *
         * @param name The name or description
         * @return This builder
         */
        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Builds a new Location instance with the current configuration.
         *
         * @return A new Location instance
         * @throws IllegalStateException if latitude or longitude are not set
         */
        public Location build() {
            if (latitude < -90 || latitude > 90) {
                throw new IllegalStateException("Latitude must be between -90 and 90 degrees");
            }
            if (longitude < -180 || longitude > 180) {
                throw new IllegalStateException("Longitude must be between -180 and 180 degrees");
            }
            return new Location(this);
        }
    }
}
