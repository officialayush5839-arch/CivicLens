
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

    public ComplaintFrame(int userId) {
        this.userId = userId;

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

        // Recalculate immediately before saving.
        String priority = PriorityClassifier.classify(
            title, description, category
        );

        String sql =
            "INSERT INTO complaints " +
            "(user_id, title, category, description, priority) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (
            Connection connection = DbConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {
            statement.setInt(1, userId);
            statement.setString(2, title);
            statement.setString(3, category);
            statement.setString(4, description);
            statement.setString(5, priority);

            statement.executeUpdate();

            JOptionPane.showMessageDialog(
                this,
                "Complaint submitted successfully!\n" +
                "Automatically assigned priority: " + priority +
                "\nYour complaint status is PENDING."
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
