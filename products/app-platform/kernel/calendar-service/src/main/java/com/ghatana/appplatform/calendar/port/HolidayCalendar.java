package com.ghatana.appplatform.calendar.port;

import com.ghatana.appplatform.calendar.domain.BsHoliday;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Port (hexagonal architecture) for jurisdiction-scoped holiday calendar operations.
 *
 * <p>All mutations return a {@link Promise} to support ActiveJ's async eventloop model.
 * Implementations must not block the eventloop thread directly; use
 * {@link io.activej.promise.Promise#ofBlocking} for JDBC calls.
 *
 * @doc.type interface
 * @doc.purpose Read/write port for the holiday calendar
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface HolidayCalendar {

    /**
     * Adds a new holiday to the calendar.
     *
     * @param holiday the holiday to add; must not be null
     * @return a promise that resolves when the holiday is persisted
     */
    Promise<Void> addHoliday(BsHoliday holiday);

    /**
     * Returns all holidays for a given jurisdiction in a given BS year.
     *
     * @param jurisdiction ISO 3166-1/3166-2 jurisdiction code (e.g. "NP", "NP-BAG")
     * @param bsYear       the BS year to query
     * @return a promise resolving to a (potentially empty) list of holidays
     */
    Promise<List<BsHoliday>> getHolidays(String jurisdiction, int bsYear);

    /**
     * Deletes a holiday by its unique identifier.
     *
     * @param id the holiday ID to delete
     * @return a promise that resolves when the holiday is removed (no-op if absent)
     */
    Promise<Void> deleteHoliday(String id);
}
