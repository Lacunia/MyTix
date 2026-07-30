import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import utils.ValidationUtils;

/**
 * Organizer-side event/performance/pricing management.
 * Covers: create event, add performance, define price tiers, assign
 * sections to tiers, set resale cap, update tier price, block/unblock seats,
 * cancel a performance.
 */
public class EventOperations {

    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private final Connection conn;

    public EventOperations(Connection conn) {
        this.conn = conn;
    }

    // Insert into Events (organizerId, taxonomyId, title, description, resalePriceCap).
    public void createEvent(/* organizerId, taxonomyId, title, description, resalePriceCap */) {
        // TODO
    }

    // Insert into Performances (eventId, venueId, dateTime).
    public void addPerformance(/* eventId, venueId, dateTime */) {
        // TODO
    }

    // Insert into PriceTiers for a performance (tierName, price) — one row per tier.
    public void definePriceTiers(/* performanceId, tiers */) {
        // TODO
    }

    // Insert into PerformanceSectionAssignments (sectionId, performanceId, tierId).
    // Every section of the venue must be assigned exactly one tier for this performance.
    public void assignSectionsToTiers(/* performanceId, sectionId -> tierId map */) {
        // TODO
    }

    // Update Events.resalePriceCap for an event.
    public void setResaleCap(/* eventId, newCapMultiplier */) {
        // TODO
    }

    // Update PriceTiers.price for a tier, but only if no ticket has been sold
    // in that tier for that performance yet (check Tickets before allowing the
    // update — see requirement: "otherwise the organizer should be informed").
    public boolean updateTierPrice(/* tierId, newPrice */) {
        // TODO — return false (and explain why) if a ticket already sold in this tier
        return false;
    }

    /**
     * Blocks an available seat for a performance (obstructed view, production
     * equipment, etc.), making it unsellable.
     *
     * organizerId - the organizer attempting the block (must own this event)
     * performanceId, seatId - the seat to block
     * reason - free-text reason (e.g. "Obstructed view")
     */
    public boolean blockSeat(int organizerId, int performanceId, int seatId, String reason) {
        try {
            conn.setAutoCommit(false);

            if (!isOrganizerOfPerformance(organizerId, performanceId)) {
                rollbackQuietly();
                return false;
            }

            // Confirm the seat is actually part of this performance's venue.
            String seatCheckSql =
                "SELECT sec.sectionId " +
                "FROM Seats st " +
                "JOIN SectionRows r ON r.rowId = st.rowId " +
                "JOIN Sections sec ON sec.sectionId = r.sectionId " +
                "JOIN PerformanceSectionAssignments psa " +
                "    ON psa.sectionId = sec.sectionId AND psa.performanceId = ? " +
                "WHERE st.seatId = ?";

            try (PreparedStatement ps = conn.prepareStatement(seatCheckSql)) {
                ps.setInt(1, performanceId);
                ps.setInt(2, seatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Seat " + seatId + " is not part of this performance's venue.");
                        rollbackQuietly();
                        return false;
                    }
                }
            }

            // An organizer cannot block a seat that is already sold.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM Tickets WHERE performanceId = ? AND seatId = ? AND status = 'Active'")) {
                ps.setInt(1, performanceId);
                ps.setInt(2, seatId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Seat " + seatId + " is already sold for this performance and cannot be blocked.");
                        rollbackQuietly();
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO BlockedSeats (performanceId, seatId, reason) VALUES (?, ?, ?)")) {
                ps.setInt(1, performanceId);
                ps.setInt(2, seatId);
                ps.setString(3, reason);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Seat " + seatId + " blocked for performance " + performanceId + ".");
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            if (e.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                System.out.println("Seat " + seatId + " is already blocked for this performance.");
                return false;
            }
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Unblocks a previously blocked seat, making it sellable again.
     *
     * organizerId - the organizer attempting the unblock (must own this event)
     * performanceId, seatId - the seat to unblock
     */
    public boolean unblockSeat(int organizerId, int performanceId, int seatId) {
        try {
            conn.setAutoCommit(false);

            if (!isOrganizerOfPerformance(organizerId, performanceId)) {
                rollbackQuietly();
                return false;
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM BlockedSeats WHERE performanceId = ? AND seatId = ?")) {
                ps.setInt(1, performanceId);
                ps.setInt(2, seatId);
                int rows = ps.executeUpdate();
                conn.commit();

                if (rows == 0) {
                    System.out.println("Seat " + seatId + " was not blocked for this performance.");
                    return false;
                }
                System.out.println("Seat " + seatId + " unblocked for performance " + performanceId + ".");
                return true;
            }

        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    // Set Performances.status = 'Cancelled', refund every sold ticket for it,
    // and record the cancellation (Tickets.status = 'Cancelled by organizer').
    public void cancelPerformance(/* performanceId */) {
        // TODO
    }

    // Confirms the given organizer owns the event this performance belongs to.
    private boolean isOrganizerOfPerformance(int organizerId, int performanceId) throws SQLException {
        String sql =
            "SELECT e.organizerId " +
            "FROM Performances p " +
            "JOIN Events e ON e.eventId = p.eventId " +
            "WHERE p.performanceId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, performanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Performance " + performanceId + " does not exist.");
                    return false;
                }
                boolean owns = rs.getInt("organizerId") == organizerId;
                if (!owns) {
                    System.out.println("Organizer " + organizerId + " does not manage this performance's event.");
                }
                return owns;
            }
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // Best-effort rollback -- nothing further to do if this itself fails.
        }
    }

    private void restoreAutoCommit() {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Best-effort restore.
        }
    }
}
