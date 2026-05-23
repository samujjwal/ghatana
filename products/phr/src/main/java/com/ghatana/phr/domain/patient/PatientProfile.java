package com.ghatana.phr.domain.patient;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Patient profile information following FHIR R4 Patient resource structure.
 *
 * @doc.type class
 * @doc.purpose Contains patient profile data including demographics and contact information
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PatientProfile(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String gender,
    String email,
    String phone,
    Address address,
    String emergencyContactName,
    String emergencyContactPhone
) {
    public PatientProfile {
        Objects.requireNonNull(firstName, "firstName must not be null");
        Objects.requireNonNull(lastName, "lastName must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(gender, "gender must not be null");
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
