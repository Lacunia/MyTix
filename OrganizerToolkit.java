import java.sql.Connection;

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
    public void suggestTierStructure(/* venueId, genre, ... */) {
        // TODO
    }

    // Suggest a price for each tier of a new performance.
    public void suggestTierPrices(/* performanceId or venueId+genre, tierStructure */) {
        // TODO
    }

    // Extra credit: given a proposed price change for a tier, estimate the
    // expected change in revenue.
    public void estimateRevenueImpact(/* tierId, proposedPrice */) {
        // TODO
    }
}
