import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Account management: create/delete users, and promote a user to customer
 * or organizer (see schema.sql Users/Customers/Organizers/PaymentMethods).
 */
public class UserOperations {

    private final Connection conn;

    public UserOperations(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a tuple into Users and returns its generated userId.
     */
    private int insertUserRow(String name, String email, String address, LocalDate dateOfBirth, String accountType) throws SQLException {
        // Check age constraint first (the SQL trigger serves as a second layer of security)
        if (dateOfBirth.isAfter(LocalDate.now().minusYears(18))) {
            throw new IllegalArgumentException("User must be at least 18 years old to open an account.");
        }

        String sql = "INSERT INTO Users (name, email, address, dateOfBirth, accountType) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, address);
            ps.setDate(4, java.sql.Date.valueOf(dateOfBirth));
            ps.setString(5, accountType);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /**
     * Creates a customer profile: 
     * Users row + Customers row + a payment method, all in one transaction. 
     * Returns the new customerId (same value as the generated userId).
     */
    public int createCustomer(String name, String email, String address, LocalDate dateOfBirth,
                              String cardholderName, String cardNumber, String expiryDate) {
        try {
            // To keep the 2 transactions atomic
            conn.setAutoCommit(false);

            int userId = insertUserRow(name, email, address, dateOfBirth, "Customer");

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Customers (customerId) VALUES (?)")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PaymentMethods (customerId, cardholderName, cardNumber, expiryDate) " +
                    "VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setString(2, cardholderName);
                ps.setString(3, cardNumber);
                ps.setString(4, expiryDate);
                ps.executeUpdate();
            }

            conn.commit();
            return userId;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Creates an organizer profile: 
     * Users row + Organizers row, all in one transaction.
     * Returns the new organizerId (same value as the generated userId).
     */
    public int createOrganizer(String name, String email, String address, LocalDate dateOfBirth) {
        try {
            conn.setAutoCommit(false);

            int userId = insertUserRow(name, email, address, dateOfBirth, "Organizer");

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Organizers (organizerId) VALUES (?)")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }

            conn.commit();
            return userId;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Deletes a user profile. Relies on the schema's foreign-key CONSTRAINTS
     * to prevent deleting anyone with real order/ticket/event history
     */
    public void deleteUser(int userId) {
        String sql = "DELETE FROM Users WHERE userId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                System.out.println("No user found with ID " + userId + ".");
            } else {
                System.out.println("User " + userId + " deleted.");
            }
        } catch (SQLException e) {
            // MySQL error 1451: "Cannot delete or update a parent row: a
            // foreign key constraint fails" -- this user has retained history.
            if (e.getErrorCode() == 1451) {
                System.out.println("Cannot delete user " + userId +
                    ": they have existing orders, tickets, events, or other history that must be retained.");
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // nothing further to do if this fails.
        }
    }

    private void restoreAutoCommit() {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // nothing to do if fails
        }
    }
}
