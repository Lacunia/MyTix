-- Minimal smoke-test seed data for Q1, Q6, Q7, R1, R2, R3, R6.
-- Not a substitute for the full assignment-required sample data set —
-- just enough rows, with known literal IDs, to exercise each implemented
-- feature by hand. Run after schema.sql.

-- ============================================================================
-- USERS (2 organizers, 3 customers)
-- ============================================================================

INSERT INTO Users (userId, name, email, address, dateOfBirth, accountType) VALUES
(1, 'Alice Organizer', 'alice.org@example.com', '1 Org St', '1985-01-01', 'Organizer'),
(2, 'Bob Organizer',   'bob.org@example.com',   '2 Org St', '1980-05-05', 'Organizer'),
(3, 'Carol Customer',  'carol@example.com',     '10 Main St', '1995-03-03', 'Customer'),
(4, 'Dave Customer',   'dave@example.com',      '20 Main St', '1990-07-07', 'Customer'),
(5, 'Eve Customer',    'eve@example.com',       '30 Main St', '2000-11-11', 'Customer');

INSERT INTO Organizers (organizerId) VALUES (1), (2), (3);
INSERT INTO Customers (customerId) VALUES (3), (4), (5);

INSERT INTO PaymentMethods (paymentId, customerId, cardholderName, cardNumber, expiryDate) VALUES
(1, 3, 'Carol Customer', '4111111111111111', '12/28'),
(2, 4, 'Dave Customer',  '4111111111112222', '11/27'),
(3, 5, 'Eve Customer',   '4111111111113333', '09/29');

-- ============================================================================
-- TAXONOMY
-- ============================================================================

INSERT INTO Taxonomy (taxonomyId, segment, genre) VALUES
(1, 'Music', 'Rock'),
(2, 'Music', 'Pop'),
(3, 'Sports', 'Basketball');

-- ============================================================================
-- VENUES (2 cities, 2 countries)
-- ============================================================================

INSERT INTO Venues (venueId, name, latitude, longitude, address, postalCode, city, country) VALUES
(1, 'Toronto Arena',   43.643300, -79.379200, '40 Bay St',      'M5J2X2', 'Toronto', 'Canada'),
(2, 'Toronto Theatre', 43.650700, -79.347000, '260 King St',    'M5H1K7', 'Toronto', 'Canada'),
(3, 'NYC Garden',      40.750500, -73.993400, '4 Penn Plaza',   '10001',  'New York', 'USA');

-- ============================================================================
-- SECTIONS / ROWS / SEATS
-- Venue 1: reserved-seating "Floor" (2 rows x 5 seats) + GA "Lawn"
-- Venue 2 & 3: one reserved-seating section each, smaller
-- ============================================================================

INSERT INTO Sections (sectionId, venueId, sectionName, isReservedSeating, standingCapacity) VALUES
(1, 1, 'Floor', TRUE, NULL),
(2, 1, 'Lawn', FALSE, 100),
(3, 2, 'Orchestra', TRUE, NULL),
(4, 3, 'Floor', TRUE, NULL);

INSERT INTO SectionRows (rowId, sectionId, rowName) VALUES
(1, 1, 'A'),
(2, 1, 'B'),
(3, 3, 'A'),
(4, 4, 'A');

-- Row A (Floor, venue 1): seats 1-5 -- used for Q7's consecutive-seat search
INSERT INTO Seats (seatId, rowId, seatNumber) VALUES
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5),
(6, 2, 1), (7, 2, 2), (8, 2, 3),
(9, 3, 1), (10, 3, 2), (11, 3, 3),
(12, 4, 1), (13, 4, 2), (14, 4, 3);

-- ============================================================================
-- EVENTS
-- ============================================================================

INSERT INTO Events (eventId, organizerId, taxonomyId, title, description, resalePriceCap) VALUES
(1, 1, 1, 'Rock Fest', 'A rock concert tour', 1.20),
(2, 2, 3, 'City Hoops Game', 'A basketball game', 1.20);

-- ============================================================================
-- PERFORMANCES (past + upcoming, one touring across venues)
-- ============================================================================

INSERT INTO Performances (performanceId, eventId, venueId, name, dateTime, status) VALUES
(1, 1, 1, 'Rock Fest - Toronto Arena', DATE_ADD(NOW(), INTERVAL 14 DAY), 'Scheduled'),
(2, 1, 3, 'Rock Fest - NYC Garden',    DATE_ADD(NOW(), INTERVAL 30 DAY), 'Scheduled'),
(3, 2, 2, 'City Hoops - Past Game',    DATE_SUB(NOW(), INTERVAL 60 DAY), 'Scheduled');

-- ============================================================================
-- PRICE TIERS & SECTION ASSIGNMENTS
-- ============================================================================

INSERT INTO PriceTiers (tierId, performanceId, tierName, price) VALUES
(1, 1, 'P1', 180.00),
(2, 1, 'P2', 90.00),
(3, 2, 'P1', 200.00),
(4, 2, 'P2', 100.00),
(5, 3, 'P1', 75.00);

INSERT INTO PerformanceSectionAssignments (sectionId, performanceId, tierId) VALUES
(1, 1, 1),  -- Floor (venue 1) -> P1 for performance 1
(2, 1, 2),  -- Lawn  (venue 1) -> P2 for performance 1
(4, 2, 3),  -- Floor (venue 3) -> P1 for performance 2
(3, 3, 5);  -- Orchestra (venue 2) -> P1 for performance 3 (past)

-- ============================================================================
-- ORDERS & TICKETS
-- Performance 1 (upcoming, >7 days away): seats 1,2,3 sold (leaves 4,5 free
-- and consecutive -- exercises Q7); seat 8 blocked.
-- Performance 3 (past): sold out its 3 seats -- exercises R1/R3 revenue.
-- ============================================================================

INSERT INTO Orders (orderId, customerId, performanceId, paymentId, purchaseTime, totalPaid) VALUES
(1, 3, 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), 540.00),
(2, 4, 3, 2, DATE_SUB(NOW(), INTERVAL 61 DAY), 225.00);

INSERT INTO Tickets (ticketId, orderId, performanceId, sectionId, seatId, price, currentOwnerId, status) VALUES
(1, 1, 1, 1, 1, 180.00, 3, 'Active'),
(2, 1, 1, 1, 2, 180.00, 3, 'Active'),
(3, 1, 1, 1, 3, 180.00, 3, 'Active'),
(4, 2, 3, 3, 9, 75.00, 4, 'Active'),
(5, 2, 3, 3, 10, 75.00, 4, 'Active'),
(6, 2, 3, 3, 11, 75.00, 4, 'Active');

-- A cancelled ticket, to exercise R6 (customer cancellation ranking).
-- Seat 6 is Row B / Floor (section 1, reserved seating), so a seatId is required.
INSERT INTO Tickets (ticketId, orderId, performanceId, sectionId, seatId, price, currentOwnerId, status, cancelledAt) VALUES
(7, 1, 1, 1, 6, 180.00, 3, 'Cancelled by customer', NOW());

-- A blocked seat (Row B, venue 1, performance 1) -- exercises Q6's "blocked" count
INSERT INTO BlockedSeats (performanceId, seatId, reason) VALUES
(1, 8, 'Obstructed view');

-- A cancelled performance, to exercise R6 (organizer cancellation ranking)
INSERT INTO Performances (performanceId, eventId, venueId, name, dateTime, status, cancelledAt) VALUES
(4, 2, 2, 'City Hoops - Cancelled Game', DATE_SUB(NOW(), INTERVAL 10 DAY), 'Cancelled', NOW());
