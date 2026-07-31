import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import utils.ValidationUtils;

/**
 * Customer-side booking and cancellation. This is where "a seat must never
 * be sold to two different customers for the same performance" has to be
 * guaranteed.
 */
public class BookingOperations {

    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private final Connection conn;

    public BookingOperations(Connection conn) {
        this.conn = conn;
    }

    /**
     * Books specific reserved seats for a performance. If any
     * requested seat isn't available (sold, blocked, or not assigned a tier
     * for this performance), the whole booking is rejected.
     */
    public boolean bookReservedSeats(int customerId, int performanceId, int paymentId, List<Integer> seatIds) {
        if (seatIds.isEmpty()) {
            System.out.println("No seats specified.");
            return false;
        }

        try {
            conn.setAutoCommit(false);

            if (!ValidationUtils.customerExists(conn, customerId)
                    || !ValidationUtils.paymentBelongsToCustomer(conn, paymentId, customerId)) {
                rollbackQuietly();
                return false;
            }

            if (!isPerformanceBookable(performanceId)) {
                rollbackQuietly();
                return false;
            }

            // Look up each seat's price via its section's tier for this
            // performance, and confirm it's neither sold nor blocked.
            // (Note: this indirectly also verify that the selected performance's section
            //  is indeed reserved seating. If it is general admission, those seats will
            //  not be found for this performance.)
            String seatCheckSql =
                "SELECT pt.price " +
                "FROM Seats st " +
                "JOIN SectionRows r ON r.rowId = st.rowId " +
                "JOIN Sections sec ON sec.sectionId = r.sectionId " +
                "JOIN PerformanceSectionAssignments psa " +
                "    ON psa.sectionId = sec.sectionId AND psa.performanceId = ? " +
                "JOIN PriceTiers pt ON pt.tierId = psa.tierId " +
                "WHERE st.seatId = ? " +
                "  AND st.seatId NOT IN (SELECT seatId FROM BlockedSeats WHERE performanceId = ?) " +
                "  AND st.seatId NOT IN ( " +
                "        SELECT seatId FROM Tickets " +
                "        WHERE performanceId = ? AND status = 'Active' AND seatId IS NOT NULL " +
                "      )";

            double totalPrice = 0;
            double[] seatPrices = new double[seatIds.size()];

            try (PreparedStatement ps = conn.prepareStatement(seatCheckSql)) {
                // For each seat to buy, check its availability
                for (int i = 0; i < seatIds.size(); i++) {
                    int seatId = seatIds.get(i);
                    ps.setInt(1, performanceId);
                    ps.setInt(2, seatId);
                    ps.setInt(3, performanceId);
                    ps.setInt(4, performanceId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            System.out.println("Seat " + seatId + " is not available for this performance.");
                            rollbackQuietly();
                            return false;
                        }
                        seatPrices[i] = rs.getDouble("price");
                        totalPrice += seatPrices[i];
                    }
                }
            }

            // Insert the entire order
            int orderId = insertOrder(customerId, performanceId, paymentId, totalPrice);

            // Insert the tickets -
            // The TRIGGER unique_seat_per_performance helps prevent double-booking; 
            // a duplicate-key error here means someone else booked one of these seats a moment ago.
            String sectionLookupSql =
                "SELECT sec.sectionId " +
                "FROM Seats st " +
                "JOIN SectionRows r ON r.rowId = st.rowId " +
                "JOIN Sections sec ON sec.sectionId = r.sectionId " +
                "WHERE st.seatId = ?";

            String insertTicketSql =
                "INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'Active')";

            try (PreparedStatement sectionPs = conn.prepareStatement(sectionLookupSql);
                 PreparedStatement insertPs = conn.prepareStatement(insertTicketSql)) {

                // Insert 1 ticket record per seat
                for (int i = 0; i < seatIds.size(); i++) {
                    int seatId = seatIds.get(i);

                    // Obtain the seat's section info
                    sectionPs.setInt(1, seatId);
                    int sectionId;
                    try (ResultSet rs = sectionPs.executeQuery()) {
                        rs.next();
                        sectionId = rs.getInt("sectionId");
                    }

                    // insert
                    insertPs.setInt(1, orderId);
                    insertPs.setInt(2, performanceId);
                    insertPs.setInt(3, sectionId);
                    insertPs.setInt(4, seatId);
                    insertPs.setDouble(5, seatPrices[i]);
                    insertPs.setInt(6, customerId);
                    insertPs.executeUpdate();
                }
            }

            conn.commit();
            System.out.println("Booked " + seatIds.size() + " seat(s), order #" + orderId + ", total $" + totalPrice);
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            if (e.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                System.out.println("One of the requested seats was just booked by someone else. Please try again.");
                return false;
            }
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Books `quantity` general-admission tickets against a section's
     * remaining standing capacity for a performance.
     */
    public boolean bookGeneralAdmission(int customerId, int performanceId, int paymentId, int sectionId, int quantity) {
        if (quantity <= 0) {
            System.out.println("Quantity must be positive.");
            return false;
        }

        try {
            conn.setAutoCommit(false);

            if (!ValidationUtils.customerExists(conn, customerId)
                    || !ValidationUtils.paymentBelongsToCustomer(conn, paymentId, customerId)) {
                rollbackQuietly();
                return false;
            }

            if (!isPerformanceBookable(performanceId)) {
                rollbackQuietly();
                return false;
            }

            // Lock this section's row so no other transaction can book
            // against the same GA capacity concurrently (using FOR UPDATE)
            int standingCapacity;
            boolean isReservedSeating;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT standingCapacity, isReservedSeating FROM Sections WHERE sectionId = ? FOR UPDATE")) {
                ps.setInt(1, sectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Section " + sectionId + " does not exist.");
                        rollbackQuietly();
                        return false;
                    }
                    standingCapacity = rs.getInt("standingCapacity");
                    isReservedSeating = rs.getBoolean("isReservedSeating");
                }
            }

            if (isReservedSeating) {
                System.out.println("Section " + sectionId + " is reserved seating -- use bookReservedSeats instead.");
                rollbackQuietly();
                return false;
            }

            // Obtain price for the section
            double price;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pt.price " +
                    "FROM PerformanceSectionAssignments psa " +
                    "JOIN PriceTiers pt ON pt.tierId = psa.tierId " +
                    "WHERE psa.sectionId = ? AND psa.performanceId = ?")) {
                ps.setInt(1, sectionId);
                ps.setInt(2, performanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("This section has no price tier assigned for this performance.");
                        rollbackQuietly();
                        return false;
                    }
                    price = rs.getDouble("price");
                }
            }

            // Obtain number of already sold tickets for this section
            int soldCount;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS soldCount FROM Tickets " +
                    "WHERE performanceId = ? AND sectionId = ? AND status = 'Active'")) {
                ps.setInt(1, performanceId);
                ps.setInt(2, sectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    soldCount = rs.getInt("soldCount");
                }
            }

            // Verify availability, before inserting the order and ticket infos
            if (standingCapacity - soldCount < quantity) {
                System.out.println("Only " + (standingCapacity - soldCount) + " GA spot(s) remaining, requested " + quantity + ".");
                rollbackQuietly();
                return false;
            }

            int orderId = insertOrder(customerId, performanceId, paymentId, price * quantity);

            try (PreparedStatement insertPs = conn.prepareStatement(
                    "INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status) " +
                    "VALUES (?, ?, ?, NULL, ?, ?, 'Active')")) {
                for (int i = 0; i < quantity; i++) {
                    insertPs.setInt(1, orderId);
                    insertPs.setInt(2, performanceId);
                    insertPs.setInt(3, sectionId);
                    insertPs.setDouble(4, price);
                    insertPs.setInt(5, customerId);
                    insertPs.executeUpdate();
                }
            }

            conn.commit();
            System.out.println("Booked " + quantity + " GA ticket(s), order #" + orderId + ", total $" + (price * quantity));
            return true;

        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /** 
     * A performance must exist and still be Scheduled (not Cancelled) to book against.
     */
    private boolean isPerformanceBookable(int performanceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM Performances WHERE performanceId = ?")) {
            ps.setInt(1, performanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Performance " + performanceId + " does not exist.");
                    return false;
                }
                if (!"Scheduled".equals(rs.getString("status"))) {
                    System.out.println("Performance " + performanceId + " has been cancelled.");
                    return false;
                }
                return true;
            }
        }
    }

    /**
     * Insert a tuple into Orders, and return the generated orderId
     */
    private int insertOrder(int customerId, int performanceId, int paymentId, double totalPaid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Orders (customerId, performanceId, paymentId, totalPaid) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setInt(2, performanceId);
            ps.setInt(3, paymentId);
            ps.setDouble(4, totalPaid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // Only the customer who placed the order may cancel; only allowed up to
    // seven days before the performance. Full refund, Tickets.status =
    // 'Cancelled by customer', seat becomes available again.
    public boolean cancelTickets(int customerId, List<Integer> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return false;
        }
        if (ticketIds.stream().distinct().count() != ticketIds.size()) {
            throw new IllegalArgumentException("A ticket may only be cancelled once per request.");
        }

        try {
            conn.setAutoCommit(false);
            List<CancelledTicket> cancellable = new ArrayList<>();
            String lookup =
                "SELECT t.ticketId, t.price, o.paymentId, p.dateTime " +
                "FROM Tickets t JOIN Orders o ON o.orderId = t.orderId " +
                "JOIN Performances p ON p.performanceId = t.performanceId " +
                "WHERE t.ticketId = ? AND o.customerId = ? AND t.status = 'Active' FOR UPDATE";
            try (PreparedStatement ps = conn.prepareStatement(lookup)) {
                for (int ticketId : ticketIds) {
                    ps.setInt(1, ticketId);
                    ps.setInt(2, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getTimestamp("dateTime").toLocalDateTime()
                                .isBefore(LocalDateTime.now().plusDays(7))) {
                            rollbackQuietly();
                            return false;
                        }
                        cancellable.add(new CancelledTicket(ticketId, rs.getInt("paymentId"), rs.getDouble("price")));
                    }
                }
            }

            try (PreparedStatement cancel = conn.prepareStatement(
                     "UPDATE Tickets SET status = 'Cancelled by customer', cancelledAt = NOW() " +
                     "WHERE ticketId = ? AND status = 'Active'");
                 PreparedStatement withdraw = conn.prepareStatement(
                     "UPDATE ResaleListings SET status = 'Withdrawn' WHERE ticketId = ? AND status = 'Active'");
                 PreparedStatement refund = conn.prepareStatement(
                     "INSERT INTO Refunds (ticketId, paymentId, amount, reason) VALUES (?, ?, ?, 'Customer cancellation')")) {
                for (CancelledTicket ticket : cancellable) {
                    cancel.setInt(1, ticket.ticketId);
                    if (cancel.executeUpdate() != 1) throw new SQLException("Ticket cancellation race detected.");
                    withdraw.setInt(1, ticket.ticketId);
                    withdraw.executeUpdate();
                    refund.setInt(1, ticket.ticketId);
                    refund.setInt(2, ticket.paymentId);
                    refund.setDouble(3, ticket.amount);
                    refund.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException("Unable to cancel tickets.", e);
        } finally {
            restoreAutoCommit();
        }
    }

    private static final class CancelledTicket {
        final int ticketId;
        final int paymentId;
        final double amount;
        CancelledTicket(int ticketId, int paymentId, double amount) {
            this.ticketId = ticketId;
            this.paymentId = paymentId;
            this.amount = amount;
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
