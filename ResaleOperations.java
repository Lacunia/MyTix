import java.sql.Connection;

/**
 * Resale marketplace: list a ticket, withdraw a listing, buy a listing.
 * The price cap check is enforced in the DB by trg_resale_price_cap in
 * schema.sql, but validate client-side too so the user gets a clear message
 * instead of a raw SQL error.
 */
public class ResaleOperations {

    private final Connection conn;

    public ResaleOperations(Connection conn) {
        this.conn = conn;
    }

    // Insert into ResaleListings. Ticket must be owned by this customer and Active.
    public boolean listTicketForResale(/* ticketId, sellerId, askingPrice */) {
        // TODO
        return false;
    }

    // Set ResaleListings.status = 'Withdrawn'. Only before it's sold.
    public void withdrawListing(/* listingId, sellerId */) {
        // TODO
    }

    // Transfer ownership: update Tickets.currentOwnerId, insert a row into
    // TicketOwnershipHistory, set ResaleListings.status = 'Sold'.
    public boolean purchaseListing(/* listingId, buyerId, paymentId */) {
        // TODO
        return false;
    }
}
