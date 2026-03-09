package com.ghatana.platform.testing.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Provides common test data generators for standard data types.
 *
 * @doc.type class
 * @doc.purpose Common test data generators for strings, names, dates, and identifiers
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class CommonTestData {
    
    private static final RandomDataBuilder RANDOM = new RandomDataBuilder();
    
    private CommonTestData() {
        // Utility class
    }
    
    // String generators
    public static final DataGenerator<String> UUID_STRING = 
        () -> UUID.randomUUID().toString();
    
    public static DataGenerator<String> email() {
        return () -> {
            int pick = ThreadLocalRandom.current().nextInt(3);
            String local;
            if (pick == 0) {
                local = RANDOM.alphaNumeric(8).generate();
                return local + "@example.com";
            } else if (pick == 1) {
                local = RANDOM.alphaNumeric(12).generate();
                return local + "@test.org";
            } else {
                local = RANDOM.alphaNumeric(6).generate();
                return local + "@mail.net";
            }
        };
    }
    
    public static DataGenerator<String> firstName() {
        List<String> firstNames = List.of(
            "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles",
            "Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica", "Sarah", "Karen"
        );
        return RANDOM.oneOf(firstNames);
    }
    
    public static DataGenerator<String> lastName() {
        List<String> lastNames = List.of(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
            "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"
        );
        return RANDOM.oneOf(lastNames);
    }
    
    public static DataGenerator<String> fullName() {
        return () -> String.format("%s %s", 
            firstName().generate(), 
            lastName().generate()
        );
    }
    
    public static DataGenerator<String> phoneNumber() {
        return () -> {
            if (RANDOM.bool().generate()) {
                return String.format("(%03d) %03d-%04d",
                    RANDOM.integer(100, 999).generate(),
                    RANDOM.integer(100, 999).generate(),
                    RANDOM.integer(1000, 9999).generate());
            } else {
                return String.format("%d-%03d-%03d-%04d",
                    RANDOM.integer(1, 9).generate(),
                    RANDOM.integer(100, 999).generate(),
                    RANDOM.integer(100, 999).generate(),
                    RANDOM.integer(1000, 9999).generate());
            }
        };
    }
    
    // Numeric generators
    public static DataGenerator<Integer> age() {
        return RANDOM.integer(1, 120);
    }
    
    public static DataGenerator<Double> price(double min, double max) {
        return RANDOM.doubleValue(min, max)
                .map(value -> Math.round(value * 100.0) / 100.0);
    }
    
    public static DataGenerator<Double> percentage() {
        return RANDOM.doubleValue(0.0, 100.0)
                .map(value -> Math.round(value * 100.0) / 100.0);
    }
    
    // Date/Time generators
    public static DataGenerator<LocalDate> dateOfBirth() {
        LocalDate now = LocalDate.now();
        LocalDate minDate = now.minusYears(120);
        LocalDate maxDate = now.minusYears(1);
        return RANDOM.date(minDate, maxDate);
    }
    
    public static DataGenerator<LocalDateTime> timestamp() {
        return () -> LocalDateTime.now().minusSeconds(
            ThreadLocalRandom.current().nextInt(0, 60 * 60 * 24 * 365) // Up to 1 year in the past
        );
    }
    
    // Internet-related generators
    public static DataGenerator<String> ipAddress() {
        return () -> String.format("%d.%d.%d.%d",
            RANDOM.integer(1, 255).generate(),
            RANDOM.integer(0, 255).generate(),
            RANDOM.integer(0, 255).generate(),
            RANDOM.integer(1, 254).generate()
        );
    }
    
    public static DataGenerator<String> url() {
        List<String> domains = List.of("example.com", "test.org", "demo.net", "sample.io");
        List<String> protocols = List.of("http", "https");
        List<String> paths = List.of("", "/", "/path", "/test", "/api/v1/resource");
        
        return () -> {
            String protocol = RANDOM.oneOf(protocols).generate();
            String domain = RANDOM.oneOf(domains).generate();
            String path = RANDOM.oneOf(paths).generate();
            
            if (RANDOM.bool().generate()) {
                return String.format("%s://%s%s", protocol, domain, path);
            } else {
                String subdomain = RANDOM.alphaNumeric(RANDOM.integer(3, 10).generate()).generate().toLowerCase(Locale.ROOT);
                return String.format("%s://%s.%s%s", protocol, subdomain, domain, path);
            }
        };
    }
    
    // Business-related generators
    public static DataGenerator<String> creditCardNumber() {
        // Luhn algorithm compliant credit card number generator
        return () -> {
            // Start with a random 15-digit number (Visa/Mastercard start with 4/5)
            long number = 400000000000000L + (long)(Math.random() * 1000000000000L);
            
            // Convert to string and calculate check digit using Luhn algorithm
            String numStr = String.valueOf(number);
            int sum = 0;
            boolean alternate = true;
            
            for (int i = numStr.length() - 1; i >= 0; i--) {
                int n = Integer.parseInt(numStr.substring(i, i + 1));
                if (alternate) {
                    n *= 2;
                    if (n > 9) {
                        n = (n % 10) + 1;
                    }
                }
                sum += n;
                alternate = !alternate;
            }
            
            int checkDigit = (10 - (sum % 10)) % 10;
            return numStr + checkDigit;
        };
    }
    
    public static DataGenerator<String> currencyCode() {
        List<String> currencyCodes = List.of(
            "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "SEK", "NZD"
        );
        return RANDOM.oneOf(currencyCodes);
    }
    
    // Address generators
    public static DataGenerator<String> streetAddress() {
        List<String> streetNames = List.of(
            "Main", "Oak", "Pine", "Maple", "Cedar", "Elm", "Washington", "Lake", "Hill", "Park"
        );
        List<String> streetSuffixes = List.of(
            "St", "Ave", "Blvd", "Rd", "Ln", "Dr", "Ct", "Pl", "Way"
        );
        
        return () -> {
            String number = String.valueOf(RANDOM.integer(1, 9999).generate());
            String street = RANDOM.oneOf(streetNames).generate();
            String suffix = RANDOM.oneOf(streetSuffixes).generate();
            
            // Sometimes add apartment/unit number
            if (RANDOM.bool().generate()) {
                String unitType = RANDOM.oneOf(List.of("Apt", "Unit", "Ste")).generate();
                String unitNumber = String.valueOf(RANDOM.integer(1, 999).generate());
                return String.format("%s %s %s, %s %s", 
                    number, street, suffix, unitType, unitNumber);
            } else {
                return String.format("%s %s %s", number, street, suffix);
            }
        };
    }
    
    public static DataGenerator<String> city() {
        List<String> cities = List.of(
            "New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio",
            "San Diego", "Dallas", "San Jose", "Austin", "Jacksonville", "San Francisco", "Indianapolis"
        );
        return RANDOM.oneOf(cities);
    }
    
    public static DataGenerator<String> usState() {
        List<String> states = List.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA",
            "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT",
            "VA", "WA", "WV", "WI", "WY"
        );
        return RANDOM.oneOf(states);
    }
    
    public static DataGenerator<String> zipCode() {
        return () -> String.format("%05d", RANDOM.integer(501, 99950).generate());
    }
    
    // Helper method to generate a list of unique values
    public static <T> DataGenerator<List<T>> uniqueList(DataGenerator<T> generator, int size) {
        return () -> {
            Set<T> result = new HashSet<>();
            while (result.size() < size) {
                result.add(generator.generate());
            }
            return new ArrayList<>(result);
        };
    }
    
    // Helper method to generate a value that is different from a set of excluded values
    public static <T> DataGenerator<T> differentFrom(DataGenerator<T> generator, Set<T> excluded) {
        return () -> {
            T value;
            int attempts = 0;
            int maxAttempts = 100;
            
            do {
                if (attempts++ >= maxAttempts) {
                    throw new IllegalStateException("Failed to generate a unique value after " + maxAttempts + " attempts");
                }
                value = generator.generate();
            } while (excluded.contains(value));
            
            return value;
        };
    }
}
