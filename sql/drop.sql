-- Drop the complete MyTix schema. This file is safe to run on an empty schema.

DROP VIEW IF EXISTS PerformanceCheapestPrice;
DROP VIEW IF EXISTS SectionAvailability;
DROP VIEW IF EXISTS AvailableSeatsByPerformance;
DROP VIEW IF EXISTS EventPerformanceLocations;

DROP TABLE IF EXISTS Refunds;
DROP TABLE IF EXISTS PostalCodeAdjacency;
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

DROP TRIGGER IF EXISTS trg_users_minimum_age_insert;
DROP TRIGGER IF EXISTS trg_users_minimum_age_update;
DROP TRIGGER IF EXISTS trg_section_venue_match;
DROP TRIGGER IF EXISTS trg_resale_price_cap;
DROP TRIGGER IF EXISTS trg_one_active_listing_per_ticket;
DROP TRIGGER IF EXISTS trg_ticket_seat_consistency;
DROP TRIGGER IF EXISTS trg_ticket_section_assigned;
DROP TRIGGER IF EXISTS trg_ga_capacity_check;
DROP TRIGGER IF EXISTS trg_order_payment_owner;
