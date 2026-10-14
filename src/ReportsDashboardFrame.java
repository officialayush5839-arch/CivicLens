import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

public class ReportsDashboardFrame extends JFrame {

    private final JLabel totalLabel = new JLabel("0");
    private final JLabel resolvedLabel = new JLabel("0");
    private final JLabel unresolvedLabel = new JLabel("0");
    private final JLabel rateLabel = new JLabel("0%");

    private final JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 15, 15));

    public ReportsDashboardFrame() {
        setTitle("Reports & Analytics");
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        JLabel heading = new JLabel(
                "Reports & Analytics", SwingConstants.CENTER);
        heading.setFont(new Font("Arial", Font.BOLD, 26));

        JPanel summaryPanel = new JPanel(new GridLayout(1, 4, 12, 12));
        summaryPanel.add(createCard("Total Complaints", totalLabel));
        summaryPanel.add(createCard("Resolved", resolvedLabel));
        summaryPanel.add(createCard("Unresolved", unresolvedLabel));
        summaryPanel.add(createCard("Resolution Rate", rateLabel));

        JButton refreshButton = new JButton("Refresh Reports");
        refreshButton.addActionListener(e -> loadReports());

        JPanel topPanel = new JPanel(new BorderLayout(10, 15));
        topPanel.add(heading, BorderLayout.NORTH);
        topPanel.add(summaryPanel, BorderLayout.CENTER);
        topPanel.add(refreshButton, BorderLayout.SOUTH);
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));

        chartsPanel.setBorder(
                BorderFactory.createEmptyBorder(5, 15, 15, 15));

        add(topPanel, BorderLayout.NORTH);
        add(chartsPanel, BorderLayout.CENTER);

        loadReports();
    }

    private JPanel createCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(5, 10));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(15, 10, 15, 10)));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.PLAIN, 15));

        valueLabel.setFont(new Font("Arial", Font.BOLD, 25));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private void loadReports() {
        String[] statuses = {
            "SUBMITTED", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"
        };

        String[] priorities = {
            "LOW", "MEDIUM", "HIGH", "CRITICAL"
        };

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> priorityCounts = new LinkedHashMap<>();

        for (String status : statuses) {
            statusCounts.put(status, 0);
        }

        for (String priority : priorities) {
            priorityCounts.put(priority, 0);
        }

        int total = 0;
        int resolved = 0;

        String statusSql =
                "SELECT status, COUNT(*) AS total " +
                "FROM complaints GROUP BY status";

        String prioritySql =
                "SELECT priority, COUNT(*) AS total " +
                "FROM complaints GROUP BY priority";

        try (Connection connection = DbConnection.getConnection()) {

            try (PreparedStatement statement =
                    connection.prepareStatement(statusSql);
                 ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    String status = result.getString("status");
                    int count = result.getInt("total");

                    statusCounts.put(status, count);
                    total += count;

                    if ("RESOLVED".equals(status)
                            || "CLOSED".equals(status)) {
                        resolved += count;
                    }
                }
            }

            try (PreparedStatement statement =
                    connection.prepareStatement(prioritySql);
                 ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    String priority = result.getString("priority");
                    int count = result.getInt("total");
                    priorityCounts.put(priority, count);
                }
            }

            totalLabel.setText(String.valueOf(total));
            resolvedLabel.setText(String.valueOf(resolved));
            unresolvedLabel.setText(String.valueOf(total - resolved));

            double rate = total == 0
                    ? 0.0
                    : (resolved * 100.0 / total);

            rateLabel.setText(String.format("%.1f%%", rate));

            updateCharts(statusCounts, priorityCounts);

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load reports from the database.\n"
                            + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateCharts(
            Map<String, Integer> statusCounts,
            Map<String, Integer> priorityCounts) {

        DefaultPieDataset<String> pieDataset = new DefaultPieDataset<>();

        for (Map.Entry<String, Integer> entry
                : statusCounts.entrySet()) {
            pieDataset.setValue(entry.getKey(), entry.getValue());
        }

        JFreeChart pieChart = ChartFactory.createPieChart(
                "Complaints by Status",
                pieDataset,
                true,
                true,
                false);

        PiePlot<?> piePlot = (PiePlot<?>) pieChart.getPlot();
        piePlot.setLabelFont(new Font("Arial", Font.PLAIN, 11));

        DefaultCategoryDataset barDataset =
                new DefaultCategoryDataset();

        for (Map.Entry<String, Integer> entry
                : priorityCounts.entrySet()) {
            barDataset.addValue(
                    entry.getValue(),
                    "Complaints",
                    entry.getKey());
        }

        JFreeChart barChart = ChartFactory.createBarChart(
                "Complaints by Priority",
                "Priority",
                "Number of Complaints",
                barDataset);

        chartsPanel.removeAll();
        chartsPanel.add(new ChartPanel(pieChart));
        chartsPanel.add(new ChartPanel(barChart));

        chartsPanel.revalidate();
        chartsPanel.repaint();
    }
}
