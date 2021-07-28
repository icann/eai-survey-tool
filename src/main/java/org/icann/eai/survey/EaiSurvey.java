package org.icann.eai.survey;

import org.xbill.DNS.Type;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Properties;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Main class.
 */
public class EaiSurvey {
    private static final String DEFAULT_CONFIG_FILENAME = "META-INF/default_configuration.properties";
    private static final String CONFIG_FILENAME = "config.properties";

    private static final Logger logger = Logger.getLogger(EaiSurvey.class.getCanonicalName());

    public static void main(String[] args) throws Exception {
        ClassLoader cl = EaiSurvey.class.getClassLoader();

        // --- Logging configuration ---
        try (InputStream in = cl.getResourceAsStream("logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
            }
        }

        // --- Load Default Configuration ---
        Properties p = new Properties();
        try (InputStream in = cl.getResourceAsStream(DEFAULT_CONFIG_FILENAME)) {
            p.load(in);
            logger.info("Default Configuration loaded: " + DEFAULT_CONFIG_FILENAME);
        }

        // --- Load User Configuration ---
        try (InputStream in = cl.getResourceAsStream(CONFIG_FILENAME)) {
            if (in != null) {
                p.load(in);
                logger.info("Configuration loaded: " + CONFIG_FILENAME);
            }
        }

        // --- Init Context ---
        Context.init(p);

        // --- Check if special command ---
        if (args.length > 0 && "SPECIAL_IP".equals(args[0])) {
            specialIP();
            return;
        }

        // --- Find Progress ---
        Step step = getProgress();

        // --- Store started timestamp ---
        if (Step.STARTED.getId() >= step.getId()) {
            updateProgress(Step.STARTED);
        }

        // --- Search Mail Servers ---
        if (Step.SEARCH_MX.getId() >= step.getId()) {
            logger.info("Searching mail servers");
            MxSearcher mxSearcher = new MxSearcher();
            mxSearcher.execute();
            updateProgress(Step.SEARCH_MX);
            logger.info("Mail servers searched");
        } else {
            logger.info("Mail servers already searched");
        }

        // --- Resolve IPv4 Mail Servers ---
        if (Step.RESOLVE_IPV4_MX.getId() >= step.getId()) {
            logger.info("Resolving (IPv4) mail servers");
            MxResolver mxResolver = new MxResolver(Type.A);
            mxResolver.execute();
            updateProgress(Step.RESOLVE_IPV4_MX);
            logger.info("Mail servers (IPv4) resolved");
        } else {
            logger.info("Mail servers (IPv4) already resolved");
        }

        // --- Resolve IPv6 Mail Servers ---
        if (Step.RESOLVE_IPV6_MX.getId() >= step.getId()) {
            logger.info("Resolving (IPv6) mail servers");
            MxResolver mxResolver = new MxResolver(Type.AAAA);
            mxResolver.execute();
            updateProgress(Step.RESOLVE_IPV6_MX);
            logger.info("Mail servers (IPv6) resolved");
        } else {
            logger.info("Mail servers (IPv6) already resolved");
        }

        // --- Testing Mail servers ---
        if (Step.PROBE_MX.getId() >= step.getId()) {
            logger.info("Testing mail servers");
            MxTester mxTester = new MxTester();
            mxTester.execute();
            updateProgress(Step.PROBE_MX);
            logger.info("Mail servers tested");
        } else {
            logger.info("Mail servers already tested");
        }

        // --- GeoIP Resolver ---
        if (Step.RESOLVE_GEOIP.getId() >= step.getId()) {
            logger.info("Resolving GeoIP");
            GeoIpResolver resolver = new GeoIpResolver();
            resolver.execute();
            updateProgress(Step.RESOLVE_GEOIP);
            logger.info("GeoIP resolved");
        } else {
            logger.info("GeoIP already resolved");
        }
    }

    private static Step getProgress() throws Exception {
        DataSource database = Context.getInstance().getDatabase();
        SqlProvider provider = Context.getInstance().getSqlProvider();
        String sql = provider.getSql(EaiSurvey.class, "get_progress");
        try (Connection con = database.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (!rs.next()) {
                return Step.DONE;
            }
            int id = rs.getInt("id");
            return Step.findByPrimaryKey(id);
        }

    }

    private static void updateProgress(Step step) throws Exception {
        DataSource database = Context.getInstance().getDatabase();
        SqlProvider provider = Context.getInstance().getSqlProvider();
        String sql = provider.getSql(EaiSurvey.class, "update_progress");
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1, step.getId());
            stmt.executeUpdate();
            con.commit();
        }
    }

    private static void specialIP() throws Exception {
        DataSource database = Context.getInstance().getDatabase();
        SqlProvider provider = Context.getInstance().getSqlProvider();
        String sql = provider.getSql(EaiSurvey.class, "get_all_ips");
        try (Connection con = database.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            long count = 0;
            long special = 0;
            while (rs.next()) {
                String ip = rs.getString("ip");
                if (SpecialIpBlock.isSpecial(ip)) special++;
                count++;
            }

            NumberFormat format = NumberFormat.getInstance();
            System.out.println("Total:   " + format.format(count));
            System.out.println("Special: " + format.format(special));
        }
    }
}
