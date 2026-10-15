import javax.swing.*;
import java.sql.Connection;
import civiclens.sla.SlaEngine;

/**
 * CivicLens — Explainable and Accountable Complaint Resolution Platform.
 * Application Entry Point.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  CivicLens — Explainable and Accountable Complaint Resolution    ");
        System.out.println("==================================================================");

        // 1. Initialize Modern FlatLaf Look and Feel
        initializeModernTheme();

        // 2. Perform Database Health Check & Downtime SLA Reconciliation
        performStartupReconciliation();

        // 3. Initialize Localhost Automation Web Server for 3D Presentation
        boolean noWeb = false;
        for (String arg : args) {
            if ("--no-web".equalsIgnoreCase(arg)) {
                noWeb = true;
                break;
            }
        }
        if (!noWeb) {
            startLocalhostAutomation();
        }

        // 4. Launch Demo Mode or Login Frame
        boolean isDemo = false;
        for (String arg : args) {
            if ("--demo".equalsIgnoreCase(arg) || "-demo".equalsIgnoreCase(arg)) {
                isDemo = true;
                break;
            }
        }

        if (isDemo || AppConfig.isDemoMode()) {
            System.out.println(">>> Launching in Interactive Demonstration Mode (--demo)");
            AppConfig.setDemoMode(true);
            DemoLauncher.runDemoScenarios();
            SwingUtilities.invokeLater(() -> {
                UserSession adminDemo = new UserSession(1, "System Administrator", "admin@civiclens.gov", UserSession.Role.ADMIN);
                UserSession.setCurrentSession(adminDemo);
                AdminDashboardFrame adminFrame = new AdminDashboardFrame(adminDemo);
                adminFrame.setVisible(true);
            });
        } else {
            System.out.println(">>> Launching CivicLens Login Portal...");
            SwingUtilities.invokeLater(() -> {
                new LoginFrame().setVisible(true);
            });
        }
    }

    private static void initializeModernTheme() {
        try {
            // Attempt FlatLaf initialization
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
            System.out.println("[UI] FlatLaf modern desktop theme activated successfully.");
        } catch (Throwable t) {
            try {
                // Graceful fallback to System Look and Feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                System.out.println("[UI] Using system look and feel: " + UIManager.getLookAndFeel().getName());
            } catch (Exception e) {
                System.err.println("[UI] Warning: Unable to initialize system look and feel: " + e.getMessage());
            }
        }
    }

    private static void performStartupReconciliation() {
        try (Connection conn = DbConnection.getConnection()) {
            System.out.println("[Core] Database connected successfully: " + AppConfig.getDatabaseType());
            int reconciled = SlaEngine.reconcileDowntime(conn);
            if (reconciled > 0) {
                System.out.println("[SLA] Downtime Reconciliation: Reconciled " + reconciled + " overdue complaints.");
            } else {
                System.out.println("[SLA] Startup check: SLA deadlines and queues synchronized.");
            }
        } catch (Exception e) {
            System.err.println("[Core] Warning during startup verification: " + e.getMessage());
        }
    }

    private static void startLocalhostAutomation() {
        Thread serverThread = new Thread(() -> {
            try {
                civiclens.automation.PresentationServer server =
                        new civiclens.automation.PresentationServer(8080, java.nio.file.Paths.get("."));
                server.start(true);
            } catch (Exception ex) {
                System.out.println("[Automation] Localhost web server info: " + ex.getMessage());
            }
        }, "CivicLens-Localhost-Automation");
        serverThread.setDaemon(true);
        serverThread.start();
    }
}
