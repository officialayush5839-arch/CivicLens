import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, multi-tiered configuration loader for CivicLens.
 *
 * Precedence hierarchy (highest to lowest):
 * 1. Runtime programmatic overrides (AppConfig.setOverride)
 * 2. JVM System Properties (-Ddb.type=... / -DDB_TYPE=...)
 * 3. OS Environment Variables (DB_TYPE=... / db.type=...)
 * 4. .env file in project root or current working directory
 * 5. config.properties file in project root or classpath
 * 6. Hardcoded default value passed to getter
 *
 * Features automatic key normalization between SNAKE_CASE and dot.case.
 */
public class AppConfig {

    private static final Map<String, String> ENV_CACHE = new HashMap<>();
    private static final Properties PROPS_CACHE = new Properties();
    private static final Map<String, String> OVERRIDES = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static volatile Boolean demoModeOverride = null;

    static {
        loadConfig();
    }

    /**
     * Loads or reloads configurations from .env, config.properties, and environment.
     */
    public static synchronized void loadConfig() {
        ENV_CACHE.clear();
        PROPS_CACHE.clear();

        // 1. Try loading .env from current directory or project root
        loadDotEnv(".env");

        // 2. Try loading config.properties from file or classpath
        loadProperties("config.properties");

        initialized = true;
    }

    public static synchronized void reload() {
        loadConfig();
    }

    private static void loadDotEnv(String filename) {
        Path envPath = Paths.get(filename);
        if (!Files.exists(envPath)) {
            // Also check parent directory if running inside a subfolder
            Path parentPath = Paths.get("..", filename);
            if (Files.exists(parentPath)) {
                envPath = parentPath;
            } else {
                return;
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) ||
                        (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    ENV_CACHE.put(key, val);
                }
            }
        } catch (IOException ignored) {
            // Graceful fallback if .env cannot be read
        }
    }

    private static void loadProperties(String filename) {
        Path propPath = Paths.get(filename);
        if (Files.exists(propPath)) {
            try (InputStream in = Files.newInputStream(propPath)) {
                PROPS_CACHE.load(in);
                return;
            } catch (IOException ignored) {
            }
        }

        // Try classpath resource
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(filename)) {
            if (in != null) {
                PROPS_CACHE.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Resolves a configuration key across all tiers with automatic key normalization.
     */
    public static String get(String key, String defaultValue) {
        if (key == null) return defaultValue;

        if (!initialized) {
            loadConfig();
        }

        String snakeKey = toSnakeCase(key);
        String dotKey = toDotCase(key);

        // Tier 1: Overrides
        if (OVERRIDES.containsKey(key)) return OVERRIDES.get(key);
        if (OVERRIDES.containsKey(snakeKey)) return OVERRIDES.get(snakeKey);
        if (OVERRIDES.containsKey(dotKey)) return OVERRIDES.get(dotKey);

        // Tier 2: JVM System Properties
        String sysVal = System.getProperty(key);
        if (sysVal != null && !sysVal.isEmpty()) return sysVal;
        sysVal = System.getProperty(snakeKey);
        if (sysVal != null && !sysVal.isEmpty()) return sysVal;
        sysVal = System.getProperty(dotKey);
        if (sysVal != null && !sysVal.isEmpty()) return sysVal;

        // Tier 3: OS Environment Variables
        String envVal = System.getenv(snakeKey);
        if (envVal != null && !envVal.isEmpty()) return envVal;
        envVal = System.getenv(key);
        if (envVal != null && !envVal.isEmpty()) return envVal;
        envVal = System.getenv(dotKey);
        if (envVal != null && !envVal.isEmpty()) return envVal;

        // Tier 4: .env file cache
        if (ENV_CACHE.containsKey(snakeKey)) return ENV_CACHE.get(snakeKey);
        if (ENV_CACHE.containsKey(key)) return ENV_CACHE.get(key);
        if (ENV_CACHE.containsKey(dotKey)) return ENV_CACHE.get(dotKey);

        // Tier 5: config.properties cache
        if (PROPS_CACHE.containsKey(dotKey)) return PROPS_CACHE.getProperty(dotKey);
        if (PROPS_CACHE.containsKey(key)) return PROPS_CACHE.getProperty(key);
        if (PROPS_CACHE.containsKey(snakeKey)) return PROPS_CACHE.getProperty(snakeKey);

        // Tier 6: Default value
        return defaultValue;
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val.trim()) || "1".equals(val.trim());
    }

    public static double getDouble(String key, double defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    // Programmatic override controls
    public static void setOverride(String key, String value) {
        if (key != null) {
            if (value != null) {
                OVERRIDES.put(key, value);
            } else {
                OVERRIDES.remove(key);
            }
        }
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
        demoModeOverride = null;
    }

    // Domain / System helper methods
    public static boolean isDemoMode() {
        if (demoModeOverride != null) {
            return demoModeOverride;
        }
        return getBoolean("app.demo.mode", getBoolean("APP_DEMO_MODE", false));
    }

    public static void setDemoMode(boolean demo) {
        demoModeOverride = demo;
        setOverride("app.demo.mode", String.valueOf(demo));
    }

    public static String getDatabaseType() {
        String type = get("db.type", get("DB_TYPE", "sqlite"));
        if (type == null) return "sqlite";
        String normalized = type.trim().toLowerCase();
        if ("mysql".equals(normalized)) return "mysql";
        return "sqlite";
    }

    public static boolean isAutoFallbackEnabled() {
        return getBoolean("db.auto.fallback", getBoolean("DB_AUTO_FALLBACK", true));
    }

    public static String getSqliteDbPath() {
        return get("sqlite.db.path", get("SQLITE_DB_PATH", "civiclens.db"));
    }

    public static String getDbUrl() {
        return get("db.url", get("DB_URL", "jdbc:mysql://localhost:3306/civiclens_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
    }

    public static String getDbUser() {
        return get("db.user", get("DB_USER", "root"));
    }

    public static String getDbPassword() {
        return get("db.password", get("DB_PASSWORD", ""));
    }

    public static int getBcryptRounds() {
        return getInt("bcrypt.rounds", getInt("BCRYPT_ROUNDS", 12));
    }

    // Helper normalization routines
    private static String toSnakeCase(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    private static String toDotCase(String key) {
        return key.replace('_', '.').replace('-', '.').toLowerCase();
    }
}
