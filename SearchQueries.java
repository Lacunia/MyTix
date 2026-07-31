import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    public void searchByPostalCode(String postalCode) {
        String normalized = postalCode.replaceAll("\\s+", "").toUpperCase();
        if (normalized.isBlank()) throw new IllegalArgumentException("Postal code is required.");
        String prefix = normalized.substring(0, Math.min(3, normalized.length()));
        String extra = " AND (REPLACE(UPPER(v.postalCode), ' ', '') = ? OR EXISTS (" +
            "SELECT 1 FROM PostalCodeAdjacency pa WHERE pa.postalPrefix = ? " +
            "AND pa.adjacentPrefix = LEFT(REPLACE(UPPER(v.postalCode), ' ', ''), 3)))";
        runFiltered(extra, List.of(normalized, prefix), new PerformanceFilter());
    }

    // Q3: exact address match -> that venue's upcoming performances.
    public void searchByAddress(String address) {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("Address is required.");
        runFiltered(" AND v.address = ?", List.of(address.trim()), new PerformanceFilter());
    }

    // Q4: temporal refinement of Q1/Q2/Q3 — add a date range + minimum
    // available ticket count filter.
    public void searchWithDateRange(LocalDateTime start, LocalDateTime end, int minAvailable) {
        PerformanceFilter filter = new PerformanceFilter();
        filter.start = start; filter.end = end; filter.minAvailable = minAvailable;
        filteredSearch(filter);
    }

    // Q5: general filtered search — city, segment/genre, date range, price
    // range on cheapest available ticket, min available count, reserved
    // seating vs GA — all combinable.
    public void filteredSearch(PerformanceFilter filter) {
        if (filter == null) filter = new PerformanceFilter();
        runFiltered("", List.of(), filter);
    }

    /** Optional Q5 filters; any non-null property is combined with the others. */
    public static final class PerformanceFilter {
        public String segment, genre, city;
        public LocalDateTime start, end;
        public Double minPrice, maxPrice;
        public Integer minAvailable;
        public Boolean reservedSeating;
    }

    private void runFiltered(String extraWhere, List<Object> extraParameters, PerformanceFilter f) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.performanceId, p.name, e.title, v.name AS venue, v.city, p.dateTime, " +
            "MIN(pt.price) AS cheapestPrice, SUM(sa.availableSeatCount) AS available " +
            "FROM Performances p JOIN Events e ON e.eventId=p.eventId JOIN Taxonomy tx ON tx.taxonomyId=e.taxonomyId " +
            "JOIN Venues v ON v.venueId=p.venueId JOIN SectionAvailability sa ON sa.performanceId=p.performanceId " +
            "JOIN PerformanceSectionAssignments psa ON psa.performanceId=sa.performanceId AND psa.sectionId=sa.sectionId " +
            "JOIN PriceTiers pt ON pt.tierId=psa.tierId WHERE p.status='Scheduled' AND p.dateTime > NOW()");
        List<Object> params = new ArrayList<>(extraParameters);
        sql.append(extraWhere);
        if (f.segment != null) { sql.append(" AND tx.segment = ?"); params.add(f.segment); }
        if (f.genre != null) { sql.append(" AND tx.genre = ?"); params.add(f.genre); }
        if (f.city != null) { sql.append(" AND v.city = ?"); params.add(f.city); }
        if (f.start != null) { sql.append(" AND p.dateTime >= ?"); params.add(f.start); }
        if (f.end != null) { sql.append(" AND p.dateTime < ?"); params.add(f.end); }
        if (f.reservedSeating != null) { sql.append(" AND EXISTS (SELECT 1 FROM Sections s WHERE s.venueId=v.venueId AND s.isReservedSeating=?)"); params.add(f.reservedSeating); }
        sql.append(" GROUP BY p.performanceId, p.name, e.title, v.name, v.city, p.dateTime");
        if (f.minAvailable != null) { sql.append(" HAVING SUM(sa.availableSeatCount) >= ?"); params.add(f.minAvailable); }
        if (f.minPrice != null) { sql.append(f.minAvailable == null ? " HAVING" : " AND").append(" MIN(pt.price) >= ?"); params.add(f.minPrice); }
        if (f.maxPrice != null) { sql.append((f.minAvailable == null && f.minPrice == null) ? " HAVING" : " AND").append(" MIN(pt.price) <= ?"); params.add(f.maxPrice); }
        sql.append(" ORDER BY p.dateTime, cheapestPrice");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs=ps.executeQuery()) {
                System.out.printf("%-5s %-28s %-20s %-18s %-20s %10s %10s%n", "ID", "Performance", "Venue", "City", "Date", "From $", "Available");
                while(rs.next()) System.out.printf("%-5d %-28s %-20s %-18s %-20s %10.2f %10d%n", rs.getInt(1),rs.getString(2),rs.getString(4),rs.getString(5),rs.getTimestamp(6),rs.getDouble(7),rs.getInt(8));
            }
        } catch(SQLException e) { throw new RuntimeException("Search failed.", e); }
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
