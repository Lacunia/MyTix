import java.sql.*;
import java.util.Scanner;
import java.time.LocalDate;

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

            boolean running = true;
            while (running) {
                System.out.println("\n=== MyTix Action Menu ===");
                System.out.println("1) Q1  - Search performances by location");
                System.out.println("2) Q6  - Seat map summary for a performance");
                System.out.println("3) Q7  - Best available seats");
                System.out.println("4) R1  - Tickets & revenue by city");
                System.out.println("5) R1b - Tickets & revenue by venue in a city");
                System.out.println("6) R2  - Event/performance counts by taxonomy & location");
                System.out.println("7) R3  - Rank organizers by revenue (overall)");
                System.out.println("8) R3b - Rank organizers by revenue per country");
                System.out.println("9) R3c - Rank organizers by revenue in a city");
                System.out.println("10) R6 - Most cancellations in a year");
                System.out.println("0) Exit");
                System.out.print("Choice (e.g. for option 1, enter 1): ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> {
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
                    case "2" -> {
                        System.out.print("Enter the performance ID: ");
                        int perfId = Integer.parseInt(scanner.nextLine());

                        search.seatMapSummary(perfId);
                    }
                    case "3" -> {
                        System.out.print("Enter the performance ID: ");
                        int perfId = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the quantity of tickets: ");
                        int qty = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter the budget (press Enter for none): ");
                        String budgetInput = scanner.nextLine().trim();
                        Double budget = budgetInput.isEmpty() ? null : Double.parseDouble(budgetInput);

                        search.bestAvailable(perfId, qty, budget);
                    }
                    case "4" -> {
                        System.out.print("Enter the report start date (YYYY-MM-DD): ");
                        LocalDate start = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the report end date (YYYY-MM-DD): ");
                        LocalDate end = LocalDate.parse(scanner.nextLine());

                        reports.ticketsAndRevenueByCity(start, end);
                    }
                    case "5" -> {
                        System.out.print("Enter the report start date (YYYY-MM-DD): ");
                        LocalDate start = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the report end date (YYYY-MM-DD): ");
                        LocalDate end = LocalDate.parse(scanner.nextLine());

                        System.out.print("Enter the city: ");
                        String city = scanner.nextLine();

                        reports.ticketsAndRevenueByVenue(start, end, city);
                    }
                    case "6" -> reports.eventCountsByTaxonomyAndLocation();
                    case "7" -> reports.rankOrganizersByRevenueOverall();
                    case "8" -> reports.rankOrganizersByRevenuePerCountry();
                    case "9" -> {
                        System.out.print("Enter the city: ");
                        reports.rankOrganizersByRevenueByCity(scanner.nextLine());
                    }
                    case "10" -> {
                        System.out.print("Enter the report year: ");
                        reports.mostCancellations(Integer.parseInt(scanner.nextLine()));
                    }
                    case "0" -> running = false;
                    default -> System.out.println("Invalid choice.");
                }
            }
        }
    }
}