import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class LoginFrame extends JFrame {

    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);

    public LoginFrame() {
        setTitle("Smart Complaint System - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 250);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(
            BorderFactory.createEmptyBorder(25, 20, 25, 20)
        );

        panel.add(new JLabel("Email:"));
        panel.add(emailField);

        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        JButton loginButton = new JButton("Login");
        JButton clearButton = new JButton("Clear");
JButton registerButton = new JButton("Register");

registerButton.addActionListener(e -> {
    RegisterFrame registerFrame = new RegisterFrame();
    registerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    registerFrame.setVisible(true);
});
        panel.add(clearButton);
        panel.add(loginButton);
       panel.add(registerButton);
        loginButton.addActionListener(e -> loginUser());

        clearButton.addActionListener(e -> {
            emailField.setText("");
            passwordField.setText("");
        });

        add(panel);
    }

    private void loginUser() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "Please enter your email and password."
            );
            return;
        }

        String sql =
            "SELECT user_id, full_name, email, password_hash, role " +
            "FROM users WHERE email = ?";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setString(1, email);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    String storedHash =
                        result.getString("password_hash");

                    if (storedHash != null &&
                        BCrypt.checkpw(password, storedHash)) {

                        
                        int userId = result.getInt("user_id");
                        String name = result.getString("full_name");
                        String userEmail = result.getString("email");
                        String role = result.getString("role");

                        UserSession.Role roleEnum = UserSession.Role.fromString(role);
                        if (roleEnum == null) {
                            JOptionPane.showMessageDialog(this, "Unknown user role: " + role, "Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        UserSession session = new UserSession(userId, name, userEmail, roleEnum);
                        UserSession.setCurrentSession(session);

                        JOptionPane.showMessageDialog(
                            this,
                            "Login successful!\nWelcome, " + name
                        );

                        if (session.isAdmin()) {
                            AdminDashboardFrame adminDashboard =
                                new AdminDashboardFrame(session);
                            adminDashboard.setVisible(true);

                        } else if (session.isEmployee()) {
                            EmployeeDashboardFrame employeeDashboard =
                                new EmployeeDashboardFrame(session);
                            employeeDashboard.setVisible(true);

                        } else {
                            DashboardFrame dashboard =
                                new DashboardFrame(session);
                            dashboard.setVisible(true);
                        }

                        dispose();

                    } else {
                        JOptionPane.showMessageDialog(
                            this,
                            "Invalid email or password."
                        );
                    }
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "Invalid email or password."
                    );
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Database error: " + ex.getMessage()
            );
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to verify the stored password. " +
                "Please check the database password hash."
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new LoginFrame().setVisible(true)
        );
    }
}

