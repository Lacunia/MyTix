import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import utils.ValidationUtils;

/**
 * Resale marketplace: list a ticket, withdraw a listing, buy a listing.
 * The price cap check is enforced in the DB by trg_resale_price_cap in
 * schema.sql, but it's also validated client-side first so the user gets a
 * clear message instead of a raw SQL error -- the trigger remains the real
 * guarantee (e.g. against a listing inserted by any other code path).
 */
public class ResaleOperations {

    private final Connection conn;

    public ResaleOperations(Connection conn) {
        this.conn = conn;
    }

    /**
     * Lists an owned, active ticket for resale at askingPrice, subject to
     * the event's resale cap.
     *
     * ticketId - the ticket to list
     * sellerId - must be the ticket's current owner
     * askingPrice - must not exceed faceValue * event.resalePriceCap
     */
    public boolean listTicketForResale(int ticketId, int sellerId, double askingPrice) {
        try {
            conn.setAutoCommit(false);

            if (!ValidationUtils.customerExists(conn, sellerId)) {
                rollbackQuietly();
                return false;
            }

            // First, lookup related information
            String lookupSql =
                "SELECT t.currentOwnerId, t.status, t.price AS faceValue, e.resalePriceCap " +
                "FROM Tickets t " +
                "JOIN Performances p ON p.performanceId = t.performanceId " +
                "JOIN Events e ON e.eventId = p.eventId " +
                "WHERE t.ticketId = ?";

            int currentOwnerId;
            String status;
            double faceValue;
            double resalePriceCap;

            try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Ticket " + ticketId + " does not exist.");
                        rollbackQuietly();
                        return false;
                    }
                    currentOwnerId = rs.getInt("currentOwnerId");
                    status = rs.getString("status");
                    faceValue = rs.getDouble("faceValue");
                    resalePriceCap = rs.getDouble("resalePriceCap");
                }
            }

            if (currentOwnerId != sellerId) {
                System.out.println("Customer " + sellerId + " does not own ticket " + ticketId + ".");
                rollbackQuietly();
                return false;
            }
            if (!"Active".equals(status)) {
                System.out.println("Ticket " + ticketId + " is not active and cannot be listed for resale.");
                rollbackQuietly();
                return false;
            }

            double cap = faceValue * resalePriceCap;
            if (askingPrice > cap) {
                System.out.printf("Asking price $%.2f exceeds the resale cap of $%.2f for this ticket.%n", askingPrice, cap);
                rollbackQuietly();
                return false;
            }

            // A ticket shouldn't have two simultaneously Active listings.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM ResaleListings WHERE ticketId = ? AND status = 'Active'")) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Ticket " + ticketId + " already has an active resale listing.");
                        rollbackQuietly();
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ResaleListings (ticketId, sellerId, resalePrice) VALUES (?, ?, ?)")) {
                ps.setInt(1, ticketId);
                ps.setInt(2, sellerId);
                ps.setDouble(3, askingPrice);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Ticket " + ticketId + " listed for resale at $" + askingPrice + ".");
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            // TRIGGER trg_resale_price_cap SIGNALs SQLSTATE '45000' if this
            // a resale price is above the cap despite the client-side check above.
            if ("45000".equals(e.getSQLState())) {
                System.out.println(e.getMessage());
                return false;
            }
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Withdraws an active resale listing.
     *
     * listingId - the listing to withdraw
     * sellerId - must match the listing's seller
     */
    public boolean withdrawListing(int listingId, int sellerId) {
        try {
            conn.setAutoCommit(false);

            if (!ValidationUtils.customerExists(conn, sellerId)) {
                rollbackQuietly();
                return false;
            }

            // First, retrieve the listing details
            String lookupSql = "SELECT sellerId, status FROM ResaleListings WHERE listingId = ?";
            int actualSellerId;
            String status;

            try (PreparedStatement ps = conn.prepareStatement(lookupSql)) {
                ps.setInt(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Listing " + listingId + " does not exist.");
                        rollbackQuietly();
                        return false;
                    }
                    actualSellerId = rs.getInt("sellerId");
                    status = rs.getString("status");
                }
            }

            // Perform checks and validations
            if (actualSellerId != sellerId) {
                System.out.println("Customer " + sellerId + " does not own listing " + listingId + ".");
                rollbackQuietly();
                return false;
            }
            if (!"Active".equals(status)) {
                System.out.println("Listing " + listingId + " is " + status + " and can no longer be withdrawn.");
                rollbackQuietly();
                return false;
            }

            // Withdraw listing
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ResaleListings SET status = 'Withdrawn' WHERE listingId = ?")) {
                ps.setInt(1, listingId);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Listing " + listingId + " withdrawn.");
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Purchases an active resale listing. 
     * The listing row is locked with SELECT ... FOR UPDATE so 
     * two buyers can never both purchase the same listing.
     *
     * listingId - the listing to purchase
     * buyerId - the purchasing customer (must not be the seller)
     * paymentId - the buyer's payment method for this purchase
     */
    public boolean purchaseListing(int listingId, int buyerId, int paymentId) {
        try {
            conn.setAutoCommit(false);

            if (!ValidationUtils.customerExists(conn, buyerId)
                    || !ValidationUtils.paymentBelongsToCustomer(conn, paymentId, buyerId)) {
                rollbackQuietly();
                return false;
            }

            // Lock the listing row so a concurrent purchase attempt on the
            // same listing blocks until this transaction commits/rolls back.
            String lockSql = "SELECT ticketId, sellerId, resalePrice, status FROM ResaleListings WHERE listingId = ? FOR UPDATE";
            int ticketId;
            int sellerId;
            double resalePrice;
            String status;

            try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                ps.setInt(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Listing " + listingId + " does not exist.");
                        rollbackQuietly();
                        return false;
                    }
                    ticketId = rs.getInt("ticketId");
                    sellerId = rs.getInt("sellerId");
                    resalePrice = rs.getDouble("resalePrice");
                    status = rs.getString("status");
                }
            }

            // Perform checks and validations
            if (!"Active".equals(status)) {
                System.out.println("Listing " + listingId + " is " + status + " and is no longer available.");
                rollbackQuietly();
                return false;
            }
            if (buyerId == sellerId) {
                System.out.println("You cannot purchase your own listing.");
                rollbackQuietly();
                return false;
            }

            // Update ticket ownership
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE Tickets SET currentOwnerId = ? WHERE ticketId = ?")) {
                ps.setInt(1, buyerId);
                ps.setInt(2, ticketId);
                ps.executeUpdate();
            }

            // Create ticket ownership history
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO TicketOwnershipHistory (ticketId, sellerId, buyerId, paymentId, transactionPrice) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, ticketId);
                ps.setInt(2, sellerId);
                ps.setInt(3, buyerId);
                ps.setInt(4, paymentId);
                ps.setDouble(5, resalePrice);
                ps.executeUpdate();
            }

            // Update resale listing to sold
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ResaleListings SET status = 'Sold' WHERE listingId = ?")) {
                ps.setInt(1, listingId);
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Ticket " + ticketId + " purchased by customer " + buyerId + " for $" + resalePrice + ".");
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
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
