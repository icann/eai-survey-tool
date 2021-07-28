package org.icann.eai.survey;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Application Context. This class follows the Singleton pattern.
 */
public class Context {
    private static Context SINGLETON;
    private final Configuration config;
    private final DataSource database;
    private final SqlProvider sqlProvider;

    public static void init(Properties p) throws Exception {
        SINGLETON = new Context(p);
    }

    public static Context getInstance() {
        return SINGLETON;
    }

    private Context(Properties p) throws Exception {
        config = new Configuration(p);
        database = config.getDataSource();
        sqlProvider = new SqlProvider();
    }

    public static void poisonPill() {
        System.exit(1);
    }

    // --- Getters ---
    public Configuration getConfig() {
        return config;
    }

    public DataSource getDatabase() {
        return database;
    }

    public SqlProvider getSqlProvider() {
        return sqlProvider;
    }
}
