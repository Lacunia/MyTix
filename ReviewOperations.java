import java.sql.Connection;

/**
 * Customer reviews of events/venues. Before inserting into Comments, verify:
 * the customer held a ticket (status != cancelled) for the given
 * performanceId, and that performance's dateTime is in the past. schema.sql's
 * unique_customer_performance_review constraint blocks a second review of
 * the same performance, but the "attended" and "already happened" checks
 * must happen here since they aren't expressible as static CHECKs.
 */
public class ReviewOperations {

    private final Connection conn;

    public ReviewOperations(Connection conn) {
        this.conn = conn;
    }

    public boolean insertReview(/* customerId, performanceId, content, eventRating, venueRating */) {
        // TODO
        return false;
    }
}
