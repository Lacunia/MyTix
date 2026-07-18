DROP TABLE IF EXISTS Venues;
DROP TABLE IF EXISTS Taxonomy;
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Customers;
DROP TABLE IF EXISTS Organizers;
DROP TABLE IF EXISTS Events;
DROP TABLE IF EXISTS Artists;
DROP TABLE IF EXISTS EventLineups;
DROP TABLE IF EXISTS Sections;
DROP TABLE IF EXISTS Rows;
DROP TABLE IF EXISTS Seats;
DROP TABLE IF EXISTS Performances;
DROP TABLE IF EXISTS PriceTiers;

-- ============================================================================
-- ENUM TYPES
-- ============================================================================

CREATE TYPE billing_order_enum AS ENUM ('Headliner', 'Special guest', 'Opening act');
CREATE TYPE performance_status_enum AS ENUM ('Scheduled', 'Cancelled');
CREATE TYPE account_type_enum AS ENUM ('Customer', 'Organizer');
CREATE TYPE ticket_status_enum AS ENUM ('Active', 'Cancelled by customer', 'Cancelled by organizer');
CREATE TYPE listing_status_enum AS ENUM ('Active', 'Sold', 'Withdrawn');

-- ============================================================================
-- USERS & ACCOUNT SUBCLASSES
-- ============================================================================

CREATE TABLE Users (
    userId SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    address VARCHAR(255) NOT NULL,
    dateOfBirth DATE NOT NULL,
    accountType account_type_enum NOT NULL DEFAULT 'Customer',
    -- Ensures users are at least 18 years old to open an account
    CONSTRAINT chk_minimum_age CHECK (dateOfBirth <= CURRENT_DATE - INTERVAL '18 years')
);

CREATE TABLE Customers (
    customerId INT PRIMARY KEY REFERENCES Users(userId) ON DELETE CASCADE,
    paymentId INT NOT NULL REFERENCES PaymentMethods(paymentId) ON DELETE CASCADE
);

CREATE TABLE Organizers (
    organizerId INT PRIMARY KEY REFERENCES Users(userId) ON DELETE CASCADE
);

CREATE TABLE PaymentMethods (
    paymentId SERIAL PRIMARY KEY,
    customerId INT NOT NULL REFERENCES Customers(customerId) ON DELETE CASCADE,
    cardholderName VARCHAR(255) NOT NULL,
    cardNumber VARCHAR(19) NOT NULL,
    expiryDate CHAR(5) NOT NULL,
);

-- ============================================================================
-- EVENTS & LINEUPS
-- ============================================================================

CREATE TABLE Events (
    eventId SERIAL PRIMARY KEY,
    organizerId INT NOT NULL REFERENCES Organizers(organizerId),
    taxonomyId INT NOT NULL REFERENCES Taxonomy(taxonomyId),
    title VARCHAR(255),
    description TEXT,
    resalePriceCap DECIMAL(5,2) NOT NULL DEFAULT 1.20 CHECK (amount >= 0) 
    -- e.g., 1.20 equals 120% cap
);

CREATE TABLE Taxonomy (
    taxonomyId SERIAL PRIMARY KEY,
    segment VARCHAR(100) NOT NULL,
    genre VARCHAR(100) NOT NULL,
    CONSTRAINT unique_segment_genre UNIQUE (segment, genre)
);

CREATE TABLE Artists (
    artistId SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE EventLineups (
    artistId INT REFERENCES Artists(artistId) ON DELETE CASCADE,
    eventId INT REFERENCES Events(eventId) ON DELETE CASCADE,
    billingOrder billing_order_enum NOT NULL,
    PRIMARY KEY (artistId, eventId)
);

-- ============================================================================
-- PHYSICAL VENUE LAYOUT (SEATING)
-- ============================================================================

CREATE TABLE Venues (
    venueId SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    address VARCHAR(255) NOT NULL,
    postalCode VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL
);

CREATE TABLE Sections (
    sectionId SERIAL PRIMARY KEY,
    venueId INT NOT NULL REFERENCES Venues(venueId) ON DELETE CASCADE,
    sectionName VARCHAR(100) NOT NULL,
    isReservedSeating BOOLEAN NOT NULL,
    standingCapacity INT,
    CONSTRAINT unique_venue_section UNIQUE (venueId, sectionName),
    -- If reserved seating is TRUE, standing capacity must be NULL. Otherwise, it must be provided.
    CONSTRAINT chk_standing_capacity CHECK (
        (isReservedSeating = TRUE AND standingCapacity IS NULL) OR
        (isReservedSeating = FALSE AND standingCapacity IS NOT NULL)
    )
);

CREATE TABLE Rows (
    rowId SERIAL PRIMARY KEY,
    sectionId INT NOT NULL REFERENCES Sections(sectionId) ON DELETE CASCADE,
    rowName VARCHAR(50) NOT NULL,
    CONSTRAINT unique_section_row UNIQUE (sectionId, rowName)
);

CREATE TABLE Seats (
    seatId SERIAL PRIMARY KEY,
    rowId INT NOT NULL REFERENCES Rows(rowId) ON DELETE CASCADE,
    seatNumber INT NOT NULL,
    CONSTRAINT unique_row_seat UNIQUE (rowId, seatNumber)
);

-- ============================================================================
-- PERFORMANCES, PRICING & SEAT AVAILABILITY
-- ============================================================================

CREATE TABLE Performances (
    performanceId SERIAL PRIMARY KEY,
    eventId INT NOT NULL REFERENCES Events(eventId) ON DELETE RESTRICT,
    venueId INT NOT NULL REFERENCES Venues(venueId) ON DELETE RESTRICT,
    dateTime TIMESTAMP NOT NULL,
    status performance_status_enum NOT NULL DEFAULT 'Scheduled'
);

CREATE TABLE PriceTiers (
    tierId SERIAL PRIMARY KEY,
    performanceId INT NOT NULL REFERENCES Performances(performanceId) ON DELETE CASCADE,
    tierName VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    CONSTRAINT unique_performance_tier UNIQUE (performanceId, tierName)
);

CREATE TABLE PerformanceSectionAssignments (
    sectionId INT REFERENCES Sections(sectionId) ON DELETE RESTRICT,
    performanceId INT REFERENCES Performances(performanceId) ON DELETE CASCADE,
    tierId INT NOT NULL REFERENCES PriceTiers(tierId) ON DELETE RESTRICT,
    PRIMARY KEY (sectionId, performanceId)
);

CREATE TABLE BlockedSeats (
    performanceId INT REFERENCES Performances(performanceId) ON DELETE CASCADE,
    seatId INT REFERENCES Seats(seatId) ON DELETE CASCADE,
    reason VARCHAR(255),
    PRIMARY KEY (performanceId, seatId)
);

-- ============================================================================
-- ORDERS, TICKETS & RESALE
-- ============================================================================

CREATE TABLE Orders (
    orderId SERIAL PRIMARY KEY,
    customerId INT NOT NULL REFERENCES Customers(customerId),
    performanceId INT NOT NULL REFERENCES Performances(performanceId),
    purchaseTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paymentId INT NOT NULL REFERENCES Customers(paymentId),
    totalPaid DECIMAL(10,2) NOT NULL
);

CREATE TABLE Tickets (
    ticketId SERIAL PRIMARY KEY,
    orderId INT NOT NULL REFERENCES Orders(orderId),
    performanceId INT NOT NULL REFERENCES Performances(performanceId),
    sectionId INT NOT NULL REFERENCES Sections(sectionId),
    seatId INT REFERENCES Seats(seatId), -- Nullable for general admission / standing
    price DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    currentOwnerId INT NOT NULL REFERENCES Customers(customerId),
    status ticket_status_enum NOT NULL DEFAULT 'Active'
);

CREATE TABLE TicketOwnershipHistory (
    historyId SERIAL PRIMARY KEY,
    ticketId INT NOT NULL REFERENCES Tickets(ticketId) ON DELETE CASCADE,
    sellerId INT NOT NULL REFERENCES Customers(customerId),
    buyerId INT NOT NULL REFERENCES Customers(customerId),
    transactionPrice DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    transactionDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ResaleListings (
    listingId SERIAL PRIMARY KEY,
    ticketId INT NOT NULL REFERENCES Tickets(ticketId) ON DELETE CASCADE,
    sellerId INT NOT NULL REFERENCES Customers(customerId),
    resalePrice DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    postedDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status listing_status_enum NOT NULL DEFAULT 'Active'
    -- Note: The 120% price cap constraint is dynamic relative to the original ticket price. 
    -- This is strictly enforced via an application-level business check or a database BEFORE INSERT trigger.
);

-- ============================================================================
-- USER INTERACTION
-- ============================================================================

CREATE TABLE Comments (
    commentId SERIAL PRIMARY KEY,
    customerId INT NOT NULL REFERENCES Customers(customerId) ON DELETE CASCADE,
    eventId INT NOT NULL REFERENCES Events(eventId) ON DELETE CASCADE,
    venueId INT NOT NULL REFERENCES Venues(venueId) ON DELETE CASCADE,
    content TEXT NOT NULL,
    eventRating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    venueRating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT unique_user_performance_review UNIQUE (userId, eventId)
    -- Additional dynamic parameters (verifying they attended the event, that the performance is in the past,
    -- and that their ticket status was not cancelled) should be handled via a conditional API layer or dynamic SQL triggers.
);