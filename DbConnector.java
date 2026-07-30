import java.sql.*;
import java.util.Scanner;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compile:  javac -cp mysql-connector-java-8.0.29.jar demo.java
 * Run:      java  -cp .:mysql-connector-java-8.0.29.jar demo
 * Windows:  java  -cp .;mysql-connector-java-8.0.29.jar demo
 */
public class DbConnector {

    static final EnvConfig config = new EnvConfig();
    static final String URL  = config.get("DB_URL");
    static final String USER = config.get("DB_USER");
    static final String PASS = config.get("DB_PASSWORD");

    public static void main(String[] args) throws SQLException {

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {

            System.out.println("Connected! Welcome to MyTix!\n");

            // // ── Add a student from user input ────────────────────────────────
            // Scanner scanner = new Scanner(System.in);

            // System.out.print("Enter name: ");
            // String name = scanner.nextLine();

            // System.out.print("Enter email: ");
            // String email = scanner.nextLine();

            // System.out.print("Enter date of birth (YYYY-MM-DD): ");
            // String dob = scanner.nextLine();

            // System.out.print("Enter GPA: ");
            // double gpa = Double.parseDouble(scanner.nextLine());

            // PreparedStatement ps = conn.prepareStatement(
            //     "INSERT INTO student (name, email, dob, gpa) VALUES (?, ?, ?, ?)",
            //     Statement.RETURN_GENERATED_KEYS
            // );
            // ps.setString(1, name);
            // ps.setString(2, email);
            // ps.setString(3, dob);
            // ps.setDouble(4, gpa);
            // ps.executeUpdate();

            // ResultSet keys = ps.getGeneratedKeys();
            // if (keys.next()) {
            //     System.out.println("Inserted student with id = " + keys.getInt(1) + "\n");
            // }

            // Statement stmt = conn.createStatement();

            // // ── SELECT all students ──────────────────────────────────────────
            // ResultSet rs = stmt.executeQuery("SELECT * FROM student");
            // System.out.println("Students:");
            // System.out.printf("%-5s %-10s %-25s %-12s %-5s%n", "ID", "Name", "Email", "DOB", "GPA");
            // System.out.println("-".repeat(60));
            // while (rs.next()) {
            //     System.out.printf("%-5d %-10s %-25s %-12s %-5.2f%n",
            //         rs.getInt   ("student_id"),
            //         rs.getString("name"),
            //         rs.getString("email"),
            //         rs.getString("dob"),
            //         rs.getDouble("gpa")
            //     );
            // }

            // // ── SELECT all courses ───────────────────────────────────────────
            // rs = stmt.executeQuery("SELECT * FROM course");
            // System.out.println("\nCourses:");
            // System.out.printf("%-5s %-10s %-25s %-8s%n", "ID", "Code", "Title", "Credits");
            // System.out.println("-".repeat(50));
            // while (rs.next()) {
            //     System.out.printf("%-5d %-10s %-25s %-8d%n",
            //         rs.getInt   ("course_id"),
            //         rs.getString("code"),
            //         rs.getString("title"),
            //         rs.getInt   ("credits")
            //     );
            // }

            // // ── SELECT all enrollments with JOIN ─────────────────────────────
            // rs = stmt.executeQuery(
            //     "SELECT s.name, c.code, c.title, e.grade, e.enrolled_at " +
            //     "FROM enrollment e " +
            //     "JOIN student s ON e.student_id = s.student_id " +
            //     "JOIN course  c ON e.course_id  = c.course_id"
            // );
            // System.out.println("\nEnrollments:");
            // System.out.printf("%-10s %-10s %-20s %-7s %-12s%n", "Student", "Code", "Course", "Grade", "Enrolled");
            // System.out.println("-".repeat(62));
            // while (rs.next()) {
            //     System.out.printf("%-10s %-10s %-20s %-7.1f %-12s%n",
            //         rs.getString("name"),
            //         rs.getString("code"),
            //         rs.getString("title"),
            //         rs.getDouble("grade"),
            //         rs.getString("enrolled_at")
            //     );
            // }

            // TODO: add our code below

            // Wire up each feature area using the skeleton classes below — pass
            // this same `conn` into whichever one you need, call its methods,
            // and print/format the results here in the terminal UI:
            //
            //   UserOperations     -> create/delete users (customers & organizers)
            //   EventOperations    -> create event, add performance, price tiers,
            //                         assign sections to tiers, resale cap,
            //                         update tier price, block/unblock seats,
            //                         cancel a performance
            //   BookingOperations  -> book tickets, cancel tickets
            //   ResaleOperations   -> list/withdraw/purchase a resale listing
            //   ReviewOperations   -> insert a review for an attended performance
            //   SearchQueries      -> Q1-Q7 (location/postal/address search,
            //                         filters, seat map, best-available)
            //   Reports            -> R1-R9 (revenue, counts, rankings, etc.)
            //   OrganizerToolkit   -> suggested tier structure/pricing for a
            //                         new performance (+ extra-credit revenue estimate)
            //
            // e.g.: new UserOperations(conn).createUser(...);

            Scanner scanner = new Scanner(System.in);
            SearchQueries search = new SearchQueries(conn);
            Reports reports = new Reports(conn);
            UserOperations userOps = new UserOperations(conn);
            BookingOperations bookingOps = new BookingOperations(conn);
            EventOperations eventOps = new EventOperations(conn);

            boolean running = true;
            while (running) {
                System.out.println("\n=== MyTix ===");
                System.out.println("--- User Management Operations ---");
                System.out.println("u1) Create customer profile");
                System.out.println("u2) Create organizer profile");
                System.out.println("u3) Delete user");

                System.out.println("--- Ticket Booking Operations ---");
                System.out.println("b1a) Book reserved seating tickets");
                System.out.println("b1b) Book general admission tickets");

                System.out.println("--- Organizer Operations ---");
                System.out.println("o7) Block a seat");
                System.out.println("o8) Unblock a seat");

                System.out.println("--- Queries ---");
                System.out.println("q1) Q1  - Search performances by location");
                System.out.println("q6) Q6  - Seat map summary for a performance");
                System.out.println("q7) Q7  - Best available seats");

                System.out.println("--- Reports ---");
                System.out.println("r1) R1  - Tickets & revenue by city");
                System.out.println("r1b) R1b - Tickets & revenue by venue in a city");
                System.out.println("r2) R2  - Event/performance counts by taxonomy & location");
                System.out.println("r3) R3  - Rank organizers by revenue (overall)");
                System.out.println("r3b) R3b - Rank organizers by revenue per country");
                System.out.println("r3c) R3c - Rank organizers by revenue in a city");
                System.out.println("r6) R6 - Most cancellations in a year");
                System.out.println("r9) R9 - Top noun phrases in comments per event");

                System.out.println("0) Exit");
                System.out.print("Choice (e.g. for option u1, enter u1): ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    // ---------------- User Management Operations ----------------
                    case "u1" -> {
                        System.out.print("Enter the Name: ");
                        String name = scanner.nextLine();

                        System.out.print("Enter the Email: ");
                        String email = scanner.nextLine();

                        System.out.print("Enter the Address: ");
                        String address = scanner.nextLine();

                        System.out.print("Enter the Date of birth (YYYY-MM-DD): ");
                        LocalDate dob = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the Cardholder name: ");
                        String cardholderName = scanner.nextLine();

                        System.out.print("Enter the Card number: ");
                        String cardNumber = scanner.nextLine();

                        System.out.print("Enter the Expiry (MM/YY): ");
                        String expiry = scanner.nextLine();

                        int id = userOps.createCustomer(name, email, address, dob, cardholderName, cardNumber, expiry);
                        System.out.println("Created customer with ID " + id);
                    }
                    case "u2" -> {
                        System.out.print("Enter the Name: ");
                        String name = scanner.nextLine();

                        System.out.print("Enter the Email: ");
                        String email = scanner.nextLine();

                        System.out.print("Enter the Address: ");
                        String address = scanner.nextLine();
                        
                        System.out.print("Enter the Date of birth (YYYY-MM-DD): ");
                        LocalDate dob = LocalDate.parse(scanner.nextLine());

                        int id = userOps.createOrganizer(name, email, address, dob);
                        System.out.println("Created organizer with ID " + id);
                    }
                    case "u3" -> {
                        System.out.print("User ID to delete: ");
                        userOps.deleteUser(Integer.parseInt(scanner.nextLine()));
                    }

                    // ---------- Ticket Booking Operations ----------
                    case "b1a" -> {
                        System.out.print("Enter the Customer ID: ");
                        int customerId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Performance ID: ");
                        int performanceId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Payment ID: ");
                        int paymentId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Seat IDs (comma-separated): ");
                        List<Integer> seatIds = Arrays.stream(scanner.nextLine().split(","))
                            .map(String::trim)
                            .map(Integer::parseInt)
                            .collect(Collectors.toList());

                        bookingOps.bookReservedSeats(customerId, performanceId, paymentId, seatIds);
                    }
                    case "b1b" -> {
                        System.out.print("Enter the Customer ID: ");
                        int customerId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Performance ID: ");
                        int performanceId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Section ID: ");
                        int sectionId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Payment ID: ");
                        int paymentId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Quantity: ");
                        int quantity = Integer.parseInt(scanner.nextLine());

                        bookingOps.bookGeneralAdmission(customerId, performanceId, paymentId, sectionId, quantity);
                    }

                    // ---------- Organizer Operations ---------- 
                    case "o7" -> {
                        System.out.print("Enter the Organizer ID: ");
                        int organizerId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Performance ID: ");
                        int performanceId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Seat ID to block: ");
                        int seatId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Reason for blocking: ");
                        String reason = scanner.nextLine();

                        eventOps.blockSeat(organizerId, performanceId, seatId, reason);
                    }
                    case "o8" -> {
                        System.out.print("Enter the Organizer ID: ");
                        int organizerId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Performance ID: ");
                        int performanceId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the Seat ID to unblock: ");
                        int seatId = Integer.parseInt(scanner.nextLine());

                        eventOps.unblockSeat(organizerId, performanceId, seatId);
                    }

                    // ---------- Queries ----------
                    case "q1" -> {
                        System.out.print("Enter a latitude: ");
                        double lat = Double.parseDouble(scanner.nextLine());

                        System.out.print("Enter a longitude: ");
                        double lon = Double.parseDouble(scanner.nextLine());

                        System.out.print("Search radius in km (press Enter for default - "
                            + SearchQueries.getDefaultRadiusKm() + " km): ");
                        String radiusInput = scanner.nextLine().trim();
                        double radiusKm = radiusInput.isEmpty()
                            ? SearchQueries.getDefaultRadiusKm()
                            : Double.parseDouble(radiusInput);

                        System.out.print("Rank by 'distance' or 'price' (press Enter for 'distance'): ");
                        String rankByInput = scanner.nextLine().trim();
                        String rankBy = rankByInput.isEmpty() ? "distance" : rankByInput;

                        System.out.print("Direction 'asc' or 'desc' (press Enter for 'asc'): ");
                        String directionInput = scanner.nextLine().trim();
                        String direction = directionInput.isEmpty() ? "asc" : directionInput;

                        search.searchByLocation(lat, lon, radiusKm, rankBy, direction);
                    }
                    case "q6" -> {
                        System.out.print("Enter the performance ID: ");
                        int perfId = Integer.parseInt(scanner.nextLine());

                        search.seatMapSummary(perfId);
                    }
                    case "q7" -> {
                        System.out.print("Enter the performance ID: ");
                        int perfId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the quantity of tickets: ");
                        int qty = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the budget (press Enter for none): ");
                        String budgetInput = scanner.nextLine().trim();
                        Double budget = budgetInput.isEmpty() ? null : Double.parseDouble(budgetInput);

                        search.bestAvailable(perfId, qty, budget);
                    }

                    // ---------- Reports ----------
                    case "r1" -> {
                        System.out.print("Enter the report start date (YYYY-MM-DD): ");
                        LocalDate start = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the report end date (YYYY-MM-DD): ");
                        LocalDate end = LocalDate.parse(scanner.nextLine());

                        reports.ticketsAndRevenueByCity(start, end);
                    }
                    case "r1b" -> {
                        System.out.print("Enter the report start date (YYYY-MM-DD): ");
                        LocalDate start = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the report end date (YYYY-MM-DD): ");
                        LocalDate end = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the city: ");
                        String city = scanner.nextLine();

                        reports.ticketsAndRevenueByVenue(start, end, city);
                    }
                    case "r2" -> reports.eventCountsByTaxonomyAndLocation();
                    case "r3" -> reports.rankOrganizersByRevenueOverall();
                    case "r3b" -> reports.rankOrganizersByRevenuePerCountry();
                    case "r3c" -> {
                        System.out.print("Enter the city: ");
                        reports.rankOrganizersByRevenueByCity(scanner.nextLine());
                    }
                    case "r6" -> {
                        System.out.print("Enter the report year: ");
                        reports.mostCancellations(Integer.parseInt(scanner.nextLine()));
                    }
                    case "r9" -> reports.topNounPhrasesByEvent();

                    case "0" -> running = false;
                    default -> System.out.println("Invalid choice.");
                }
            }
        }
    }
}