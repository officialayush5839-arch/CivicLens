import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class RegisterFrame extends JFrame {

    private final JTextField nameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);

    public RegisterFrame() {
        setTitle("Smart Complaint System - Registration");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(420, 320);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        );

        panel.add(new JLabel("Full Name:"));
        panel.add(nameField);

        panel.add(new JLabel("Email:"));
        panel.add(emailField);

        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        JButton registerButton = new JButton("Register");
        panel.add(new JLabel());
        panel.add(registerButton);

        registerButton.addActionListener(e -> registerUser());

        add(panel);
    }

    private void registerUser() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "Please fill in all fields."
            );
            return;
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            JOptionPane.showMessageDialog(
                this, "Please enter a valid email address."
            );
            return;
        }

        if (password.length() < 8) {
            JOptionPane.showMessageDialog(
                this, "Password must contain at least 8 characters."
            );
            return;
        }

        String hashedPassword =
            BCrypt.hashpw(password, BCrypt.gensalt(12));

        String sql = "INSERT INTO users " +
            "(full_name, email, password_hash, role) " +
            "VALUES (?, ?, ?, 'CITIZEN')";

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
                this, "Registration successful!"
            );

            nameField.setText("");
            emailField.setText("");
            passwordField.setText("");

        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                JOptionPane.showMessageDialog(
                    this, "This email may already be registered."
                );
            } else {
                JOptionPane.showMessageDialog(
                    this, "Database error: " + ex.getMessage()
                );
            }
        }
    }

    public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new RegisterFrame().setVisible(true));
}
    }


