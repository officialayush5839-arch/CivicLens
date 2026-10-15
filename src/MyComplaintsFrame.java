import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import civiclens.lifecycle.ResolutionService;

/**
 * Modernized Citizen Complaints Portal.
 * Displays personal complaint history, resolution evidence records,
 * satisfaction feedback submission, and formal authenticated appeals.
 */
public class MyComplaintsFrame extends JFrame {

    private final int userId;
    private final UserSession session;

    private final DefaultTableModel tableModel =
        new DefaultTableModel(
            new String[]{
                "ID", "Title", "Category", "Priority", "Status", "Date Submitted"
            }, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

    private final JTable complaintsTable = new JTable(tableModel);

    public MyComplaintsFrame(UserSession session) {
        if (session == null || !session.isCitizen()) {
            throw new SecurityException("Access Denied: Citizen role required to access My Complaints.");
        }
        this.session = session;
        this.userId = session.getUserId();

        setTitle("CivicLens - My Submitted Complaints");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(960, 480);
        setLocationRelativeTo(null);

        JLabel heading = new JLabel("My Complaints Tracker");
        heading.setFont(new Font("Arial", Font.BOLD, 20));

        JButton refreshButton = new JButton("Refresh");
        JButton viewEvidenceButton = new JButton("View Resolution Evidence");
        JButton feedbackButton = new JButton("Rate Resolution");
        JButton appealButton = new JButton("Appeal / Reopen");

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(heading, BorderLayout.WEST);
        topPanel.add(refreshButton, BorderLayout.EAST);
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomPanel.add(viewEvidenceButton);
        bottomPanel.add(feedbackButton);
        bottomPanel.add(appealButton);

        complaintsTable.setRowHeight(25);
        complaintsTable.setAutoCreateRowSorter(true);
        complaintsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(complaintsTable);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> loadComplaints());
        viewEvidenceButton.addActionListener(e -> handleViewEvidence());
        feedbackButton.addActionListener(e -> handleFeedback());
        appealButton.addActionListener(e -> handleAppeal());

        loadComplaints();
    }

    public MyComplaintsFrame(int userId) {
        this(resolveCitizenSession(userId));
    }

    private static UserSession resolveCitizenSession(int userId) {
        UserSession current = UserSession.getCurrentSession();
        if (current != null && current.isCitizen() && current.getUserId() == userId) {
            return current;
        }
        return new UserSession(userId, "Citizen", "citizen" + userId + "@civiclens.gov", UserSession.Role.CITIZEN);
    }

    private void loadComplaints() {
        tableModel.setRowCount(0);

        String sql =
            "SELECT complaint_id, title, category, priority, status, created_at " +
            "FROM complaints " +
            "WHERE user_id = ? " +
            "ORDER BY created_at DESC";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tableModel.addRow(new Object[]{
                        result.getInt("complaint_id"),
                        result.getString("title"),
                        result.getString("category"),
                        result.getString("priority"),
                        result.getString("status"),
                        result.getTimestamp("created_at")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to load complaints:\n" + ex.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int getSelectedComplaintId() {
        int row = complaintsTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint first.");
            return -1;
        }
        int modelRow = complaintsTable.convertRowIndexToModel(row);
        return ((Number) tableModel.getValueAt(modelRow, 0)).intValue();
    }

    private String getSelectedStatus() {
        int row = complaintsTable.getSelectedRow();
        if (row == -1) return "";
        int modelRow = complaintsTable.convertRowIndexToModel(row);
        return String.valueOf(tableModel.getValueAt(modelRow, 4));
    }

    private void handleViewEvidence() {
        int cid = getSelectedComplaintId();
        if (cid == -1) return;

        try (Connection conn = DbConnection.getConnection()) {
            String sql = "SELECT action_taken, resolution_category, evidence_notes, resolved_at FROM resolution_records WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, cid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String msg = "RESOLUTION EVIDENCE (Complaint #" + cid + "):\n\n" +
                                     "Action Taken: " + rs.getString("action_taken") + "\n" +
                                     "Category: " + rs.getString("resolution_category") + "\n" +
                                     "Resolved At: " + rs.getTimestamp("resolved_at") + "\n\n" +
                                     "Evidence Details:\n" + (rs.getString("evidence_notes") != null ? rs.getString("evidence_notes") : "None provided");
                        JTextArea area = new JTextArea(8, 40);
                        area.setText(msg);
                        area.setEditable(false);
                        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Resolution Record", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "No resolution record filed yet. Complaint is still being processed.");
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage());
        }
    }

    private void handleFeedback() {
        int cid = getSelectedComplaintId();
        if (cid == -1) return;

        String status = getSelectedStatus();
        if (!"RESOLVED".equalsIgnoreCase(status) && !"CLOSED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Feedback can only be submitted for RESOLVED complaints.");
            return;
        }

        JComboBox<Integer> ratingBox = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});
        JTextField commentField = new JTextField(25);

        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(new JLabel("Rating (1-5 stars):"));
        p.add(ratingBox);
        p.add(new JLabel("Optional Feedback Comments:"));
        p.add(commentField);

        int opt = JOptionPane.showConfirmDialog(this, p, "Satisfaction Rating (Complaint #" + cid + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opt == JOptionPane.OK_OPTION) {
            int rating = (Integer) ratingBox.getSelectedItem();
            String comments = commentField.getText().trim();
            try (Connection conn = DbConnection.getConnection()) {
                ResolutionService.submitFeedback(conn, cid, userId, rating, comments);
                JOptionPane.showMessageDialog(this, "Thank you! Your feedback has been recorded.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to submit feedback: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleAppeal() {
        int cid = getSelectedComplaintId();
        if (cid == -1) return;

        String status = getSelectedStatus();
        if (!"RESOLVED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Only complaints in RESOLVED status can be appealed or reopened.");
            return;
        }

        JTextArea reasonArea = new JTextArea(4, 30);
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(new JLabel("Mandatory Appeal / Reopen Rationale (min 10 characters):"), BorderLayout.NORTH);
        p.add(new JScrollPane(reasonArea), BorderLayout.CENTER);

        int opt = JOptionPane.showConfirmDialog(this, p, "Submit Formal Appeal (Complaint #" + cid + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opt == JOptionPane.OK_OPTION) {
            String reason = reasonArea.getText().trim();
            if (reason.length() < 10) {
                JOptionPane.showMessageDialog(this, "Appeal rationale must be at least 10 characters.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try (Connection conn = DbConnection.getConnection()) {
                int appealId = ResolutionService.submitAppeal(conn, cid, userId, reason);
                JOptionPane.showMessageDialog(this, "Appeal #" + appealId + " submitted successfully. Complaint status updated to APPEALED.");
                loadComplaints();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to submit appeal: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
