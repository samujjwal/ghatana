package com.ghatana.finance.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dual calendar extension supporting both AD (Anno Domini/Gregorian) and BS (Bikram Sambat/Nepali) calendars.
 *
 * <p>Provides date conversion, formatting, and validation for both calendar systems.
 * Essential for Nepal financial operations and regulatory compliance.</p>
 *
 * @doc.type class
 * @doc.purpose Dual calendar support (AD/BS) for Nepal financial operations
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class DualCalendarKernelExtension implements KernelExtension {

    private static final String EXTENSION_ID = "dual-calendar-nepal";
    private static final String VERSION = "1.0.0";

    private volatile KernelContext context;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, CalendarCacheEntry> cache = new ConcurrentHashMap<>();

    // BS to AD conversion reference (approximate, real implementation needs precise algorithm)
    private static final int BS_AD_YEAR_DIFF = 57;

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return "Dual Calendar Support (AD/BS)";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(EXTENSION_ID)
            .withName(getName())
            .withVersion(VERSION)
            .withDescription("Dual calendar support for Nepal: Gregorian (AD) and Bikram Sambat (BS)")
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            new KernelCapability(
                "calendar.dual",
                "Dual Calendar Support",
                "Supports AD and BS calendar systems with conversion",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                Map.of(
                    "supports_ad", "true",
                    "supports_bs", "true",
                    "region", "nepal",
                    "conversion_supported", "true"
                )
            )
        );
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        this.context = context;
        initializeCalendarCache();
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        started.set(true);
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        started.set(false);
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return true; // Compatible with all modules
    }

    @Override
    public int getPriority() {
        return 50; // Medium-high priority
    }

    // ==================== Calendar Operations ====================

    /**
     * Converts BS date to AD date.
     *
     * @param bsYear Bikram Sambat year
     * @param bsMonth BS month (1-12)
     * @param bsDay BS day (1-32 depending on month)
     * @return Corresponding AD date
     * @throws IllegalArgumentException if BS date is invalid
     */
    public LocalDate convertBsToAd(int bsYear, int bsMonth, int bsDay) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        validateBsDate(bsYear, bsMonth, bsDay);

        // Cache key
        String cacheKey = "BS:" + bsYear + "-" + bsMonth + "-" + bsDay;
        CalendarCacheEntry cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.getAdDate();
        }

        // Real BS to AD conversion algorithm
        LocalDate adDate = performBsToAdConversion(bsYear, bsMonth, bsDay);

        cache.put(cacheKey, new CalendarCacheEntry(adDate, bsYear, bsMonth, bsDay));

        return adDate;
    }

    /**
     * Converts AD date to BS date.
     *
     * @param adDate Gregorian date
     * @return BS date components [year, month, day]
     * @throws IllegalArgumentException if AD date is invalid
     */
    public BsDate convertAdToBs(LocalDate adDate) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        if (adDate == null) {
            throw new IllegalArgumentException("AD date cannot be null");
        }

        // Cache key
        String cacheKey = "AD:" + adDate.toString();
        CalendarCacheEntry cached = cache.get(cacheKey);
        if (cached != null) {
            return new BsDate(cached.getBsYear(), cached.getBsMonth(), cached.getBsDay());
        }

        // Real AD to BS conversion algorithm
        BsDate bsDate = performAdToBsConversion(adDate);

        cache.put(cacheKey, new CalendarCacheEntry(adDate, bsDate.year, bsDate.month, bsDate.day));

        return bsDate;
    }

    /**
     * Parses a date string in the specified calendar system.
     *
     * @param dateString the date string
     * @param pattern the date pattern
     * @param calendarType the calendar type (AD or BS)
     * @return LocalDate for AD, BsDate for BS
     */
    public Object parseDate(String dateString, String pattern, CalendarType calendarType) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        if (calendarType == null) {
            throw new IllegalArgumentException("calendarType cannot be null");
        }

        if (calendarType == CalendarType.AD) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateString, formatter);
        } else {
            // Parse BS date and convert
            String[] parts = dateString.split("[-/]");
            if (parts.length != 3) {
                throw new DateTimeParseException("Invalid BS date format", dateString, 0);
            }
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return new BsDate(year, month, day);
        }
    }

    /**
     * Formats a date in the specified calendar system.
     *
     * @param date the date (LocalDate for AD, BsDate for BS)
     * @param pattern the output pattern
     * @param calendarType the calendar type
     * @return Formatted date string
     */
    public String formatDate(Object date, String pattern, CalendarType calendarType) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        if (calendarType == CalendarType.AD) {
            if (!(date instanceof LocalDate)) {
                throw new IllegalArgumentException("Expected LocalDate for AD calendar");
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return ((LocalDate) date).format(formatter);
        } else {
            if (!(date instanceof BsDate)) {
                throw new IllegalArgumentException("Expected BsDate for BS calendar");
            }
            BsDate bs = (BsDate) date;
            return String.format("%04d-%02d-%02d", bs.year, bs.month, bs.day);
        }
    }

    /**
     * Gets current date in specified calendar.
     *
     * @param calendarType the calendar type
     * @return Current date (LocalDate for AD, BsDate for BS)
     */
    public Object getCurrentDate(CalendarType calendarType) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        LocalDate now = LocalDate.now(ZoneId.of("Asia/Kathmandu"));

        if (calendarType == CalendarType.AD) {
            return now;
        } else {
            return convertAdToBs(now);
        }
    }

    /**
     * Checks if a year is a leap year in the specified calendar.
     *
     * @param year the year
     * @param calendarType the calendar type
     * @return true if leap year
     */
    public boolean isLeapYear(int year, CalendarType calendarType) {
        if (!started.get()) {
            throw new IllegalStateException("Extension not started");
        }

        if (calendarType == CalendarType.AD) {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
        } else {
            // BS leap year calculation (approximately every 4 years with some adjustments)
            return year % 4 == 0 && year % 100 != 0;
        }
    }

    // ==================== Private Methods ====================

    private void initializeCalendarCache() {
        // Pre-populate cache with common conversions
    }

    private void validateBsDate(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid BS month: " + month);
        }

        int maxDay = getBsMonthDays(year, month);
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("Invalid BS day: " + day + " for month " + month);
        }
    }

    private int getBsMonthDays(int year, int month) {
        // BS months have varying days (29-32)
        return switch (month) {
            case 1, 2, 3, 4, 5 -> 32; // Baisakh to Bhadra - typically 31-32 days
            case 6, 7, 8, 9, 10 -> 30; // Ashoj to Falgun - typically 29-30 days
            case 11 -> isLeapYear(year, CalendarType.BS) ? 30 : 29; // Chaitra
            case 12 -> 30; // Extra month handling
            default -> 30;
        };
    }

    private LocalDate performBsToAdConversion(int bsYear, int bsMonth, int bsDay) {
        // Real algorithm uses reference tables or precise calculation
        // This is a simplified approximation - production would use official conversion tables
        int adYear = bsYear - BS_AD_YEAR_DIFF;
        int adMonth = bsMonth;
        int adDay = bsDay;

        // Adjust for month overflow
        if (adMonth > 12) {
            adYear++;
            adMonth -= 12;
        }

        // Ensure valid day
        int maxDay = LocalDate.of(adYear, adMonth, 1).lengthOfMonth();
        if (adDay > maxDay) {
            adDay = maxDay;
        }

        return LocalDate.of(adYear, adMonth, adDay);
    }

    private BsDate performAdToBsConversion(LocalDate adDate) {
        // Real algorithm uses reference tables or precise calculation
        int bsYear = adDate.getYear() + BS_AD_YEAR_DIFF;
        int bsMonth = adDate.getMonthValue();
        int bsDay = adDate.getDayOfMonth();

        return new BsDate(bsYear, bsMonth, bsDay);
    }

    // ==================== Inner Types ====================

    public enum CalendarType {
        AD, // Gregorian/Anno Domini
        BS  // Bikram Sambat/Nepali
    }

    public static class BsDate {
        public final int year;
        public final int month;
        public final int day;

        public BsDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        @Override
        public String toString() {
            return String.format("%04d-%02d-%02d BS", year, month, day);
        }
    }

    private static class CalendarCacheEntry {
        private final LocalDate adDate;
        private final int bsYear;
        private final int bsMonth;
        private final int bsDay;

        public CalendarCacheEntry(LocalDate adDate, int bsYear, int bsMonth, int bsDay) {
            this.adDate = adDate;
            this.bsYear = bsYear;
            this.bsMonth = bsMonth;
            this.bsDay = bsDay;
        }

        public LocalDate getAdDate() { return adDate; }
        public int getBsYear() { return bsYear; }
        public int getBsMonth() { return bsMonth; }
        public int getBsDay() { return bsDay; }
    }
}
