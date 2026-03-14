package com.ghatana.appplatform.calendar.adapter;

import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.BsHoliday;
import com.ghatana.appplatform.calendar.domain.HolidayType;
import com.ghatana.appplatform.calendar.port.HolidayCalendar;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * PostgreSQL implementation of {@link HolidayCalendar}.
 *
 * <p>Wraps all JDBC calls in {@link Promise#ofBlocking} so they never block the
 * ActiveJ eventloop thread. Each method opens and closes its own connection from
 * the injected {@link DataSource} (HikariCP pool).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL-backed holiday calendar using non-blocking JDBC
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresHolidayCalendar implements HolidayCalendar {

    private final DataSource dataSource;
    private final Executor   executor;

    public PostgresHolidayCalendar(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Promise<Void> addHoliday(BsHoliday holiday) {
        Objects.requireNonNull(holiday, "holiday");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO calendar_holidays
                    (id, bs_year, bs_month, bs_day, gregorian_date, name, type, jurisdiction, recurring_bs_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, holiday.id());
                ps.setInt(2, holiday.date().year());
                ps.setInt(3, holiday.date().month());
                ps.setInt(4, holiday.date().day());
                ps.setObject(5, holiday.gregorianDate());
                ps.setString(6, holiday.name());
                ps.setString(7, holiday.type().name());
                ps.setString(8, holiday.jurisdiction());
                ps.setBoolean(9, holiday.recurringBsDate());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<List<BsHoliday>> getHolidays(String jurisdiction, int bsYear) {
        Objects.requireNonNull(jurisdiction, "jurisdiction");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, bs_year, bs_month, bs_day, gregorian_date, name, type, jurisdiction, recurring_bs_date
                  FROM calendar_holidays
                 WHERE jurisdiction = ? AND bs_year = ?
                 ORDER BY bs_month, bs_day
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, jurisdiction);
                ps.setInt(2, bsYear);
                try (ResultSet rs = ps.executeQuery()) {
                    List<BsHoliday> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            }
        });
    }

    @Override
    public Promise<Void> deleteHoliday(String id) {
        Objects.requireNonNull(id, "id");
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM calendar_holidays WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    private static BsHoliday mapRow(ResultSet rs) throws SQLException {
        BsDate date = BsDate.of(
            rs.getInt("bs_year"),
            rs.getInt("bs_month"),
            rs.getInt("bs_day"));
        LocalDate gregorianDate = rs.getObject("gregorian_date", LocalDate.class);
        return new BsHoliday(
            rs.getString("id"),
            date,
            gregorianDate,
            rs.getString("name"),
            HolidayType.valueOf(rs.getString("type")),
            rs.getString("jurisdiction"),
            rs.getBoolean("recurring_bs_date"));
    }
}
