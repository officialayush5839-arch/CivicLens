import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import civiclens.lifecycle.ResolutionService;
import civiclens.sla.SlaEngine;
import civiclens.sla.SlaDeadlineInfo;

/**
 * Modernized Employee Dashboard with real-time SLA countdowns,
 * structured evidence-backed resolution dialogs, and lifecycle enforcement.
 */
public class EmployeeDashboardFrame extends JFrame {

    private final int employeeId;
    private final String employeeName;

    private final DefaultTableModel model = new DefaultTableModel(
        new String[]{
            "ID", "Title", "Category", "Priority", "Status", "SLA Status", "Remaining Time", "Created At"
        }, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(model);

    private final JComboBox<String> statusBox =
        new JComboBox<>(new String[]{
            "IN_PROGRESS", "RESOLVED"
        });

    private final UserSession session;

    public EmployeeDashboardFrame(UserSession session) {
        if (session == null || !session.isEmployee()) {
            throw new SecurityException("Access Denied: Employee role required to access Employee Dashboard.");
        }
        this.session = session;
        this.employeeId = session.getUserId();
        this.employeeName = session.getFullName();

        setTitle("CivicLens - Staff Operations Queue (" + employeeName + ")");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1150, 560);
        setLocationRelativeTo(null);

        JLabel heading = new JLabel(
            "Operations Queue - Assigned Officer: " + employeeName
        );
        heading.setFont(new Font("Arial", Font.BOLD, 20));

        JButton refreshButton = new JButton("Refresh Queue");
        JButton updateButton = new JButton("Execute Action");
        JButton viewDetailsButton = new JButton("View Full Details");
        JButton logoutButton = new JButton("Logout");

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controls.add(new JLabel("Transition to:"));
        controls.add(statusBox);
        controls.add(updateButton);
        controls.add(viewDetailsButton);
        controls.add(refreshButton);
        controls.add(logoutButton);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        top.add(heading, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);

        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadAssignedComplaints());
        updateButton.addActionListener(e -> handleStatusTransition());
        viewDetailsButton.addActionListener(e -> viewSelectedComplaintDetails());

        logoutButton.addActionListener(e -> {
            dispose();
            UserSession.clearCurrentSession();
            new LoginFrame().setVisible(true);
        });

        loadAssignedComplaints();
    }

    public EmployeeDashboardFrame(int employeeId, String employeeName) {
        this(resolveEmployeeSession(employeeId, employeeName));
    }

    private static UserSession resolveEmployeeSession(int employeeId, String employeeName) {
        UserSession current = UserSession.getCurrentSession();
        if (current != null && current.isEmployee() && current.getUserId() == employeeId) {
            return current;
        }
        return new UserSession(employeeId, employeeName, "employee" + employeeId + "@civiclens.gov", UserSession.Role.EMPLOYEE);
    }

    private void loadAssignedComplaints() {
        model.setRowCount(0);

        String sql =
            "SELECT complaint_id, title, category, priority, status, created_at " +
            "FROM complaints " +
            "WHERE assigned_to = ? " +
            "ORDER BY created_at DESC";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, employeeId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int cid = result.getInt("complaint_id");
                    String prio = result.getString("priority");
                    String stat = result.getString("status");
                    Timestamp ts = result.getTimestamp("created_at");

                    // Query SLA status
                    SlaDeadlineInfo sla = SlaEngine.getDeadlineInfo(connection, cid);
                    String slaStateStr = sla != null ? sla.getSlaState().name() : "N/A";
                    String remainingStr = sla != null ? sla.getFormattedRemainingTime() : "N/A";

                    model.addRow(new Object[]{
                        cid,
                        result.getString("title"),
                        result.getString("category"),
                        prio,
                        stat,
                        slaStateStr,
                        remainingStr,
                        ts
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to load assigned complaints:\n" + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleStatusTransition() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint first.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        int complaintId = ((Number) model.getValueAt(modelRow, 0)).intValue();
        String currentStatus = String.valueOf(model.getValueAt(modelRow, 4));
        String newStatus = (String) statusBox.getSelectedItem();

        if ("RESOLVED".equalsIgnoreCase(currentStatus) || "CLOSED".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "This complaint is already resolved.");
            return;
        }

        try (Connection conn = DbConnection.getConnection()) {
            if ("IN_PROGRESS".equalsIgnoreCase(newStatus)) {
                ResolutionService.startWork(conn, complaintId, employeeId);
                JOptionPane.showMessageDialog(this, "Complaint #" + complaintId + " is now IN_PROGRESS.");
                loadAssignedComplaints();
            } else if ("RESOLVED".equalsIgnoreCase(newStatus)) {
                promptAndResolveComplaint(conn, complaintId);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Operation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void promptAndResolveComplaint(Connection conn, int complaintId) throws SQLException {
        // Structured Resolution Input Dialog
        JTextField actionField = new JTextField(30);
        JComboBox<String> categoryBox = new JComboBox<>(new String[]{
                "Repaired Component", "Configuration Change", "Staff Deployed", "Administrative Action", "User Educated"
        });
        JTextArea evidenceArea = new JTextArea(4, 30);
        JTextArea internalNotesArea = new JTextArea(3, 30);

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Action Taken (Mandatory, min 5 chars):"));
        panel.add(actionField);
        panel.add(new JLabel("Resolution Category:"));
        panel.add(categoryBox);
        panel.add(new JLabel("Evidence / Verification Notes:"));
        panel.add(new JScrollPane(evidenceArea));
        panel.add(new JLabel("Internal Staff Notes:"));
        panel.add(new JScrollPane(internalNotesArea));

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Structured Resolution Evidence (Complaint #" + complaintId + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String action = actionField.getText().trim();
            String cat = (String) categoryBox.getSelectedItem();
            String evidence = evidenceArea.getText().trim();
            String notes = internalNotesArea.getText().trim();

            if (action.length() < 5) {
                JOptionPane.showMessageDialog(this, "Action taken must be at least 5 characters.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ResolutionService.resolveComplaint(conn, complaintId, employeeId, action, cat, evidence, notes);
            JOptionPane.showMessageDialog(this, "Complaint #" + complaintId + " successfully marked RESOLVED with structured evidence.");
            loadAssignedComplaints();
        }
    }

    private void viewSelectedComplaintDetails() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint to inspect.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        int complaintId = ((Number) model.getValueAt(modelRow, 0)).intValue();

        try (Connection conn = DbConnection.getConnection()) {
            String sql = "SELECT description, internal_notes FROM complaints WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String desc = rs.getString("description");
                        String notes = rs.getString("internal_notes");
                        JTextArea area = new JTextArea(10, 40);
                        area.setText("COMPLAINT #" + complaintId + " DETAILS:\n\n" +
                                     "Citizen Description:\n" + desc + "\n\n" +
                                     "Internal Staff Notes:\n" + (notes != null ? notes : "(None)"));
                        area.setEditable(false);
                        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Complaint Details", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error fetching details: " + ex.getMessage());
        }
    }
}