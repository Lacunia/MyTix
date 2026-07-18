import java.sql.Connection;

/**
 * Customer-side booking and cancellation. This is where "a seat must never
 * be sold to two different customers for the same performance" has to be
 * guaranteed — wrap the availability check + insert in a transaction
 * (conn.setAutoCommit(false)) and rely on the unique_seat_per_performance
 * constraint in schema.sql as the last line of defense against races.
 */
public class BookingOperations {

    private final Connection conn;

    public BookingOperations(Connection conn) {
        this.conn = conn;
    }

    // Verify requested seats/GA capacity are available (not sold, not blocked,
    // or within remaining GA capacity), then insert Orders + one Tickets row
    // per seat (or per GA ticket), recording each ticket's face value.
    public boolean bookTickets(/* customerId, performanceId, paymentId, seatIds or (sectionId, quantity) */) {
        // TODO
        return false;
    }

    // Only the customer who placed the order may cancel; only allowed up to
    // seven days before the performance. Full refund, Tickets.status =
    // 'Cancelled by customer', seat becomes available again.
    public boolean cancelTickets(/* customerId, ticketIds */) {
        // TODO
        return false;
    }
}
