import java.sql.*;

import utils.ValidationUtils;

/**
 * Resale marketplace: list a ticket, withdraw a listing, buy a listing.
 * The price cap check is enforced in the DB by trg_resale_price_cap in
 * schema.sql, but validate client-side too so the user gets a clear message
 * instead of a raw SQL error.
 */
public class ResaleOperations {

    private final Connection conn;

    public ResaleOperations(Connection conn) {
        this.conn = conn;
    }

    // Insert into ResaleListings. Ticket must be owned by this customer, Active,
    // and for a performance that hasn't happened yet (and hasn't been cancelled).
    public boolean listTicketForResale(int ticketId, int sellerId, double askingPrice) {
        if (askingPrice < 0) throw new IllegalArgumentException("Resale price cannot be negative.");
        String sql =
            "INSERT INTO ResaleListings (ticketId, sellerId, resalePrice) " +
            "SELECT t.ticketId, ?, ? FROM Tickets t " +
            "JOIN Performances p ON p.performanceId = t.performanceId " +
            "WHERE t.ticketId = ? AND t.currentOwnerId = ? AND t.status = 'Active' " +
            "AND p.status = 'Scheduled' AND p.dateTime > NOW() " +
            "AND NOT EXISTS (SELECT 1 FROM ResaleListings rl WHERE rl.ticketId = t.ticketId AND rl.status = 'Active')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId); ps.setDouble(2, askingPrice); ps.setInt(3, ticketId); ps.setInt(4, sellerId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Unable to create resale listing (it may exceed the event cap).", e);
        }
    }

    // Set ResaleListings.status = 'Withdrawn'. Only before it's sold.
    public void withdrawListing(int listingId, int sellerId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ResaleListings SET status = 'Withdrawn' WHERE listingId = ? AND sellerId = ? AND status = 'Active'")) {
            ps.setInt(1, listingId); ps.setInt(2, sellerId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Active listing not found for this seller.");
        } catch (SQLException e) { throw new RuntimeException("Unable to withdraw listing.", e); }
    }

    // Transfer ownership: update Tickets.currentOwnerId, insert a row into
    // TicketOwnershipHistory, set ResaleListings.status = 'Sold'.
    public boolean purchaseListing(int listingId, int buyerId, int paymentId) {
        try {
            conn.setAutoCommit(false);
            if (!ValidationUtils.paymentBelongsToCustomer(conn, paymentId, buyerId)) {
                conn.rollback(); return false;
            }
            int ticketId, sellerId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT rl.ticketId, rl.sellerId FROM ResaleListings rl " +
                    "JOIN Tickets t ON t.ticketId = rl.ticketId " +
                    "JOIN Performances p ON p.performanceId = t.performanceId " +
                    "WHERE rl.listingId = ? AND rl.status = 'Active' " +
                    "AND p.status = 'Scheduled' AND p.dateTime > NOW() FOR UPDATE")) {
                ps.setInt(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return false; }
                    ticketId = rs.getInt("ticketId"); sellerId = rs.getInt("sellerId");
                }
            }
            if (sellerId == buyerId) { conn.rollback(); return false; }
            try (PreparedStatement ownership = conn.prepareStatement(
                    "UPDATE Tickets SET currentOwnerId = ? WHERE ticketId = ? AND currentOwnerId = ? AND status = 'Active'" );
                 PreparedStatement history = conn.prepareStatement(
                    "INSERT INTO TicketOwnershipHistory (ticketId, sellerId, buyerId, transactionPrice) " +
                    "SELECT ticketId, sellerId, ?, resalePrice FROM ResaleListings WHERE listingId = ?" );
                 PreparedStatement sold = conn.prepareStatement(
                    "UPDATE ResaleListings SET status = 'Sold' WHERE listingId = ? AND status = 'Active'")) {
                ownership.setInt(1, buyerId); ownership.setInt(2, ticketId); ownership.setInt(3, sellerId);
                if (ownership.executeUpdate() != 1) { conn.rollback(); return false; }
                history.setInt(1, buyerId); history.setInt(2, listingId); history.executeUpdate();
                sold.setInt(1, listingId); if (sold.executeUpdate() != 1) throw new SQLException("Listing changed during purchase.");
            }
            conn.commit(); return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) { }
            throw new RuntimeException("Unable to purchase listing.", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }
}
