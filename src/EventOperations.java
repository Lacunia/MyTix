import java.sql.*;
import java.util.Scanner;
import java.util.List;
import java.util.Map;

/**
 * Organizer-side event/performance/pricing management.
 * Covers: create event, add performance, define price tiers, assign
 * sections to tiers, set resale cap, update tier price, block/unblock seats,
 * cancel a performance.
 */
public class EventOperations {

    private final Connection conn;

    /** Simple value object used when defining several price tiers at once. */
    public static final class Tier {
        private final String name;
        private final java.math.BigDecimal price;

        public Tier(String name, java.math.BigDecimal price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public java.math.BigDecimal getPrice() {
            return price;
        }
    }

    public EventOperations(Connection conn) {
        this.conn = conn;
    }

    /**
     * Returns the ID for a segment/genre pair, creating it if it does not
     * already exist. The unique (segment, genre) constraint prevents duplicates.
     */
    public int createTaxonomy(String segment, String genre) throws SQLException {
        if (segment == null || segment.isBlank() || genre == null || genre.isBlank()) {
            throw new IllegalArgumentException("Segment and genre are required.");
        }
        // Ticketmaster names this segment "Arts & Theatre". Accept the
        // common shorthand without storing a second, incompatible taxonomy.
        if (segment.trim().equalsIgnoreCase("Theatre")) {
            segment = "Arts & Theatre";
        }

        String sql = "INSERT INTO Taxonomy (segment, genre) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE taxonomyId = LAST_INSERT_ID(taxonomyId)";

        try (PreparedStatement statement = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, segment.trim());
            statement.setString(2, genre.trim());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Taxonomy was saved, but its ID was not returned.");
    }

    /**
     * Creates an event owned by an organizer and returns its generated event ID.
     * The organizer and taxonomy IDs must already exist in the database.
     */
    public int createEvent(int organizerId, int taxonomyId, String title,
                           String description, double resalePriceCap)
            throws SQLException {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Event title is required.");
        }
        if (resalePriceCap <= 0) {
            throw new IllegalArgumentException("Resale price cap must be at least 1.00.");
        }

        String sql = "INSERT INTO Events "
                   + "(organizerId, taxonomyId, title, description, resalePriceCap) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement statement = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, organizerId);
            statement.setInt(2, taxonomyId);
            statement.setString(3, title.trim());
            statement.setString(4, description);
            statement.setDouble(5, resalePriceCap);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }

        throw new SQLException("Event was created, but its generated ID was not returned.");
    }

    /** Finds an artist by exact name or creates the artist if they are new. */
    public int findOrCreateArtist(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Artist name is required.");
        }
        try (PreparedStatement find = conn.prepareStatement(
                "SELECT artistId FROM Artists WHERE name = ? ORDER BY artistId LIMIT 1")) {
            find.setString(1, name.trim());
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to look up artist.", e);
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO Artists (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name.trim());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to create artist.", e);
        }
        throw new RuntimeException("Artist was created, but its ID was not returned.");
    }

    /** Adds an artist to an event with the required billing order. */
    public void addArtistToEvent(int eventId, int artistId, String billingOrder) {
        if (!List.of("Headliner", "Special guest", "Opening act").contains(billingOrder)) {
            throw new IllegalArgumentException("Billing order must be Headliner, Special guest, or Opening act.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO EventLineups (artistId, eventId, billingOrder) VALUES (?, ?, ?)")) {
            ps.setInt(1, artistId);
            ps.setInt(2, eventId);
            ps.setString(3, billingOrder);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to add artist to this event (the artist may already be listed).", e);
        }
    }

    /**
     * Adds a performance to an event.
     */
    public int addPerformance(int eventId, int venueId, java.time.LocalDateTime dateTime) {
        return addPerformance(eventId, venueId, "Performance", dateTime);
    }

    public int addPerformance(int eventId, int venueId, String name, java.time.LocalDateTime dateTime) {
        // check that the event exists and venue exists, and that the dateTime is in the future
        if (eventId <= 0) {
            throw new IllegalArgumentException("Invalid event ID.");
        }
        if (venueId <= 0) {
            throw new IllegalArgumentException("Invalid venue ID.");
        }
        if (dateTime == null || dateTime.isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Performance date and time must be in the future.");
        }
        
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Performance name is required.");
        }
        String sql = "INSERT INTO Performances (eventId, venueId, name, dateTime, status) "
                   + "VALUES (?, ?, ?, ?, 'Scheduled')";

        try (PreparedStatement statement = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, eventId);
            statement.setInt(2, venueId);
            statement.setString(3, name.trim());
            statement.setObject(4, dateTime);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add performance: " + e.getMessage(), e);
        }

        throw new RuntimeException("Performance was created, but its generated ID was not returned.");
    }

    /**
     * Defines price tiers for a performance.
     */
    public void definePriceTiers(int performanceId, List<Tier> tiers) {
        String sql = "INSERT INTO PriceTiers (performanceId, tierName, price) VALUES (?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (Tier tier : tiers) {
                statement.setInt(1, performanceId);
                statement.setString(2, tier.getName());
                statement.setBigDecimal(3, tier.getPrice());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to define price tiers: " + e.getMessage(), e);
        }
    }

    // Insert into PerformanceSectionAssignments (sectionId, performanceId, tierId).
    // Every section of the venue must be assigned exactly one tier for this performance.
    public void assignSectionsToTiers(int performanceId, Map<Integer, Integer> sectionToTierMap) {
        if (sectionToTierMap == null || sectionToTierMap.isEmpty()) {
            throw new IllegalArgumentException("At least one section assignment is required.");
        }
        String sql = "INSERT INTO PerformanceSectionAssignments (sectionId, performanceId, tierId) VALUES (?, ?, ?)";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
            for (Map.Entry<Integer, Integer> entry : sectionToTierMap.entrySet()) {
                int sectionId = entry.getKey();
                int tierId = entry.getValue();
                statement.setInt(1, sectionId);
                statement.setInt(2, performanceId);
                statement.setInt(3, tierId);
                statement.addBatch();
            }
            statement.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw new RuntimeException("Failed to assign sections to tiers: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    // Update Events.resalePriceCap for an event.
    public void setResaleCap(int eventId, double newCap) {
        if (newCap <= 0) {
            throw new IllegalArgumentException("Resale price cap must be at least 1.00.");
        }

        String sql = "UPDATE Events SET resalePriceCap = ? WHERE eventId = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setDouble(1, newCap);
            statement.setInt(2, eventId);
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("No event found with ID: " + eventId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set resale cap: " + e.getMessage(), e);
        }
        
    }

    // Update PriceTiers.price for a tier, but only if no ticket has been sold
    // in that tier for that performance yet (check Tickets before allowing the
    // update — see requirement: "otherwise the organizer should be informed").
    public boolean updateTierPrice(int performanceId, int tierId, double newPrice) {
        if (newPrice <= 0) {
            throw new IllegalArgumentException("New price must be greater than 0.");
        }

        String sql =
            "UPDATE PriceTiers pt " +
            "SET pt.price = ? " +
            "WHERE pt.tierId = ? " +
            "  AND pt.performanceId = ? " +
            "  AND NOT EXISTS ( " +
            "      SELECT 1 " +
            "      FROM Tickets t " +
            "      JOIN PerformanceSectionAssignments psa " +
            "        ON psa.performanceId = t.performanceId " +
            "       AND psa.sectionId = t.sectionId " +
            "      WHERE t.performanceId = ? " +
            "        AND psa.tierId = ? " +
            "        AND t.status = 'Active' " +
            "  )";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setDouble(1, newPrice);
            statement.setInt(2, tierId);
            statement.setInt(3, performanceId);
            statement.setInt(4, performanceId);
            statement.setInt(5, tierId);

            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update tier price.", e);
        }
    }

    // Insert into BlockedSeats. Must first confirm the seat isn't already sold
    // for this performance (organizer cannot block a sold seat).
    public boolean blockSeat(int performanceId, int seatId) {
        String checkSql = "SELECT COUNT(*) FROM Tickets WHERE performanceId = ? AND seatId = ?";
        String insertSql = "INSERT INTO BlockedSeats (performanceId, seatId) VALUES (?, ?)";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, performanceId);
            checkStmt.setInt(2, seatId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return false; // Seat is already sold
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if seat is sold.", e);
        }

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setInt(1, performanceId);
            insertStmt.setInt(2, seatId);
            insertStmt.executeUpdate();
            return true; // Seat successfully blocked
        } catch (SQLException e) {
            throw new RuntimeException("Failed to block seat.", e);
        }
    }

    // Delete the row from BlockedSeats for (performanceId, seatId).
    public void unblockSeat(int performanceId, int seatId) {
        String sql = "DELETE FROM BlockedSeats WHERE performanceId = ? AND seatId = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, performanceId);
            statement.setInt(2, seatId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unblock seat.", e);
        }
    }

    // Set Performances.status = 'Cancelled', refund every sold ticket for it,
    // and record the cancellation (Tickets.status = 'Cancelled by organizer').
    public void cancelPerformance(int performanceId) {
        String updatePerformanceSql = "UPDATE Performances SET status = 'Cancelled', cancelledAt = NOW() WHERE performanceId = ? AND status = 'Scheduled'";
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement updatePerformanceStmt = conn.prepareStatement(updatePerformanceSql)) {
            updatePerformanceStmt.setInt(1, performanceId);
            int rowsAffected = updatePerformanceStmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new IllegalArgumentException("Performance does not exist or has already been cancelled.");
            }
            }
            try (PreparedStatement refund = conn.prepareStatement(
                     "INSERT INTO Refunds (ticketId, paymentId, amount, reason) " +
                     "SELECT t.ticketId, o.paymentId, t.price, 'Organizer cancellation' " +
                     "FROM Tickets t JOIN Orders o ON o.orderId = t.orderId " +
                     "WHERE t.performanceId = ? AND t.status = 'Active'" );
                 PreparedStatement withdraw = conn.prepareStatement(
                     "UPDATE ResaleListings rl JOIN Tickets t ON t.ticketId = rl.ticketId " +
                     "SET rl.status = 'Withdrawn' WHERE t.performanceId = ? AND rl.status = 'Active'");
                 PreparedStatement tickets = conn.prepareStatement(
                     "UPDATE Tickets SET status = 'Cancelled by organizer', cancelledAt = NOW() " +
                     "WHERE performanceId = ? AND status = 'Active'")) {
                refund.setInt(1, performanceId); refund.executeUpdate();
                withdraw.setInt(1, performanceId); withdraw.executeUpdate();
                tickets.setInt(1, performanceId); tickets.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw new RuntimeException("Failed to cancel performance.", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }
}
