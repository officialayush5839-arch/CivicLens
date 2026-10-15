
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MyComplaintsFrame extends JFrame {

    private final int userId;

    private final DefaultTableModel tableModel =
        new DefaultTableModel(
            new String[]{
                "Complaint ID",
                "Title",
                "Category",
                "Priority",
                "Status",
                "Date Submitted"
            }, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

    private final JTable complaintsTable =
        new JTable(tableModel);

    public MyComplaintsFrame(int userId) {
        this.userId = userId;

        setTitle("Smart Complaint System - My Complaints");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 400);
        setLocationRelativeTo(null);

        JLabel heading = new JLabel("My Complaints");
        heading.setFont(new Font("Arial", Font.BOLD, 22));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadComplaints());

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(heading, BorderLayout.WEST);
        topPanel.add(refreshButton, BorderLayout.EAST);
        topPanel.setBorder(
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        );

        complaintsTable.setRowHeight(25);
        complaintsTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane =
            new JScrollPane(complaintsTable);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        loadComplaints();
    }

    private void loadComplaints() {
        tableModel.setRowCount(0);

        String sql =
            "SELECT complaint_id, title, category, priority, " +
            "status, created_at " +
            "FROM complaints " +
            "WHERE user_id = ? " +
            "ORDER BY created_at DESC";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql)
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

            if (tableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(
                    this,
                    "You haven't submitted any complaints yet."
                );
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to load complaints:\n" + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}

