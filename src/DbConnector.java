import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Console entry point.  Guests, customers, and organizers are deliberately
 * given separate menus; database IDs remain an implementation detail. */
public class DbConnector {
    private static final EnvConfig ENV = new EnvConfig();
    static final String URL = ENV.get("DB_URL");
    static final String USER = ENV.get("DB_USER");
    static final String PASS = ENV.get("DB_PASSWORD");

    public static void main(String[] args) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Scanner in = new Scanner(System.in)) {
            System.out.println("Connected! Welcome to MyTix!");
            while (true) {
                System.out.println("\n1) Browse as guest  2) Login as customer  3) Login as organizer  4) Sign up as customer  5) Sign up as organizer  0) Exit");
                try {
                    switch (in.nextLine().trim()) {
                        case "1" -> guestMenu(conn, in);
                        case "2" -> {
                            Integer id = login(conn, in, "Customer");
                            if (id != null) {
                                customerMenu(conn, in, id);
                            }
                        }
                        case "3" -> {
                            Integer id = login(conn, in, "Organizer");
                            if (id != null) {
                                organizerMenu(conn, in, id);
                            }
                        }
                        case "4" -> {
                            int id = signUpCustomer(conn, in);
                            System.out.println("Customer account created. You are now logged in.");
                            customerMenu(conn, in, id);
                        }
                        case "5" -> {
                            int id = signUpOrganizer(conn, in);
                            System.out.println("Organizer account created. You are now logged in.");
                            organizerMenu(conn, in, id);
                        }
                        case "0" -> {
                            return;
                        }
                        default -> System.out.println("Invalid choice.");
                    }
                } catch (Exception e) {
                    printError(e);
                }
            }
        }
    }

    private static Integer login(Connection c, Scanner in, String type) throws SQLException {
        System.out.print(type + " ID (optional; press Enter for email): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT userId FROM Users WHERE userId=? AND accountType=?")) {
                ps.setInt(1, Integer.parseInt(id));
                ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            System.out.println("No matching " + type.toLowerCase() + " account.");
            return null;
        }
        System.out.print(type + " email: ");
        String email = in.nextLine().trim();
        try (PreparedStatement ps = c.prepareStatement("SELECT userId FROM Users WHERE email=? AND accountType=?")) {
            ps.setString(1, email);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        System.out.println("No matching " + type.toLowerCase() + " account.");
        return null;
    }

    private static int signUpCustomer(Connection c, Scanner in) {
        System.out.println("Customer sign-up — date format: YYYY-MM-DD");
        System.out.print("Full name: ");
        String name = in.nextLine().trim();
        System.out.print("Email: ");
        String email = in.nextLine().trim();
        System.out.print("Address: ");
        String address = in.nextLine().trim();
        System.out.print("Date of birth (YYYY-MM-DD): ");
        LocalDate dob = LocalDate.parse(in.nextLine().trim());
        System.out.print("Cardholder name: ");
        String holder = in.nextLine().trim();
        System.out.print("Card number: ");
        String card = in.nextLine().trim();
        System.out.print("Expiry (MM/YY): ");
        String expiry = in.nextLine().trim();
        return new UserOperations(c).createCustomer(name, email, address, dob, holder, card, expiry);
    }

    private static int signUpOrganizer(Connection c, Scanner in) {
        System.out.println("Organizer sign-up — date format: YYYY-MM-DD");
        System.out.print("Full name: ");
        String name = in.nextLine().trim();
        System.out.print("Email: ");
        String email = in.nextLine().trim();
        System.out.print("Address: ");
        String address = in.nextLine().trim();
        System.out.print("Date of birth (YYYY-MM-DD): ");
        LocalDate dob = LocalDate.parse(in.nextLine().trim());
        return new UserOperations(c).createOrganizer(name, email, address, dob);
    }

    private static void customerMenu(Connection c, Scanner in, int customerId) throws SQLException {
        BookingOperations booking = new BookingOperations(c);
        ResaleOperations resale = new ResaleOperations(c);
        ReviewOperations reviews = new ReviewOperations(c);
        while (true) {
            System.out.println("\nCustomer: 1) Reserved booking 2) GA booking 3) Cancel tickets 4) List ticket 5) Withdraw listing 6) Buy listing 7) Review attended event 8) Delete account 0) Logout");
            try {
                switch (in.nextLine().trim()) {
                    case "1" -> {
                        int p = performance(c, in);
                        booking.bookReservedSeats(customerId, p, payment(c, customerId), seats(c, p, in, false));
                    }
                    case "2" -> {
                        int p = performance(c, in);
                        int s = section(c, p, in, false);
                        System.out.print("Quantity: ");
                        booking.bookGeneralAdmission(customerId, p, payment(c, customerId), s, Integer.parseInt(in.nextLine()));
                    }
                    case "3" -> {
                        if (printCancellableTickets(c, customerId)) {
                            System.out.print("Your ticket numbers to cancel (comma-separated): ");
                            booking.cancelTickets(customerId, numbers(in.nextLine()));
                        }
                    }
                    case "4" -> {
                        if (printResellableTickets(c, customerId)) {
                            System.out.print("Your ticket number: ");
                            int ticketId = Integer.parseInt(in.nextLine());
                            System.out.print("Asking price: ");
                            resale.listTicketForResale(ticketId, customerId, Double.parseDouble(in.nextLine()));
                        }
                    }
                    case "5" -> {
                        if (printWithdrawableListings(c, customerId)) {
                            System.out.print("Listing number (0 to go back): ");
                            String listingId = in.nextLine().trim();
                            if (!listingId.equals("0")) {
                                resale.withdrawListing(Integer.parseInt(listingId), customerId);
                            }
                        }
                    }
                    case "6" -> {
                        if (printBuyableListings(c, customerId)) {
                            System.out.print("Listing number (0 to go back): ");
                            String listingId = in.nextLine().trim();
                            if (!listingId.equals("0")) {
                                boolean purchased = resale.purchaseListing(Integer.parseInt(listingId), customerId, payment(c, customerId));
                                System.out.println(purchased ? "Listing purchased." : "Listing is no longer available.");
                            }
                        }
                    }
                    case "7" -> {
                        if (printReviewablePerformances(c, customerId)) {
                            System.out.print("Performance ID: ");
                            int performanceId = Integer.parseInt(in.nextLine());
                            System.out.print("Comment: ");
                            String text = in.nextLine();
                            System.out.print("Event rating (1-5): ");
                            int er = Integer.parseInt(in.nextLine());
                            System.out.print("Venue rating (1-5): ");
                            int vr = Integer.parseInt(in.nextLine());
                            boolean added = reviews.insertReview(customerId, performanceId, text, er, vr);
                            System.out.println(added ? "Review added." : "You have not attended this performance, or already reviewed it.");
                        }
                    }
                    case "8" -> {
                        System.out.print("Delete this account permanently? (y/N): ");
                        if (in.nextLine().trim().equalsIgnoreCase("y") && new UserOperations(c).deleteUser(customerId)) {
                            return;
                        }
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                printError(e);
            }
        }
    }

    private static void organizerMenu(Connection c, Scanner in, int organizerId) throws SQLException {
        EventOperations events = new EventOperations(c);
        OrganizerToolkit toolkit = new OrganizerToolkit(c);
        while (true) {
            System.out.println("\nOrganizer: 1) Create event 2) Add performance 3) Define tier 4) Assign section/tier 5) Set resale cap 6) Update tier price 7) Block seat 8) Cancel performance 9) Unblock seat 10) Add artist/lineup 11) Pricing suggestions 12) Delete account 0) Logout");
            try {
                switch (in.nextLine().trim()) {
                    case "1" -> {
                        int tax = taxonomy(c, in, events);
                        System.out.print("Event title: ");
                        String title = in.nextLine();
                        System.out.print("Description: ");
                        String desc = in.nextLine();
                        System.out.print("Resale cap multiplier (e.g. 1.20): ");
                        events.createEvent(organizerId, tax, title, desc, Double.parseDouble(in.nextLine()));
                    }
                    case "2" -> {
                        int e = ownedEvent(c, in, organizerId);
                        int v = venue(c, in);
                        System.out.print("Performance name: ");
                        String n = in.nextLine();
                        System.out.print("Date/time (YYYY-MM-DDTHH:MM): ");
                        events.addPerformance(e, v, n, LocalDateTime.parse(in.nextLine()));
                    }
                    case "3" -> {
                        int p = ownedPerformance(c, in, organizerId);
                        System.out.print("Tier name: ");
                        String n = in.nextLine();
                        System.out.print("Price: ");
                        events.definePriceTiers(p, List.of(new EventOperations.Tier(n, new BigDecimal(in.nextLine()))));
                    }
                    case "4" -> {
                        int p = ownedPerformance(c, in, organizerId);
                        int s = unassignedSection(c, p, in);
                        int t = tier(c, p, in);
                        events.assignSectionsToTiers(p, Map.of(s, t));
                    }
                    case "5" -> {
                        int e = ownedEvent(c, in, organizerId);
                        System.out.print("New cap multiplier: ");
                        events.setResaleCap(e, Double.parseDouble(in.nextLine()));
                    }
                    case "6" -> {
                        int p = ownedPerformance(c, in, organizerId);
                        int t = tier(c, p, in);
                        System.out.print("New price: ");
                        boolean updated = events.updateTierPrice(p, t, Double.parseDouble(in.nextLine()));
                        System.out.println(updated ? "Price updated." : "Price cannot change after a sale.");
                    }
                    case "7" -> {
                        int p = ownedPerformance(c, in, organizerId);
                        boolean blocked = events.blockSeat(p, seats(c, p, in, false).get(0));
                        System.out.println(blocked ? "Seat blocked." : "Seat has been sold.");
                    }
                    case "8" -> events.cancelPerformance(ownedPerformance(c, in, organizerId));
                    case "9" -> {
                        int p = ownedPerformance(c, in, organizerId);
                        events.unblockSeat(p, seats(c, p, in, true).get(0));
                        System.out.println("Seat unblocked.");
                    }
                    case "10" -> {
                        int e = ownedEvent(c, in, organizerId);
                        int a = artist(c, in, events);
                        System.out.print("Billing order (Headliner, Special guest, Opening act): ");
                        events.addArtistToEvent(e, a, in.nextLine().trim());
                        System.out.println("Artist added to the lineup.");
                    }
                    case "11" -> pricingSuggestions(c, in, organizerId, toolkit);
                    case "12" -> {
                        System.out.print("Delete this account permanently? (y/N): ");
                        if (in.nextLine().trim().equalsIgnoreCase("y") && new UserOperations(c).deleteUser(organizerId)) {
                            return;
                        }
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                printError(e);
            }
        }
    }

    private static void guestMenu(Connection c, Scanner in) throws SQLException {
        SearchQueries q = new SearchQueries(c);
        Reports r = new Reports(c);
        while (true) {
            System.out.println("\nGuest access — queries and reports");
            System.out.println("Formats: date YYYY-MM-DD (e.g. 2026-08-15); date/time YYYY-MM-DDTHH:MM (e.g. 2026-08-15T19:30); amounts 49.99; blank means no filter.");
            System.out.println("Queries: 1) Q1 Location  2) Q2 Postal  3) Q3 Address  4) Q4 Date/capacity  4b) Q4+Q1 Location+date  4c) Q4+Q2 Postal+date  4d) Q4+Q3 Address+date  5) Q5 Filters  6) Q6 Seat map  7) Q7 Best seats");
            System.out.println("Reports: r1) City revenue  r1b) Venue revenue  r2) Taxonomy  r3/r3b/r3c) Organizers  r4) Scalpers  r5) Orders  r6) Cancellations  r7/r7b/r7c) Sell-through  r8) Resale  r9) Comments  0) Back");
            System.out.print("Choice: ");
            String x = in.nextLine().trim();
            try {
                switch (x) {
                    case "1" -> {
                        System.out.print("Latitude: ");
                        double a = Double.parseDouble(in.nextLine());
                        System.out.print("Longitude: ");
                        double b = Double.parseDouble(in.nextLine());
                        System.out.print("Radius km (blank=25): ");
                        String d = in.nextLine();
                        System.out.print("Rank distance/price: ");
                        String by = in.nextLine();
                        System.out.print("asc/desc: ");
                        q.searchByLocation(a, b, d.isBlank() ? 25 : Double.parseDouble(d), by, in.nextLine());
                    }
                    case "2" -> {
                        System.out.print("Postal code: ");
                        q.searchByPostalCode(in.nextLine());
                    }
                    case "3" -> {
                        System.out.print("Exact address: ");
                        q.searchByAddress(in.nextLine());
                    }
                    case "4" -> {
                        System.out.print("Start date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime s = LocalDateTime.parse(in.nextLine());
                        System.out.print("End date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime e = LocalDateTime.parse(in.nextLine());
                        System.out.print("Minimum available tickets (whole number): ");
                        q.searchWithDateRange(s, e, Integer.parseInt(in.nextLine()));
                    }
                    case "4b" -> {
                        System.out.print("Latitude: ");
                        double a = Double.parseDouble(in.nextLine());
                        System.out.print("Longitude: ");
                        double b = Double.parseDouble(in.nextLine());
                        System.out.print("Radius km (blank=25): ");
                        String rk = in.nextLine();
                        System.out.print("Start date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime s = LocalDateTime.parse(in.nextLine());
                        System.out.print("End date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime e = LocalDateTime.parse(in.nextLine());
                        System.out.print("Minimum available tickets (whole number): ");
                        q.searchByLocationWithDateRange(a, b, rk.isBlank() ? 25 : Double.parseDouble(rk), s, e, Integer.parseInt(in.nextLine()));
                    }
                    case "4c" -> {
                        System.out.print("Postal code: ");
                        String pc = in.nextLine();
                        System.out.print("Start date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime s = LocalDateTime.parse(in.nextLine());
                        System.out.print("End date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime e = LocalDateTime.parse(in.nextLine());
                        System.out.print("Minimum available tickets (whole number): ");
                        q.searchByPostalCodeWithDateRange(pc, s, e, Integer.parseInt(in.nextLine()));
                    }
                    case "4d" -> {
                        System.out.print("Exact address: ");
                        String ad = in.nextLine();
                        System.out.print("Start date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime s = LocalDateTime.parse(in.nextLine());
                        System.out.print("End date/time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime e = LocalDateTime.parse(in.nextLine());
                        System.out.print("Minimum available tickets (whole number): ");
                        q.searchByAddressWithDateRange(ad, s, e, Integer.parseInt(in.nextLine()));
                    }
                    case "5" -> filter(q, in);
                    case "6" -> q.seatMapSummary(performance(c, in));
                    case "7" -> {
                        int p = performance(c, in);
                        System.out.print("Quantity: ");
                        int n = Integer.parseInt(in.nextLine());
                        System.out.print("Budget (blank=none): ");
                        String b = in.nextLine();
                        q.bestAvailable(p, n, b.isBlank() ? null : Double.parseDouble(b));
                    }
                    case "r1" -> dates(in, (s, e) -> r.ticketsAndRevenueByCity(s, e));
                    case "r1b" -> dates(in, (s, e) -> {
                        System.out.print("City: ");
                        r.ticketsAndRevenueByVenue(s, e, in.nextLine());
                    });
                    case "r2" -> r.eventCountsByTaxonomyAndLocation();
                    case "r3" -> r.rankOrganizersByRevenueOverall();
                    case "r3b" -> r.rankOrganizersByRevenuePerCountry();
                    case "r3c" -> {
                        System.out.print("City: ");
                        r.rankOrganizersByRevenueByCity(in.nextLine());
                    }
                    case "r4" -> r.possibleScalpersByCity();
                    case "r5" -> dates(in, r::rankCustomersByOrders);
                    case "r6" -> {
                        System.out.print("Year: ");
                        r.mostCancellations(Integer.parseInt(in.nextLine()));
                    }
                    case "r7" -> {
                        System.out.print("Month YYYY-MM-DD: ");
                        String m = in.nextLine();
                        System.out.print("City (blank=all): ");
                        String city = in.nextLine();
                        r.sellThroughByTier(LocalDate.parse(m), city.isBlank() ? null : city);
                    }
                    case "r7b" -> {
                        System.out.print("Month YYYY-MM-DD: ");
                        String m = in.nextLine();
                        System.out.print("City (blank=all): ");
                        String city = in.nextLine();
                        r.sellThroughByPerformance(LocalDate.parse(m), city.isBlank() ? null : city);
                    }
                    case "r7c" -> {
                        System.out.print("Month YYYY-MM-DD: ");
                        String m = in.nextLine();
                        r.sellThroughExtremesByCityForMonth(LocalDate.parse(m));
                    }
                    case "r8" -> dates(in, r::resaleReport);
                    case "r9" -> r.topNounPhrasesByEvent();
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (Exception e) {
                printError(e);
            }
        }
    }

    @FunctionalInterface
    private interface DateReport {
        void run(LocalDate start, LocalDate end);
    }

    private static void printError(Exception error) {
        String message = error.getMessage();
        if (error instanceof NumberFormatException || error instanceof java.time.format.DateTimeParseException) {
            System.out.println("Invalid input. Please use the requested number or date format.");
        } else if (error instanceof IllegalArgumentException) {
            System.out.println("Invalid input" + (message == null ? "." : ": " + message));
        } else if (error instanceof SQLException) {
            System.out.println("Database error. Your request was not completed.");
        } else {
            System.out.println("Unable to complete that request" + (message == null ? "." : ": " + message));
        }
    }

    private static void dates(Scanner in, DateReport report) {
        System.out.print("Start date (YYYY-MM-DD): ");
        LocalDate s = LocalDate.parse(in.nextLine());
        System.out.print("End date (YYYY-MM-DD): ");
        report.run(s, LocalDate.parse(in.nextLine()));
    }

    private static void filter(SearchQueries q, Scanner in) {
        SearchQueries.PerformanceFilter f = new SearchQueries.PerformanceFilter();
        System.out.print("City (text; blank=any): ");
        f.city = blank(in);
        System.out.print("Segment (text; blank=any): ");
        f.segment = blank(in);
        System.out.print("Genre (text; blank=any): ");
        f.genre = blank(in);
        System.out.print("Start (YYYY-MM-DDTHH:MM; blank=any): ");
        String s = in.nextLine();
        if (!s.isBlank()) {
            f.start = LocalDateTime.parse(s);
        }
        System.out.print("End (YYYY-MM-DDTHH:MM; blank=any): ");
        s = in.nextLine();
        if (!s.isBlank()) {
            f.end = LocalDateTime.parse(s);
        }
        System.out.print("Min price (e.g. 49.99; blank=any): ");
        s = in.nextLine();
        if (!s.isBlank()) {
            f.minPrice = Double.parseDouble(s);
        }
        System.out.print("Max price (e.g. 150.00; blank=any): ");
        s = in.nextLine();
        if (!s.isBlank()) {
            f.maxPrice = Double.parseDouble(s);
        }
        System.out.print("Min available tickets (whole number; blank=any): ");
        s = in.nextLine();
        if (!s.isBlank()) {
            f.minAvailable = Integer.parseInt(s);
        }
        System.out.print("Seating: reserved, GA, or blank for any: ");
        s = in.nextLine();
        if (s.equalsIgnoreCase("reserved")) {
            f.reservedSeating = true;
        }
        if (s.equalsIgnoreCase("ga")) {
            f.reservedSeating = false;
        }
        q.filteredSearch(f);
    }

    private static String blank(Scanner in) {
        String s = in.nextLine().trim();
        return s.isBlank() ? null : s;
    }

    private static int performance(Connection c, Scanner in) throws SQLException {
        System.out.print("Performance ID (optional; press Enter to search by name and time): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT performanceId FROM Performances WHERE performanceId=?", Integer.parseInt(id));
        }
        System.out.print("Performance name: ");
        String n = in.nextLine();
        System.out.print("Date/time YYYY-MM-DDTHH:MM: ");
        String d = in.nextLine().trim();
        LocalDateTime.parse(d);
        return one(c, "SELECT performanceId FROM Performances WHERE name=? AND DATE_FORMAT(dateTime, '%Y-%m-%dT%H:%i')=?", n, d);
    }

    private static int ownedPerformance(Connection c, Scanner in, int o) throws SQLException {
        printOwnedPerformances(c, o);
        int p = performance(c, in);
        return one(c, "SELECT p.performanceId FROM Performances p JOIN Events e ON e.eventId=p.eventId WHERE p.performanceId=? AND e.organizerId=?", p, o);
    }

    private static int ownedEvent(Connection c, Scanner in, int o) throws SQLException {
        printOwnedEvents(c, o);
        System.out.print("Event ID (optional; press Enter for title): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT eventId FROM Events WHERE eventId=? AND organizerId=?", Integer.parseInt(id), o);
        }
        System.out.print("Exact event title: ");
        return one(c, "SELECT eventId FROM Events WHERE title=? AND organizerId=?", in.nextLine(), o);
    }

    private static int venue(Connection c, Scanner in) throws SQLException {
        printVenues(c);
        System.out.print("Venue ID (optional; press Enter for exact address): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT venueId FROM Venues WHERE venueId=?", Integer.parseInt(id));
        }
        System.out.print("Venue exact address: ");
        return one(c, "SELECT venueId FROM Venues WHERE address=?", in.nextLine());
    }

    private static int section(Connection c, int p, Scanner in) throws SQLException {
        return section(c, p, in, null);
    }

    private static int section(Connection c, int p, Scanner in, Boolean reservedOnly) throws SQLException {
        printSections(c, p, reservedOnly);
        System.out.print("Section ID (optional; press Enter for section name): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            String sql = "SELECT psa.sectionId FROM PerformanceSectionAssignments psa "
                + "JOIN Sections s ON s.sectionId=psa.sectionId "
                + "WHERE psa.performanceId=? AND psa.sectionId=?"
                + (reservedOnly == null ? "" : " AND s.isReservedSeating=?");
            Object[] params = reservedOnly == null
                ? new Object[]{p, Integer.parseInt(id)}
                : new Object[]{p, Integer.parseInt(id), reservedOnly};
            return one(c, sql, params);
        }
        System.out.print("Section name: ");
        String name = in.nextLine();
        String sql = "SELECT psa.sectionId FROM PerformanceSectionAssignments psa "
            + "JOIN Sections s ON s.sectionId=psa.sectionId "
            + "WHERE psa.performanceId=? AND s.sectionName=?"
            + (reservedOnly == null ? "" : " AND s.isReservedSeating=?");
        Object[] params = reservedOnly == null ? new Object[]{p, name} : new Object[]{p, name, reservedOnly};
        return one(c, sql, params);
    }

    private static int unassignedSection(Connection c, int performanceId, Scanner in) throws SQLException {
        String sql = "SELECT s.sectionId,s.sectionName,CASE WHEN s.isReservedSeating THEN 'Reserved' ELSE 'General admission' END seating "
            + "FROM Sections s "
            + "JOIN Performances p ON p.venueId=s.venueId "
            + "LEFT JOIN PerformanceSectionAssignments psa ON psa.performanceId=p.performanceId AND psa.sectionId=s.sectionId "
            + "WHERE p.performanceId=? AND psa.sectionId IS NULL "
            + "ORDER BY s.sectionName";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, performanceId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Sections still needing a tier assignment:");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("  %d) %s — %s%n", rs.getInt("sectionId"), rs.getString("sectionName"), rs.getString("seating"));
                }
                if (!found) {
                    throw new IllegalArgumentException("Every section already has a price tier assigned.");
                }
            }
        }
        System.out.print("Section ID: ");
        int sectionId = Integer.parseInt(in.nextLine().trim());
        return one(c, "SELECT s.sectionId FROM Sections s "
            + "JOIN Performances p ON p.venueId=s.venueId "
            + "LEFT JOIN PerformanceSectionAssignments psa ON psa.performanceId=p.performanceId AND psa.sectionId=s.sectionId "
            + "WHERE p.performanceId=? AND s.sectionId=? AND psa.sectionId IS NULL", performanceId, sectionId);
    }

    private static int section(Connection c, int p, String n) throws SQLException {
        return one(c, "SELECT s.sectionId FROM Sections s JOIN PerformanceSectionAssignments x ON x.sectionId=s.sectionId WHERE x.performanceId=? AND s.sectionName=?", p, n);
    }

    private static int tier(Connection c, int p, Scanner in) throws SQLException {
        printTiers(c, p);
        System.out.print("Tier ID (optional; press Enter for tier name): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT tierId FROM PriceTiers WHERE performanceId=? AND tierId=?", p, Integer.parseInt(id));
        }
        System.out.print("Tier name: ");
        return tier(c, p, in.nextLine());
    }

    private static int tier(Connection c, int p, String n) throws SQLException {
        return one(c, "SELECT tierId FROM PriceTiers WHERE performanceId=? AND tierName=?", p, n);
    }

    private static int artist(Connection c, Scanner in, EventOperations events) throws SQLException {
        printArtists(c);
        System.out.print("Artist ID (optional; press Enter to find or create by name): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT artistId FROM Artists WHERE artistId=?", Integer.parseInt(id));
        }
        System.out.print("Artist name: ");
        return events.findOrCreateArtist(in.nextLine());
    }

    private static int taxonomy(Connection c, Scanner in, EventOperations events) throws SQLException {
        printTaxonomies(c);
        System.out.print("Taxonomy ID (optional; press Enter to add a new segment/genre): ");
        String id = in.nextLine().trim();
        if (!id.isBlank()) {
            return one(c, "SELECT taxonomyId FROM Taxonomy WHERE taxonomyId=?", Integer.parseInt(id));
        }
        System.out.print("Ticketmaster segment (e.g. Music, Arts & Theatre, Sports): ");
        String segment = in.nextLine();
        System.out.print("Genre: ");
        String genre = in.nextLine();
        return events.createTaxonomy(segment, genre);
    }

    private static void pricingSuggestions(Connection c, Scanner in, int organizerId, OrganizerToolkit toolkit) throws SQLException {
        int v = venue(c, in);
        System.out.print("Genre: ");
        String g = in.nextLine().trim();
        toolkit.suggestTierStructure(v, g);
        toolkit.suggestTierPrices(v, g);
        System.out.print("Estimate a price change too? (y/N): ");
        if (in.nextLine().trim().equalsIgnoreCase("y")) {
            int p = ownedPerformance(c, in, organizerId);
            int t = tier(c, p, in);
            System.out.print("Proposed price: ");
            toolkit.estimateRevenueImpact(t, new BigDecimal(in.nextLine().trim()));
        }
    }

    private static int payment(Connection c, int u) throws SQLException {
        return one(c, "SELECT paymentId FROM PaymentMethods WHERE customerId=? ORDER BY paymentId DESC LIMIT 1", u);
    }

    private static boolean printResellableTickets(Connection c, int customerId) throws SQLException {
        String sql = "SELECT t.ticketId,e.title,p.name performanceName,v.name venueName,s.sectionName, "
            + "COALESCE(CONCAT('Seat ',r.rowName,'-',st.seatNumber),'General admission') seat, t.price "
            + "FROM Tickets t "
            + "JOIN Performances p ON p.performanceId=t.performanceId "
            + "JOIN Events e ON e.eventId=p.eventId "
            + "JOIN Venues v ON v.venueId=p.venueId "
            + "JOIN Sections s ON s.sectionId=t.sectionId "
            + "LEFT JOIN Seats st ON st.seatId=t.seatId "
            + "LEFT JOIN SectionRows r ON r.rowId=st.rowId "
            + "LEFT JOIN ResaleListings rl ON rl.ticketId=t.ticketId AND rl.status='Active' "
            + "WHERE t.currentOwnerId=? AND t.status='Active' AND rl.listingId IS NULL "
            + "ORDER BY p.dateTime,t.ticketId";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    if (!found) {
                        System.out.println("Tickets available to list for resale:");
                    }
                    found = true;
                    System.out.printf("  %d) %s — %s at %s, %s, face value $%.2f%n",
                        rs.getInt("ticketId"), rs.getString("title"), rs.getString("performanceName"),
                        rs.getString("venueName"), rs.getString("seat"), rs.getDouble("price"));
                }
                if (!found) {
                    System.out.println("No tickets purchased are available to list for resale.");
                }
                return found;
            }
        }
    }

    private static boolean printCancellableTickets(Connection c, int customerId) throws SQLException {
        String sql = "SELECT t.ticketId,e.title,p.name performanceName,p.dateTime, "
            + "COALESCE(CONCAT('Seat ',r.rowName,'-',st.seatNumber),'General admission') seat,t.price "
            + "FROM Tickets t "
            + "JOIN Orders o ON o.orderId=t.orderId "
            + "JOIN Performances p ON p.performanceId=t.performanceId "
            + "JOIN Events e ON e.eventId=p.eventId "
            + "LEFT JOIN Seats st ON st.seatId=t.seatId "
            + "LEFT JOIN SectionRows r ON r.rowId=st.rowId "
            + "WHERE o.customerId=? AND t.status='Active' AND p.dateTime>=NOW()+INTERVAL 7 DAY "
            + "ORDER BY p.dateTime,t.ticketId";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    if (!found) {
                        System.out.println("Tickets available to cancel:");
                    }
                    found = true;
                    System.out.printf("  %d) %s — %s on %s, %s, $%.2f%n",
                        rs.getInt("ticketId"), rs.getString("title"), rs.getString("performanceName"),
                        rs.getTimestamp("dateTime"), rs.getString("seat"), rs.getDouble("price"));
                }
                if (!found) {
                    System.out.println("No tickets purchased are available to cancel.");
                }
                return found;
            }
        }
    }

    private static boolean printWithdrawableListings(Connection c, int customerId) throws SQLException {
        String sql = "SELECT rl.listingId,rl.ticketId,e.title,p.name performanceName,rl.resalePrice,rl.postedDate "
            + "FROM ResaleListings rl "
            + "JOIN Tickets t ON t.ticketId=rl.ticketId "
            + "JOIN Performances p ON p.performanceId=t.performanceId "
            + "JOIN Events e ON e.eventId=p.eventId "
            + "WHERE rl.sellerId=? AND rl.status='Active' "
            + "ORDER BY rl.postedDate";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    if (!found) {
                        System.out.println("Active resale listings (enter 0 to go back):");
                    }
                    found = true;
                    System.out.printf("  %d) Ticket %d — %s / %s, asking $%.2f%n",
                        rs.getInt("listingId"), rs.getInt("ticketId"), rs.getString("title"),
                        rs.getString("performanceName"), rs.getDouble("resalePrice"));
                }
                if (!found) {
                    System.out.println("No active resale listings.");
                }
                return found;
            }
        }
    }

    private static boolean printBuyableListings(Connection c, int customerId) throws SQLException {
        String sql = "SELECT rl.listingId,e.title,p.name performanceName,v.name venueName, "
            + "COALESCE(CONCAT('Seat ',r.rowName,'-',st.seatNumber),'General admission') seat,rl.resalePrice,t.price faceValue "
            + "FROM ResaleListings rl "
            + "JOIN Tickets t ON t.ticketId=rl.ticketId "
            + "JOIN Performances p ON p.performanceId=t.performanceId "
            + "JOIN Events e ON e.eventId=p.eventId "
            + "JOIN Venues v ON v.venueId=p.venueId "
            + "LEFT JOIN Seats st ON st.seatId=t.seatId "
            + "LEFT JOIN SectionRows r ON r.rowId=st.rowId "
            + "WHERE rl.status='Active' AND rl.sellerId<>? "
            + "ORDER BY p.dateTime,rl.listingId";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    if (!found) {
                        System.out.println("Listings available to buy (enter 0 to go back):");
                    }
                    found = true;
                    System.out.printf("  %d) %s — %s at %s, %s, asking $%.2f (face value $%.2f)%n",
                        rs.getInt("listingId"), rs.getString("title"), rs.getString("performanceName"),
                        rs.getString("venueName"), rs.getString("seat"), rs.getDouble("resalePrice"), rs.getDouble("faceValue"));
                }
                if (!found) {
                    System.out.println("No resale listings are currently available.");
                }
                return found;
            }
        }
    }

    private static boolean printReviewablePerformances(Connection c, int customerId) throws SQLException {
        String sql = "SELECT DISTINCT p.performanceId,e.title,p.name performanceName,p.dateTime,v.name venueName "
            + "FROM Tickets t "
            + "JOIN Performances p ON p.performanceId=t.performanceId "
            + "JOIN Events e ON e.eventId=p.eventId "
            + "JOIN Venues v ON v.venueId=p.venueId "
            + "LEFT JOIN Comments c ON c.customerId=? AND c.performanceId=p.performanceId "
            + "WHERE t.currentOwnerId=? AND t.status='Active' AND p.dateTime<NOW() AND c.commentId IS NULL "
            + "ORDER BY p.dateTime DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    if (!found) {
                        System.out.println("Attended performances available to review:");
                    }
                    found = true;
                    System.out.printf("  %d) %s — %s at %s on %s%n",
                        rs.getInt("performanceId"), rs.getString("title"), rs.getString("performanceName"),
                        rs.getString("venueName"), rs.getTimestamp("dateTime"));
                }
                if (!found) {
                    System.out.println("No attended events are available to review.");
                }
                return found;
            }
        }
    }

    private static int one(Connection c, String sql, Object... v) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < v.length; i++) {
                p.setObject(i + 1, v[i]);
            }
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    throw new IllegalArgumentException("No matching record found.");
                }
                return r.getInt(1);
            }
        }
    }

    private static List<Integer> seats(Connection c, int p, Scanner in, boolean blockedOnly) throws SQLException {
        int sec = section(c, p, in, true);
        printRows(c, p, sec, blockedOnly);
        System.out.print("Row name: ");
        String row = in.nextLine();
        printSeatOptions(c, p, sec, row, blockedOnly);
        System.out.print("Seat number(s), comma-separated: ");
        String ns = in.nextLine();
        List<Integer> out = new ArrayList<>();
        for (String n : ns.split(",")) {
            out.add(one(c, "SELECT st.seatId FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId WHERE r.sectionId=? AND r.rowName=? AND st.seatNumber=?",
                sec, row, Integer.parseInt(n.trim())));
        }
        return out;
    }

    private static void printSections(Connection c, int performanceId, Boolean reservedOnly) throws SQLException {
        String sql = "SELECT s.sectionId,s.sectionName,CASE WHEN s.isReservedSeating THEN 'Reserved' ELSE 'General admission' END seating,pt.tierName,pt.price "
            + "FROM PerformanceSectionAssignments psa "
            + "JOIN Sections s ON s.sectionId=psa.sectionId "
            + "JOIN PriceTiers pt ON pt.tierId=psa.tierId "
            + "WHERE psa.performanceId=?"
            + (reservedOnly == null ? "" : " AND s.isReservedSeating=?")
            + " ORDER BY s.sectionName";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, performanceId);
            if (reservedOnly != null) {
                ps.setBoolean(2, reservedOnly);
            }
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Available sections:");
                while (rs.next()) {
                    System.out.printf("  %d) %s — %s, %s ($%.2f)%n",
                        rs.getInt("sectionId"), rs.getString("sectionName"), rs.getString("seating"),
                        rs.getString("tierName"), rs.getDouble("price"));
                }
            }
        }
    }

    private static String seatCondition(boolean blockedOnly) {
        if (blockedOnly) {
            return "EXISTS (SELECT 1 FROM BlockedSeats bs WHERE bs.performanceId=? AND bs.seatId=st.seatId)";
        }
        return "NOT EXISTS (SELECT 1 FROM BlockedSeats bs WHERE bs.performanceId=? AND bs.seatId=st.seatId) "
            + "AND NOT EXISTS (SELECT 1 FROM Tickets t WHERE t.performanceId=? AND t.seatId=st.seatId AND t.status='Active')";
    }

    private static void printTaxonomies(Connection c) throws SQLException {
        printOptions(c, "Existing taxonomy choices:", "SELECT taxonomyId,segment,genre FROM Taxonomy ORDER BY segment,genre",
            rs -> String.format("  %d) %s / %s", rs.getInt("taxonomyId"), rs.getString("segment"), rs.getString("genre")));
    }

    private static void printOwnedEvents(Connection c, int organizerId) throws SQLException {
        printOptions(c, "Your events:", "SELECT eventId,title,resalePriceCap FROM Events WHERE organizerId=? ORDER BY eventId",
            new Object[]{organizerId},
            rs -> String.format("  %d) %s (resale cap %.2fx)", rs.getInt("eventId"), rs.getString("title"), rs.getDouble("resalePriceCap")));
    }

    private static void printOwnedPerformances(Connection c, int organizerId) throws SQLException {
        printOptions(c, "Your scheduled performances:",
            "SELECT p.performanceId,e.title,p.name,p.dateTime,p.status FROM Performances p "
                + "JOIN Events e ON e.eventId=p.eventId WHERE e.organizerId=? AND p.status='Scheduled' ORDER BY p.dateTime",
            new Object[]{organizerId},
            rs -> String.format("  %d) %s — %s on %s (%s)",
                rs.getInt("performanceId"), rs.getString("title"), rs.getString("name"), rs.getTimestamp("dateTime"), rs.getString("status")));
    }

    private static void printVenues(Connection c) throws SQLException {
        printOptions(c, "Venues:", "SELECT venueId,name,address,city,country FROM Venues ORDER BY city,name",
            rs -> String.format("  %d) %s — %s, %s, %s",
                rs.getInt("venueId"), rs.getString("name"), rs.getString("address"), rs.getString("city"), rs.getString("country")));
    }

    private static void printTiers(Connection c, int performanceId) throws SQLException {
        printOptions(c, "Price tiers:", "SELECT tierId,tierName,price FROM PriceTiers WHERE performanceId=? ORDER BY price DESC",
            new Object[]{performanceId},
            rs -> String.format("  %d) %s ($%.2f)", rs.getInt("tierId"), rs.getString("tierName"), rs.getDouble("price")));
    }

    private static void printArtists(Connection c) throws SQLException {
        printOptions(c, "Artists:", "SELECT artistId,name FROM Artists ORDER BY name",
            rs -> String.format("  %d) %s", rs.getInt("artistId"), rs.getString("name")));
    }

    @FunctionalInterface
    private interface OptionText {
        String format(ResultSet result) throws SQLException;
    }

    private static void printOptions(Connection c, String heading, String sql, OptionText formatter) throws SQLException {
        printOptions(c, heading, sql, new Object[]{}, formatter);
    }

    private static void printOptions(Connection c, String heading, String sql, Object[] params, OptionText formatter) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println(heading);
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println(formatter.format(rs));
                }
                if (!found) {
                    System.out.println("  No options are available.");
                }
            }
        }
    }

    private static void printRows(Connection c, int performanceId, int sectionId, boolean blockedOnly) throws SQLException {
        String sql = "SELECT r.rowName,COUNT(st.seatId) seats FROM SectionRows r "
            + "JOIN Seats st ON st.rowId=r.rowId "
            + "WHERE r.sectionId=? AND " + seatCondition(blockedOnly)
            + " GROUP BY r.rowId,r.rowName ORDER BY r.rowName";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sectionId);
            ps.setInt(2, performanceId);
            if (!blockedOnly) {
                ps.setInt(3, performanceId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println(blockedOnly ? "Rows with blocked seats:" : "Rows with available seats:");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("  %s (%d selectable seats)%n", rs.getString("rowName"), rs.getInt("seats"));
                }
                if (!found) {
                    System.out.println("  No selectable rows are available.");
                }
            }
        }
    }

    private static void printSeatOptions(Connection c, int performanceId, int sectionId, String rowName, boolean blockedOnly) throws SQLException {
        String sql = "SELECT st.seatNumber FROM Seats st "
            + "JOIN SectionRows r ON r.rowId=st.rowId "
            + "WHERE r.sectionId=? AND r.rowName=? AND " + seatCondition(blockedOnly)
            + " ORDER BY st.seatNumber";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sectionId);
            ps.setString(2, rowName);
            ps.setInt(3, performanceId);
            if (!blockedOnly) {
                ps.setInt(4, performanceId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println(blockedOnly ? "Blocked seat options:" : "Available seat options:");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.printf("  %d%n", rs.getInt("seatNumber"));
                }
                if (!found) {
                    System.out.println("  No selectable seats are available in this row.");
                }
            }
        }
    }

    private static List<Integer> numbers(String text) {
        List<Integer> out = new ArrayList<>();
        for (String n : text.split(",")) {
            out.add(Integer.parseInt(n.trim()));
        }
        return out;
    }
}
