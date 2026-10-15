import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class EmployeeDashboardFrame extends JFrame {

    private final int employeeId;
    private final String employeeName;

    private final DefaultTableModel model = new DefaultTableModel(
        new String[]{
            "Complaint ID", "Title", "Category",
            "Description", "Priority", "Status", "Created At"
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

    public EmployeeDashboardFrame(int employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;

        setTitle("Smart Complaint System - Employee Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 500);
        setLocationRelativeTo(null);

        JLabel heading = new JLabel(
            "Employee Dashboard - Welcome, " + employeeName
        );
        heading.setFont(new Font("Arial", Font.BOLD, 20));

        JButton refreshButton = new JButton("Refresh");
        JButton updateButton = new JButton("Update Status");
        JButton logoutButton = new JButton("Logout");

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("New status:"));
        controls.add(statusBox);
        controls.add(updateButton);
        controls.add(refreshButton);
        controls.add(logoutButton);

        JPanel top = new JPanel(new BorderLayout(10, 10));
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        top.add(heading, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);

        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadAssignedComplaints());
        updateButton.addActionListener(e -> updateStatus());

        logoutButton.addActionListener(e -> {
            new LoginFrame().setVisible(true);
            dispose();
        });

        loadAssignedComplaints();
    }

    private void loadAssignedComplaints() {
        model.setRowCount(0);

        String sql =
            "SELECT complaint_id, title, category, description, " +
            "priority, status, created_at " +
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
                    model.addRow(new Object[]{
                        result.getInt("complaint_id"),
                        result.getString("title"),
                        result.getString("category"),
                        result.getString("description"),
                        result.getString("priority"),
                        result.getString("status"),
                        result.getTimestamp("created_at")
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

    private void updateStatus() {
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(
                this, "Please select a complaint first."
            );
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);

        int complaintId =
            ((Number) model.getValueAt(modelRow, 0)).intValue();

        String currentStatus =
            String.valueOf(model.getValueAt(modelRow, 5));

        if ("RESOLVED".equals(currentStatus)) {
            JOptionPane.showMessageDialog(
                this, "This complaint is already resolved."
            );
            return;
        }

        String newStatus = (String) statusBox.getSelectedItem();

        String sql =
            "UPDATE complaints SET status = ? " +
            "WHERE complaint_id = ? AND assigned_to = ? " +
            "AND status IN ('ASSIGNED', 'IN_PROGRESS')";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, newStatus);
            statement.setInt(2, complaintId);
            statement.setInt(3, employeeId);

            int updated = statement.executeUpdate();

            if (updated > 0) {
                JOptionPane.showMessageDialog(
                    this,
                    "Complaint #" + complaintId +
                    " updated to " + newStatus + "."
                );
                loadAssignedComplaints();
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Status was not updated. Refresh and check the complaint."
                );
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to update status:\n" + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}