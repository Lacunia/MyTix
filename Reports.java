import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
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

/**
 * R1-R9 analytics reports. All must be implemented in SQL and invoked from
 * Java (per project rules), except R9's noun-phrase extraction which may use
 * a Java text-processing library on top of the raw Comments.content pulled
 * back from SQL.
 */
public class Reports {

    private final Connection conn;

    // Fields for R9
    private static final int TOP_PHRASE_COUNT = 10;
    // Common English function words to strip out before extracting phrase
    // candidates.
    private static final Set<String> STOPWORDS = new java.util.HashSet<>(Arrays.asList(
        "a", "an", "the", "and", "or", "but", "so", "if", "of", "in", "on", "at",
        "to", "for", "with", "from", "by", "as", "about", "into", "over", "after",
        "before", "between", "through", "during", "this", "that", "these", "those",
        "it", "its", "it's", "i", "we", "you", "he", "she", "they", "them", "his",
        "her", "their", "our", "your", "my", "me", "us", "is", "was", "were", "are",
        "be", "been", "being", "am", "will", "would", "could", "should", "can",
        "do", "does", "did", "have", "has", "had", "not", "no", "very", "really",
        "just", "there", "here", "up", "down", "out", "all", "some", "one", "also",
        "too", "then", "than", "what", "who", "which", "when", "where", "how"
    ));

    public Reports(Connection conn) {
        this.conn = conn;
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
                System.out.printf("%-10s %-25s %12s %15s%n", "Venue Id", "Venue", "Tickets Sold", "Gross Revenue");
                System.out.println("-".repeat(67));

                while (rs.next()) {
                    System.out.printf("%-10d %-25s %12d %15.2f%n",
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
            System.out.println("-".repeat(65));
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
            System.out.println("-".repeat(45));
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
            System.out.println("-".repeat(65));
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
            System.out.printf("%-20s %-20s %-25s %10s %15s%n", "Country", "City", "VenueName", "Events", "Performances");
            System.out.println("-".repeat(90));
            try (ResultSet rs = stmt.executeQuery(byCountryCityVenue)) {
                while (rs.next()) {
                    System.out.printf("%-20s %-20s %-25s %10d %15d%n",
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

    // R4: per city, customers who — in the past year — listed for resale more
    // than half of the tickets they purchased, having purchased >= 10 (scalper flag).
    public void possibleScalpersByCity() {
        // TODO
    }

    // R5: rank customers by number of orders in a time period, and (separately)
    // by number of orders per city — only customers with >= 2 orders that year for the latter.
    public void rankCustomersByOrders(/* startDate, endDate */) {
        // TODO
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
            "JOIN Customers c ON c.customerId = t.currentOwnerId " +
            "JOIN Users u ON u.userId = c.customerId " +
            "WHERE t.status = 'Cancelled by customer' " +
            "  AND YEAR(t.cancelledAt) = ? " +
            "GROUP BY c.customerId, u.name " +
            "HAVING COUNT(*) = ( " +
            "    SELECT MAX(count) " +
            "    FROM ( " +
            "        SELECT COUNT(*) AS count " +
            "        FROM Tickets t2 " +
            "        WHERE t2.status = 'Cancelled by customer' " +
            "          AND YEAR(t2.cancelledAt) = ? " +
            "        GROUP BY t2.currentOwnerId " +
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

    // R7: sell-through rate per performance and per price tier (blocked seats
    // excluded from sellable capacity, GA capacity counts). Also: for a given
    // month, by city, performances that sold out vs sold < 25%.
    public void sellThroughReport(/* month, city (optional) */) {
        // TODO
    }

    // R8: per event — completed resales count, avg markup over face value,
    // fraction of listings priced exactly at cap; top 10 events by resale
    // volume in a period.
    public void resaleReport(/* startDate, endDate */) {
        // TODO
    }

    // R9: most popular noun phrases per event, derived from Comments.content
    // (SQL pulls the raw text; a Java NLP library does the phrase extraction).

    /**
     * R9
     */
    public void topNounPhrasesByEvent() {
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
            for (String phrase : extractPhraseCandidates(allComments)) {
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
            System.out.printf("%-30s %10s%n", "Phrase", "Count");
            System.out.println("-".repeat(42));
            for (Map.Entry<String, Integer> phraseEntry : topPhrases) {
                System.out.printf("%-30s %10d%n", phraseEntry.getKey(), phraseEntry.getValue());
            }
        }
    }

    /**
     * Extracts noun-phrase candidates from a block of comment text: splits on
     * sentence punctuation first (so phrases never span two sentences), then
     * within each sentence, groups consecutive non-stopword words together and
     * treats each such run as possible phrase material.
     */
    private static List<String> extractPhraseCandidates(String text) {
        List<String> candidates = new ArrayList<>();

        // Split on common sentence punctuations
        String[] sentences = text.toLowerCase().split("[.,!?;:]+");

        for (String sentence : sentences) {
            // Strip remaining punctuation and split into individual words
            String[] tokens = sentence.split("[^a-zA-Z']+");
            List<String> run = new ArrayList<>();

            for (String token : tokens) {
                if (token.isEmpty()) continue;

                if (STOPWORDS.contains(token)) {
                    // When hitting a stopword, we end the current run of content
                    // words and flush (add) what we've accumulated so far as candidates
                    flushRun(run, candidates);
                    run.clear();
                } else {
                    // If not a stopword - keep accumulating into current run
                    run.add(token);
                }
            }

            // Sentence ended - flush 
            flushRun(run, candidates);
        }
        return candidates;
    }

    /**
     * Turns one run of consecutive non-stopword words into phrase candidates:
     * the run itself (capped at 4 words), every 2-word sub-phrase within it,
     * and each individual word -- so both short common phrases and single
     * frequent nouns can appear in frequent count.
     */
    private static void flushRun(List<String> run, List<String> candidates) {
        if (run.isEmpty()) return;

        // Add the whole run as one phrase, capped at 4 words to avoid long/unhelpful phrases
        if (run.size() > 2) {
            int len = Math.min(run.size(), 4);
            candidates.add(String.join(" ", run.subList(0, len)));
        }

        // Add every adjacent pair within the run (e.g. "sound quality")
        for (int i = 0; i + 1 < run.size(); i++) {
            candidates.add(run.get(i) + " " + run.get(i + 1));
        }

        // Add every individual words
        candidates.addAll(run);
    }
}
