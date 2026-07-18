import java.sql.Connection;

/**
 * Q1-Q7 search/browse queries. Each method should build and run one SQL
 * query (joins/CTEs/window functions as needed) and return/print the result
 * set — keep the SQL itself in these methods, not scattered in the UI class.
 */
public class SearchQueries {

    private final Connection conn;

    public SearchQueries(Connection conn) {
        this.conn = conn;
    }

    // Q1: upcoming performances near (lat, lon) within a distance (default provided),
    // ranked by distance OR by cheapest available ticket price (asc/desc).
    public void searchByLocation(/* lat, lon, distanceKm, rankBy */) {
        // TODO
    }

    // Q2: upcoming performances at venues in the same or adjacent postal codes.
    public void searchByPostalCode(/* postalCode */) {
        // TODO
    }

    // Q3: exact address match -> that venue's upcoming performances.
    public void searchByAddress(/* address */) {
        // TODO
    }

    // Q4: temporal refinement of Q1/Q2/Q3 — add a date range + minimum
    // available ticket count filter.
    public void searchWithDateRange(/* ..., startDate, endDate, minAvailable */) {
        // TODO
    }

    // Q5: general filtered search — city, segment/genre, date range, price
    // range on cheapest available ticket, min available count, reserved
    // seating vs GA — all combinable.
    public void filteredSearch(/* filters object/params */) {
        // TODO
    }

    // Q6: seat map summary for a performance — per section: tier, price,
    // available seats/remaining GA capacity, sold count, blocked count.
    public void seatMapSummary(/* performanceId */) {
        // TODO
    }

    // Q7: best available — q consecutive seats (by seat number) in the same
    // row with lowest total price, optionally under a budget.
    public void bestAvailable(/* performanceId, quantity, budget */) {
        // TODO
    }
}
