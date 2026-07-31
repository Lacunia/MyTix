import java.sql.*;

/**
 * Customer reviews of events/venues. Before inserting into Comments, verify:
 * the customer held a ticket (status != cancelled) for the given
 * performanceId, and that performance's dateTime is in the past. schema.sql's
 * unique_customer_performance_review constraint blocks a second review of
 * the same performance, but the "attended" and "already happened" checks
 * must happen here since they aren't expressible as static CHECKs.
 */
public class ReviewOperations {

    private final Connection conn;

    public ReviewOperations(Connection conn) {
        this.conn = conn;
    }

    public boolean insertReview(int customerId, String eventTitle, String content, int eventRating, int venueRating) {
        if (content == null || content.isBlank() || eventRating < 1 || eventRating > 5 || venueRating < 1 || venueRating > 5) {
            throw new IllegalArgumentException("Comment and ratings from 1 to 5 are required.");
        }
        String attendance = "SELECT p.performanceId FROM Tickets t JOIN Performances p ON p.performanceId=t.performanceId " +
            "JOIN Events e ON e.eventId=p.eventId WHERE t.currentOwnerId=? AND t.status='Active' AND e.title=? AND p.dateTime<NOW() ORDER BY p.dateTime DESC LIMIT 1";
        try (PreparedStatement find=conn.prepareStatement(attendance)) {
            find.setInt(1,customerId); find.setString(2,eventTitle.trim());
            try(ResultSet rs=find.executeQuery()) {
                if(!rs.next()) return false;
                try(PreparedStatement insert=conn.prepareStatement("INSERT INTO Comments (customerId,performanceId,content,eventRating,venueRating) VALUES (?,?,?,?,?)")) {
                    insert.setInt(1,customerId);insert.setInt(2,rs.getInt(1));insert.setString(3,content.trim());insert.setInt(4,eventRating);insert.setInt(5,venueRating);
                    return insert.executeUpdate()==1;
                }
            }
        } catch(SQLException e) { throw new RuntimeException("Unable to add review.",e); }
    }
}
