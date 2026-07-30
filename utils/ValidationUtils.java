package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Shared existence/ownership checks used across the Operations classes
 * (BookingOperations, ResaleOperations, ReviewOperations, ...), so a bad
 * customerId/organizerId/paymentId fails with a clear message instead of a
 * raw foreign-key-violation SQLException from INSERT.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class -- not meant to be instantiated.
    }

    public static boolean customerExists(Connection conn, int customerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Customers WHERE customerId = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Customer " + customerId + " does not exist.");
                    return false;
                }
                return true;
            }
        }
    }

    public static boolean organizerExists(Connection conn, int organizerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM Organizers WHERE organizerId = ?")) {
            ps.setInt(1, organizerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Organizer " + organizerId + " does not exist.");
                    return false;
                }
                return true;
            }
        }
    }

    // Confirms paymentId is a real payment method that belongs to customerId
    public static boolean paymentBelongsToCustomer(Connection conn, int paymentId, int customerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM PaymentMethods WHERE paymentId = ? AND customerId = ?")) {
            ps.setInt(1, paymentId);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Payment method " + paymentId + " does not belong to customer " + customerId + ".");
                    return false;
                }
                return true;
            }
        }
    }
}
