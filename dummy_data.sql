-- Sample data for a freshly created MyTix database.
-- Run schema.sql first, then run this file once. It does not delete existing data.

-- Users, account subtypes, and payment methods
INSERT INTO Users (userId, name, email, address, dateOfBirth, accountType) VALUES
    (1, 'Alice Carter', 'alice.carter@example.com', '10 King St W, Toronto, ON', '1995-04-12', 'Customer'),
    (2, 'Ben Morris', 'ben.morris@example.com', '44 Queen St E, Toronto, ON', '1992-08-21', 'Customer'),
    (3, 'Chloe Nguyen', 'chloe.nguyen@example.com', '90 Main St, Ottawa, ON', '1998-01-17', 'Customer'),
    (4, 'Northstar Live', 'events@northstarlive.example.com', '100 Front St W, Toronto, ON', '1985-06-30', 'Organizer'),
    (5, 'Capital Arts Group', 'bookings@capitalarts.example.com', '1 Elgin St, Ottawa, ON', '1980-11-05', 'Organizer');

INSERT INTO Customers (customerId) VALUES (1), (2), (3);
INSERT INTO Organizers (organizerId) VALUES (4), (5);

INSERT INTO PaymentMethods (paymentId, customerId, cardholderName, cardNumber, expiryDate) VALUES
    (1, 1, 'Alice Carter', '4111111111111111', '12/28'),
    (2, 2, 'Ben Morris', '5555555555554444', '06/29'),
    (3, 3, 'Chloe Nguyen', '4000000000000002', '09/27');

-- Event categories
INSERT INTO Taxonomy (taxonomyId, segment, genre) VALUES
    (1, 'Music', 'Pop'),
    (2, 'Music', 'Rock'),
    (3, 'Sports', 'Hockey'),
    (4, 'Theatre', 'Musical'),
    (5, 'Comedy', 'Stand-up');

-- Venues and their physical layouts
INSERT INTO Venues (venueId, name, latitude, longitude, address, postalCode, city, country) VALUES
    (1, 'Harbour Centre', 43.642600, -79.387100, '100 Harbour St', 'M5J 1E6', 'Toronto', 'Canada'),
    (2, 'Capital Hall', 45.423600, -75.700900, '1 Elgin St', 'K1P 5W1', 'Ottawa', 'Canada');

INSERT INTO Sections (sectionId, venueId, sectionName, isReservedSeating, standingCapacity) VALUES
    (1, 1, 'Floor A', TRUE, NULL),
    (2, 1, 'Balcony', TRUE, NULL),
    (3, 1, 'General Admission', FALSE, 200),
    (4, 2, 'Orchestra', TRUE, NULL),
    (5, 2, 'Open Floor', FALSE, 150);

INSERT INTO SectionRows (rowId, sectionId, rowName) VALUES
    (1, 1, 'A'),
    (2, 2, 'B'),
    (3, 4, 'A');

INSERT INTO Seats (seatId, rowId, seatNumber) VALUES
    (1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4),
    (5, 2, 1), (6, 2, 2), (7, 2, 3), (8, 2, 4),
    (9, 3, 1), (10, 3, 2), (11, 3, 3), (12, 3, 4);

-- Events, artists, and scheduled performances
INSERT INTO Events (eventId, organizerId, taxonomyId, title, description, resalePriceCap) VALUES
    (1, 4, 1, 'Northern Lights Festival', 'A one-night pop concert in Toronto.', 1.20),
    (2, 5, 4, 'City Stories', 'A contemporary musical at Capital Hall.', 1.15),
    (3, 4, 5, 'Friday Night Laughs', 'An evening of stand-up comedy.', 1.25);

INSERT INTO Artists (artistId, name) VALUES
    (1, 'Maya Stone'),
    (2, 'The Metro Lines'),
    (3, 'Jordan Lee'),
    (4, 'The Capital Players');

INSERT INTO EventLineups (artistId, eventId, billingOrder) VALUES
    (1, 1, 'Headliner'),
    (2, 1, 'Opening act'),
    (4, 2, 'Headliner'),
    (3, 3, 'Headliner');

INSERT INTO Performances (performanceId, eventId, venueId, dateTime, status) VALUES
    (1, 1, 1, '2026-09-12 19:30:00', 'Scheduled'),
    (2, 2, 2, '2026-10-03 20:00:00', 'Scheduled'),
    (3, 3, 1, '2026-08-15 20:00:00', 'Cancelled');

-- Price tiers and the section-to-tier mapping for each performance
INSERT INTO PriceTiers (tierId, performanceId, tierName, price) VALUES
    (1, 1, 'VIP Floor', 200.00),
    (2, 1, 'Balcony', 120.00),
    (3, 1, 'General Admission', 50.00),
    (4, 2, 'Orchestra', 150.00),
    (5, 2, 'Open Floor', 65.00),
    (6, 3, 'Standard Admission', 40.00);

INSERT INTO PerformanceSectionAssignments (sectionId, performanceId, tierId) VALUES
    (1, 1, 1),
    (2, 1, 2),
    (3, 1, 3),
    (4, 2, 4),
    (5, 2, 5),
    (3, 3, 6);

-- One reserved seat is unavailable for production equipment.
INSERT INTO BlockedSeats (performanceId, seatId, reason) VALUES
    (1, 4, 'Camera platform');

-- Initial orders and tickets. NULL seatId represents general-admission tickets.
INSERT INTO Orders (orderId, customerId, performanceId, paymentId, purchaseTime, totalPaid) VALUES
    (1, 1, 1, 1, '2026-07-20 10:15:00', 400.00),
    (2, 2, 1, 2, '2026-07-21 12:00:00', 170.00),
    (3, 3, 2, 3, '2026-07-22 09:30:00', 150.00);

INSERT INTO Tickets (ticketId, orderId, performanceId, sectionId, seatId, price, currentOwnerId, status) VALUES
    (1, 1, 1, 1, 1, 200.00, 1, 'Active'),
    (2, 1, 1, 1, 2, 200.00, 2, 'Active'),
    (3, 2, 1, 2, 5, 120.00, 3, 'Active'),
    (4, 2, 1, 3, NULL, 50.00, 2, 'Active'),
    (5, 3, 2, 4, 9, 150.00, 3, 'Active');

-- Resale history and both an active and completed resale listing.
INSERT INTO TicketOwnershipHistory (historyId, ticketId, sellerId, buyerId, transactionPrice, transactionDate) VALUES
    (1, 2, 1, 2, 220.00, '2026-07-21 18:00:00'),
    (2, 3, 2, 3, 130.00, '2026-07-22 14:30:00');

INSERT INTO ResaleListings (listingId, ticketId, sellerId, resalePrice, postedDate, status) VALUES
    (1, 2, 2, 230.00, '2026-07-23 09:00:00', 'Active'),
    (2, 3, 2, 130.00, '2026-07-22 12:00:00', 'Sold');

-- Reviews for the sample performances.
INSERT INTO Comments (commentId, customerId, performanceId, content, eventRating, venueRating) VALUES
    (1, 1, 1, 'Looking forward to the show and the venue layout is easy to navigate.', 5, 4),
    (2, 3, 2, 'The orchestra seating is excellent.', 4, 5);
