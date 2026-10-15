
import javax.swing.*;
import java.awt.*;

public class DashboardFrame extends JFrame {

    private final int userId;
    private final String fullName;
    private final String email;
    private final String role;
    private final UserSession session;

    public DashboardFrame(UserSession session) {
        if (session == null || !session.isCitizen()) {
            throw new SecurityException("Access Denied: Citizen role required to access Citizen Dashboard.");
        }
        this.session = session;
        this.userId = session.getUserId();
        this.fullName = session.getFullName();
        this.email = session.getEmail();
        this.role = session.getRole().name();

        setTitle("Smart Complaint System - Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 430);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(
            BorderFactory.createEmptyBorder(20, 25, 20, 25)
        );

        JLabel heading = new JLabel(
            "Smart Complaint and Issue Management System"
        );
        heading.setFont(new Font("Arial", Font.BOLD, 19));

        JLabel welcome = new JLabel(
            "Welcome, " + fullName + "!"
        );
        welcome.setFont(new Font("Arial", Font.PLAIN, 16));

        JPanel header = new JPanel(new GridLayout(2, 1, 5, 5));
        header.add(heading);
        header.add(welcome);

        JButton submitButton = new JButton("Submit Complaint");
        JButton viewButton = new JButton("My Complaints");
        JButton profileButton = new JButton("My Profile");
        JButton logoutButton = new JButton("Logout");

        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        buttonPanel.add(submitButton);
        buttonPanel.add(viewButton);
        buttonPanel.add(profileButton);
        buttonPanel.add(logoutButton);

        JLabel footer = new JLabel(
            "Logged in as: " + role,
            SwingConstants.CENTER
        );

        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        mainPanel.add(footer, BorderLayout.SOUTH);

        submitButton.addActionListener(e -> {
            ComplaintFrame complaintFrame =
                new ComplaintFrame(session);

            complaintFrame.setVisible(true);
        });

        viewButton.addActionListener(e -> {
            MyComplaintsFrame complaintsFrame =
                new MyComplaintsFrame(session);

            complaintsFrame.setVisible(true);
        });

        profileButton.addActionListener(e ->
            JOptionPane.showMessageDialog(
                this,
                "User ID: " + userId +
                "\nName: " + fullName +
                "\nEmail: " + email +
                "\nRole: " + role,
                "My Profile",
                JOptionPane.INFORMATION_MESSAGE
            )
        );

        logoutButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to logout?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION
            );

            if (choice == JOptionPane.YES_OPTION) {
                dispose();
                UserSession.clearCurrentSession();
                new LoginFrame().setVisible(true);
            }
        });

        add(mainPanel);
    }

    public DashboardFrame(
            int userId,
            String fullName,
            String email,
            String role) {
        this(resolveCitizenSession(userId, fullName, email, role));
    }

    private static UserSession resolveCitizenSession(int userId, String fullName, String email, String role) {
        UserSession current = UserSession.getCurrentSession();
        if (current != null && current.isCitizen() && current.getUserId() == userId) {
            return current;
        }
        UserSession.Role r = UserSession.Role.fromString(role);
        if (r == null) {
            r = UserSession.Role.CITIZEN;
        }
        return new UserSession(userId, fullName, email, r);
    }
}
