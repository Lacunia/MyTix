import java.sql.*;
import java.util.Scanner;

/**
 * Compile:  javac -cp mysql-connector-java-8.0.29.jar demo.java
 * Run:      java  -cp .:mysql-connector-java-8.0.29.jar demo
 * Windows:  java  -cp .;mysql-connector-java-8.0.29.jar demo
 */
public class demo {

    static final String URL  = "jdbc:mysql://localhost:3306/mydb";
    static final String USER = "root";
    static final String PASS = "";

    public static void main(String[] args) throws SQLException {

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {

            System.out.println("Connected!\n");

            // ── Add a student from user input ────────────────────────────────
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter name: ");
            String name = scanner.nextLine();

            System.out.print("Enter email: ");
            String email = scanner.nextLine();

            System.out.print("Enter date of birth (YYYY-MM-DD): ");
            String dob = scanner.nextLine();

            System.out.print("Enter GPA: ");
            double gpa = Double.parseDouble(scanner.nextLine());

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO student (name, email, dob, gpa) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, dob);
            ps.setDouble(4, gpa);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                System.out.println("Inserted student with id = " + keys.getInt(1) + "\n");
            }

            Statement stmt = conn.createStatement();

            // ── SELECT all students ──────────────────────────────────────────
            ResultSet rs = stmt.executeQuery("SELECT * FROM student");
            System.out.println("Students:");
            System.out.printf("%-5s %-10s %-25s %-12s %-5s%n", "ID", "Name", "Email", "DOB", "GPA");
            System.out.println("-".repeat(60));
            while (rs.next()) {
                System.out.printf("%-5d %-10s %-25s %-12s %-5.2f%n",
                    rs.getInt   ("student_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("dob"),
                    rs.getDouble("gpa")
                );
            }

            // ── SELECT all courses ───────────────────────────────────────────
            rs = stmt.executeQuery("SELECT * FROM course");
            System.out.println("\nCourses:");
            System.out.printf("%-5s %-10s %-25s %-8s%n", "ID", "Code", "Title", "Credits");
            System.out.println("-".repeat(50));
            while (rs.next()) {
                System.out.printf("%-5d %-10s %-25s %-8d%n",
                    rs.getInt   ("course_id"),
                    rs.getString("code"),
                    rs.getString("title"),
                    rs.getInt   ("credits")
                );
            }

            // ── SELECT all enrollments with JOIN ─────────────────────────────
            rs = stmt.executeQuery(
                "SELECT s.name, c.code, c.title, e.grade, e.enrolled_at " +
                "FROM enrollment e " +
                "JOIN student s ON e.student_id = s.student_id " +
                "JOIN course  c ON e.course_id  = c.course_id"
            );
            System.out.println("\nEnrollments:");
            System.out.printf("%-10s %-10s %-20s %-7s %-12s%n", "Student", "Code", "Course", "Grade", "Enrolled");
            System.out.println("-".repeat(62));
            while (rs.next()) {
                System.out.printf("%-10s %-10s %-20s %-7.1f %-12s%n",
                    rs.getString("name"),
                    rs.getString("code"),
                    rs.getString("title"),
                    rs.getDouble("grade"),
                    rs.getString("enrolled_at")
                );
            }
        }
    }
}
