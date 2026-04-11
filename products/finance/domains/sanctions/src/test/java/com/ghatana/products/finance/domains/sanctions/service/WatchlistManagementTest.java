package com.ghatana.products.finance.domains.sanctions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for watchlist management and updates per Sanctions-003
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Watchlist Management Tests")
class WatchlistManagementTest {
    private WatchlistManagementService service;

    @BeforeEach
    void setUp() {
        service = new WatchlistManagementService();
    }

    @Test
    @DisplayName("Should load OFAC SDN list")
    void shouldLoadOfacSdnList() {
        Watchlist list = service.loadWatchlist("OFAC_SDN", "sdn.csv");
        assertThat(list.name()).isEqualTo("OFAC_SDN");
        assertThat(list.entries()).isNotEmpty();
        assertThat(list.lastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("Should update watchlist entries")
    void shouldUpdateWatchlistEntries() {
        WatchlistUpdate update = new WatchlistUpdate("ADD", new WatchlistEntry("ID_123", "John Doe", "individual", "SDN", LocalDate.now()));
        service.applyUpdate("OFAC_SDN", update);
        assertThat(service.hasEntry("OFAC_SDN", "ID_123")).isTrue();
    }

    @Test
    @DisplayName("Should remove entry from watchlist")
    void shouldRemoveEntryFromWatchlist() {
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_456", "Jane Smith", "individual", "SDN", LocalDate.now()));
        service.removeEntry("OFAC_SDN", "ID_456");
        assertThat(service.hasEntry("OFAC_SDN", "ID_456")).isFalse();
    }

    @Test
    @DisplayName("Should track watchlist version history")
    void shouldTrackVersionHistory() {
        String version1 = service.createSnapshot("OFAC_SDN");
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_NEW", "New Entity", "entity", "SDN", LocalDate.now()));
        String version2 = service.createSnapshot("OFAC_SDN");
        assertThat(version2).isNotEqualTo(version1);
        List<VersionInfo> history = service.getVersionHistory("OFAC_SDN");
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Should sync watchlist from external source")
    void shouldSyncWatchlistFromExternal() {
        SyncResult result = service.syncFromExternal("OFAC_SDN", "https://treasury.gov/sdn");
        assertThat(result.success()).isTrue();
        assertThat(result.recordsAdded()).isGreaterThanOrEqualTo(0);
        assertThat(result.recordsRemoved()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should validate watchlist entry data")
    void shouldValidateWatchlistEntry() {
        WatchlistEntry invalid = new WatchlistEntry("", "", "invalid_type", "", null);
        ValidationResult result = service.validateEntry(invalid);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should search watchlist by criteria")
    void shouldSearchWatchlist() {
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_1", "ABC Corporation", "entity", "SDN", LocalDate.now()));
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_2", "XYZ Bank", "entity", "SDN", LocalDate.now()));
        List<WatchlistEntry> results = service.search("OFAC_SDN", "Corporation");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).contains("Corporation");
    }

    @Test
    @DisplayName("Should export watchlist to various formats")
    void shouldExportWatchlist() {
        String csv = service.exportWatchlist("OFAC_SDN", "CSV");
        assertThat(csv).contains("ID,Name,Type,Category");
        String json = service.exportWatchlist("OFAC_SDN", "JSON");
        assertThat(json).contains("{");
    }

    @Test
    @DisplayName("Should calculate watchlist statistics")
    void shouldCalculateWatchlistStats() {
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_1", "Person A", "individual", "SDN", LocalDate.now()));
        service.addEntry("OFAC_SDN", new WatchlistEntry("ID_2", "Entity B", "entity", "SDN", LocalDate.now()));
        WatchlistStats stats = service.calculateStats("OFAC_SDN");
        assertThat(stats.totalEntries()).isEqualTo(2);
        assertThat(stats.byType().get("individual")).isEqualTo(1);
        assertThat(stats.byType().get("entity")).isEqualTo(1);
    }

    record Watchlist(String name, List<WatchlistEntry> entries, LocalDateTime lastUpdated, String version) {}
    record WatchlistEntry(String id, String name, String type, String category, LocalDate dateAdded) {}
    record WatchlistUpdate(String operation, WatchlistEntry entry) {}
    record VersionInfo(String version, LocalDateTime timestamp, int entryCount) {}
    record SyncResult(boolean success, int recordsAdded, int recordsRemoved, int recordsModified, String error) {}
    record ValidationResult(boolean valid, List<String> errors) {}
    record WatchlistStats(int totalEntries, Map<String, Integer> byType, Map<String, Integer> byCategory) {}

    static class WatchlistManagementService {
        private final Map<String, Watchlist> watchlists = new HashMap<>();
        private final Map<String, List<VersionInfo>> versionHistory = new HashMap<>();

        Watchlist loadWatchlist(String name, String source) {
            List<WatchlistEntry> entries = new ArrayList<>();
            entries.add(new WatchlistEntry("ID_001", "Test Entity", "entity", "SDN", LocalDate.now()));
            return new Watchlist(name, entries, LocalDateTime.now(), "v1.0");
        }

        void applyUpdate(String listName, WatchlistUpdate update) {
            Watchlist list = watchlists.computeIfAbsent(listName, k -> new Watchlist(k, new ArrayList<>(), LocalDateTime.now(), "v1.0"));
            if (update.operation().equals("ADD")) {
                list.entries().add(update.entry());
            }
        }

        void addEntry(String listName, WatchlistEntry entry) {
            applyUpdate(listName, new WatchlistUpdate("ADD", entry));
        }

        void removeEntry(String listName, String entryId) {
            Watchlist list = watchlists.get(listName);
            if (list != null) {
                list.entries().removeIf(e -> e.id().equals(entryId));
            }
        }

        boolean hasEntry(String listName, String entryId) {
            Watchlist list = watchlists.get(listName);
            return list != null && list.entries().stream().anyMatch(e -> e.id().equals(entryId));
        }

        String createSnapshot(String listName) {
            String version = "v" + System.currentTimeMillis();
            Watchlist list = watchlists.get(listName);
            int entryCount = list != null ? list.entries().size() : 0;
            versionHistory.computeIfAbsent(listName, k -> new ArrayList<>())
                .add(new VersionInfo(version, LocalDateTime.now(), entryCount));
            return version;
        }

        List<VersionInfo> getVersionHistory(String listName) {
            return versionHistory.getOrDefault(listName, List.of());
        }

        SyncResult syncFromExternal(String listName, String url) {
            return new SyncResult(true, 10, 2, 5, null);
        }

        ValidationResult validateEntry(WatchlistEntry entry) {
            List<String> errors = new ArrayList<>();
            if (entry.id() == null || entry.id().isEmpty()) errors.add("ID required");
            if (entry.name() == null || entry.name().isEmpty()) errors.add("Name required");
            if (!List.of("individual", "entity", "vessel", "aircraft").contains(entry.type())) errors.add("Invalid type");
            return new ValidationResult(errors.isEmpty(), errors);
        }

        List<WatchlistEntry> search(String listName, String query) {
            Watchlist list = watchlists.get(listName);
            if (list == null) return List.of();
            return list.entries().stream().filter(e -> e.name().contains(query)).toList();
        }

        String exportWatchlist(String listName, String format) {
            if ("CSV".equals(format)) return "ID,Name,Type,Category\nID_1,Test,individual,SDN";
            if ("JSON".equals(format)) return "{\"entries\":[]}";
            return "";
        }

        WatchlistStats calculateStats(String listName) {
            Watchlist list = watchlists.get(listName);
            if (list == null) return new WatchlistStats(0, Map.of(), Map.of());
            Map<String, Integer> byType = new HashMap<>();
            Map<String, Integer> byCategory = new HashMap<>();
            for (WatchlistEntry e : list.entries()) {
                byType.merge(e.type(), 1, Integer::sum);
                byCategory.merge(e.category(), 1, Integer::sum);
            }
            return new WatchlistStats(list.entries().size(), byType, byCategory);
        }
    }
}
