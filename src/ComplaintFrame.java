
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ComplaintFrame extends JFrame {

    private final int userId;

    private final JTextField titleField = new JTextField(25);

    private final JComboBox<String> categoryBox =
        new JComboBox<>(new String[]{
            "Technical Issue",
            "Infrastructure",
            "Academic",
            "Administrative",
            "Other"
        });

    private final JComboBox<String> priorityBox =
        new JComboBox<>(new String[]{
            "LOW", "MEDIUM", "HIGH", "CRITICAL"
        });

    private final JTextArea descriptionArea = new JTextArea(6, 25);
    private final UserSession session;

    public ComplaintFrame(UserSession session) {
        if (session == null || !session.isCitizen()) {
            throw new SecurityException("Access Denied: Citizen role required to submit complaints.");
        }
        this.session = session;
        this.userId = session.getUserId();
        initUI();
    }

    public ComplaintFrame(int userId) {
        this(resolveCitizenSession(userId));
    }

    private static UserSession resolveCitizenSession(int userId) {
        UserSession current = UserSession.getCurrentSession();
        if (current != null && current.isCitizen() && current.getUserId() == userId) {
            return current;
        }
        return new UserSession(userId, "Citizen", "citizen" + userId + "@civiclens.gov", UserSession.Role.CITIZEN);
    }

    private void initUI() {
        setTitle("Smart Complaint System - Submit Complaint");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(550, 500);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(
            BorderFactory.createEmptyBorder(20, 25, 20, 25)
        );

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 7, 7, 7);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel heading = new JLabel("Submit a New Complaint");
        heading.setFont(new Font("Arial", Font.BOLD, 20));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(heading, gbc);

        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Complaint Title:"), gbc);

        gbc.gridx = 1;
        panel.add(titleField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Category:"), gbc);

        gbc.gridx = 1;
        panel.add(categoryBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("Automatic Priority:"), gbc);

        priorityBox.setEnabled(false);
        gbc.gridx = 1;
        panel.add(priorityBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("Description:"), gbc);

        gbc.gridx = 1;
        JScrollPane scrollPane =
            new JScrollPane(descriptionArea);
        panel.add(scrollPane, gbc);

        JLabel noteLabel = new JLabel(
            "Priority is suggested automatically from complaint details."
        );
        noteLabel.setFont(new Font("Arial", Font.ITALIC, 11));

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        panel.add(noteLabel, gbc);

        JButton submitButton = new JButton("Submit Complaint");
        JButton clearButton = new JButton("Clear");

        JPanel buttonPanel = new JPanel(
            new FlowLayout(FlowLayout.RIGHT)
        );
        buttonPanel.add(clearButton);
        buttonPanel.add(submitButton);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        titleField.getDocument().addDocumentListener(
            new javax.swing.event.DocumentListener() {
                public void insertUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }

                public void removeUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }

                public void changedUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }
            }
        );

        descriptionArea.getDocument().addDocumentListener(
            new javax.swing.event.DocumentListener() {
                public void insertUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }

                public void removeUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }

                public void changedUpdate(
                        javax.swing.event.DocumentEvent e) {
                    updatePriority();
                }
            }
        );

        categoryBox.addActionListener(e -> updatePriority());

        submitButton.addActionListener(e -> submitComplaint());

        clearButton.addActionListener(e -> {
            titleField.setText("");
            categoryBox.setSelectedIndex(0);
            descriptionArea.setText("");
            updatePriority();
        });

        add(panel);
        updatePriority();
    }

    private void updatePriority() {
        String priority = PriorityClassifier.classify(
            titleField.getText(),
            descriptionArea.getText(),
            (String) categoryBox.getSelectedItem()
        );

        priorityBox.setSelectedItem(priority);
    }

    private void submitComplaint() {
        String title = titleField.getText().trim();
        String category =
            (String) categoryBox.getSelectedItem();
        String description =
            descriptionArea.getText().trim();

        if (title.isEmpty() || description.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter both a title and description."
            );
            return;
        }

        if (title.length() > 150) {
            JOptionPane.showMessageDialog(
                this,
                "The title cannot exceed 150 characters."
            );
            return;
        }

        // Recalculate explainable triage immediately before saving
        civiclens.triage.TriageResult triage = PriorityClassifier.classifyWithExplanation(title, description, category);
        String priority = triage.getPriorityString();

        String sql =
            "INSERT INTO complaints " +
            "(user_id, title, category, description, priority, status) " +
            "VALUES (?, ?, ?, ?, ?, 'SUBMITTED')";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setInt(1, userId);
            statement.setString(2, title);
            statement.setString(3, category);
            statement.setString(4, description);
            statement.setString(5, priority);
            statement.executeUpdate();

            int newComplaintId = -1;
            try (java.sql.ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    newComplaintId = rs.getInt(1);
                }
            }

            // Initialize deterministic SLA deadlines
            if (newComplaintId > 0) {
                civiclens.sla.SlaEngine.initializeDeadlines(connection, newComplaintId, priority, category, java.time.Instant.now());
                String auditDetails = "Submitted. Triage Justification: " + triage.getJustification() +
                        (triage.getUrgencyDrivers().isEmpty() ? "" : " | Drivers: " + String.join(", ", triage.getUrgencyDrivers()));
                civiclens.audit.AuditTrailService.logEvent(connection, newComplaintId, userId, "CITIZEN", "COMPLAINT_SUBMITTED",
                        null, "SUBMITTED", auditDetails);

                // Check for duplicate or related incidents
                java.util.List<civiclens.similarity.DuplicateCandidate> duplicates =
                        civiclens.similarity.DuplicateDetectionEngine.findDuplicatesInDb(connection, newComplaintId, title, description, category, 0.40);
                if (!duplicates.isEmpty()) {
                    System.out.println("[Triage] Detected " + duplicates.size() + " related incident candidates for Complaint #" + newComplaintId);
                }
            }

            StringBuilder msg = new StringBuilder();
            msg.append("Complaint #").append(newComplaintId).append(" submitted successfully!\n");
            msg.append("Assigned Priority: ").append(priority).append(" [").append(triage.getConfidenceString()).append("]\n");
            msg.append("Justification: ").append(triage.getJustification()).append("\n");
            if (!triage.getUrgencyDrivers().isEmpty()) {
                msg.append("Urgency Drivers: ").append(String.join(", ", triage.getUrgencyDrivers())).append("\n");
            }
            msg.append("Status: SUBMITTED");

            JOptionPane.showMessageDialog(
                this,
                msg.toString()
            );

            titleField.setText("");
            categoryBox.setSelectedIndex(0);
            descriptionArea.setText("");
            updatePriority();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to save complaint.\n" +
                "Error: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
