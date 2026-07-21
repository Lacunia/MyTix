-- ============================================================================
-- DROP TRIGGERS & TABLES
-- ============================================================================

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

-- ============================================================================
-- USERS & ACCOUNT SUBCLASSES
-- ============================================================================

CREATE TABLE Users (
    userId INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    address VARCHAR(255) NOT NULL,
    dateOfBirth DATE NOT NULL,
    accountType ENUM('Customer', 'Organizer') NOT NULL DEFAULT 'Customer',

    -- Ensures users are at least 18 years old to open an account
    CONSTRAINT chk_minimum_age CHECK (dateOfBirth <= CURDATE() - INTERVAL 18 YEAR)
);

CREATE TABLE Customers (
    customerId INT PRIMARY KEY,

    CONSTRAINT fk_customers_user FOREIGN KEY (customerId) REFERENCES Users(userId) ON DELETE CASCADE
);

CREATE TABLE PaymentMethods (
    paymentId INT AUTO_INCREMENT PRIMARY KEY,
    customerId INT NOT NULL,
    cardholderName VARCHAR(255) NOT NULL,
    cardNumber VARCHAR(19) NOT NULL,
    expiryDate CHAR(5) NOT NULL,

    CONSTRAINT fk_paymentmethods_customer FOREIGN KEY (customerId) REFERENCES Customers(customerId) ON DELETE CASCADE
);

CREATE TABLE Organizers (
    organizerId INT PRIMARY KEY,

    CONSTRAINT fk_organizers_user FOREIGN KEY (organizerId) REFERENCES Users(userId) ON DELETE CASCADE
);

-- ============================================================================
-- TAXONOMY, EVENTS & LINEUPS
-- ============================================================================

CREATE TABLE Taxonomy (
    taxonomyId INT AUTO_INCREMENT PRIMARY KEY,
    segment VARCHAR(100) NOT NULL,
    genre VARCHAR(100) NOT NULL,

    CONSTRAINT unique_segment_genre UNIQUE (segment, genre)
);

CREATE TABLE Events (
    eventId INT AUTO_INCREMENT PRIMARY KEY,
    organizerId INT NOT NULL,
    taxonomyId INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    -- e.g., 1.20 equals 120% cap
    resalePriceCap DECIMAL(5,2) NOT NULL DEFAULT 1.20,

    CONSTRAINT fk_events_organizer FOREIGN KEY (organizerId) REFERENCES Organizers(organizerId),
    CONSTRAINT fk_events_taxonomy FOREIGN KEY (taxonomyId) REFERENCES Taxonomy(taxonomyId),
    -- A cap below 1.00 (100% of face value) would make resale impossible
    CONSTRAINT chk_resale_price_cap_non_neg CHECK (resalePriceCap >= 1.00)
);

CREATE TABLE Artists (
    artistId INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE EventLineups (
    artistId INT NOT NULL,
    eventId INT NOT NULL,
    billingOrder ENUM('Headliner', 'Special guest', 'Opening act') NOT NULL,

    PRIMARY KEY (artistId, eventId),
    CONSTRAINT fk_eventlineups_artist FOREIGN KEY (artistId) REFERENCES Artists(artistId) ON DELETE CASCADE,
    CONSTRAINT fk_eventlineups_event FOREIGN KEY (eventId) REFERENCES Events(eventId) ON DELETE CASCADE
);

-- ============================================================================
-- PHYSICAL VENUE LAYOUT (SEATING)
-- ============================================================================

CREATE TABLE Venues (
    venueId INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    address VARCHAR(255) NOT NULL,
    postalCode VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL
);

CREATE TABLE Sections (
    sectionId INT AUTO_INCREMENT PRIMARY KEY,
    venueId INT NOT NULL,
    sectionName VARCHAR(100) NOT NULL,
    isReservedSeating BOOLEAN NOT NULL,
    standingCapacity INT,

    CONSTRAINT fk_sections_venue FOREIGN KEY (venueId) REFERENCES Venues(venueId) ON DELETE CASCADE,
    CONSTRAINT unique_venue_section UNIQUE (venueId, sectionName),

    -- If reserved seating is TRUE, standing capacity must be NULL. Otherwise, it must be provided.
    CONSTRAINT chk_standing_capacity CHECK (
        (isReservedSeating = TRUE AND standingCapacity IS NULL) OR
        (isReservedSeating = FALSE AND standingCapacity IS NOT NULL AND standingCapacity > 0)
    )
);

CREATE TABLE SectionRows (
    rowId INT AUTO_INCREMENT PRIMARY KEY,
    sectionId INT NOT NULL,
    rowName VARCHAR(50) NOT NULL,

    CONSTRAINT fk_rows_section FOREIGN KEY (sectionId) REFERENCES Sections(sectionId) ON DELETE CASCADE,
    CONSTRAINT unique_section_row UNIQUE (sectionId, rowName)
);

CREATE TABLE Seats (
    seatId INT AUTO_INCREMENT PRIMARY KEY,
    rowId INT NOT NULL,
    seatNumber INT NOT NULL,

    CONSTRAINT fk_seats_row FOREIGN KEY (rowId) REFERENCES SectionRows(rowId) ON DELETE CASCADE,
    CONSTRAINT unique_row_seat UNIQUE (rowId, seatNumber)
);

-- ============================================================================
-- PERFORMANCES, PRICING & SEAT AVAILABILITY
-- ============================================================================

CREATE TABLE Performances (
    performanceId INT AUTO_INCREMENT PRIMARY KEY,
    eventId INT NOT NULL,
    venueId INT NOT NULL,
    dateTime DATETIME NOT NULL,
    status ENUM('Scheduled', 'Cancelled') NOT NULL DEFAULT 'Scheduled',

    CONSTRAINT fk_performances_event FOREIGN KEY (eventId) REFERENCES Events(eventId) ON DELETE RESTRICT,
    CONSTRAINT fk_performances_venue FOREIGN KEY (venueId) REFERENCES Venues(venueId) ON DELETE RESTRICT
);

CREATE TABLE PriceTiers (
    tierId INT AUTO_INCREMENT PRIMARY KEY,
    performanceId INT NOT NULL,
    tierName VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,

    CONSTRAINT fk_pricetiers_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId) ON DELETE CASCADE,
    CONSTRAINT unique_performance_tier UNIQUE (performanceId, tierName),
    CONSTRAINT chk_tier_price_non_neg CHECK (price >= 0)
);

CREATE TABLE PerformanceSectionAssignments (
    sectionId INT NOT NULL,
    performanceId INT NOT NULL,
    tierId INT NOT NULL,

    PRIMARY KEY (sectionId, performanceId),
    CONSTRAINT fk_psa_section FOREIGN KEY (sectionId) REFERENCES Sections(sectionId) ON DELETE RESTRICT,
    CONSTRAINT fk_psa_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId) ON DELETE CASCADE,
    CONSTRAINT fk_psa_tier FOREIGN KEY (tierId) REFERENCES PriceTiers(tierId) ON DELETE RESTRICT
);

CREATE TABLE BlockedSeats (
    performanceId INT NOT NULL,
    seatId INT NOT NULL,
    reason VARCHAR(255),

    PRIMARY KEY (performanceId, seatId),
    CONSTRAINT fk_blockedseats_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId) ON DELETE CASCADE,
    CONSTRAINT fk_blockedseats_seat FOREIGN KEY (seatId) REFERENCES Seats(seatId) ON DELETE CASCADE
);

-- ============================================================================
-- ORDERS, TICKETS & RESALE
-- ============================================================================

CREATE TABLE Orders (
    orderId INT AUTO_INCREMENT PRIMARY KEY,
    customerId INT NOT NULL,
    performanceId INT NOT NULL,
    paymentId INT NOT NULL,
    purchaseTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    totalPaid DECIMAL(10,2) NOT NULL,

    CONSTRAINT fk_orders_customer FOREIGN KEY (customerId) REFERENCES Customers(customerId),
    CONSTRAINT fk_orders_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId),
    CONSTRAINT fk_orders_payment FOREIGN KEY (paymentId) REFERENCES PaymentMethods(paymentId),
    CONSTRAINT chk_orders_totalpaid_non_neg CHECK (totalPaid >= 0)
);

CREATE TABLE Tickets (
    ticketId INT AUTO_INCREMENT PRIMARY KEY,
    orderId INT NOT NULL,
    performanceId INT NOT NULL,
    sectionId INT NOT NULL,
    seatId INT, -- Nullable for general admission / standing
    price DECIMAL(10,2) NOT NULL,
    currentOwnerId INT NOT NULL,
    status ENUM('Active', 'Cancelled by customer', 'Cancelled by organizer') NOT NULL DEFAULT 'Active',

    CONSTRAINT fk_tickets_order FOREIGN KEY (orderId) REFERENCES Orders(orderId),
    CONSTRAINT fk_tickets_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId),
    CONSTRAINT fk_tickets_section FOREIGN KEY (sectionId) REFERENCES Sections(sectionId),
    CONSTRAINT fk_tickets_seat FOREIGN KEY (seatId) REFERENCES Seats(seatId),
    CONSTRAINT fk_tickets_owner FOREIGN KEY (currentOwnerId) REFERENCES Customers(customerId),
    -- A given seat can be sold at most once per performance
    CONSTRAINT unique_seat_per_performance UNIQUE (performanceId, seatId),
    CONSTRAINT chk_ticket_price_non_neg CHECK (price >= 0)
);

CREATE TABLE TicketOwnershipHistory (
    historyId INT AUTO_INCREMENT PRIMARY KEY,
    ticketId INT NOT NULL,
    sellerId INT NOT NULL,
    buyerId INT NOT NULL,
    transactionPrice DECIMAL(10,2) NOT NULL,
    transactionDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_toh_ticket FOREIGN KEY (ticketId) REFERENCES Tickets(ticketId) ON DELETE CASCADE,
    CONSTRAINT fk_toh_seller FOREIGN KEY (sellerId) REFERENCES Customers(customerId),
    CONSTRAINT fk_toh_buyer FOREIGN KEY (buyerId) REFERENCES Customers(customerId),
    CONSTRAINT chk_trxn_price_non_neg CHECK (transactionPrice >= 0)
);

CREATE TABLE ResaleListings (
    listingId INT AUTO_INCREMENT PRIMARY KEY,
    ticketId INT NOT NULL,
    sellerId INT NOT NULL,
    resalePrice DECIMAL(10,2) NOT NULL,
    postedDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status ENUM('Active', 'Sold', 'Withdrawn') NOT NULL DEFAULT 'Active',
    -- Note: The price cap constraint is dynamic relative to the original ticket's face
    -- value and the event's resalePriceCap.

    CONSTRAINT fk_resalelistings_ticket FOREIGN KEY (ticketId) REFERENCES Tickets(ticketId) ON DELETE CASCADE,
    CONSTRAINT fk_resalelistings_seller FOREIGN KEY (sellerId) REFERENCES Customers(customerId),
    CONSTRAINT chk_resale_price_non_neg CHECK (resalePrice >= 0)
);

-- ============================================================================
-- USER INTERACTION
-- ============================================================================

CREATE TABLE Comments (
    commentId INT AUTO_INCREMENT PRIMARY KEY,
    customerId INT NOT NULL,
    performanceId INT NOT NULL,
    content TEXT NOT NULL,
    eventRating INT NOT NULL,
    venueRating INT NOT NULL,

    CONSTRAINT fk_comments_customer FOREIGN KEY (customerId) REFERENCES Customers(customerId) ON DELETE CASCADE,
    CONSTRAINT fk_comments_performance FOREIGN KEY (performanceId) REFERENCES Performances(performanceId) ON DELETE CASCADE,
    -- The event and venue being rated are derived via performanceId -> Events / Venues,
    -- so "at most once per performance attended" is a single UNIQUE key here.
    CONSTRAINT unique_customer_performance_review UNIQUE (customerId, performanceId),
    CONSTRAINT chk_comments_event_rating CHECK (eventRating BETWEEN 1 AND 5),
    CONSTRAINT chk_comments_venue_rating CHECK (venueRating BETWEEN 1 AND 5)
    -- Additional dynamic parameters (verifying they attended the performance, that it
    -- has taken place, and that their ticket status was not cancelled) is handled from backend.
);

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- A section can only be assigned a price tier for a performance that is actually
-- held at the section's own venue.
-- - Below, for PerformanceSectionAssignments, we check that the section's venueId
--   matches the performance's venueId.
CREATE TRIGGER trg_section_venue_match
BEFORE INSERT ON PerformanceSectionAssignments
FOR EACH ROW
BEGIN
    DECLARE sectionVenueId INT;
    DECLARE performanceVenueId INT;

    SELECT venueId INTO sectionVenueId 
    FROM Sections 
    WHERE sectionId = NEW.sectionId;

    SELECT venueId INTO performanceVenueId 
    FROM Performances 
    WHERE performanceId = NEW.performanceId;

    IF sectionVenueId IS NULL OR performanceVenueId IS NULL OR sectionVenueId <> performanceVenueId THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Section does not belong to the venue of this performance';
    END IF;
END$$

-- A resale listing's price cannot exceed the event's resale cap applied to the
-- ticket's original face value.
CREATE TRIGGER trg_resale_price_cap
BEFORE INSERT ON ResaleListings
FOR EACH ROW
BEGIN
    DECLARE faceValue DECIMAL(10,2);
    DECLARE cap DECIMAL(5,2);

    SELECT t.price, e.resalePriceCap INTO faceValue, cap
    FROM Tickets t
    JOIN Performances p ON t.performanceId = p.performanceId
    JOIN Events e ON p.eventId = e.eventId
    WHERE t.ticketId = NEW.ticketId;

    IF NEW.resalePrice > faceValue * cap THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Resale price exceeds the resale cap for this event';
    END IF;
END$$

-- A ticket must have a seatId if and only if its section is reserved seating.
CREATE TRIGGER trg_ticket_seat_consistency
BEFORE INSERT ON Tickets
FOR EACH ROW
BEGIN
    DECLARE reservedSeating BOOLEAN;

    SELECT isReservedSeating INTO reservedSeating 
    FROM Sections 
    WHERE sectionId = NEW.sectionId;

    IF reservedSeating = TRUE AND NEW.seatId IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'A reserved-seating ticket must specify a seat';
    ELSEIF reservedSeating = FALSE AND NEW.seatId IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'A general-admission ticket must not specify a seat';
    END IF;
END$$
