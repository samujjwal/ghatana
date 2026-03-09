package com.ghatana.platform.testing.data;

import net.datafaker.Faker;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating test data.
 
 *
 * @doc.type class
 * @doc.purpose Test data generator
 * @doc.layer core
 * @doc.pattern Component
*/
public class TestDataGenerator {
    private static final Faker faker = new Faker();
    private static final ZoneId UTC = ZoneId.of("UTC");

    private TestDataGenerator() {
        // Utility class
    }

    public static String randomString() {
        return UUID.randomUUID().toString();
    }

    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static long randomLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static boolean randomBoolean() {
        return faker.bool().bool();
    }

    public static LocalDate randomDate() {
        return Instant.ofEpochMilli(
            ThreadLocalRandom.current().nextLong(
                Instant.now().minusSeconds(31536000).toEpochMilli(), // 1 year ago
                Instant.now().toEpochMilli()
            )
        ).atZone(UTC).toLocalDate();
    }

    public static String randomName() {
        return faker.name().fullName();
    }

    public static String randomWord() {
        return faker.lorem().word();
    }

    public static String randomSentence() {
        return faker.lorem().sentence();
    }
}
