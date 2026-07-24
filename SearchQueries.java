import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Q1-Q7 search/browse queries. Each method should build and run one SQL
 * query (joins/CTEs/window functions as needed) and return/print the result
 * set — keep the SQL itself in these methods, not scattered in the UI class.
 */
public class SearchQueries {

    // Default distance for Q1
    private static final double DEFAULT_RADIUS_KM = 25.0;

    private final Connection conn;

    public SearchQueries(Connection conn) {
        this.conn = conn;
    }

    // Helper function to get the default radius
    public static double getDefaultRadiusKm() {
        return DEFAULT_RADIUS_KM;
    }

    /**
     * Q1:
     * lat, lon - target location's latitude and longitude
     * radiusKm - include performances at venues within this many km
     * rankBy - "price" or "distance", indicating how the results should be ranked/ordered
     * direction - "asc" or "desc", indicating in which direction to order the results
     */
    public void searchByLocation(double lat, double lon, double radiusKm, String rankBy, String direction) {
        String orderDirection = direction.equalsIgnoreCase("desc") ? "DESC" : "ASC";
        String orderClause = rankBy.equals("price")
            ? "cheapestPrice " + orderDirection
            : "distanceKm " + orderDirection;

        /**
         * Note: to calculate distance and radius, we use the MySQL built-in function ST_Distance_Sphere
         * Reference: https://dev.mysql.com/doc/refman/8.4/en/spatial-convenience-functions.html
         */
        String query =
            "SELECT * " +
            "FROM ( " +
            "    SELECT p.performanceId, p.name AS performanceName, v.name AS venueName, " +
            "           p.dateTime AS performanceTime, cp.cheapestPrice, " +
            "           ST_Distance_Sphere(POINT(v.longitude, v.latitude), POINT(?, ?)) / 1000 AS distanceKm " +
            "    FROM Performances p " +
            "    JOIN Venues v ON v.venueId = p.venueId " +
            "    JOIN PerformanceCheapestPrice cp ON cp.performanceId = p.performanceId " +
            "    WHERE p.status = 'Scheduled' AND p.dateTime > NOW() " +
            ") x " +
            "WHERE distanceKm <= ? " +
            "ORDER BY " + orderClause;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, lon);
            ps.setDouble(2, lat);
            ps.setDouble(3, radiusKm);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf(
                    "%-6s %-30s %-25s %-25s %10s %10s%n", 
                    "ID", "Performance", "Venue", "Date/Time", "Dist(km)", "From $"
                );
                System.out.println("-".repeat(115));

                while (rs.next()) {
                    System.out.printf("%-6d %-30s %-25s %-25s %10.2f %10.2f%n",
                        rs.getInt("performanceId"),
                        rs.getString("performanceName"),
                        rs.getString("venueName"),
                        rs.getTimestamp("performanceTime").toString(),
                        rs.getDouble("distanceKm"),
                        rs.getDouble("cheapestPrice")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * Q6:
     * performanceId - the performance whose seat map to summarize
     */
    public void seatMapSummary(int performanceId) {
        String query = 
            "SELECT s.sectionName, pt.tierName, pt.price, s.isReservedSeating, " +
            "       sa.availableSeatCount AS available, " +
                    // COALESCE - return the first non-null value (from left to right)
            "       COALESCE(soldT.soldCount, 0) AS sold, " +
            "       COALESCE(blockedT.blockedCount, 0) AS blocked " +
            "FROM SectionAvailability sa " +
            "JOIN Sections s ON s.sectionId = sa.sectionId " + 
            "JOIN PerformanceSectionAssignments psa " + 
            "    ON psa.performanceId = sa.performanceId AND psa.sectionId = sa.sectionId " + 
            "JOIN PriceTiers pt ON pt.tierId = psa.tierId " +
            "LEFT JOIN ( " +
            "    SELECT sectionId, COUNT(*) AS soldCount " +
            "    FROM Tickets " +
            "    WHERE performanceId = ? AND status = 'Active' " +
            "    GROUP BY sectionId " +
            ") soldT ON soldT.sectionId = sa.sectionId " + 
            "LEFT JOIN ( " + 
            "    SELECT r.sectionId, COUNT(*) AS blockedCount " +
            "    FROM BlockedSeats bs " +
            "    JOIN Seats st ON st.seatId = bs.seatId " + 
            "    JOIN SectionRows r ON r.rowId = st.rowId " +
            "    WHERE bs.performanceId = ? " +
            "    GROUP BY r.sectionId " +
            ") blockedT ON blockedT.sectionId = sa.sectionId " +
            "WHERE psa.performanceId = ? " +
            "ORDER BY s.sectionName"; 
        
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, performanceId);
            ps.setInt(2, performanceId);
            ps.setInt(3, performanceId);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf(
                    "%-15s %-10s %-10s %-18s %10s %10s %10s%n", 
                    "Section", "Tier", "Price $", "Reserved Seating", "Available", "Sold", "Blocked"
                );
                System.out.println("-".repeat(91));

                while (rs.next()) {
                    System.out.printf("%-15s %-10s %-10.2f %-18s %10d %10d %10d%n",
                        rs.getString("sectionName"),
                        rs.getString("tierName"),
                        rs.getDouble("price"),
                        rs.getBoolean("isReservedSeating") ? "Yes" : "No",
                        rs.getInt("available"),
                        rs.getInt("sold"),
                        rs.getInt("blocked")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Q7: best available — q consecutive seats (by seat number) in the same
    // row with lowest total price, optionally under a budget.
    /**
     * Q7:
     * performanceId - the target performance
     * quantity - desired number of tickets
     * budget - optional budget
     */
    public void bestAvailable(int performanceId, int quantity, Double budget) {
        String findBestStartQuery =
            "SELECT s1.rowId, s1.rowName, s1.sectionName, s1.seatNumber AS startSeatNumber, " +
            "       s1.price, (s1.price * ?) AS totalPrice " +
            "FROM AvailableSeatsByPerformance s1 " +
            "WHERE s1.performanceId = ? " +
               // See if the # of consecutive seats starting at startSeatNumber has enough seats
            "  AND ( " +
            "      SELECT COUNT(*) " +
            "      FROM AvailableSeatsByPerformance s2 " +
            "      WHERE s2.performanceId = s1.performanceId " +
            "        AND s2.rowId = s1.rowId " +
            "        AND s2.seatNumber BETWEEN s1.seatNumber AND (s1.seatNumber + ? - 1) " +
            "  ) = ? " +
            (budget != null ? "  AND (s1.price * ?) <= ? " : "") +
            "ORDER BY totalPrice ASC " +
            "LIMIT 1";  // Only want the cheapest start seat (to a consecutive seat block of 'quantity' seats)
        
        // Later fetch the consecutive seat block that fit the requirements
        String fetchSeatsQuery =
            "SELECT seatId, seatNumber, rowName, sectionName, price " +
            "FROM AvailableSeatsByPerformance " +
            "WHERE rowId = ? AND seatNumber BETWEEN ? AND ? " +
            "ORDER BY seatNumber";
        
        // First, find the best starting seat (of a consecutive block)
        try (PreparedStatement bestPs = conn.prepareStatement(findBestStartQuery)) {
            bestPs.setInt(1, quantity);       // totalPrice = price * quantity
            bestPs.setInt(2, performanceId);
            bestPs.setInt(3, quantity);        // BETWEEN s1.seatNumber AND s1.seatNumber + quantity - 1
            bestPs.setInt(4, quantity);        // COUNT(*) must equal quantity
            if (budget != null) {
                bestPs.setInt(5, quantity);    // budget check: price * quantity
                bestPs.setDouble(6, budget);   // <= budget
            }

            int rowId;
            int startSeatNumber;
            double totalPrice;

            try (ResultSet rs = bestPs.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No " + quantity + " consecutive seats available"
                        + (budget != null ? " within budget $" + budget : "") + ".");
                    return;
                }
                rowId = rs.getInt("rowId");
                startSeatNumber = rs.getInt("startSeatNumber");
                totalPrice = rs.getDouble("totalPrice");
            }

            // Then, use the result from above, to get the consecutive seats
            try (PreparedStatement seatsPs = conn.prepareStatement(fetchSeatsQuery)) {
                seatsPs.setInt(1, rowId);
                seatsPs.setInt(2, startSeatNumber);
                seatsPs.setInt(3, startSeatNumber + quantity - 1);

                try (ResultSet rs = seatsPs.executeQuery()) {
                    System.out.printf("%-8s %-8s %-15s %-15s %10s%n",
                        "SeatId", "Seat#", "Row", "Section", "Price");
                    System.out.println("-".repeat(60));

                    // Print out the consecutive seats (1 per row)
                    while (rs.next()) {
                        System.out.printf("%-8d %-8d %-15s %-15s %10.2f%n",
                            rs.getInt("seatId"),
                            rs.getInt("seatNumber"),
                            rs.getString("rowName"),
                            rs.getString("sectionName"),
                            rs.getDouble("price"));
                    }
                    System.out.printf("%nTotal for %d seats: $%.2f%n", quantity, totalPrice);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
