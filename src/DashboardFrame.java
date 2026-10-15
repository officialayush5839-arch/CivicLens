
import javax.swing.*;
import java.awt.*;

public class DashboardFrame extends JFrame {

    private final int userId;
    private final String fullName;
    private final String email;
    private final String role;

    public DashboardFrame(
            int userId,
            String fullName,
            String email,
            String role) {

        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;

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
        new ComplaintFrame(userId);

    complaintFrame.setVisible(true);
});


        viewButton.addActionListener(e -> {
    MyComplaintsFrame complaintsFrame =
        new MyComplaintsFrame(userId);

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
                new LoginFrame().setVisible(true);
            }
        });

        add(mainPanel);
    }
}
