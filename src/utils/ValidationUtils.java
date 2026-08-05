package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared ownership/existence checks used inside transactional operations. */
public final class ValidationUtils {
    private ValidationUtils() { }

    /** Requires a local part, an @ symbol, and a dot-domain suffix such as .com or .ca. */
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^\\s@]+@[^\\s@]+\\.[A-Za-z]{2,}$");
    }

    public static boolean customerExists(Connection conn, int customerId) throws SQLException {
        return exists(conn, "SELECT 1 FROM Customers WHERE customerId = ?", customerId);
    }

    public static boolean paymentBelongsToCustomer(Connection conn, int paymentId, int customerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM PaymentMethods WHERE paymentId = ? AND customerId = ?")) {
            ps.setInt(1, paymentId);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static boolean exists(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}
