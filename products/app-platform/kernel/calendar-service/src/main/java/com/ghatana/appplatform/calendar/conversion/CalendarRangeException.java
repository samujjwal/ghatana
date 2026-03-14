package com.ghatana.appplatform.calendar.conversion;

/**
 * Thrown when a date falls outside the supported BS calendar range (2070–2100).
 *
 * @doc.type class
 * @doc.purpose Exception for out-of-range BS calendar dates (K-15)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class CalendarRangeException extends RuntimeException {

    private final Object inputDate;

    public CalendarRangeException(String message, Object inputDate) {
        super(message);
        this.inputDate = inputDate;
    }

    /** The date value that was out of range. */
    public Object getInputDate() {
        return inputDate;
    }
}
