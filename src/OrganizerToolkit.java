import java.sql.*;
import java.math.BigDecimal;

/**
 * Pricing/tier-structure suggestions for a new performance, based on
 * comparable performances (same genre, similar venue capacity, same city,
 * recent dates — exact comparability rule is a design decision to document
 * in the report). Extra credit: estimate expected revenue change for a
 * suggested tier price change.
 */
public class OrganizerToolkit {

    private final Connection conn;

    public OrganizerToolkit(Connection conn) {
        this.conn = conn;
    }

    // Suggest a number of tiers and a capacity share per tier for a new
    // performance, based on comparable past performances.
    public void suggestTierStructure(int venueId, String genre) {
        String sql =
            "WITH venue_capacity AS ( " +
            "    SELECT SUM(CASE WHEN s.isReservedSeating " +
            "        THEN (SELECT COUNT(*) FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId WHERE r.sectionId=s.sectionId) " +
            "        ELSE s.standingCapacity END) capacity " +
            "    FROM Sections s WHERE s.venueId=? " +
            "), comparables AS ( " +
            "    SELECT p.performanceId, COUNT(DISTINCT pt.tierId) tierCount " +
            "    FROM Performances p " +
            "    JOIN Events e ON e.eventId=p.eventId " +
            "    JOIN Taxonomy tx ON tx.taxonomyId=e.taxonomyId " +
            "    JOIN Sections s ON s.venueId=p.venueId " +
            "    JOIN PriceTiers pt ON pt.performanceId=p.performanceId " +
            "    WHERE tx.genre=? AND p.dateTime<NOW() AND p.status='Scheduled' " +
            "    GROUP BY p.performanceId " +
            ") " +
            "SELECT ROUND(AVG(tierCount)) suggestedTierCount, " +
            "       (SELECT capacity FROM venue_capacity) targetCapacity, " +
            "       COUNT(*) comparables " +
            "FROM comparables";
        display(sql, venueId, genre);
    }

    // Suggest a price for each tier of a new performance. 
    // Comparability strategy: prefer performances of the same genre in the same city
    // (closest proxy for local market conditions); if none exist yet,
    // broaden to the same genre anywhere, so the tool still produces a
    // usable suggestion rather than silently returning nothing.
    public void suggestTierPrices(int venueId, String genre) {
        String base = "SELECT pt.tierName, ROUND(AVG(pt.price),2) suggestedPrice, ROUND(AVG(sa.availableSeatCount),0) averageRemainingCapacity, COUNT(*) comparableSections " +
            "FROM Performances p JOIN Events e ON e.eventId=p.eventId JOIN Taxonomy tx ON tx.taxonomyId=e.taxonomyId JOIN Venues v ON v.venueId=p.venueId " +
            "JOIN PriceTiers pt ON pt.performanceId=p.performanceId JOIN SectionAvailability sa ON sa.performanceId=p.performanceId " +
            "JOIN PerformanceSectionAssignments psa ON psa.performanceId=sa.performanceId AND psa.sectionId=sa.sectionId AND psa.tierId=pt.tierId " +
            "WHERE tx.genre=? AND p.dateTime<NOW() AND p.status='Scheduled' ";
        String cityScoped = base + "AND v.city=(SELECT city FROM Venues WHERE venueId=?) GROUP BY pt.tierName ORDER BY suggestedPrice";
        String genreOnly = base + "GROUP BY pt.tierName ORDER BY suggestedPrice";

        System.out.println("Tier price suggestions (same city + genre, past performances):");
        if (display(cityScoped, genre, venueId) == 0) {
            System.out.println("No comparable performances in this city yet -- broadening to same genre, any city:");
            if (display(genreOnly, genre) == 0) {
                System.out.println("No comparable historical performances found for genre '" + genre + "'. " +
                    "Try again once more performances of this genre have taken place, or set prices manually.");
            }
        }
    }

    // Extra credit: given a proposed price change for a tier, estimate the
    // expected change in revenue.
    public void estimateRevenueImpact(int tierId, BigDecimal proposedPrice) {
        if (proposedPrice == null || proposedPrice.signum() < 0) {
            throw new IllegalArgumentException("Proposed price must be non-negative.");
        }
        String sql =
            "SELECT pt.tierName, pt.price currentPrice, ? proposedPrice, " +
            "       SUM(sa.availableSeatCount + COALESCE(sold.soldCount,0)) sellableCapacity, " +
            "       COALESCE(SUM(sold.soldCount),0) historicalSold, " +
            "       ROUND(COALESCE(SUM(sold.soldCount),0)*pt.price,2) currentEstimatedRevenue, " +
            "       ROUND(LEAST(SUM(sa.availableSeatCount+COALESCE(sold.soldCount,0)), " +
            "                   COALESCE(SUM(sold.soldCount),0)*(pt.price/NULLIF(?,0)))*?,2) proposedEstimatedRevenue " +
            "FROM PriceTiers pt " +
            "JOIN PerformanceSectionAssignments psa ON psa.tierId=pt.tierId " +
            "JOIN SectionAvailability sa ON sa.performanceId=psa.performanceId AND sa.sectionId=psa.sectionId " +
            "LEFT JOIN ( " +
            "    SELECT performanceId, sectionId, COUNT(*) soldCount FROM Tickets WHERE status='Active' GROUP BY performanceId,sectionId " +
            ") sold ON sold.performanceId=psa.performanceId AND sold.sectionId=psa.sectionId " +
            "WHERE pt.tierId=? " +
            "GROUP BY pt.tierId, pt.tierName, pt.price";
        display(sql, proposedPrice, proposedPrice, proposedPrice, tierId);
    }

    // Returns the number of rows printed, so callers can detect an empty
    // result (e.g. to fall back to a broader comparability rule) instead of
    // silently printing nothing.
    private int display(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData m = rs.getMetaData();
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    for (int i = 1; i <= m.getColumnCount(); i++) {
                        System.out.print(m.getColumnLabel(i) + "=" + rs.getObject(i) + (i == m.getColumnCount() ? "\n" : ", "));
                    }
                }
                return rowCount;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Pricing recommendation failed.", e);
        }
    }
}
