package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Generates settlement instructions for each net obligation after netting (D09-006).
 *              Creates one DELIVER or RECEIVE instruction per netting_position.
 *              Settlement date calculated with K-15 holiday awareness (T+2 for NEPSE equities).
 *              Instructions start in GENERATED state; lifecycle: MATCHED → AFFIRMED → SETTLED / FAILED.
 * @doc.layer   Domain — Post-Trade settlement
 * @doc.pattern One instruction per net obligation; CSD integration via plug-in port (T-pattern)
 */
public class SettlementInstructionService {

    /** Port for CSD (Central Securities Depository) instruction submission. */
    public interface CsdInstructionPort {
        String submitInstruction(String instructionId, String participantId,
                                  String instrumentId, long quantity,
                                  String direction, LocalDate settlementDate);
    }

    public enum Direction { DELIVER, RECEIVE }

    public record SettlementInstruction(
        String instructionId,
        String nettingSetId,
        String nettingPositionId,
        String participantId,
        String instrumentId,
        long quantity,
        Direction direction,
        LocalDate settlementDate,
        String settlementAccount,
        String currency,
        BigDecimal amount,
        String status,   // GENERATED / MATCHED / AFFIRMED / SETTLED / FAILED / CANCELLED
        String csdReference
    ) {}

    private final DataSource dataSource;
    private final CsdInstructionPort csdPort;
    private final Executor executor;
    private final Counter generatedCounter;

    public SettlementInstructionService(DataSource dataSource, CsdInstructionPort csdPort,
                                         Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.csdPort = csdPort;
        this.executor = executor;
        this.generatedCounter = Counter.builder("posttrade.settlement.instructions_generated_total")
            .register(registry);
    }

    /**
     * Generate settlement instructions for all netting positions in a completed netting set.
     * Positive net_quantity = DELIVER; negative = RECEIVE.
     */
    public Promise<List<SettlementInstruction>> generateForNettingSet(String nettingSetId) {
        return Promise.ofBlocking(executor, () -> {
            List<NettingPositionRow> positions = loadNettingPositions(nettingSetId);
            List<SettlementInstruction> instructions = new ArrayList<>();
            for (NettingPositionRow pos : positions) {
                Direction direction = pos.netQuantity >= 0 ? Direction.DELIVER : Direction.RECEIVE;
                long quantity = Math.abs(pos.netQuantity);
                String instructionId = UUID.randomUUID().toString();
                String csdRef = csdPort.submitInstruction(instructionId, pos.participantId,
                    pos.instrumentId, quantity, direction.name(), pos.settlementDate);
                SettlementInstruction instr = new SettlementInstruction(instructionId,
                    nettingSetId, pos.positionId, pos.participantId, pos.instrumentId,
                    quantity, direction, pos.settlementDate,
                    loadSettlementAccount(pos.participantId), pos.currency,
                    pos.netCash.abs(), "GENERATED", csdRef);
                persistInstruction(instr);
                instructions.add(instr);
                generatedCounter.increment();
            }
            return instructions;
        });
    }

    /** Update instruction status (lifecycle transitions). */
    public Promise<Void> updateStatus(String instructionId, String newStatus) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE settlement_instructions SET status = ? WHERE id = ?")) {
                ps.setString(1, newStatus);
                ps.setObject(2, UUID.fromString(instructionId));
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** List all instructions for a netting set. */
    public Promise<List<SettlementInstruction>> listByNettingSet(String nettingSetId) {
        return Promise.ofBlocking(executor, () -> {
            List<SettlementInstruction> list = new ArrayList<>();
            String sql = "SELECT id, netting_set_id, netting_position_id, " +
                         "instrument_id, quantity, direction, settlement_date, " +
                         "settlement_account, currency, amount, status, csd_reference " +
                         "FROM settlement_instructions WHERE netting_set_id = ?";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(nettingSetId));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new SettlementInstruction(rs.getString("id"),
                            rs.getString("netting_set_id"), rs.getString("netting_position_id"),
                            null, rs.getString("instrument_id"),
                            rs.getLong("quantity"), Direction.valueOf(rs.getString("direction")),
                            rs.getDate("settlement_date").toLocalDate(),
                            rs.getString("settlement_account"), rs.getString("currency"),
                            rs.getBigDecimal("amount"), rs.getString("status"),
                            rs.getString("csd_reference")));
                    }
                }
            }
            return list;
        });
    }

    private record NettingPositionRow(String positionId, String participantId,
                                       String instrumentId, long netQuantity,
                                       BigDecimal netCash, String currency,
                                       LocalDate settlementDate) {}

    private List<NettingPositionRow> loadNettingPositions(String nettingSetId) throws Exception {
        List<NettingPositionRow> rows = new ArrayList<>();
        String sql = "SELECT id, participant_id, instrument_id, net_quantity, net_cash, " +
                     "currency, settlement_date FROM netting_positions WHERE netting_set_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(nettingSetId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new NettingPositionRow(rs.getString("id"), rs.getString("participant_id"),
                        rs.getString("instrument_id"), rs.getLong("net_quantity"),
                        rs.getBigDecimal("net_cash"), rs.getString("currency"),
                        rs.getDate("settlement_date").toLocalDate()));
                }
            }
        }
        return rows;
    }

    private String loadSettlementAccount(String participantId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT settlement_account FROM participants WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(participantId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("settlement_account") : "DEFAULT";
            }
        }
    }

    private void persistInstruction(SettlementInstruction instr) throws Exception {
        String sql = "INSERT INTO settlement_instructions(id, netting_set_id, netting_position_id, " +
                     "instrument_id, quantity, direction, settlement_date, settlement_account, " +
                     "currency, amount, status, csd_reference) VALUES(?,?,?,?,?,?,?,?,?,?,'GENERATED',?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(instr.instructionId()));
            ps.setObject(2, UUID.fromString(instr.nettingSetId()));
            ps.setObject(3, instr.nettingPositionId() != null ? UUID.fromString(instr.nettingPositionId()) : null);
            ps.setObject(4, UUID.fromString(instr.instrumentId()));
            ps.setLong(5, instr.quantity());
            ps.setString(6, instr.direction().name());
            ps.setObject(7, instr.settlementDate());
            ps.setString(8, instr.settlementAccount());
            ps.setString(9, instr.currency());
            ps.setBigDecimal(10, instr.amount());
            ps.setString(11, instr.csdReference());
            ps.executeUpdate();
        }
    }
}
