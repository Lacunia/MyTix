import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Console entry point.  Guests, customers, and organizers are deliberately
 * given separate menus; database IDs remain an implementation detail. */
public class DbConnector {
    private static final EnvConfig ENV = new EnvConfig();
    static final String URL = ENV.get("DB_URL"), USER = ENV.get("DB_USER"), PASS = ENV.get("DB_PASSWORD");

    public static void main(String[] args) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS); Scanner in = new Scanner(System.in)) {
            System.out.println("Connected! Welcome to MyTix!");
            while (true) {
                System.out.println("\n1) Browse as guest  2) Login as customer  3) Login as organizer  4) Sign up as customer  5) Sign up as organizer  0) Exit");
                switch (in.nextLine().trim()) {
                    case "1" -> guestMenu(conn, in);
                    case "2" -> { Integer id = login(conn, in, "Customer"); if (id != null) customerMenu(conn, in, id); }
                    case "3" -> { Integer id = login(conn, in, "Organizer"); if (id != null) organizerMenu(conn, in, id); }
                    case "4" -> { int id = signUpCustomer(conn, in); System.out.println("Customer account created. You are now logged in."); customerMenu(conn, in, id); }
                    case "5" -> { int id = signUpOrganizer(conn, in); System.out.println("Organizer account created. You are now logged in."); organizerMenu(conn, in, id); }
                    case "0" -> { return; }
                    default -> System.out.println("Invalid choice.");
                }
            }
        }
    }

    private static Integer login(Connection c, Scanner in, String type) throws SQLException {
        System.out.print(type + " ID (optional; press Enter for email): "); String id = in.nextLine().trim();
        if (!id.isBlank()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT userId FROM Users WHERE userId=? AND accountType=?")) {
                ps.setInt(1, Integer.parseInt(id)); ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
            }
            System.out.println("No matching " + type.toLowerCase() + " account."); return null;
        }
        System.out.print(type + " email: "); String email = in.nextLine().trim();
        try (PreparedStatement ps = c.prepareStatement("SELECT userId FROM Users WHERE email=? AND accountType=?")) {
            ps.setString(1, email); ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        System.out.println("No matching " + type.toLowerCase() + " account."); return null;
    }

    private static int signUpCustomer(Connection c, Scanner in) {
        System.out.println("Customer sign-up — date format: YYYY-MM-DD");
        System.out.print("Full name: "); String name=in.nextLine().trim();
        System.out.print("Email: "); String email=in.nextLine().trim();
        System.out.print("Address: "); String address=in.nextLine().trim();
        System.out.print("Date of birth (YYYY-MM-DD): "); LocalDate dob=LocalDate.parse(in.nextLine().trim());
        System.out.print("Cardholder name: "); String holder=in.nextLine().trim();
        System.out.print("Card number: "); String card=in.nextLine().trim();
        System.out.print("Expiry (MM/YY): "); String expiry=in.nextLine().trim();
        return new UserOperations(c).createCustomer(name,email,address,dob,holder,card,expiry);
    }

    private static int signUpOrganizer(Connection c, Scanner in) {
        System.out.println("Organizer sign-up — date format: YYYY-MM-DD");
        System.out.print("Full name: "); String name=in.nextLine().trim();
        System.out.print("Email: "); String email=in.nextLine().trim();
        System.out.print("Address: "); String address=in.nextLine().trim();
        System.out.print("Date of birth (YYYY-MM-DD): "); LocalDate dob=LocalDate.parse(in.nextLine().trim());
        return new UserOperations(c).createOrganizer(name,email,address,dob);
    }

    private static void customerMenu(Connection c, Scanner in, int customerId) throws SQLException {
        BookingOperations booking = new BookingOperations(c); ResaleOperations resale = new ResaleOperations(c); ReviewOperations reviews = new ReviewOperations(c);
        while (true) {
            System.out.println("\nCustomer: 1) Reserved booking 2) GA booking 3) Cancel tickets 4) List ticket 5) Withdraw listing 6) Buy listing 7) Review attended event 8) Delete account 0) Logout");
            switch (in.nextLine().trim()) {
                case "1" -> { int p=performance(c,in); booking.bookReservedSeats(customerId,p,payment(c,customerId),seats(c,p,in)); }
                case "2" -> { int p=performance(c,in); int s=section(c,p,in); System.out.print("Quantity: "); booking.bookGeneralAdmission(customerId,p,payment(c,customerId),s,Integer.parseInt(in.nextLine())); }
                case "3" -> { System.out.print("Your ticket numbers to cancel (comma-separated): "); booking.cancelTickets(customerId, numbers(in.nextLine())); }
                case "4" -> { System.out.print("Your ticket number: "); System.out.print("Asking price: "); resale.listTicketForResale(Integer.parseInt(in.nextLine()),customerId,Double.parseDouble(in.nextLine())); }
                case "5" -> { System.out.print("Listing number: "); resale.withdrawListing(Integer.parseInt(in.nextLine()),customerId); }
                case "6" -> { System.out.print("Listing number: "); resale.purchaseListing(Integer.parseInt(in.nextLine()),customerId,payment(c,customerId)); }
                case "7" -> { System.out.print("Exact event title: "); String title=in.nextLine(); System.out.print("Comment: "); String text=in.nextLine(); System.out.print("Event rating (1-5): "); int er=Integer.parseInt(in.nextLine()); System.out.print("Venue rating (1-5): "); System.out.println(reviews.insertReview(customerId,title,text,er,Integer.parseInt(in.nextLine())) ? "Review added." : "You have not attended this event, or already reviewed it."); }
                case "8" -> { System.out.print("Delete this account permanently? (y/N): "); if (in.nextLine().trim().equalsIgnoreCase("y") && new UserOperations(c).deleteUser(customerId)) return; }
                case "0" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void organizerMenu(Connection c, Scanner in, int organizerId) throws SQLException {
        EventOperations events = new EventOperations(c);
        OrganizerToolkit toolkit = new OrganizerToolkit(c);
        while (true) {
            System.out.println("\nOrganizer: 1) Create event 2) Add performance 3) Define tier 4) Assign section/tier 5) Set resale cap 6) Update tier price 7) Block seat 8) Cancel performance 9) Unblock seat 10) Add artist/lineup 11) Pricing suggestions 12) Delete account 0) Logout");
            switch (in.nextLine().trim()) {
                case "1" -> { System.out.print("Ticketmaster segment (e.g. Music, Arts & Theatre, Sports): "); String segment=in.nextLine(); System.out.print("Genre: "); String genre=in.nextLine(); System.out.print("Event title: "); String title=in.nextLine(); System.out.print("Description: "); String desc=in.nextLine(); System.out.print("Resale cap multiplier (e.g. 1.20): "); events.createEvent(organizerId,events.createTaxonomy(segment,genre),title,desc,Double.parseDouble(in.nextLine())); }
                case "2" -> { int e=ownedEvent(c,in,organizerId); int v=venue(c,in); System.out.print("Performance name: "); String n=in.nextLine(); System.out.print("Date/time (YYYY-MM-DDTHH:MM): "); events.addPerformance(e,v,n,LocalDateTime.parse(in.nextLine())); }
                case "3" -> { int p=ownedPerformance(c,in,organizerId); System.out.print("Tier name: "); String n=in.nextLine(); System.out.print("Price: "); events.definePriceTiers(p,List.of(new EventOperations.Tier(n,new BigDecimal(in.nextLine())))); }
                case "4" -> { int p=ownedPerformance(c,in,organizerId); int s=section(c,p,in); int t=tier(c,p,in); events.assignSectionsToTiers(p,Map.of(s,t)); }
                case "5" -> { int e=ownedEvent(c,in,organizerId); System.out.print("New cap multiplier: "); events.setResaleCap(e,Double.parseDouble(in.nextLine())); }
                case "6" -> { int p=ownedPerformance(c,in,organizerId); int t=tier(c,p,in); System.out.print("New price: "); System.out.println(events.updateTierPrice(p,t,Double.parseDouble(in.nextLine())) ? "Price updated." : "Price cannot change after a sale."); }
                case "7" -> { int p=ownedPerformance(c,in,organizerId); System.out.println(events.blockSeat(p,seats(c,p,in).get(0)) ? "Seat blocked." : "Seat has been sold."); }
                case "8" -> events.cancelPerformance(ownedPerformance(c,in,organizerId));
                case "9" -> { int p=ownedPerformance(c,in,organizerId); events.unblockSeat(p,seats(c,p,in).get(0)); System.out.println("Seat unblocked."); }
                case "10" -> { int e=ownedEvent(c,in,organizerId); int a=artist(c,in,events); System.out.print("Billing order (Headliner, Special guest, Opening act): "); events.addArtistToEvent(e,a,in.nextLine().trim()); System.out.println("Artist added to the lineup."); }
                case "11" -> pricingSuggestions(c,in,organizerId,toolkit);
                case "12" -> { System.out.print("Delete this account permanently? (y/N): "); if (in.nextLine().trim().equalsIgnoreCase("y") && new UserOperations(c).deleteUser(organizerId)) return; }
                case "0" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void guestMenu(Connection c, Scanner in) throws SQLException {
        SearchQueries q=new SearchQueries(c); Reports r=new Reports(c);
        while(true) {
            System.out.println("\nGuest access — queries and reports");
            System.out.println("Formats: date YYYY-MM-DD (e.g. 2026-08-15); date/time YYYY-MM-DDTHH:MM (e.g. 2026-08-15T19:30); amounts 49.99; blank means no filter.");
            System.out.println("Queries: 1) Q1 Location  2) Q2 Postal  3) Q3 Address  4) Q4 Date/capacity  5) Q5 Filters  6) Q6 Seat map  7) Q7 Best seats");
            System.out.println("Reports: r1) City revenue  r1b) Venue revenue  r2) Taxonomy  r3/r3b/r3c) Organizers  r4) Scalpers  r5) Orders  r6) Cancellations  r7) Sell-through  r8) Resale  r9) Comments  0) Back");
            System.out.print("Choice: "); String x=in.nextLine().trim();
            switch(x) {
                case "1" -> { System.out.print("Latitude: "); double a=Double.parseDouble(in.nextLine()); System.out.print("Longitude: "); double b=Double.parseDouble(in.nextLine()); System.out.print("Radius km (blank=25): "); String d=in.nextLine(); System.out.print("Rank distance/price: "); String by=in.nextLine(); System.out.print("asc/desc: "); q.searchByLocation(a,b,d.isBlank()?25:Double.parseDouble(d),by,in.nextLine()); }
                case "2" -> { System.out.print("Postal code: "); q.searchByPostalCode(in.nextLine()); } case "3" -> { System.out.print("Exact address: "); q.searchByAddress(in.nextLine()); }
                case "4" -> { System.out.print("Start date/time (YYYY-MM-DDTHH:MM): "); LocalDateTime s=LocalDateTime.parse(in.nextLine()); System.out.print("End date/time (YYYY-MM-DDTHH:MM): "); LocalDateTime e=LocalDateTime.parse(in.nextLine()); System.out.print("Minimum available tickets (whole number): "); q.searchWithDateRange(s,e,Integer.parseInt(in.nextLine())); }
                case "5" -> filter(q,in); case "6" -> q.seatMapSummary(performance(c,in)); case "7" -> { int p=performance(c,in); System.out.print("Quantity: "); int n=Integer.parseInt(in.nextLine()); System.out.print("Budget (blank=none): "); String b=in.nextLine(); q.bestAvailable(p,n,b.isBlank()?null:Double.parseDouble(b)); }
                case "r1" -> dates(in,(s,e)->r.ticketsAndRevenueByCity(s,e)); case "r1b" -> dates(in,(s,e)->{System.out.print("City: ");r.ticketsAndRevenueByVenue(s,e,in.nextLine());}); case "r2" -> r.eventCountsByTaxonomyAndLocation(); case "r3" -> r.rankOrganizersByRevenueOverall(); case "r3b" -> r.rankOrganizersByRevenuePerCountry(); case "r3c" -> {System.out.print("City: ");r.rankOrganizersByRevenueByCity(in.nextLine());} case "r4" -> r.possibleScalpersByCity(); case "r5" -> dates(in,r::rankCustomersByOrders); case "r6" -> {System.out.print("Year: ");r.mostCancellations(Integer.parseInt(in.nextLine()));} case "r7" -> {System.out.print("Month YYYY-MM-DD: ");String m=in.nextLine();System.out.print("City (blank=all): ");String city=in.nextLine();r.sellThroughReport(LocalDate.parse(m),city.isBlank()?null:city);} case "r8" -> dates(in,r::resaleReport); case "r9" -> r.topNounPhrasesByEvent(); case "0" -> {return;} default -> System.out.println("Invalid choice."); }
        }
    }

    @FunctionalInterface private interface DateReport { void run(LocalDate start, LocalDate end); }
    private static void dates(Scanner in, DateReport report) { System.out.print("Start date (YYYY-MM-DD): ");LocalDate s=LocalDate.parse(in.nextLine());System.out.print("End date (YYYY-MM-DD): ");report.run(s,LocalDate.parse(in.nextLine())); }
    private static void filter(SearchQueries q, Scanner in) { SearchQueries.PerformanceFilter f=new SearchQueries.PerformanceFilter();System.out.print("City (text; blank=any): ");f.city=blank(in);System.out.print("Segment (text; blank=any): ");f.segment=blank(in);System.out.print("Genre (text; blank=any): ");f.genre=blank(in);System.out.print("Start (YYYY-MM-DDTHH:MM; blank=any): ");String s=in.nextLine();if(!s.isBlank())f.start=LocalDateTime.parse(s);System.out.print("End (YYYY-MM-DDTHH:MM; blank=any): ");s=in.nextLine();if(!s.isBlank())f.end=LocalDateTime.parse(s);System.out.print("Min price (e.g. 49.99; blank=any): ");s=in.nextLine();if(!s.isBlank())f.minPrice=Double.parseDouble(s);System.out.print("Max price (e.g. 150.00; blank=any): ");s=in.nextLine();if(!s.isBlank())f.maxPrice=Double.parseDouble(s);System.out.print("Min available tickets (whole number; blank=any): ");s=in.nextLine();if(!s.isBlank())f.minAvailable=Integer.parseInt(s);System.out.print("Seating: reserved, GA, or blank for any: ");s=in.nextLine();if(s.equalsIgnoreCase("reserved"))f.reservedSeating=true; if(s.equalsIgnoreCase("ga"))f.reservedSeating=false;q.filteredSearch(f); }
    private static String blank(Scanner in){String s=in.nextLine().trim();return s.isBlank()?null:s;}
    private static int performance(Connection c,Scanner in)throws SQLException{
        System.out.print("Performance ID (optional; press Enter to search by name and time): ");
        String id=in.nextLine().trim();
        if(!id.isBlank()) return one(c,"SELECT performanceId FROM Performances WHERE performanceId=?",Integer.parseInt(id));
        System.out.print("Performance name: ");String n=in.nextLine();
        System.out.print("Date/time YYYY-MM-DDTHH:MM: ");String d=in.nextLine().trim();
        LocalDateTime.parse(d);
        return one(c,"SELECT performanceId FROM Performances WHERE name=? AND DATE_FORMAT(dateTime, '%Y-%m-%dT%H:%i')=?",n,d);
    }
    private static int ownedPerformance(Connection c,Scanner in,int o)throws SQLException{int p=performance(c,in);return one(c,"SELECT p.performanceId FROM Performances p JOIN Events e ON e.eventId=p.eventId WHERE p.performanceId=? AND e.organizerId=?",p,o);}
    private static int ownedEvent(Connection c,Scanner in,int o)throws SQLException{System.out.print("Event ID (optional; press Enter for title): ");String id=in.nextLine().trim();if(!id.isBlank())return one(c,"SELECT eventId FROM Events WHERE eventId=? AND organizerId=?",Integer.parseInt(id),o);System.out.print("Exact event title: ");return one(c,"SELECT eventId FROM Events WHERE title=? AND organizerId=?",in.nextLine(),o);}
    private static int venue(Connection c,Scanner in)throws SQLException{System.out.print("Venue ID (optional; press Enter for exact address): ");String id=in.nextLine().trim();if(!id.isBlank())return one(c,"SELECT venueId FROM Venues WHERE venueId=?",Integer.parseInt(id));System.out.print("Venue exact address: ");return one(c,"SELECT venueId FROM Venues WHERE address=?",in.nextLine());}
    private static int section(Connection c,int p,Scanner in)throws SQLException{System.out.print("Section ID (optional; press Enter for section name): ");String id=in.nextLine().trim();if(!id.isBlank())return one(c,"SELECT sectionId FROM PerformanceSectionAssignments WHERE performanceId=? AND sectionId=?",p,Integer.parseInt(id));System.out.print("Section name: ");return section(c,p,in.nextLine());}
    private static int section(Connection c,int p,String n)throws SQLException{return one(c,"SELECT s.sectionId FROM Sections s JOIN PerformanceSectionAssignments x ON x.sectionId=s.sectionId WHERE x.performanceId=? AND s.sectionName=?",p,n);}
    private static int tier(Connection c,int p,Scanner in)throws SQLException{System.out.print("Tier ID (optional; press Enter for tier name): ");String id=in.nextLine().trim();if(!id.isBlank())return one(c,"SELECT tierId FROM PriceTiers WHERE performanceId=? AND tierId=?",p,Integer.parseInt(id));System.out.print("Tier name: ");return tier(c,p,in.nextLine());}
    private static int tier(Connection c,int p,String n)throws SQLException{return one(c,"SELECT tierId FROM PriceTiers WHERE performanceId=? AND tierName=?",p,n);}
    private static int artist(Connection c,Scanner in,EventOperations events)throws SQLException{System.out.print("Artist ID (optional; press Enter to find or create by name): ");String id=in.nextLine().trim();if(!id.isBlank())return one(c,"SELECT artistId FROM Artists WHERE artistId=?",Integer.parseInt(id));System.out.print("Artist name: ");return events.findOrCreateArtist(in.nextLine());}
    private static void pricingSuggestions(Connection c,Scanner in,int organizerId,OrganizerToolkit toolkit)throws SQLException{int v=venue(c,in);System.out.print("Genre: ");String g=in.nextLine().trim();toolkit.suggestTierStructure(v,g);toolkit.suggestTierPrices(v,g);System.out.print("Estimate a price change too? (y/N): ");if(in.nextLine().trim().equalsIgnoreCase("y")){int p=ownedPerformance(c,in,organizerId);int t=tier(c,p,in);System.out.print("Proposed price: ");toolkit.estimateRevenueImpact(t,new BigDecimal(in.nextLine().trim()));}}
    private static int payment(Connection c,int u)throws SQLException{return one(c,"SELECT paymentId FROM PaymentMethods WHERE customerId=? ORDER BY paymentId DESC LIMIT 1",u);}
    private static int one(Connection c,String sql,Object...v)throws SQLException{try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<v.length;i++)p.setObject(i+1,v[i]);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("No matching record found.");return r.getInt(1);}}}
    private static List<Integer> seats(Connection c,int p,Scanner in)throws SQLException{int sec=section(c,p,in);System.out.print("Row name: ");String row=in.nextLine();System.out.print("Seat number(s), comma-separated: ");String ns=in.nextLine();List<Integer>out=new ArrayList<>();for(String n:ns.split(","))out.add(one(c,"SELECT st.seatId FROM Seats st JOIN SectionRows r ON r.rowId=st.rowId WHERE r.sectionId=? AND r.rowName=? AND st.seatNumber=?",sec,row,Integer.parseInt(n.trim())));return out;}
    private static List<Integer> numbers(String text){List<Integer>out=new ArrayList<>();for(String n:text.split(","))out.add(Integer.parseInt(n.trim()));return out;}
}
