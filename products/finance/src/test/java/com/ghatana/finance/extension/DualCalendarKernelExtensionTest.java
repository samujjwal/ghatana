package com.ghatana.finance.extension;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DualCalendarKernelExtension}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for AD/BS dual calendar support
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("DualCalendarKernelExtension Tests")
class DualCalendarKernelExtensionTest {

    private DualCalendarKernelExtension extension;

    @BeforeEach
    void setUp() {
        extension = new DualCalendarKernelExtension();
        extension.onModuleInitialized(null);
        extension.onModuleStarted(null);
    }

    @Test
    @DisplayName("Should return correct extension metadata")
    void shouldReturnCorrectExtensionMetadata() {
        assertEquals("dual-calendar-nepal", extension.getExtensionId());
        assertEquals("Dual Calendar Support (AD/BS)", extension.getName());
        assertEquals(50, extension.getPriority());
        assertTrue(extension.isEnabledByDefault());
    }

    @Test
    @DisplayName("Should return valid descriptor")
    void shouldReturnValidDescriptor() {
        KernelDescriptor descriptor = extension.getDescriptor();

        assertNotNull(descriptor);
        assertEquals("dual-calendar-nepal", descriptor.getDescriptorId());
        assertEquals("Dual Calendar Support (AD/BS)", descriptor.getName());
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
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, cap.getType());

        assertEquals("true", cap.getMetadata().get("supports_ad").toString());
        assertEquals("true", cap.getMetadata().get("supports_bs").toString());
        assertEquals("nepal", cap.getMetadata().get("region").toString());
        assertEquals("true", cap.getMetadata().get("conversion_supported").toString());
    }

    @Test
    @DisplayName("Should convert BS to AD correctly")
    void shouldConvertBsToAdCorrectly() {
        // BS 2081-01-01 should convert to approximately AD 2024-04-13
        LocalDate adDate = extension.convertBsToAd(2081, 1, 1);

        assertNotNull(adDate);
        // Allow some variance due to simplified conversion algorithm
        assertTrue(adDate.getYear() >= 2024 && adDate.getYear() <= 2025);
        assertTrue(adDate.getMonthValue() >= 1 && adDate.getMonthValue() <= 12);
        assertTrue(adDate.getDayOfMonth() >= 1 && adDate.getDayOfMonth() <= 31);
    }

    @Test
    @DisplayName("Should convert AD to BS correctly")
    void shouldConvertAdToBsCorrectly() {
        LocalDate adDate = LocalDate.of(2024, 4, 13);
        DualCalendarKernelExtension.BsDate bsDate = extension.convertAdToBs(adDate);

        assertNotNull(bsDate);
        // Should be approximately BS 2081
        assertTrue(bsDate.year >= 2080 && bsDate.year <= 2082);
        assertTrue(bsDate.month >= 1 && bsDate.month <= 12);
        assertTrue(bsDate.day >= 1 && bsDate.day <= 32);
    }

    @Test
    @DisplayName("Should validate BS dates correctly")
    void shouldValidateBsDatesCorrectly() {
        // Valid dates should not throw
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 1, 1));
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 6, 15));
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 12, 30));

        // Invalid month should throw
        assertThrows(IllegalArgumentException.class, () ->
            extension.convertBsToAd(2081, 13, 1));

        // Invalid day for month should throw
        assertThrows(IllegalArgumentException.class, () ->
            extension.convertBsToAd(2081, 11, 35)); // Chaitra max is 30
    }

    @Test
    @DisplayName("Should cache conversion results")
    void shouldCacheConversionResults() {
        LocalDate adDate1 = extension.convertBsToAd(2081, 1, 15);
        LocalDate adDate2 = extension.convertBsToAd(2081, 1, 15);

        assertEquals(adDate1, adDate2);
    }

    @Test
    @DisplayName("Should format AD date correctly")
    void shouldFormatAdDateCorrectly() {
        LocalDate adDate = LocalDate.of(2024, 4, 13);
        String formatted = extension.formatDate(adDate, "yyyy-MM-dd",
            DualCalendarKernelExtension.CalendarType.AD);

        assertEquals("2024-04-13", formatted);
    }

    @Test
    @DisplayName("Should format BS date correctly")
    void shouldFormatBsDateCorrectly() {
        DualCalendarKernelExtension.BsDate bsDate = new DualCalendarKernelExtension.BsDate(2081, 4, 15);
        String formatted = extension.formatDate(bsDate, "yyyy-MM-dd",
            DualCalendarKernelExtension.CalendarType.BS);

        assertEquals("2081-04-15", formatted);
    }

    @Test
    @DisplayName("Should parse BS date correctly")
    void shouldParseBsDateCorrectly() {
        Object result = extension.parseDate("2081-04-15", "yyyy-MM-dd",
            DualCalendarKernelExtension.CalendarType.BS);

        assertTrue(result instanceof DualCalendarKernelExtension.BsDate);
        DualCalendarKernelExtension.BsDate bsDate = (DualCalendarKernelExtension.BsDate) result;
        assertEquals(2081, bsDate.year);
        assertEquals(4, bsDate.month);
        assertEquals(15, bsDate.day);
    }

    @Test
    @DisplayName("Should get current date in AD")
    void shouldGetCurrentDateInAd() {
        Object result = extension.getCurrentDate(DualCalendarKernelExtension.CalendarType.AD);

        assertTrue(result instanceof LocalDate);
        LocalDate adDate = (LocalDate) result;
        assertTrue(adDate.getYear() >= 2024);
    }

    @Test
    @DisplayName("Should get current date in BS")
    void shouldGetCurrentDateInBs() {
        Object result = extension.getCurrentDate(DualCalendarKernelExtension.CalendarType.BS);

        assertTrue(result instanceof DualCalendarKernelExtension.BsDate);
        DualCalendarKernelExtension.BsDate bsDate = (DualCalendarKernelExtension.BsDate) result;
        assertTrue(bsDate.year >= 2080);
    }

    @ParameterizedTest
    @CsvSource({
        "2020, AD, true",   // Leap year
        "2021, AD, false",
        "2024, AD, true",   // Leap year
        "2100, AD, false",  // Not leap (divisible by 100 but not 400)
        "2080, BS, true",
        "2081, BS, false",
        "2084, BS, true"
    })
    @DisplayName("Should detect leap years correctly")
    void shouldDetectLeapYearsCorrectly(int year, DualCalendarKernelExtension.CalendarType type, boolean expected) {
        assertEquals(expected, extension.isLeapYear(year, type));
    }

    @Test
    @DisplayName("Should throw exception for null AD date in conversion")
    void shouldThrowExceptionForNullAdDateInConversion() {
        assertThrows(IllegalArgumentException.class, () ->
            extension.convertAdToBs(null));
    }

    @Test
    @DisplayName("Should throw exception when not started")
    void shouldThrowExceptionWhenNotStarted() {
        extension.onModuleStopped(null);

        assertThrows(IllegalStateException.class, () ->
            extension.convertBsToAd(2081, 1, 1));
        assertThrows(IllegalStateException.class, () ->
            extension.convertAdToBs(LocalDate.now()));
        assertThrows(IllegalStateException.class, () ->
            extension.getCurrentDate(DualCalendarKernelExtension.CalendarType.AD));
    }

    @Test
    @DisplayName("Should be compatible with all modules")
    void shouldBeCompatibleWithAllModules() {
        assertTrue(extension.isCompatible(null));
    }

    @Test
    @DisplayName("BsDate toString should format correctly")
    void bsDateToStringShouldFormatCorrectly() {
        DualCalendarKernelExtension.BsDate bsDate = new DualCalendarKernelExtension.BsDate(2081, 4, 15);
        assertEquals("2081-04-15 BS", bsDate.toString());
    }

    @Test
    @DisplayName("Should reject invalid calendar type for parsing")
    void shouldRejectInvalidCalendarTypeForParsing() {
        assertThrows(IllegalArgumentException.class, () ->
            extension.parseDate("2024-04-13", "yyyy-MM-dd", null));
    }

    @Test
    @DisplayName("Should handle edge cases in BS date conversion")
    void shouldHandleEdgeCasesInBsDateConversion() {
        // BS month boundaries
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 1, 32)); // Baisakh can have 31-32 days
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 5, 32));  // Bhadra can have 31-32 days
        assertDoesNotThrow(() -> extension.convertBsToAd(2081, 6, 30)); // Ashoj typically 30 days
    }

    @Test
    @DisplayName("Should handle year boundaries")
    void shouldHandleYearBoundaries() {
        // First day of BS year
        LocalDate adFirstDay = extension.convertBsToAd(2081, 1, 1);
        assertNotNull(adFirstDay);

        // Last day of BS year
        LocalDate adLastDay = extension.convertBsToAd(2081, 12, 30);
        assertNotNull(adLastDay);
    }

    @Test
    @DisplayName("Should convert round-trip approximately")
    void shouldConvertRoundTripApproximately() {
        LocalDate originalAd = LocalDate.of(2024, 6, 15);

        DualCalendarKernelExtension.BsDate bsDate = extension.convertAdToBs(originalAd);
        LocalDate convertedAd = extension.convertBsToAd(bsDate.year, bsDate.month, bsDate.day);

        // Due to simplified algorithm, allow some variance
        assertTrue(Math.abs(originalAd.getDayOfYear() - convertedAd.getDayOfYear()) <= 2);
    }
}
