package com.ghatana.digitalmarketing.domain.customer;

import java.util.Objects;

/**
 * Customer profile information.
 *
 * @doc.type class
 * @doc.purpose Contains customer profile data including contact information and preferences
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record CustomerProfile(
    String firstName,
    String lastName,
    String email,
    String phone,
    String company,
    String industry,
    Address address
) {
    public CustomerProfile {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("email must be valid");
        }
    }

    public record Address(
        String street,
        String city,
        String state,
        String postalCode,
        String country
    ) {
        public Address {
            Objects.requireNonNull(city, "city must not be null");
            Objects.requireNonNull(country, "country must not be null");
        }
    }
}
