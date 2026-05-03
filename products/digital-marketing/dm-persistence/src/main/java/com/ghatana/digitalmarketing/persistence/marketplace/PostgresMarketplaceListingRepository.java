package com.ghatana.digitalmarketing.persistence.marketplace;

import com.ghatana.digitalmarketing.application.marketplace.MarketplaceListingRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.domain.marketplace.MarketplaceListing;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL adapter for MarketplaceListingRepository (DMOS-P3-004).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence adapter for marketplace listings
 * @doc.layer persistence
 */
public final class PostgresMarketplaceListingRepository implements MarketplaceListingRepository {

    private static final Logger logger = LoggerFactory.getLogger(PostgresMarketplaceListingRepository.class);

    private final DataSource dataSource;

    public PostgresMarketplaceListingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Promise<MarketplaceListing> save(MarketplaceListing listing) {
        return Promise.ofBlocking(() -> {
            String sql = """
                INSERT INTO dmos_marketplace_listings
                (listing_id, name, description, author_tenant_id, version, status, rating, download_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (listing_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                version = EXCLUDED.version,
                status = EXCLUDED.status,
                rating = EXCLUDED.rating,
                download_count = EXCLUDED.download_count,
                updated_at = EXCLUDED.updated_at
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, listing.getListingId());
                stmt.setString(2, listing.getName());
                stmt.setString(3, listing.getDescription());
                stmt.setString(4, listing.getAuthorTenantId());
                stmt.setString(5, listing.getVersion());
                stmt.setString(6, listing.getStatus());
                stmt.setDouble(7, listing.getRating());
                stmt.setInt(8, listing.getDownloadCount());
                stmt.setTimestamp(9, Timestamp.from(listing.getCreatedAt()));
                stmt.setTimestamp(10, Timestamp.from(listing.getUpdatedAt()));

                stmt.executeUpdate();
                logger.info("Marketplace listing saved: {}", listing.getListingId());
                return listing;
            } catch (SQLException e) {
                logger.error("Failed to save marketplace listing", e);
                throw new RuntimeException("Failed to save marketplace listing", e);
            }
        });
    }

    @Override
    public Promise<Optional<MarketplaceListing>> findById(String listingId) {
        return Promise.ofBlocking(() -> {
            String sql = "SELECT * FROM dmos_marketplace_listings WHERE listing_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, listingId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.error("Failed to find marketplace listing by ID", e);
                throw new RuntimeException("Failed to find marketplace listing", e);
            }
        });
    }

    @Override
    public Promise<List<MarketplaceListing>> findPublished() {
        return Promise.ofBlocking(() -> {
            String sql = "SELECT * FROM dmos_marketplace_listings WHERE status = 'PUBLISHED' ORDER BY rating DESC, created_at DESC";

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                List<MarketplaceListing> listings = new ArrayList<>();
                while (rs.next()) {
                    listings.add(mapRow(rs));
                }
                return listings;
            } catch (SQLException e) {
                logger.error("Failed to find published marketplace listings", e);
                throw new RuntimeException("Failed to find published marketplace listings", e);
            }
        });
    }

    @Override
    public Promise<List<MarketplaceListing>> findByAuthor(DmTenantId authorTenantId) {
        return Promise.ofBlocking(() -> {
            String sql = "SELECT * FROM dmos_marketplace_listings WHERE author_tenant_id = ? ORDER BY created_at DESC";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, authorTenantId.value());
                ResultSet rs = stmt.executeQuery();

                List<MarketplaceListing> listings = new ArrayList<>();
                while (rs.next()) {
                    listings.add(mapRow(rs));
                }
                return listings;
            } catch (SQLException e) {
                logger.error("Failed to find marketplace listings by author", e);
                throw new RuntimeException("Failed to find marketplace listings by author", e);
            }
        });
    }

    @Override
    public Promise<MarketplaceListing> update(MarketplaceListing listing) {
        return Promise.ofBlocking(() -> {
            String sql = """
                UPDATE dmos_marketplace_listings
                SET name = ?, description = ?, version = ?, status = ?, rating = ?, download_count = ?, updated_at = ?
                WHERE listing_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, listing.getName());
                stmt.setString(2, listing.getDescription());
                stmt.setString(3, listing.getVersion());
                stmt.setString(4, listing.getStatus());
                stmt.setDouble(5, listing.getRating());
                stmt.setInt(6, listing.getDownloadCount());
                stmt.setTimestamp(7, Timestamp.from(Instant.now()));
                stmt.setString(8, listing.getListingId());

                stmt.executeUpdate();
                logger.info("Marketplace listing updated: {}", listing.getListingId());
                return listing;
            } catch (SQLException e) {
                logger.error("Failed to update marketplace listing", e);
                throw new RuntimeException("Failed to update marketplace listing", e);
            }
        });
    }

    @Override
    public Promise<Void> delete(String listingId) {
        return Promise.ofBlocking(() -> {
            String sql = "DELETE FROM dmos_marketplace_listings WHERE listing_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, listingId);
                stmt.executeUpdate();
                logger.info("Marketplace listing deleted: {}", listingId);
                return null;
            } catch (SQLException e) {
                logger.error("Failed to delete marketplace listing", e);
                throw new RuntimeException("Failed to delete marketplace listing", e);
            }
        });
    }

    @Override
    public Promise<Void> incrementDownloadCount(String listingId) {
        return Promise.ofBlocking(() -> {
            String sql = "UPDATE dmos_marketplace_listings SET download_count = download_count + 1 WHERE listing_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, listingId);
                stmt.executeUpdate();
                logger.info("Incremented download count for marketplace listing: {}", listingId);
                return null;
            } catch (SQLException e) {
                logger.error("Failed to increment download count", e);
                throw new RuntimeException("Failed to increment download count", e);
            }
        });
    }

    private MarketplaceListing mapRow(ResultSet rs) throws SQLException {
        return MarketplaceListing.builder()
            .listingId(rs.getString("listing_id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .authorTenantId(rs.getString("author_tenant_id"))
            .version(rs.getString("version"))
            .status(rs.getString("status"))
            .rating(rs.getDouble("rating"))
            .downloadCount(rs.getInt("download_count"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();
    }
}
