
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

public class AdminDashboardFrame extends JFrame {

    private JTable complaintTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> employeeBox;

    private int selectedComplaintId = -1;

    public AdminDashboardFrame() {

        setTitle("Admin Dashboard - Smart Complaint System");
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Dashboard heading
        JLabel heading = new JLabel(
                "Admin Dashboard",
                SwingConstants.CENTER
        );

        heading.setFont(new Font("Arial", Font.BOLD, 26));
        heading.setBorder(
                BorderFactory.createEmptyBorder(15, 10, 15, 10)
        );

        add(heading, BorderLayout.NORTH);

        // Complaint table without complainant identity
        String[] columns = {
                "Complaint ID",
                "Title",
                "Category",
                "Priority",
                "Status",
                "Assigned To",
                "Created At"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        complaintTable = new JTable(tableModel);
        complaintTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );
        complaintTable.setRowHeight(25);
        complaintTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane scrollPane = new JScrollPane(complaintTable);
        add(scrollPane, BorderLayout.CENTER);

        // Employee dropdown
        employeeBox = new JComboBox<>();
        loadEmployees();

        // Buttons
        JButton refreshButton = new JButton("Refresh");
        JButton assignButton = new JButton("Assign Complaint");

        JButton employeeManagementButton =
                new JButton("Employee Management");

        JButton reportsButton =
                new JButton("Reports & Analytics");

        // Controls panel
        JPanel controls = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 10)
        );

        controls.add(new JLabel("Assign to employee:"));
        controls.add(employeeBox);
        controls.add(assignButton);
        controls.add(refreshButton);
        controls.add(employeeManagementButton);
        controls.add(reportsButton);

        add(controls, BorderLayout.SOUTH);

        // Refresh complaints
        refreshButton.addActionListener(e -> refreshData());

        // Assign selected complaint
        assignButton.addActionListener(e -> assignComplaint());

        // Open employee management
        employeeManagementButton.addActionListener(e -> {
            EmployeeManagementFrame frame =
                    new EmployeeManagementFrame();

            frame.setVisible(true);
        });

        // Open reports and analytics
        reportsButton.addActionListener(e -> {
            ReportsDashboardFrame frame =
                    new ReportsDashboardFrame();

            frame.setVisible(true);
        });

        // Store selected complaint ID internally
        complaintTable.getSelectionModel()
                .addListSelectionListener(e -> {

                    if (!e.getValueIsAdjusting()) {

                        int selectedRow =
                                complaintTable.getSelectedRow();

                        if (selectedRow >= 0) {
                            selectedComplaintId = Integer.parseInt(
                                    tableModel.getValueAt(
                                            selectedRow, 0
                                    ).toString()
                            );
                        } else {
                            selectedComplaintId = -1;
                        }
                    }
                });

        // Load complaints when the dashboard opens
        refreshData();
    }

    // Load employees into the dropdown
    private void loadEmployees() {

        employeeBox.removeAllItems();

        String sql =
                "SELECT user_id, name FROM users " +
                "WHERE role = 'EMPLOYEE' ORDER BY name";

        try (Connection connection = DbConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                int employeeId = result.getInt("user_id");
                String employeeName = result.getString("name");

                employeeBox.addItem(
                        employeeId + " - " + employeeName
                );
            }

        } catch (SQLException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Could not load employees.\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Load complaints without displaying complainant identity
    private void refreshData() {

        tableModel.setRowCount(0);

        String sql =
                "SELECT complaint_id, title, category, " +
                "priority, status, assigned_to, created_at " +
                "FROM complaints ORDER BY created_at DESC";

        try (Connection connection = DbConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {

                Object assignedTo =
                        result.getObject("assigned_to");

                tableModel.addRow(new Object[] {
                        result.getInt("complaint_id"),
                        result.getString("title"),
                        result.getString("category"),
                        result.getString("priority"),
                        result.getString("status"),
                        assignedTo == null
                                ? "Unassigned"
                                : assignedTo,
                        result.getTimestamp("created_at")
                });
            }

        } catch (SQLException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Could not load complaints.\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Assign a complaint to the selected employee
    private void assignComplaint() {

        if (selectedComplaintId == -1) {

            JOptionPane.showMessageDialog(
                    this,
                    "Please select a complaint from the table first.",
                    "No Complaint Selected",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        String selectedEmployee =
                (String) employeeBox.getSelectedItem();

        if (selectedEmployee == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "No employees available. Please add an employee first.",
                    "No Employee",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        int dashIndex = selectedEmployee.indexOf(" - ");

        if (dashIndex == -1) {

            JOptionPane.showMessageDialog(
                    this,
                    "Invalid employee selection.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        int employeeId;

        try {
            employeeId = Integer.parseInt(
                    selectedEmployee.substring(0, dashIndex)
            );
        } catch (NumberFormatException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Could not identify the selected employee.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        String sql =
                "UPDATE complaints " +
                "SET assigned_to = ?, status = 'ASSIGNED', " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE complaint_id = ?";

        try (Connection connection = DbConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);
            statement.setInt(2, selectedComplaintId);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {

                JOptionPane.showMessageDialog(
                        this,
                        "Complaint assigned successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE
                );

                selectedComplaintId = -1;
                complaintTable.clearSelection();

                refreshData();

            } else {

                JOptionPane.showMessageDialog(
                        this,
                        "Complaint not found. Please refresh and try again.",
                        "Assignment Failed",
                        JOptionPane.WARNING_MESSAGE
                );
            }

        } catch (SQLException ex) {

            JOptionPane.showMessageDialog(
                    this,
                    "Could not assign complaint.\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
