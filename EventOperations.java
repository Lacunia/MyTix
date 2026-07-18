import java.sql.Connection;

/**
 * Organizer-side event/performance/pricing management.
 * Covers: create event, add performance, define price tiers, assign
 * sections to tiers, set resale cap, update tier price, block/unblock seats,
 * cancel a performance.
 */
public class EventOperations {

    private final Connection conn;

    public EventOperations(Connection conn) {
        this.conn = conn;
    }

    // Insert into Events (organizerId, taxonomyId, title, description, resalePriceCap).
    public void createEvent(/* organizerId, taxonomyId, title, description, resalePriceCap */) {
        // TODO
    }

    // Insert into Performances (eventId, venueId, dateTime).
    public void addPerformance(/* eventId, venueId, dateTime */) {
        // TODO
    }

    // Insert into PriceTiers for a performance (tierName, price) — one row per tier.
    public void definePriceTiers(/* performanceId, tiers */) {
        // TODO
    }

    // Insert into PerformanceSectionAssignments (sectionId, performanceId, tierId).
    // Every section of the venue must be assigned exactly one tier for this performance.
    public void assignSectionsToTiers(/* performanceId, sectionId -> tierId map */) {
        // TODO
    }

    // Update Events.resalePriceCap for an event.
    public void setResaleCap(/* eventId, newCapMultiplier */) {
        // TODO
    }

    // Update PriceTiers.price for a tier, but only if no ticket has been sold
    // in that tier for that performance yet (check Tickets before allowing the
    // update — see requirement: "otherwise the organizer should be informed").
    public boolean updateTierPrice(/* tierId, newPrice */) {
        // TODO — return false (and explain why) if a ticket already sold in this tier
        return false;
    }

    // Insert into BlockedSeats. Must first confirm the seat isn't already sold
    // for this performance (organizer cannot block a sold seat).
    public boolean blockSeat(/* performanceId, seatId, reason */) {
        // TODO
        return false;
    }

    // Delete the row from BlockedSeats for (performanceId, seatId).
    public void unblockSeat(/* performanceId, seatId */) {
        // TODO
    }

    // Set Performances.status = 'Cancelled', refund every sold ticket for it,
    // and record the cancellation (Tickets.status = 'Cancelled by organizer').
    public void cancelPerformance(/* performanceId */) {
        // TODO
    }
}
