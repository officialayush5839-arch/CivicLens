
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class EmployeeManagementFrame extends JFrame {

    private final JTextField nameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField =
            new JPasswordField(20);
    private final JPasswordField confirmPasswordField =
            new JPasswordField(20);
    private final UserSession session;

    public EmployeeManagementFrame(UserSession session) {
        if (session == null || !session.isAdmin()) {
            throw new SecurityException("Access Denied: Admin role required to access Employee Management.");
        }
        this.session = session;
        initUI();
    }

    public EmployeeManagementFrame() {
        this(resolveAdminSession());
    }

    private static UserSession resolveAdminSession() {
        UserSession current = UserSession.getCurrentSession();
        if (current != null && current.isAdmin()) {
            return current;
        }
        if (AppConfig.isDemoMode()) {
            return new UserSession(1, "System Administrator", "admin@civiclens.gov", UserSession.Role.ADMIN);
        }
        if (current == null) {
            throw new SecurityException("Access Denied: No authenticated Admin session found in context.");
        }
        throw new SecurityException("Access Denied: Admin role required to access Employee Management.");
    }

    private void initUI() {
        setTitle("Smart Complaint System - Employee Management");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(480, 350);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        );

        panel.add(new JLabel("Full Name:"));
        panel.add(nameField);

        panel.add(new JLabel("Email:"));
        panel.add(emailField);

        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        panel.add(new JLabel("Confirm Password:"));
        panel.add(confirmPasswordField);

        JButton createButton = new JButton("Create Employee");
        JButton clearButton = new JButton("Clear");

        panel.add(clearButton);
        panel.add(createButton);

        createButton.addActionListener(e -> createEmployee());

        clearButton.addActionListener(e -> {
            nameField.setText("");
            emailField.setText("");
            passwordField.setText("");
            confirmPasswordField.setText("");
        });

        add(panel);
    }

    private void createEmployee() {

        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password =
                new String(passwordField.getPassword());
        String confirmPassword =
                new String(confirmPasswordField.getPassword());

        if (name.isEmpty() || email.isEmpty()
                || password.isEmpty()
                || confirmPassword.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "Please fill in all fields."
            );
            return;
        }

        if (!email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            JOptionPane.showMessageDialog(
                this, "Please enter a valid email address."
            );
            return;
        }

        if (password.length() < 8) {
            JOptionPane.showMessageDialog(
                this,
                "Password must contain at least 8 characters."
            );
            return;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(
                this, "Passwords do not match."
            );
            return;
        }

        String hashedPassword =
                BCrypt.hashpw(password, BCrypt.gensalt(12));

        String sql =
            "INSERT INTO users " +
            "(full_name, email, password_hash, role) " +
            "VALUES (?, ?, ?, 'EMPLOYEE')";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement =
                    connection.prepareStatement(sql)
        ) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, hashedPassword);

            statement.executeUpdate();

            JOptionPane.showMessageDialog(
                this,
                "Employee account created successfully!"
            );

            nameField.setText("");
            emailField.setText("");
            passwordField.setText("");
            confirmPasswordField.setText("");

        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                JOptionPane.showMessageDialog(
                    this,
                    "This email is already registered."
                );
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Database error: " + ex.getMessage()
                );
            }
        }
    }
}

