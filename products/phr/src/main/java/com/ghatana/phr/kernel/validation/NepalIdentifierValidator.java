package com.ghatana.phr.kernel.validation;

import java.util.regex.Pattern;

/**
 * Nepal-specific identifier, facility, and provider validation.
 *
 * <p>This validator enforces Nepal-specific formats for:
 * <ul>
 *   <li>National ID (Citizenship card numbers)</li>
 *   <li>Facility registration codes (Department of Health Services)</li>
 *   <li>Provider license numbers (Nepal Medical Council)</li>
 *   <li>District codes (Nepal administrative divisions)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates Nepal-specific healthcare identifiers and codes
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class NepalIdentifierValidator {

    private NepalIdentifierValidator() {}

    /**
     * Nepal national ID (Citizenship card) pattern.
     * Format: District code (1-2 digits) + year (4 digits) + serial number (6-8 digits)
     * Example: 44199012345678 (District 44, year 1990, serial 12345678)
     */
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("^\\d{2,4}\\d{4}\\d{6,8}$");

    /**
     * Nepal Medical Council (NMC) license number pattern.
     * Format: NMC + year (4 digits) + registration number (4-6 digits)
     * Example: NMC201912345
     */
    private static final Pattern NMC_LICENSE_PATTERN = Pattern.compile("^NMC\\d{4}\\d{4,6}$", Pattern.CASE_INSENSITIVE);

    /**
     * Nepal Nursing Council license number pattern.
     * Format: NNC + year (4 digits) + registration number (4-6 digits)
     * Example: NNC201812345
     */
    private static final Pattern NNC_LICENSE_PATTERN = Pattern.compile("^NNC\\d{4}\\d{4,6}$", Pattern.CASE_INSENSITIVE);

    /**
     * Health facility registration code pattern (Department of Health Services).
     * Format: HF + province code (1 digit) + district code (2 digits) + facility type (1 char) + serial (4 digits)
     * Example: HF314A1234 (Province 3, District 14, Hospital A, serial 1234)
     */
    private static final Pattern FACILITY_CODE_PATTERN = Pattern.compile("^HF\\d{1}\\d{2}[A-Z]\\d{4}$", Pattern.CASE_INSENSITIVE);

    /**
     * District code pattern (Nepal has 77 districts).
     * Format: 2-digit code from 01 to 77
     */
    private static final Pattern DISTRICT_CODE_PATTERN = Pattern.compile("^[0-7][1-9]$|^7[0-7]$");

    /**
     * Validates a Nepal national ID (Citizenship card number).
     *
     * @param nationalId the national ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return false;
        }
        return NATIONAL_ID_PATTERN.matcher(nationalId).matches();
    }

    /**
     * Validates a Nepal Medical Council (NMC) license number.
     *
     * @param licenseNumber the license number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidNmcLicense(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            return false;
        }
        return NMC_LICENSE_PATTERN.matcher(licenseNumber).matches();
    }

    /**
     * Validates a Nepal Nursing Council (NNC) license number.
     *
     * @param licenseNumber the license number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidNncLicense(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            return false;
        }
        return NNC_LICENSE_PATTERN.matcher(licenseNumber).matches();
    }

    /**
     * Validates a health facility registration code.
     *
     * @param facilityCode the facility code to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidFacilityCode(String facilityCode) {
        if (facilityCode == null || facilityCode.isBlank()) {
            return false;
        }
        return FACILITY_CODE_PATTERN.matcher(facilityCode).matches();
    }

    /**
     * Validates a Nepal district code.
     *
     * @param districtCode the district code to validate
     * @return true if valid (01-77), false otherwise
     */
    public static boolean isValidDistrictCode(String districtCode) {
        if (districtCode == null || districtCode.isBlank()) {
            return false;
        }
        return DISTRICT_CODE_PATTERN.matcher(districtCode).matches();
    }

    /**
     * Validates a provider license number (supports NMC and NNC).
     *
     * @param licenseNumber the license number to validate
     * @return true if valid for any recognized council, false otherwise
     */
    public static boolean isValidProviderLicense(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            return false;
        }
        return isValidNmcLicense(licenseNumber) || isValidNncLicense(licenseNumber);
    }

    /**
     * Extracts the district code from a national ID.
     *
     * @param nationalId the national ID
     * @return the district code, or null if invalid
     */
    public static String extractDistrictCode(String nationalId) {
        if (!isValidNationalId(nationalId)) {
            return null;
        }
        // District code is the first 2-4 digits
        int length = nationalId.length();
        if (length == 12) {
            return nationalId.substring(0, 2);
        } else if (length == 13) {
            return nationalId.substring(0, 3);
        } else if (length == 14) {
            return nationalId.substring(0, 4);
        }
        return nationalId.substring(0, 2);
    }

    /**
     * Extracts the registration year from a provider license.
     *
     * @param licenseNumber the license number
     * @return the year as a string, or null if invalid
     */
    public static String extractLicenseYear(String licenseNumber) {
        if (!isValidProviderLicense(licenseNumber)) {
            return null;
        }
        // Year is after the council prefix (3 characters)
        return licenseNumber.substring(3, 7);
    }

    /**
     * Validates a patient identifier for PHR use.
     *
     * <p>PHR patient IDs can be either a national ID or a system-generated UUID.
     * This method validates against both formats.</p>
     *
     * @param patientId the patient ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPatientId(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return false;
        }
        // Accept either national ID or UUID format
        return isValidNationalId(patientId) || isValidUuid(patientId);
    }

    private static boolean isValidUuid(String value) {
        // Simple UUID pattern check
        return value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }
}
