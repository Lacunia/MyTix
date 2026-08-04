-- Full MyTix demonstration dataset.
--
-- Usage (against a NEW database):
--   mysql -u <user> -p <database> < sql/schema.sql
--   mysql -u <user> -p <database> < sql/load.sql
--
-- This script deliberately uses CURRENT_TIMESTAMP-relative dates, so it remains
-- useful for demonstrations over time.  It assumes the schema is empty.

DELIMITER $$

DROP PROCEDURE IF EXISTS seed_mytix_full$$
CREATE PROCEDURE seed_mytix_full()
BEGIN
    DECLARE n INT DEFAULT 0;
    DECLARE venue INT;
    DECLARE sec1 INT;
    DECLARE sec2 INT;
    DECLARE row_id INT;
    DECLARE row_no INT;
    DECLARE seat_no INT;
    DECLARE rows_per_section INT;
    DECLARE perf INT;
    DECLARE ord INT DEFAULT 0;
    DECLARE order_id INT;
    DECLARE ticket_no INT;
    DECLARE customer INT;
    DECLARE ticket INT;
    DECLARE selected_ticket INT;

    -- Five organizers and 100 adult customers with fictional payment data.
    INSERT INTO Users (name, email, address, dateOfBirth, accountType) VALUES
      ('Northstar Live', 'hello@northstarlive.example', '100 Front St W, Toronto, ON', '1982-04-12', 'Organizer'),
      ('Maple Leaf Arts', 'bookings@mapleleafarts.example', '22 Bloor St W, Toronto, ON', '1979-08-21', 'Organizer'),
      ('Lakeside Sports', 'events@lakesidesports.example', '55 Bay St, Toronto, ON', '1980-01-17', 'Organizer'),
      ('Atlantic Stage', 'team@atlanticstage.example', '610 Burrard St, Vancouver, BC', '1985-11-05', 'Organizer'),
      ('Metro Entertainment', 'shows@metroent.example', '33 W 60th St, New York, NY', '1978-06-30', 'Organizer');
    INSERT INTO Organizers (organizerId) SELECT userId FROM Users WHERE accountType = 'Organizer';

    SET n = 1;
    WHILE n <= 100 DO
      INSERT INTO Users (name, email, address, dateOfBirth, accountType)
      VALUES (CONCAT('Customer ', LPAD(n, 3, '0')), CONCAT('customer', n, '@example.test'),
              CONCAT(n, ' Market Street, Toronto, ON'), DATE_SUB('1990-01-01', INTERVAL n DAY), 'Customer');
      SET customer = LAST_INSERT_ID();
      INSERT INTO Customers (customerId) VALUES (customer);
      INSERT INTO PaymentMethods (customerId, cardholderName, cardNumber, expiryDate)
      VALUES (customer, CONCAT('Customer ', LPAD(n, 3, '0')),
              CONCAT('4111111111', LPAD(n, 6, '0')), CONCAT(LPAD(1 + MOD(n, 12), 2, '0'), '/3', 0 + MOD(n, 5)));
      SET n = n + 1;
    END WHILE;

    INSERT INTO Taxonomy (segment, genre) VALUES
      ('Music', 'Rock'), ('Music', 'Pop'), ('Music', 'Jazz'), ('Music', 'Electronic'),
      ('Arts & Theatre', 'Musical'), ('Arts & Theatre', 'Drama'), ('Sports', 'Hockey'), ('Comedy', 'Stand-up');

    -- Eight venues, including three close downtown Toronto locations.
    INSERT INTO Venues (name, latitude, longitude, address, postalCode, city, country) VALUES
      ('Harbour Centre Arena', 43.642600, -79.387100, '100 Harbour St', 'M5J 1E6', 'Toronto', 'Canada'),
      ('King Street Theatre', 43.648700, -79.374800, '260 King St E', 'M5A 1K3', 'Toronto', 'Canada'),
      ('Distillery Music Hall', 43.650300, -79.359600, '55 Mill St', 'M5A 3C4', 'Toronto', 'Canada'),
      ('Capital Hall', 45.423600, -75.700900, '1 Elgin St', 'K1P 5W1', 'Ottawa', 'Canada'),
      ('Vancouver Waterfront Pavilion', 49.288900, -123.116500, '1055 Canada Pl', 'V6C 0C3', 'Vancouver', 'Canada'),
      ('Montreal Quartier Stage', 45.507900, -73.554300, '175 Rue Sainte-Catherine E', 'H2X 1K5', 'Montreal', 'Canada'),
      ('Hudson Square Theatre', 40.726900, -74.008900, '200 Hudson St', '10013', 'New York', 'USA'),
      ('Brooklyn River Arena', 40.678200, -73.944200, '620 Atlantic Ave', '11217', 'Brooklyn', 'USA');
    INSERT INTO PostalCodeAdjacency VALUES ('M5J', 'M5A'), ('M5A', 'M5J');

    -- Every venue has two reserved sections and one GA section.  Layout sizes vary.
    SET venue = 1;
    WHILE venue <= 8 DO
      INSERT INTO Sections (venueId, sectionName, isReservedSeating, standingCapacity) VALUES
        (venue, 'Orchestra', TRUE, NULL), (venue, 'Balcony', TRUE, NULL),
        (venue, 'General Admission', FALSE, CASE WHEN venue <= 3 THEN 50 ELSE 40 + venue * 15 END);
      SET rows_per_section = CASE venue WHEN 1 THEN 2 WHEN 2 THEN 2 WHEN 3 THEN 2 WHEN 4 THEN 3 WHEN 5 THEN 4 WHEN 6 THEN 3 WHEN 7 THEN 5 ELSE 4 END;
      SET sec1 = (venue - 1) * 3 + 1;
      SET sec2 = sec1 + 1;
      SET row_no = 1;
      WHILE row_no <= rows_per_section DO
        INSERT INTO SectionRows (sectionId, rowName) VALUES (sec1, CHAR(64 + row_no));
        SET row_id = LAST_INSERT_ID(); SET seat_no = 1;
        WHILE seat_no <= 10 DO INSERT INTO Seats (rowId, seatNumber) VALUES (row_id, seat_no); SET seat_no = seat_no + 1; END WHILE;
        INSERT INTO SectionRows (sectionId, rowName) VALUES (sec2, CHAR(64 + row_no));
        SET row_id = LAST_INSERT_ID(); SET seat_no = 1;
        WHILE seat_no <= 10 DO INSERT INTO Seats (rowId, seatNumber) VALUES (row_id, seat_no); SET seat_no = seat_no + 1; END WHILE;
        SET row_no = row_no + 1;
      END WHILE;
      SET venue = venue + 1;
    END WHILE;

    INSERT INTO Events (organizerId, taxonomyId, title, description, resalePriceCap) VALUES
      (1, 1, 'Northern Echoes Tour', 'A cross-country rock tour with a full band and local support.', 1.20),
      (2, 5, 'City Stories', 'A new musical about neighbours, memory, and a changing city block.', 1.15),
      (3, 7, 'Harbour Hawks Home Series', 'Professional hockey with family programming before the puck drop.', 1.10),
      (4, 2, 'Summer Skyline Pop', 'An outdoor pop showcase celebrating emerging Canadian songwriters.', 1.25),
      (5, 8, 'Late Night Laugh Lab', 'A sharp stand-up bill with experimental comics and surprise guests.', 1.20),
      (1, 3, 'Blue Note Evenings', 'An intimate jazz series led by acclaimed instrumentalists.', 1.15),
      (2, 6, 'Glass Houses', 'A contemporary drama about trust, inheritance, and family secrets.', 1.10),
      (3, 4, 'Pulse Electric', 'Electronic artists, immersive visuals, and a late-night dance floor.', 1.25),
      (4, 1, 'Coastal Thunder', 'A loud, melodic rock double bill from the Pacific coast.', 1.20),
      (5, 2, 'Midtown Voices', 'Pop singers share stripped-back arrangements and personal stories.', 1.15),
      (1, 5, 'Moonlit Parade', 'A warm-hearted musical with a live orchestra and large ensemble.', 1.10),
      (2, 7, 'Capital Ice Classic', 'A rivalry hockey fixture with community youth teams on the ice.', 1.10),
      (3, 8, 'Five Borough Comedy', 'New York comics deliver a fast-moving late-night showcase.', 1.20),
      (4, 3, 'West Coast Sessions', 'Jazz ensembles explore standards and new original compositions.', 1.15),
      (5, 6, 'The Last Platform', 'A suspenseful drama set during one unexpected subway delay.', 1.10),
      (1, 4, 'Neon Field', 'A high-energy electronic festival with projection mapping.', 1.25),
      (2, 1, 'Prairie Static', 'Guitar-driven rock from artists touring their new records.', 1.20),
      (3, 2, 'Golden Hour Pop', 'A bright pop concert designed for all-ages audiences.', 1.15),
      (4, 5, 'Paper Kites', 'A chamber musical with vivid costumes and close-up storytelling.', 1.10),
      (5, 8, 'Saturday Punchlines', 'A rotating line-up of established and rising stand-up performers.', 1.20);

    INSERT INTO Artists (name) VALUES
      ('The Northlines'), ('Mara Vale'), ('Glass Harbour'), ('Tomas Reed'), ('Ava June'),
      ('The City Players'), ('Nia Brooks'), ('Eli Grant'), ('The Harbour Hawks'), ('DJ Meridian'),
      ('Sofia Park'), ('Miles Ardent'), ('Rae Collins'), ('Juniper Lane'), ('The Last Train'),
      ('Kofi Wells'), ('Sienna Hart'), ('Leo Finch'), ('The Golden Hours'), ('Priya Shah');
    INSERT INTO EventLineups (artistId, eventId, billingOrder) VALUES
      (1,1,'Headliner'), (2,1,'Special guest'), (3,1,'Opening act'), (6,2,'Headliner'),
      (9,3,'Headliner'), (5,4,'Headliner'), (7,4,'Opening act'), (8,5,'Headliner'),
      (12,6,'Headliner'), (15,7,'Headliner'), (10,8,'Headliner'), (4,9,'Headliner'),
      (11,10,'Headliner'), (19,11,'Headliner'), (9,12,'Headliner'), (13,13,'Headliner'),
      (16,14,'Headliner'), (15,15,'Headliner'), (10,16,'Headliner'), (18,17,'Headliner'),
      (17,18,'Headliner'), (6,19,'Headliner'), (20,20,'Headliner');

    -- 64 performances: an eight-city tour, a ten-show theatre run, past and future dates.
    SET perf = 1;
    WHILE perf <= 64 DO
      INSERT INTO Performances (eventId, venueId, name, dateTime, status, cancelledAt)
      VALUES (
        CASE WHEN perf <= 8 THEN 1 WHEN perf BETWEEN 9 AND 18 THEN 2 ELSE 1 + MOD(perf - 1, 20) END,
        CASE WHEN perf <= 8 THEN perf WHEN perf BETWEEN 9 AND 18 THEN 2 ELSE 1 + MOD(perf - 1, 8) END,
        CONCAT('Performance ', perf),
        CASE WHEN perf <= 32 THEN DATE_SUB(CURRENT_TIMESTAMP, INTERVAL (33 - perf) * 9 DAY)
             WHEN perf = 33 THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 3 DAY)
             WHEN perf = 34 THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 DAY)
             ELSE DATE_ADD(CURRENT_TIMESTAMP, INTERVAL (perf - 30) * 4 DAY) END,
        CASE WHEN perf IN (4,5) THEN 'Cancelled' ELSE 'Scheduled' END,
        CASE WHEN perf IN (4,5) THEN DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY) ELSE NULL END);
      SET perf = perf + 1;
    END WHILE;

    SET perf = 1;
    WHILE perf <= 64 DO
      INSERT INTO PriceTiers (performanceId, tierName, price) VALUES
        (perf, 'Orchestra Premium', 120.00 + MOD(perf, 4) * 10),
        (perf, 'Balcony Standard', 80.00 + MOD(perf, 3) * 10),
        (perf, 'General Admission', 45.00 + MOD(perf, 5) * 5);
      INSERT INTO PerformanceSectionAssignments (sectionId, performanceId, tierId) VALUES
        (((SELECT venueId FROM Performances WHERE performanceId = perf) - 1) * 3 + 1, perf,
         CASE WHEN perf = 10 THEN LAST_INSERT_ID() + 2 ELSE LAST_INSERT_ID() END),
        (((SELECT venueId FROM Performances WHERE performanceId = perf) - 1) * 3 + 2, perf, LAST_INSERT_ID() + 1),
        (((SELECT venueId FROM Performances WHERE performanceId = perf) - 1) * 3 + 3, perf,
         CASE WHEN perf = 10 THEN LAST_INSERT_ID() ELSE LAST_INSERT_ID() + 2 END);
      SET perf = perf + 1;
    END WHILE;

    -- Three past sold-out performances (venues 1-3 each have 20+20+50 capacity).
    SET perf = 1;
    WHILE perf <= 3 DO
      SET ticket_no = 1;
      WHILE ticket_no <= 90 DO
        IF MOD(ticket_no - 1, 3) = 0 THEN
          SET ord = ord + 1;
          SET customer = 6 + MOD(ord - 1, 100);
          INSERT INTO Orders (customerId, performanceId, paymentId, purchaseTime, totalPaid)
          VALUES (customer, perf, 1 + MOD(ord - 1, 100), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL (100 + ord) DAY), 250.00);
          SET order_id = LAST_INSERT_ID();
        END IF;
        IF ticket_no <= 20 THEN
          INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
          VALUES (order_id, perf, (perf - 1) * 3 + 1,
                  (SELECT ordered.seatId FROM (
                     SELECT st.seatId, ROW_NUMBER() OVER (ORDER BY r.rowName, st.seatNumber) AS seat_position
                     FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId
                     WHERE r.sectionId=(perf-1)*3+1
                   ) ordered WHERE ordered.seat_position=ticket_no), 130.00, customer, 'Active');
        ELSEIF ticket_no <= 40 THEN
          INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
          VALUES (order_id, perf, (perf - 1) * 3 + 2,
                  (SELECT ordered.seatId FROM (
                     SELECT st.seatId, ROW_NUMBER() OVER (ORDER BY r.rowName, st.seatNumber) AS seat_position
                     FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId
                     WHERE r.sectionId=(perf-1)*3+2
                   ) ordered WHERE ordered.seat_position=ticket_no-20), 90.00, customer, 'Active');
        ELSE
          INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
          VALUES (order_id, perf, (perf - 1) * 3 + 3, NULL, 50.00, customer, 'Active');
        END IF;
        SET ticket_no = ticket_no + 1;
      END WHILE;
      SET perf = perf + 1;
    END WHILE;

    -- 210 more orders and 630 tickets distributed across the remaining performances.
    SET n = 1;
    WHILE n <= 210 DO
      SET perf = 4 + MOD(n - 1, 61);
      SET ord = ord + 1; SET customer = 6 + MOD(ord - 1, 100);
      INSERT INTO Orders (customerId, performanceId, paymentId, purchaseTime, totalPaid)
      VALUES (customer, perf, 1 + MOD(ord - 1, 100),
              CASE WHEN MOD(n, 20) = 0 THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 DAY)
                   ELSE DATE_SUB(CURRENT_TIMESTAMP, INTERVAL MOD(ord * 3, 350) DAY) END, 165.00);
      SET order_id = LAST_INSERT_ID();
      SET ticket_no = 1;
      WHILE ticket_no <= 3 DO
        INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
        VALUES (order_id, perf, ((SELECT venueId FROM Performances WHERE performanceId=perf)-1)*3+3,
                NULL, 55.00, customer, 'Active');
        SET ticket_no = ticket_no + 1;
      END WHILE;
      SET n = n + 1;
    END WHILE;

    -- A >7-day show with reserved availability: row A has nonconsecutive seats left;
    -- row B still has ten consecutive seats.  It also has abundant GA capacity.
    SET n = 1;
    WHILE n <= 5 DO
      SET customer = 6 + n;
      INSERT INTO Orders (customerId, performanceId, paymentId, purchaseTime, totalPaid)
      VALUES (customer, 34, 1 + n, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL n DAY), 130.00);
      SET order_id = LAST_INSERT_ID();
      INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
      VALUES (order_id, 34, ((SELECT venueId FROM Performances WHERE performanceId=34)-1)*3+1,
              (SELECT ordered.seatId FROM (
                 SELECT st.seatId, ROW_NUMBER() OVER (ORDER BY r.rowName, st.seatNumber) AS seat_position
                 FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId
                 WHERE r.sectionId=((SELECT venueId FROM Performances WHERE performanceId=34)-1)*3+1
               ) ordered WHERE ordered.seat_position=(n*2)-1), 140.00, customer, 'Active');
      SET n = n + 1;
    END WHILE;
    -- Extra purchases ensure two customers each bought 11 tickets and listed
    -- more than half of them on the resale market below.
    SET n = 1;
    WHILE n <= 4 DO
      SET customer = 6 + MOD(n - 1, 2);
      INSERT INTO Orders (customerId, performanceId, paymentId, purchaseTime, totalPaid)
      VALUES (customer, 35, 1 + MOD(n - 1, 2), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL n DAY), 55.00);
      SET order_id = LAST_INSERT_ID();
      INSERT INTO Tickets (orderId, performanceId, sectionId, seatId, price, currentOwnerId, status)
      VALUES (order_id, 35, ((SELECT venueId FROM Performances WHERE performanceId=35)-1)*3+3, NULL, 55.00, customer, 'Active');
      SET n = n + 1;
    END WHILE;
    INSERT INTO BlockedSeats (performanceId, seatId, reason)
    SELECT 34, MIN(s.seatId) + 19, 'Production camera position' FROM Seats s JOIN SectionRows r ON r.rowId=s.rowId
    WHERE r.sectionId=((SELECT venueId FROM Performances WHERE performanceId=34)-1)*3+2;

    -- Customer and organizer cancellations with refunds.
    UPDATE Tickets SET status='Cancelled by customer', cancelledAt=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 8 DAY)
    WHERE ticketId IN (271, 274, 277);
    UPDATE Tickets SET status='Cancelled by organizer', cancelledAt=DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 20 DAY)
    WHERE performanceId IN (4,5) AND status='Active';
    INSERT INTO Refunds (ticketId, paymentId, amount, reason, refundedAt)
    SELECT t.ticketId, o.paymentId, t.price,
           CASE WHEN t.status='Cancelled by customer' THEN 'Customer cancellation' ELSE 'Organizer cancellation' END,
           t.cancelledAt
    FROM Tickets t JOIN Orders o ON o.orderId=t.orderId WHERE t.status <> 'Active';

    -- Active, sold, and withdrawn resale listings.  Listings 1-14 are at their cap.
    SET n = 0;
    WHILE n < 7 DO
      SELECT ticketId INTO selected_ticket FROM Tickets WHERE currentOwnerId=6 AND status='Active' ORDER BY ticketId LIMIT n,1;
      INSERT INTO ResaleListings (ticketId, sellerId, resalePrice, postedDate, status)
      SELECT t.ticketId, 6, t.price * e.resalePriceCap, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL (n + 1) DAY),
             CASE WHEN n < 3 THEN 'Sold' WHEN n < 5 THEN 'Withdrawn' ELSE 'Active' END
      FROM Tickets t JOIN Performances p ON p.performanceId=t.performanceId JOIN Events e ON e.eventId=p.eventId WHERE t.ticketId=selected_ticket;
      IF n < 3 THEN
        INSERT INTO TicketOwnershipHistory (ticketId, sellerId, buyerId, transactionPrice, transactionDate)
        SELECT selected_ticket, 6, 20+n, resalePrice, CURRENT_TIMESTAMP FROM ResaleListings WHERE ticketId=selected_ticket;
        UPDATE Tickets SET currentOwnerId=20+n WHERE ticketId=selected_ticket;
      END IF;
      SET n=n+1;
    END WHILE;
    SET n = 0;
    WHILE n < 7 DO
      SELECT ticketId INTO selected_ticket FROM Tickets WHERE currentOwnerId=7 AND status='Active' ORDER BY ticketId LIMIT n,1;
      INSERT INTO ResaleListings (ticketId, sellerId, resalePrice, postedDate, status)
      SELECT t.ticketId, 7, t.price * e.resalePriceCap, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL (n + 2) DAY),
             CASE WHEN n < 2 THEN 'Sold' WHEN n < 4 THEN 'Withdrawn' ELSE 'Active' END
      FROM Tickets t JOIN Performances p ON p.performanceId=t.performanceId JOIN Events e ON e.eventId=p.eventId WHERE t.ticketId=selected_ticket;
      SET n=n+1;
    END WHILE;
    SELECT ticketId INTO selected_ticket FROM Tickets WHERE currentOwnerId=8 AND status='Active' ORDER BY ticketId LIMIT 1;
    INSERT INTO TicketOwnershipHistory (ticketId, sellerId, buyerId, transactionPrice, transactionDate) VALUES
      (selected_ticket, 8, 30, 55.00, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 12 DAY)),
      (selected_ticket, 30, 31, 58.00, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 DAY));
    UPDATE Tickets SET currentOwnerId=31 WHERE ticketId=selected_ticket;

    -- Thirty multi-sentence reviews for ten different past events, three each.
    SET n=1;
    WHILE n <= 30 DO
      SET perf = 19 + MOD(n - 1, 10);
      INSERT INTO Comments (customerId, performanceId, content, eventRating, venueRating)
      VALUES (40 + n, perf,
        CONCAT('The performance had an engaging opening set and a confident final act. The venue staff kept the entry line moving, while the sound mix made the quieter moments easy to hear. Our group would happily return for another evening like this.'),
        3 + MOD(n, 3), 3 + MOD(n + 1, 3));
      SET n=n+1;
    END WHILE;
END$$

CALL seed_mytix_full()$$
DROP PROCEDURE seed_mytix_full$$
DELIMITER ;
