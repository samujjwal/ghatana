package com.ghatana.finance.extension;

import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension;
import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension.CalendarRegistry;
import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension.CalendarSystem;
import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension.DualCalendarCalculator;
import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension.GregorianCalendar;
import com.ghatana.finance.kernel.extension.DualCalendarKernelExtension.NepaliCalendar;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DualCalendarKernelExtension}.
 *
 * <p>Tests cover extension metadata, calendar registry, Nepali calendar operations,
 * dual calendar conversion, and fiscal year computation.</p>
 *
 * @doc.type test
 * @doc.purpose Unit tests for AD/BS dual calendar support
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("DualCalendarKernelExtension Tests")
class DualCalendarKernelExtensionTest extends EventloopTestBase {

    private DualCalendarKernelExtension extension;

    @BeforeEach
    void setUp() {
        extension = new DualCalendarKernelExtension();
        extension.onModuleInitialized(null);
        extension.onModuleStarted(null);
    }

    // ==================== Extension Metadata ====================

    @Nested
    @DisplayName("Extension Metadata")
    class ExtensionMetadata {

        @Test
        @DisplayName("Should return correct extension identity")
        void shouldReturnCorrectExtensionIdentity() {
            assertEquals("dual-calendar", extension.getExtensionId());
            assertEquals("Dual Calendar Extension", extension.getName());
            assertEquals("1.0.0", extension.getVersion());
        }

        @Test
        @DisplayName("Should return valid descriptor")
        void shouldReturnValidDescriptor() {
            KernelDescriptor descriptor = extension.getDescriptor();

            assertNotNull(descriptor);
            assertEquals("dual-calendar", descriptor.getDescriptorId());
            assertEquals("Dual Calendar Extension", descriptor.getName());
            assertEquals("1.0.0", descriptor.getVersion());
            assertEquals(KernelDescriptor.DescriptorType.EXTENSION, descriptor.getType());
        }

        @Test
        @DisplayName("Should contribute dual calendar capability")
        void shouldContributeDualCalendarCapability() {
            Set<KernelCapability> capabilities = extension.getContributedCapabilities();

            assertEquals(1, capabilities.size());
            KernelCapability cap = capabilities.iterator().next();
            assertEquals("calendar.dual", cap.getCapabilityId());
            assertEquals(KernelCapability.CapabilityType.BUSINESS_LOGIC, cap.getType());
            assertEquals("gregorian,bikram-sambat", cap.getMetadata().get("calendars"));
            assertEquals("bikram-sambat", cap.getMetadata().get("primary"));
            assertEquals("bidirectional", cap.getMetadata().get("conversion"));
        }

        @Test
        @DisplayName("Should have medium priority")
        void shouldHaveMediumPriority() {
            assertEquals(50, extension.getPriority());
        }

        @Test
        @DisplayName("Should be compatible with all modules")
        void shouldBeCompatibleWithAllModules() {
            assertTrue(extension.isCompatible(null));
        }
    }

    // ==================== Calendar Registry ====================

    @Nested
    @DisplayName("Calendar Registry")
    class CalendarRegistryTests {

        @Test
        @DisplayName("Should register both calendar systems on init")
        void shouldRegisterBothCalendarSystemsOnInit() {
            CalendarRegistry registry = extension.getCalendarRegistry();

            assertNotNull(registry);
            Set<String> available = registry.getAvailableCalendars();
            assertEquals(2, available.size());
            assertTrue(available.contains("gregorian"));
            assertTrue(available.contains("bikram-sambat"));
        }

        @Test
        @DisplayName("Should return correct calendar by ID")
        void shouldReturnCorrectCalendarById() {
            CalendarRegistry registry = extension.getCalendarRegistry();

            CalendarSystem gregorian = registry.getCalendar("gregorian");
            assertNotNull(gregorian);
            assertEquals("Gregorian Calendar", gregorian.getName());

            CalendarSystem nepali = registry.getCalendar("bikram-sambat");
            assertNotNull(nepali);
            assertEquals("Bikram Sambat (Nepali Calendar)", nepali.getName());
        }

        @Test
        @DisplayName("Should return null for unknown calendar")
        void shouldReturnNullForUnknownCalendar() {
            CalendarRegistry registry = extension.getCalendarRegistry();
            assertNull(registry.getCalendar("lunar"));
        }
    }

    // ==================== Gregorian Calendar ====================

    @Nested
    @DisplayName("Gregorian Calendar")
    class GregorianCalendarTests {

        private GregorianCalendar calendar;

        @BeforeEach
        void setUp() {
            calendar = new GregorianCalendar();
        }

        @Test
        @DisplayName("Should return correct calendar identity")
        void shouldReturnCorrectIdentity() {
            assertEquals("gregorian", calendar.getCalendarId());
            assertEquals("Gregorian Calendar", calendar.getName());
        }

        @Test
        @DisplayName("Should return today as current date")
        void shouldReturnTodayAsCurrentDate() {
            LocalDate today = calendar.today();
            assertNotNull(today);
            assertEquals(LocalDate.now(), today);
        }

        @ParameterizedTest
        @CsvSource({
            "2024, 2, 29, true",  // Leap year
            "2023, 2, 29, false", // Not a leap year
            "2024, 1, 31, true",
            "2024, 4, 31, false", // April has 30 days
            "2024, 13, 1, false", // Invalid month
            "2024, 0, 1, false"   // Invalid month
        })
        @DisplayName("Should validate dates correctly")
        void shouldValidateDatesCorrectly(int year, int month, int day, boolean expected) {
            assertEquals(expected, calendar.isValidDate(year, month, day));
        }

        @ParameterizedTest
        @CsvSource({
            "2024, 1, 31",  // January
            "2024, 2, 29",  // Feb leap
            "2023, 2, 28",  // Feb non-leap
            "2024, 4, 30",  // April
            "2024, 12, 31"  // December
        })
        @DisplayName("Should return correct days in month")
        void shouldReturnCorrectDaysInMonth(int year, int month, int expected) {
            assertEquals(expected, calendar.daysInMonth(year, month));
        }

        @ParameterizedTest
        @CsvSource({
            "2024, 366",  // Leap year
            "2023, 365",  // Non-leap year
            "2000, 366",  // Divisible by 400 — leap
            "1900, 365"   // Divisible by 100 but not 400 — not leap
        })
        @DisplayName("Should return correct days in year")
        void shouldReturnCorrectDaysInYear(int year, int expected) {
            assertEquals(expected, calendar.daysInYear(year));
        }
    }

    // ==================== Nepali Calendar ====================

    @Nested
    @DisplayName("Nepali Calendar")
    class NepaliCalendarTests {

        private NepaliCalendar calendar;

        @BeforeEach
        void setUp() {
            calendar = new NepaliCalendar();
        }

        @Test
        @DisplayName("Should return correct calendar identity")
        void shouldReturnCorrectIdentity() {
            assertEquals("bikram-sambat", calendar.getCalendarId());
            assertEquals("Bikram Sambat (Nepali Calendar)", calendar.getName());
        }

        @ParameterizedTest
        @CsvSource({
            "2081, 1, 1, true",    // Valid: first day
            "2081, 1, 31, true",   // Valid: last day of Baisakh
            "2081, 12, 30, true",  // Valid: last day of Chaitra
            "2081, 0, 1, false",   // Invalid: month 0
            "2081, 13, 1, false",  // Invalid: month 13
            "2081, 1, 0, false",   // Invalid: day 0
            "2081, 1, 32, false"   // Invalid: day > daysInMonth
        })
        @DisplayName("Should validate BS dates correctly")
        void shouldValidateBsDatesCorrectly(int year, int month, int day, boolean expected) {
            assertEquals(expected, calendar.isValidDate(year, month, day));
        }

        @Test
        @DisplayName("Should convert BS to Gregorian approximately")
        void shouldConvertBsToGregorianApproximately() {
            // BS 2081-01-01 → approx AD 2024 (offset ~57 years)
            LocalDate ad = calendar.toGregorian(2081, 1, 1);
            assertNotNull(ad);
            assertEquals(2024, ad.getYear()); // 2081 - 57 = 2024
        }

        @Test
        @DisplayName("Should convert Gregorian to BS approximately")
        void shouldConvertGregorianToBsApproximately() {
            LocalDate ad = LocalDate.of(2024, 4, 13);
            int[] bs = calendar.fromGregorian(ad);

            assertEquals(3, bs.length);
            assertEquals(2081, bs[0]); // 2024 + 57 = 2081
            assertEquals(4, bs[1]);    // Month preserved
            assertEquals(13, bs[2]);   // Day preserved
        }

        @Test
        @DisplayName("Should return typical month day counts")
        void shouldReturnTypicalMonthDayCounts() {
            // First 3 months: 31 days each
            assertEquals(31, calendar.daysInMonth(2081, 1));
            assertEquals(31, calendar.daysInMonth(2081, 2));
            assertEquals(31, calendar.daysInMonth(2081, 3));
            // Fourth month: 32 days
            assertEquals(32, calendar.daysInMonth(2081, 4));
        }
    }

    // ==================== Dual Calendar Calculator ====================

    @Nested
    @DisplayName("Dual Calendar Calculator")
    class DualCalendarCalculatorTests {

        private DualCalendarCalculator calculator;

        @BeforeEach
        void setUp() {
            calculator = new DualCalendarCalculator();
        }

        @Test
        @DisplayName("Should return same date when converting between same calendars")
        void shouldReturnSameDateForSameCalendar() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            LocalDate result = calculator.convert(date, "gregorian", "gregorian");
            assertEquals(date, result);
        }

        @Test
        @DisplayName("Should convert Gregorian to BS")
        void shouldConvertGregorianToBs() {
            LocalDate ad = LocalDate.of(2024, 1, 15);
            LocalDate bs = calculator.convert(ad, "gregorian", "bikram-sambat");

            assertNotNull(bs);
            assertEquals(2081, bs.getYear()); // 2024 + 57
        }

        @Test
        @DisplayName("Should convert BS to Gregorian")
        void shouldConvertBsToGregorian() {
            // Create a virtual BS date as LocalDate (year=2081, month=1, day=1)
            LocalDate bsAsLocalDate = LocalDate.of(2081, 1, 1);
            LocalDate ad = calculator.convert(bsAsLocalDate, "bikram-sambat", "gregorian");

            assertNotNull(ad);
            assertEquals(2024, ad.getYear()); // 2081 - 57
        }

        @Test
        @DisplayName("Should throw for unsupported calendar conversion")
        void shouldThrowForUnsupportedCalendarConversion() {
            LocalDate date = LocalDate.of(2024, 1, 1);
            assertThrows(IllegalArgumentException.class,
                () -> calculator.convert(date, "gregorian", "lunar"));
        }

        @Test
        @DisplayName("Should get current date in Gregorian")
        void shouldGetCurrentDateInGregorian() {
            LocalDate now = calculator.now("gregorian");
            assertNotNull(now);
            assertEquals(LocalDate.now(), now);
        }

        @Test
        @DisplayName("Should get current date in BS")
        void shouldGetCurrentDateInBs() {
            LocalDate now = calculator.now("bikram-sambat");
            assertNotNull(now);
        }

        @Test
        @DisplayName("Should throw for unknown calendar in now()")
        void shouldThrowForUnknownCalendar() {
            assertThrows(IllegalArgumentException.class,
                () -> calculator.now("lunar"));
        }

        @Test
        @DisplayName("Should compute Nepal fiscal year correctly")
        void shouldComputeNepalFiscalYearCorrectly() {
            // Month 1-9 in BS → fiscal year is bsYear/(bsYear+1)
            // Month 10-12 in BS → fiscal year is (bsYear-1)/bsYear
            LocalDate date = LocalDate.of(2024, 1, 15);
            String fiscalYear = calculator.getNepalFiscalYear(date);
            assertNotNull(fiscalYear);
            assertTrue(fiscalYear.contains("/"));
        }

        @Test
        @DisplayName("Should convert round-trip approximately")
        void shouldConvertRoundTripApproximately() {
            LocalDate original = LocalDate.of(2024, 6, 15);

            LocalDate bs = calculator.convert(original, "gregorian", "bikram-sambat");
            LocalDate backToAd = calculator.convert(bs, "bikram-sambat", "gregorian");

            assertEquals(original, backToAd);
        }
    }
}
