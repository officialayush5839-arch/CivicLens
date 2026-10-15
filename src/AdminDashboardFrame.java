import java.awt.*;
import java.sql.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import civiclens.audit.AuditTrailService;
import civiclens.audit.AuditTrailService.AuditEntry;
import civiclens.lifecycle.ResolutionService;
import civiclens.similarity.DuplicateCandidate;
import civiclens.similarity.DuplicateDetectionEngine;
import civiclens.similarity.IncidentManagementService;
import civiclens.sla.SlaEngine;
import civiclens.sla.SlaDeadlineInfo;
import civiclens.triage.TriageOverrideService;

/**
 * Modernized CivicLens Administrator Control Center.
 * Provides transparent case triage, staff assignment, auditable priority overrides,
 * duplicate incident detection & linking, audit history inspection, and operational analytics.
 */
public class AdminDashboardFrame extends JFrame {

    private final UserSession session;
    private JTable complaintTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> employeeBox;

    private int selectedComplaintId = -1;

    public AdminDashboardFrame(UserSession session) {
        if (session == null || !session.isAdmin()) {
            throw new SecurityException("Access Denied: Admin role required to access Admin Dashboard.");
        }
        this.session = session;
        initUI();
    }

    public AdminDashboardFrame() {
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
        throw new SecurityException("Access Denied: Admin role required to access Admin Dashboard.");
    }

    private void initUI() {
        setTitle("CivicLens - Administrative Operations Center");
        setSize(1280, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Header
        JLabel heading = new JLabel(
                "CivicLens Administration — Welcome, " + (session != null ? session.getFullName() : "Administrator"),
                SwingConstants.CENTER
        );
        heading.setFont(new Font("Arial", Font.BOLD, 22));
        heading.setBorder(BorderFactory.createEmptyBorder(12, 10, 8, 10));
        add(heading, BorderLayout.NORTH);

        // Columns with SLA State
        String[] columns = {
                "ID", "Title", "Category", "Priority", "Status", "SLA State", "Assigned Officer", "Created At"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        complaintTable = new JTable(tableModel);
        complaintTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        complaintTable.setRowHeight(25);
        complaintTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(complaintTable);
        add(scrollPane, BorderLayout.CENTER);

        // Employee dropdown
        employeeBox = new JComboBox<>();
        loadEmployees();

        // Control Buttons
        JButton assignButton = new JButton("Assign Officer");
        JButton overrideButton = new JButton("Override Priority");
        JButton duplicateButton = new JButton("Detect Duplicates");
        JButton auditButton = new JButton("Audit History");
        JButton refreshButton = new JButton("Refresh");
        JButton employeeManagementButton = new JButton("Staff Management");
        JButton reportsButton = new JButton("Analytics Dashboard");
        JButton logoutButton = new JButton("Logout");

        // Action controls panel (2 rows)
        JPanel controls = new JPanel(new GridLayout(2, 1, 5, 5));
        controls.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p1.add(new JLabel("Assign to:"));
        p1.add(employeeBox);
        p1.add(assignButton);
        p1.add(overrideButton);
        p1.add(duplicateButton);
        p1.add(auditButton);

        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p2.add(refreshButton);
        p2.add(employeeManagementButton);
        p2.add(reportsButton);
        p2.add(logoutButton);

        controls.add(p1);
        controls.add(p2);
        add(controls, BorderLayout.SOUTH);

        // Action Listeners
        refreshButton.addActionListener(e -> refreshData());
        assignButton.addActionListener(e -> assignComplaint());
        overrideButton.addActionListener(e -> handlePriorityOverride());
        duplicateButton.addActionListener(e -> handleDetectDuplicates());
        auditButton.addActionListener(e -> handleViewAuditHistory());

        employeeManagementButton.addActionListener(e -> {
            EmployeeManagementFrame frame = new EmployeeManagementFrame(session);
            frame.setVisible(true);
        });

        reportsButton.addActionListener(e -> {
            ReportsDashboardFrame frame = new ReportsDashboardFrame(session);
            frame.setVisible(true);
        });

        logoutButton.addActionListener(e -> {
            dispose();
            UserSession.clearCurrentSession();
            new LoginFrame().setVisible(true);
        });

        complaintTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = complaintTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = complaintTable.convertRowIndexToModel(selectedRow);
                    selectedComplaintId = Integer.parseInt(tableModel.getValueAt(modelRow, 0).toString());
                } else {
                    selectedComplaintId = -1;
                }
            }
        });

        refreshData();
    }

    private void loadEmployees() {
        employeeBox.removeAllItems();
        String sql = "SELECT user_id, full_name FROM users WHERE role = 'EMPLOYEE' ORDER BY full_name";
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                employeeBox.addItem(result.getInt("user_id") + " - " + result.getString("full_name"));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load employees: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshData() {
        tableModel.setRowCount(0);
        String sql = "SELECT c.complaint_id, c.title, c.category, c.priority, c.status, " +
                     "u.full_name as assignee_name, c.created_at " +
                     "FROM complaints c " +
                     "LEFT JOIN users u ON c.assigned_to = u.user_id " +
                     "ORDER BY c.created_at DESC";

        try (Connection connection = DbConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                int cid = result.getInt("complaint_id");
                String assignee = result.getString("assignee_name");

                SlaDeadlineInfo sla = SlaEngine.getDeadlineInfo(connection, cid);
                String slaState = sla != null ? sla.getSlaState().name() : "N/A";

                tableModel.addRow(new Object[]{
                        cid,
                        result.getString("title"),
                        result.getString("category"),
                        result.getString("priority"),
                        result.getString("status"),
                        slaState,
                        assignee != null ? assignee : "Unassigned",
                        result.getTimestamp("created_at")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not load complaints: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void assignComplaint() {
        if (selectedComplaintId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint from the table first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedEmployee = (String) employeeBox.getSelectedItem();
        if (selectedEmployee == null) {
            JOptionPane.showMessageDialog(this, "No employees available.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int dashIndex = selectedEmployee.indexOf(" - ");
        int employeeId = Integer.parseInt(selectedEmployee.substring(0, dashIndex));

        try (Connection conn = DbConnection.getConnection()) {
            ResolutionService.assignComplaint(conn, selectedComplaintId, employeeId, session != null ? session.getUserId() : 1);
            JOptionPane.showMessageDialog(this, "Complaint #" + selectedComplaintId + " assigned successfully!");
            selectedComplaintId = -1;
            complaintTable.clearSelection();
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Assignment failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handlePriorityOverride() {
        if (selectedComplaintId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint to override priority.");
            return;
        }

        JComboBox<String> prioCombo = new JComboBox<>(new String[]{"CRITICAL", "HIGH", "MEDIUM", "LOW"});
        JTextField reasonField = new JTextField(25);

        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(new JLabel("New Priority:"));
        p.add(prioCombo);
        p.add(new JLabel("Mandatory Override Rationale (min 5 chars):"));
        p.add(reasonField);

        int opt = JOptionPane.showConfirmDialog(this, p, "Audited Priority Override (Complaint #" + selectedComplaintId + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (opt == JOptionPane.OK_OPTION) {
            String newPrio = (String) prioCombo.getSelectedItem();
            String reason = reasonField.getText().trim();
            if (reason.length() < 5) {
                JOptionPane.showMessageDialog(this, "Reason must be at least 5 characters.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try (Connection conn = DbConnection.getConnection()) {
                PriorityOverrideService.recordOverride(conn, selectedComplaintId, session.getUserId(), newPrio, reason);
                JOptionPane.showMessageDialog(this, "Priority successfully updated to " + newPrio + " with immutable audit record.");
                refreshData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Override failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleDetectDuplicates() {
        if (selectedComplaintId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint to check for duplicate candidates.");
            return;
        }

        try (Connection conn = DbConnection.getConnection()) {
            String sql = "SELECT title, description, category FROM complaints WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, selectedComplaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String title = rs.getString("title");
                        String desc = rs.getString("description");
                        String cat = rs.getString("category");

                        List<DuplicateCandidate> list = DuplicateDetectionEngine.findDuplicatesInDb(conn, selectedComplaintId, title, desc, cat, 0.35);
                        if (list.isEmpty()) {
                            JOptionPane.showMessageDialog(this, "No duplicate or related incident candidates detected (Threshold: 0.35).",
                                    "Candidate Detection", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            StringBuilder sb = new StringBuilder("Detected " + list.size() + " Potential Duplicate / Related Candidates:\n\n");
                            for (DuplicateCandidate c : list) {
                                sb.append(String.format("• Complaint #%d: \"%s\" (Score: %.2f)\n  Explanation: %s\n\n",
                                        c.getComplaintId(), c.getTitle(), c.getSimilarityScore(), c.getExplanation()));
                            }
                            JTextArea area = new JTextArea(12, 50);
                            area.setText(sb.toString());
                            area.setEditable(false);
                            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Duplicate Incident Candidates", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Detection error: " + ex.getMessage());
        }
    }

    private void handleViewAuditHistory() {
        if (selectedComplaintId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a complaint to view audit history.");
            return;
        }

        try (Connection conn = DbConnection.getConnection()) {
            List<AuditEntry> history = AuditTrailService.getAuditHistory(conn, selectedComplaintId);
            if (history.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No audit events recorded for Complaint #" + selectedComplaintId);
                return;
            }

            StringBuilder sb = new StringBuilder("TIMELINE AUDIT LOG FOR COMPLAINT #" + selectedComplaintId + ":\n\n");
            for (AuditEntry e : history) {
                sb.append(String.format("[%s] Action: %s\n  State: %s -> %s | Actor: %s (%s)\n  Details: %s\n\n",
                        e.createdAt, e.action, e.previousState != null ? e.previousState : "INIT",
                        e.newState, e.actorName, e.actorRole, e.details != null ? e.details : "None"));
            }

            JTextArea area = new JTextArea(14, 55);
            area.setText(sb.toString());
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Immutable Audit History", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error fetching audit history: " + ex.getMessage());
        }
    }
}
