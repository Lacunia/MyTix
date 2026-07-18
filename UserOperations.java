import java.sql.Connection;

/**
 * Account management: create/delete users, and promote a user to customer
 * or organizer (see schema.sql Users/Customers/Organizers/PaymentMethods).
 */
public class UserOperations {

    private final Connection conn;

    public UserOperations(Connection conn) {
        this.conn = conn;
    }

    // Insert into Users, then into Customers or Organizers depending on account type.
    // If creating a customer, also collect and insert their PaymentMethods row(s).
    public void createUser(/* name, email, address, dob, accountType, ... */) {
        // TODO
    }

    // Delete a user (cascades to Customers/Organizers/PaymentMethods per schema FKs).
    public void deleteUser(int userId) {
        // TODO
    }
}
