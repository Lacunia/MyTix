-- Drop tables in dependency order (children before parents)
-- so MySQL doesn't reject the drop due to an active foreign key.

DROP TABLE IF EXISTS Comments;
DROP TABLE IF EXISTS ResaleListings;
DROP TABLE IF EXISTS TicketOwnershipHistory;
DROP TABLE IF EXISTS Tickets;
DROP TABLE IF EXISTS Orders;
DROP TABLE IF EXISTS BlockedSeats;
DROP TABLE IF EXISTS PerformanceSectionAssignments;
DROP TABLE IF EXISTS PriceTiers;
DROP TABLE IF EXISTS Performances;
DROP TABLE IF EXISTS EventLineups;
DROP TABLE IF EXISTS Events;
DROP TABLE IF EXISTS Artists;
DROP TABLE IF EXISTS Seats;
DROP TABLE IF EXISTS SectionRows;
DROP TABLE IF EXISTS Sections;
DROP TABLE IF EXISTS Venues;
DROP TABLE IF EXISTS PaymentMethods;
DROP TABLE IF EXISTS Organizers;
DROP TABLE IF EXISTS Customers;
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Taxonomy;

DROP TRIGGER trg_section_venue_match
DROP TRIGGER trg_resale_price_cap
DROP TRIGGER trg_ticket_seat_consistency
