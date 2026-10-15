import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/smart_complaint_db";

    private static final String USER = "complaint_app";

    // Replace with your actual MySQL account password
    private static final String PASSWORD =
            "ChangeThis_StrongPassword_2026!";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void main(String[] args) {
        try (Connection connection = getConnection()) {
            System.out.println("Database connected successfully!");
            System.out.println("Database: " + connection.getCatalog());
        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            System.out.println("Error: " + e.getMessage());
        }
    }
}

