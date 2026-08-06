import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

/**
 * R1-R9 analytics reports. All must be implemented in SQL and invoked from
 * Java (per project rules), except R9's noun-phrase extraction, which uses
 * Apache OpenNLP (tokenizer + POS tagger + chunker) on top of the raw
 * Comments.content pulled back from SQL, per the assignment's one allowed
 * exception to "everything in SQL".
 */
public class Reports {

    private final Connection conn;

    // Fields for R9
    private static final int TOP_PHRASE_COUNT = 10;
    private static final String MODELS_DIR = "models";

    // OpenNLP models are large (MB-scale) and stateless once loaded, so they
    // are loaded once per JVM rather than per Reports instance/report call.
    private static TokenizerME tokenizer;
    private static POSTaggerME posTagger;
    private static ChunkerME chunker;

    public Reports(Connection conn) {
        this.conn = conn;
    }

    private static synchronized void ensureNlpModelsLoaded() {
        if (tokenizer != null) return;
        try {
            tokenizer = new TokenizerME(loadModel(TokenizerModel.class, "en-token.bin"));
            posTagger = new POSTaggerME(loadModel(POSModel.class, "en-pos-maxent.bin"));
            chunker = new ChunkerME(loadModel(ChunkerModel.class, "en-chunker.bin"));
        } catch (IOException e) {
            throw new RuntimeException(
                "Unable to load OpenNLP models from '" + MODELS_DIR + "/'. " +
                "R9 requires en-token.bin, en-pos-maxent.bin and en-chunker.bin in that directory.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <M> M loadModel(Class<M> modelClass, String fileName) throws IOException {
        try (InputStream in = new FileInputStream(MODELS_DIR + "/" + fileName)) {
            return modelClass.getConstructor(InputStream.class).newInstance(in);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to construct " + modelClass.getSimpleName(), e);
        }
    }

    /**
     * R1 - report by city
     * startDate - start of the date range
     * endDate - end of the date range
     */
    public void ticketsAndRevenueByCity(LocalDate startDate, LocalDate endDate) {
        String query =
            "SELECT v.city, COUNT(t.ticketId) AS ticketsSold, COALESCE(SUM(t.price), 0) AS grossRevenue " +
            "FROM Venues v " +
            "LEFT JOIN Performances p ON p.venueId = v.venueId AND p.dateTime BETWEEN ? AND ? " +
            "LEFT JOIN Tickets t ON t.performanceId = p.performanceId AND t.status = 'Active' " +
            "GROUP BY v.city " +
            "ORDER BY grossRevenue DESC";
        
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setTimestamp(1, Timestamp.valueOf(startDate.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(endDate.atTime(23, 59, 59)));

            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-25s %12s %15s%n", "City", "Tickets Sold", "Gross Revenue");
                System.out.println("-".repeat(55));

                while (rs.next()) {
                    System.out.printf("%-25s %12d %15.2f%n",
                        rs.getString("city"),
                        rs.getInt("ticketsSold"),
                        rs.getDouble("grossRevenue"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * R1 - report by venue in a city
     * startDate - start of the date range
     * endDate - end of the date range
     * city - for reporting by venue in a city
     */
    public void ticketsAndRevenueByVenue(LocalDate startDate, LocalDate endDate, String city) {
        String query =
            "SELECT v.venueId, v.name AS venueName, COUNT(t.ticketId) AS ticketsSold, COALESCE(SUM(t.price), 0) AS grossRevenue " +
            "FROM Venues v " +
            "LEFT JOIN Performances p ON p.venueId = v.venueId AND p.dateTime BETWEEN ? AND ? " +
            "LEFT JOIN Tickets t ON t.performanceId = p.performanceId AND t.status = 'Active' " +
            "WHERE v.city = ? " +
            "GROUP BY v.venueId, v.name " +
            "ORDER BY grossRevenue DESC";
        
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setTimestamp(1, Timestamp.valueOf(startDate.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(endDate.atTime(23, 59, 59)));
            ps.setString(3, city);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-10s %-32s %12s %15s%n", "Venue Id", "Venue", "Tickets Sold", "Gross Revenue");
                System.out.println("-".repeat(72));

                while (rs.next()) {
                    System.out.printf("%-10d %-32s %12d %15.2f%n",
                        rs.getInt("venueId"),
                        rs.getString("venueName"),
                        rs.getInt("ticketsSold"),
                        rs.getDouble("grossRevenue"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // R2:
    public void eventCountsByTaxonomyAndLocation() {
        // Report per segment + genre
        String bySegmentGenre = 
            "SELECT segment, genre, " +
            "       COUNT(DISTINCT eventId) AS eventCount, " +
            "       COUNT(DISTINCT performanceId) AS performanceCount " +
            "FROM EventPerformanceLocations " +
            "GROUP BY segment, genre " +
            "ORDER BY segment, genre";

        // Report per country
        String byCountry =
            "SELECT country, " +
            "       COUNT(DISTINCT eventId) AS eventCount, " +
            "       COUNT(DISTINCT performanceId) AS performanceCount " +
            "FROM EventPerformanceLocations " +
            "GROUP BY country " +
            "ORDER BY country";

        // Report per country + city
        String byCountryCity =
            "SELECT country, city, " +
            "       COUNT(DISTINCT eventId) AS eventCount, " +
            "       COUNT(DISTINCT performanceId) AS performanceCount " +
            "FROM EventPerformanceLocations " +
            "GROUP BY country, city " +
            "ORDER BY country, city";

        // Report per country + city + venue
        String byCountryCityVenue =
            "SELECT country, city, venueId, venueName, " +
            "       COUNT(DISTINCT eventId) AS eventCount, " +
            "       COUNT(DISTINCT performanceId) AS performanceCount " +
            "FROM EventPerformanceLocations " +
            "GROUP BY country, city, venueId, venueName " +
            "ORDER BY country, city, venueName";
        
        // Using Statement instead of PreparedStatement b/c there is no runtime variable here
        try (Statement stmt = conn.createStatement()) {
            System.out.println("=== Event & Performance Count per Segment/Genre ===");
            System.out.printf("%-20s %-20s %10s %15s%n", "Segment", "Genre", "Events", "Performances");
            System.out.println("-".repeat(68));
            try (ResultSet rs = stmt.executeQuery(bySegmentGenre)) {
                while (rs.next()) {
                    System.out.printf("%-20s %-20s %10d %15d%n",
                        rs.getString("segment"),
                        rs.getString("genre"),
                        rs.getInt("eventCount"),
                        rs.getInt("performanceCount"));
                }
            }

            System.out.println("\n=== Event & Performance Count per Country ===");
            System.out.printf("%-20s %10s %15s%n", "Country", "Events", "Performances");
            System.out.println("-".repeat(47));
            try (ResultSet rs = stmt.executeQuery(byCountry)) {
                while (rs.next()) {
                    System.out.printf("%-20s %10d %15d%n",
                        rs.getString("country"),
                        rs.getInt("eventCount"),
                        rs.getInt("performanceCount"));
                }
            }

            System.out.println("\n=== Event & Performance Count per Country + City ===");
            System.out.printf("%-20s %-20s %10s %15s%n", "Country", "City", "Events", "Performances");
            System.out.println("-".repeat(68));
            try (ResultSet rs = stmt.executeQuery(byCountryCity)) {
                while (rs.next()) {
                    System.out.printf("%-20s %-20s %10d %15d%n",
                        rs.getString("country"),
                        rs.getString("city"),
                        rs.getInt("eventCount"),
                        rs.getInt("performanceCount"));
                }
            }

            System.out.println("\n=== Event & Performance Count per Country + City + Venue ===");
            System.out.printf("%-20s %-20s %-32s %10s %15s%n", "Country", "City", "VenueName", "Events", "Performances");
            System.out.println("-".repeat(101));
            try (ResultSet rs = stmt.executeQuery(byCountryCityVenue)) {
                while (rs.next()) {
                    System.out.printf("%-20s %-20s %-32s %10d %15d%n",
                        rs.getString("country"),
                        rs.getString("city"),
                        rs.getString("venueName"),
                        rs.getInt("eventCount"),
                        rs.getInt("performanceCount"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * R3 - rank by organizer gross revenue
     */
    public void rankOrganizersByRevenueOverall() {
        String query =
            "SELECT o.organizerId, u.name AS organizerName, COALESCE(SUM(t.price), 0) AS grossRevenue " +
            "FROM Organizers o " +
            "JOIN Users u ON u.userId = o.organizerId " +
            "LEFT JOIN Events e ON e.organizerId = o.organizerId " +
            "LEFT JOIN Performances p ON p.eventId = e.eventId " +
            "LEFT JOIN Tickets t ON t.performanceId = p.performanceId AND t.status = 'Active' " +
            "GROUP BY o.organizerId, u.name " +
            "ORDER BY grossRevenue DESC";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            System.out.printf("%-10s %-25s %15s%n", "OrgID", "Organizer", "Gross Revenue");
            System.out.println("-".repeat(55));

            while (rs.next()) {
                System.out.printf("%-10d %-25s %15.2f%n",
                    rs.getInt("organizerId"),
                    rs.getString("organizerName"),
                    rs.getDouble("grossRevenue"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * R3 - rank organizers by gross revenue within each country
     *      So, will report organizer ranking within each country.
     */
    public void rankOrganizersByRevenuePerCountry() {
        String query =
            "SELECT v.country, o.organizerId, u.name AS organizerName, SUM(t.price) AS grossRevenue " +
            "FROM Tickets t " +
            "JOIN Performances p ON p.performanceId = t.performanceId " +
            "JOIN Venues v ON v.venueId = p.venueId " +
            "JOIN Events e ON e.eventId = p.eventId " +
            "JOIN Organizers o ON o.organizerId = e.organizerId " +
            "JOIN Users u ON u.userId = o.organizerId " +
            "WHERE t.status = 'Active' " +
            "GROUP BY v.country, o.organizerId, u.name " +
            "ORDER BY v.country, grossRevenue DESC";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            System.out.printf("%-20s %-10s %-25s %15s%n", "Country", "OrgID", "Organizer", "Gross Revenue");
            System.out.println("-".repeat(75));

            while (rs.next()) {
                System.out.printf("%-20s %-10d %-25s %15.2f%n",
                    rs.getString("country"),
                    rs.getInt("organizerId"),
                    rs.getString("organizerName"),
                    rs.getDouble("grossRevenue"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * R3 - Refine the ranking down to organizers active in one specific city
     * city - the city to rank organizers in
     */
    public void rankOrganizersByRevenueByCity(String city) {
        String query = 
            "SELECT o.organizerId, u.name AS organizerName, SUM(t.price) AS grossRevenue " +
            "FROM Tickets t " +
            "JOIN Performances p ON p.performanceId = t.performanceId " +
            "JOIN Venues v ON v.venueId = p.venueId " +
            "JOIN Events e ON e.eventId = p.eventId " +
            "JOIN Organizers o ON o.organizerId = e.organizerId " +
            "JOIN Users u ON u.userId = o.organizerId " +
            "WHERE t.status = 'Active' AND v.city = ? " +
            "GROUP BY o.organizerId, u.name " +
            "ORDER BY grossRevenue DESC";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, city);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-10s %-25s %15s%n", "OrgID", "Organizer", "Gross Revenue");
                System.out.println("-".repeat(55));

                while (rs.next()) {
                    System.out.printf("%-10d %-25s %15.2f%n",
                        rs.getInt("organizerId"),
                        rs.getString("organizerName"),
                        rs.getDouble("grossRevenue"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // R4: per city, customers who purchased >= 10 tickets (all-time, per the
    // spec's "provided they purchased at least ten") and who, within the past
    // year, LISTED (not necessarily sold) more than half of those tickets for
    // resale. "Listed" means any ResaleListings row (Active, Sold, or
    // Withdrawn all count as having been listed), scoped to postedDate, not
    // to when the ticket was originally purchased.
    public void possibleScalpersByCity() {
        String sql =
            "SELECT v.city, c.customerId, u.name, " +
            "       COUNT(DISTINCT t.ticketId) purchased, " +
            "       COUNT(DISTINCT rl.ticketId) listedForResale, " +
            "       COUNT(DISTINCT rl.ticketId)/COUNT(DISTINCT t.ticketId)*100 resalePct " +
            "FROM Orders o " +
            "JOIN Tickets t ON t.orderId=o.orderId " +
            "JOIN Customers c ON c.customerId=o.customerId " +
            "JOIN Users u ON u.userId=c.customerId " +
            "JOIN Performances p ON p.performanceId=o.performanceId " +
            "JOIN Venues v ON v.venueId=p.venueId " +
            "LEFT JOIN ResaleListings rl ON rl.ticketId=t.ticketId AND rl.sellerId=c.customerId " +
            "  AND rl.postedDate >= DATE_SUB(NOW(), INTERVAL 1 YEAR) " +
            "GROUP BY v.city, c.customerId, u.name " +
            "HAVING COUNT(DISTINCT t.ticketId) >= 10 " +
            "  AND COUNT(DISTINCT rl.ticketId) > COUNT(DISTINCT t.ticketId)/2 " +
            "ORDER BY v.city, resalePct DESC";
        printQuery(sql);
    }

    // R5: rank customers by number of orders in a time period, and (separately)
    // by number of orders per city — only customers with >= 2 orders that year for the latter.
    public void rankCustomersByOrders(LocalDate startDate, LocalDate endDate) {
        String global = "SELECT u.name, COUNT(*) orders, DENSE_RANK() OVER (ORDER BY COUNT(*) DESC) ranking " +
            "FROM Orders o JOIN Users u ON u.userId=o.customerId WHERE o.purchaseTime >= ? AND o.purchaseTime < ? GROUP BY o.customerId,u.name ORDER BY ranking,u.name";
        String city = "SELECT v.city,u.name,COUNT(*) orders,DENSE_RANK() OVER(PARTITION BY v.city ORDER BY COUNT(*) DESC) ranking " +
            "FROM Orders o JOIN Users u ON u.userId=o.customerId JOIN Performances p ON p.performanceId=o.performanceId JOIN Venues v ON v.venueId=p.venueId " +
            "WHERE o.purchaseTime >= ? AND o.purchaseTime < ? GROUP BY v.city,o.customerId,u.name HAVING COUNT(*)>=2 ORDER BY v.city,ranking,u.name";
        printQuery(global, startDate, endDate.plusDays(1));
        printQuery(city, startDate, endDate.plusDays(1));
    }

    // R6: We also wish to report the customers with the largest number of cancelled tickets and the
    // organizers with the largest number of cancelled performances within a year

    // R6: customers with the most cancelled tickets, organizers with the most
    // cancelled performances, within a year.

    /**
     * R6:
     * year - the year to report in
     */
    public void mostCancellations(int year) {
        String topCancellingCustomers =
            "SELECT c.customerId, u.name AS customerName, COUNT(*) AS cancelledTicketCount " +
            "FROM Tickets t " +
            "JOIN Orders o ON o.orderId = t.orderId " +
            "JOIN Customers c ON c.customerId = o.customerId " +
            "JOIN Users u ON u.userId = c.customerId " +
            "WHERE t.status = 'Cancelled by customer' " +
            "  AND YEAR(t.cancelledAt) = ? " +
            "GROUP BY c.customerId, u.name " +
            "HAVING COUNT(*) = ( " +
            "    SELECT MAX(count) " +
            "    FROM ( " +
            "        SELECT COUNT(*) AS count " +
            "        FROM Tickets t2 JOIN Orders o2 ON o2.orderId = t2.orderId " +
            "        WHERE t2.status = 'Cancelled by customer' " +
            "          AND YEAR(t2.cancelledAt) = ? " +
            "        GROUP BY o2.customerId " +
            "    ) cancelCounts " +
            ")";

        String topCancellingOrganizers =
            "SELECT o.organizerId, u.name AS organizerName, COUNT(*) AS cancelledPerformanceCount " +
            "FROM Performances p " +
            "JOIN Events e ON e.eventId = p.eventId " +
            "JOIN Organizers o ON o.organizerId = e.organizerId " +
            "JOIN Users u ON u.userId = o.organizerId " +
            "WHERE p.status = 'Cancelled' " +
            "  AND YEAR(p.cancelledAt) = ? " +
            "GROUP BY o.organizerId, u.name " +
            "HAVING COUNT(*) = ( " +
            "    SELECT MAX(count) " +
            "    FROM ( " +
            "        SELECT COUNT(*) AS count " +
            "        FROM Performances p2 " +
            "        JOIN Events e2 ON e2.eventId = p2.eventId " +
            "        WHERE p2.status = 'Cancelled' " +
            "          AND YEAR(p2.cancelledAt) = ? " +
            "        GROUP BY e2.organizerId " +
            "    ) cancelCounts " +
            ")";

        // Execute and output most cancelling customers
        try (PreparedStatement customersPs = conn.prepareStatement(topCancellingCustomers)) {
            customersPs.setInt(1, year);
            customersPs.setInt(2, year);

            try (ResultSet rs = customersPs.executeQuery()) {
                System.out.println("=== Customers with the Most Cancelled Tickets (" + year + ") ===");
                System.out.printf("%-10s %-25s %20s%n", "CustID", "Customer", "Cancelled Tickets");
                System.out.println("-".repeat(60));

                while (rs.next()) {
                    System.out.printf("%-10d %-25s %20d%n",
                        rs.getInt("customerId"),
                        rs.getString("customerName"),
                        rs.getInt("cancelledTicketCount"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Execute and output most cancelling organizers
        try (PreparedStatement organizersPs = conn.prepareStatement(topCancellingOrganizers)) {
            organizersPs.setInt(1, year);
            organizersPs.setInt(2, year);

            try (ResultSet rs = organizersPs.executeQuery()) {
                System.out.println("\n=== Organizers with the Most Cancelled Performances (" + year + ") ===");
                System.out.printf("%-10s %-25s %25s%n", "OrgID", "Organizer", "Cancelled Performances");
                System.out.println("-".repeat(65));

                while (rs.next()) {
                    System.out.printf("%-10d %-25s %25d%n",
                        rs.getInt("organizerId"),
                        rs.getString("organizerName"),
                        rs.getInt("cancelledPerformanceCount"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // R7: sell-through rate per price tier of a performance (blocked seats
    // excluded from sellable capacity, GA capacity counts).
    public void sellThroughByTier(LocalDate month, String city) {
        String sql =
            "SELECT p.performanceId, p.name, v.city, pt.tierName, " +
            "       SUM(sa.availableSeatCount + COALESCE(sold.soldCount,0)) sellable, " +
            "       COALESCE(SUM(sold.soldCount),0) sold, " +
            "       CASE WHEN SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0))=0 THEN 0 " +
            "            ELSE COALESCE(SUM(sold.soldCount),0)/SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0)) END sellThrough " +
            "FROM Performances p " +
            "JOIN Venues v ON v.venueId=p.venueId " +
            "JOIN PerformanceSectionAssignments psa ON psa.performanceId=p.performanceId " +
            "JOIN PriceTiers pt ON pt.tierId=psa.tierId " +
            "JOIN SectionAvailability sa ON sa.performanceId=p.performanceId AND sa.sectionId=psa.sectionId " +
            "LEFT JOIN ( " +
            "    SELECT performanceId, sectionId, COUNT(*) soldCount FROM Tickets WHERE status='Active' GROUP BY performanceId,sectionId " +
            ") sold ON sold.performanceId=p.performanceId AND sold.sectionId=psa.sectionId " +
            "WHERE p.dateTime >= ? AND p.dateTime < ? " + (city == null ? "" : "AND v.city=? ") +
            "GROUP BY p.performanceId, p.name, v.city, pt.tierName " +
            "ORDER BY p.performanceId, pt.tierName";
        System.out.println("=== R7: Sell-through rate by price tier ===");
        if (city == null) {
            printQuery(sql, month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1));
        } else {
            printQuery(sql, month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1), city);
        }
    }

    // R7: sell-through rate per performance, summed across all of its tiers/sections.
    public void sellThroughByPerformance(LocalDate month, String city) {
        String sql =
            "SELECT p.performanceId, p.name, v.city, " +
            "       SUM(sa.availableSeatCount + COALESCE(sold.soldCount,0)) sellable, " +
            "       COALESCE(SUM(sold.soldCount),0) sold, " +
            "       CASE WHEN SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0))=0 THEN 0 " +
            "            ELSE COALESCE(SUM(sold.soldCount),0)/SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0)) END sellThrough " +
            "FROM Performances p " +
            "JOIN Venues v ON v.venueId=p.venueId " +
            "JOIN PerformanceSectionAssignments psa ON psa.performanceId=p.performanceId " +
            "JOIN SectionAvailability sa ON sa.performanceId=p.performanceId AND sa.sectionId=psa.sectionId " +
            "LEFT JOIN ( " +
            "    SELECT performanceId, sectionId, COUNT(*) soldCount FROM Tickets WHERE status='Active' GROUP BY performanceId,sectionId " +
            ") sold ON sold.performanceId=p.performanceId AND sold.sectionId=psa.sectionId " +
            "WHERE p.dateTime >= ? AND p.dateTime < ? " + (city == null ? "" : "AND v.city=? ") +
            "GROUP BY p.performanceId, p.name, v.city " +
            "ORDER BY p.performanceId";
        System.out.println("\n=== R7: Sell-through rate by performance ===");
        if (city == null) {
            printQuery(sql, month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1));
        } else {
            printQuery(sql, month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1), city);
        }
    }

    // R7: for a given month, by city, performances that sold out (100%) and
    // performances that sold less than a quarter of their sellable capacity.
    public void sellThroughExtremesByCityForMonth(LocalDate month) {
        String base =
            "SELECT p.performanceId, p.name, v.city, " +
            "       SUM(sa.availableSeatCount + COALESCE(sold.soldCount,0)) sellable, " +
            "       COALESCE(SUM(sold.soldCount),0) sold, " +
            "       CASE WHEN SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0))=0 THEN 0 " +
            "            ELSE COALESCE(SUM(sold.soldCount),0)/SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0)) END sellThrough " +
            "FROM Performances p " +
            "JOIN Venues v ON v.venueId=p.venueId " +
            "JOIN PerformanceSectionAssignments psa ON psa.performanceId=p.performanceId " +
            "JOIN SectionAvailability sa ON sa.performanceId=p.performanceId AND sa.sectionId=psa.sectionId " +
            "LEFT JOIN ( " +
            "    SELECT performanceId, sectionId, COUNT(*) soldCount FROM Tickets WHERE status='Active' GROUP BY performanceId,sectionId " +
            ") sold ON sold.performanceId=p.performanceId AND sold.sectionId=psa.sectionId " +
            "WHERE p.dateTime >= ? AND p.dateTime < ? " +
            "GROUP BY p.performanceId, p.name, v.city ";

        System.out.println("\n=== R7: Sold-out performances in " + month.getMonth() + " " + month.getYear() + ", by city ===");
        printQuery("SELECT city, performanceId, name, sellable, sold FROM (" + base + ") x WHERE sellThrough >= 1 ORDER BY city, performanceId",
            month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1));

        System.out.println("\n=== R7: Performances that sold < 25% of capacity in " + month.getMonth() + " " + month.getYear() + ", by city ===");
        printQuery("SELECT city, performanceId, name, sellable, sold, sellThrough FROM (" + base + ") x WHERE sellThrough < 0.25 ORDER BY city, performanceId",
            month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1));
    }

    // R8: per event — completed resales count, avg markup over face value,
    // fraction of listings priced exactly at cap; top 10 events by resale
    // volume in a period.
    public void resaleReport(LocalDate startDate, LocalDate endDate) {
        String metrics =
            "WITH completed AS ( " +
            "  SELECT p.eventId, COUNT(*) completedResales, AVG(h.transactionPrice-t.price) avgMarkup " +
            "  FROM TicketOwnershipHistory h JOIN Tickets t ON t.ticketId=h.ticketId " +
            "  JOIN Performances p ON p.performanceId=t.performanceId " +
            "  WHERE h.transactionDate>=? AND h.transactionDate<? GROUP BY p.eventId " +
            "), listings AS ( " +
            "  SELECT p.eventId, 100*AVG(CASE WHEN ABS(rl.resalePrice-(t.price*e.resalePriceCap))<0.005 THEN 1 ELSE 0 END) exactCapPct " +
            "  FROM ResaleListings rl JOIN Tickets t ON t.ticketId=rl.ticketId " +
            "  JOIN Performances p ON p.performanceId=t.performanceId JOIN Events e ON e.eventId=p.eventId " +
            "  WHERE rl.postedDate>=? AND rl.postedDate<? GROUP BY p.eventId " +
            ") SELECT e.eventId,e.title,COALESCE(c.completedResales,0) completedResales, " +
            "COALESCE(c.avgMarkup,0) avgMarkup,COALESCE(l.exactCapPct,0) exactCapPct " +
            "FROM Events e LEFT JOIN completed c ON c.eventId=e.eventId LEFT JOIN listings l ON l.eventId=e.eventId ";
        LocalDate endExclusive = endDate.plusDays(1);
        System.out.println("=== R8: Resale metrics for every event ===");
        printQuery(metrics + "ORDER BY e.eventId", startDate, endExclusive, startDate, endExclusive);
        System.out.println("=== R8: Top 10 events by completed resale volume ===");
        printQuery(metrics + "ORDER BY completedResales DESC, e.eventId LIMIT 10", startDate, endExclusive, startDate, endExclusive);
    }

    private void printQuery(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    System.out.print(meta.getColumnLabel(i) + (i == meta.getColumnCount() ? "\n" : " | "));
                }
                while (rs.next()) {
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        System.out.print(rs.getObject(i) + (i == meta.getColumnCount() ? "\n" : " | "));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Report failed.", e);
        }
    }

    // R9: most popular noun phrases per event, derived from Comments.content
    // (SQL pulls the raw text; a Java NLP library does the phrase extraction).

    /**
     * R9
     */
    public void topNounPhrasesByEvent() {
        ensureNlpModelsLoaded();

        String query =
            "SELECT e.eventId, e.title, c.content " +
            "FROM Comments c " +
            "JOIN Performances p ON p.performanceId = c.performanceId " +
            "JOIN Events e ON e.eventId = p.eventId " +
            "ORDER BY e.eventId";

        Map<Integer, String> eventTitles = new LinkedHashMap<>();
        Map<Integer, StringBuilder> eventComments = new HashMap<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                int eventId = rs.getInt("eventId");
                eventTitles.putIfAbsent(eventId, rs.getString("title"));
                // Append this comment to the string builder for this event
                // so that all comments for this event goes into this string builder
                eventComments.computeIfAbsent(eventId, k -> new StringBuilder())
                    .append(rs.getString("content")).append(" ");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // For each event
        for (Map.Entry<Integer, String> entry : eventTitles.entrySet()) {
            int eventId = entry.getKey();
            String title = entry.getValue();
            String allComments = eventComments.get(eventId).toString();

            // Count frequencies for each candidate phrases
            Map<String, Integer> frequencies = new HashMap<>();
            for (String phrase : extractNounPhrases(allComments)) {
                // First time seeing the phrase --> inserts { "some phrase" : 1 }
                // Phrase exist in map --> look up current count, adds 1 to it, and
                //                         update map to { "some phrase" : <new count> }
                frequencies.merge(phrase, 1, Integer::sum);
            }

            // Sort by highest frequency, breaking ties alphabetically so the
            // top-N cutoff is deterministic across runs, then limit to 10
            List<Map.Entry<String, Integer>> topPhrases = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_PHRASE_COUNT)
                .collect(Collectors.toList());

            // Display top phrases for this event
            System.out.println("\n=== Event #" + eventId + " - " + title + " ===");
            System.out.printf("%-35s %10s%n", "Phrase", "Count");
            System.out.println("-".repeat(46));
            for (Map.Entry<String, Integer> phraseEntry : topPhrases) {
                System.out.printf("%-35s %10d%n", phraseEntry.getKey(), phraseEntry.getValue());
            }
        }
    }

    // A handful of pronouns/determiners that OpenNLP's chunker sometimes tags
    // as their own one-word NP (e.g. "it", "this") but that aren't meaningful
    // as word-cloud phrases.
    private static final Set<String> NP_STOPWORDS = new java.util.HashSet<>(Arrays.asList(
        "it", "its", "i", "we", "you", "he", "she", "they", "them", "this", "that",
        "these", "those", "one", "someone", "something", "everyone", "everything"
    ));

    /**
     * Extracts genuine noun phrases from a block of comment text using
     * OpenNLP: split into sentences, tokenize, POS-tag, then run the chunker
     * to identify NP (noun phrase) chunks. Only the chunker's NP spans are
     * used as candidates.
     */
    private static List<String> extractNounPhrases(String text) {
        List<String> candidates = new ArrayList<>();

        // Simple sentence split
        // -- phrases must not span across sentence boundaries.
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;

            String[] tokens = tokenizer.tokenize(sentence);
            if (tokens.length == 0) continue;
            String[] tags = posTagger.tag(tokens);
            Span[] chunks = chunker.chunkAsSpans(tokens, tags);

            for (Span chunk : chunks) {
                if (!"NP".equals(chunk.getType())) continue;

                StringBuilder phrase = new StringBuilder();
                boolean hasNoun = false;
                for (int i = chunk.getStart(); i < chunk.getEnd(); i++) {
                    if (tags[i].startsWith("NN")) {
                        hasNoun = true;
                    }
                    if (phrase.length() > 0) {
                        phrase.append(' ');
                    }
                    phrase.append(tokens[i].toLowerCase());
                }

                String phraseText = phrase.toString().replaceAll("[^a-z0-9' ]", "").trim();
                if (phraseText.isEmpty() || !hasNoun) continue;
                if (NP_STOPWORDS.contains(phraseText)) continue;

                candidates.add(phraseText);
            }
        }
        return candidates;
    }
}
