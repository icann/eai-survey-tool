package org.icann.eai.survey;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test all the MX servers.
 */
public class MxTester {
    private static final Logger logger = Logger.getLogger(MxTester.class.getCanonicalName());

    private final Configuration config;
    private final DataSource database;
    private final SqlProvider sqlProvider;

    public MxTester() {
        Context context = Context.getInstance();
        this.config = context.getConfig();
        this.database = context.getDatabase();
        this.sqlProvider = context.getSqlProvider();
    }

    private QuarantineVault createQuarantineValue() {
        QuarantineVault vault = new QuarantineVault();
        vault.setPeriod(config.getSmtpTesterQuarantinePeriod().toMillis());
        vault.setTimeout(config.getSmtpTesterQuarantineTimeout().toMillis());
        vault.setExpire(config.getSmtpTesterQuarantineExpire().toMillis());
        vault.setCidr4(config.getSmtpTesterQuarantineCidrIpv4());
        vault.setCidr6(config.getSmtpTesterQuarantineCidrIpv6());
        return vault;
    }

    public void execute() throws SQLException, InterruptedException {
        SynchronousQueue<String> inQueue = new SynchronousQueue<>();
        SynchronousQueue<SmtpTesterResult> outQueue = new SynchronousQueue<>();
        Sentinel sentinel = new Sentinel();
        sentinel.start();

        // --- Resolver Threads ---
        InetAddress[] locals4 = config.getLocalAddressesIpv4();
        InetAddress[] locals6 = config.getLocalAddressesIpv6();
        QuarantineVault[] vaults = new QuarantineVault[locals4.length];
        int count = config.getSmtpTesterThreads();
        for (int i = 0; i < vaults.length; i++) {
            vaults[i] = createQuarantineValue();
            vaults[i].start();
        }
        int i = 0;
        Thread[] threads = new Thread[locals4.length * count];
        for (int j = 0; j < count; j++) {
            for (int k = 0; k < locals4.length; k++) {
                threads[i] = new TesterThread(inQueue, outQueue, vaults[k], sentinel, locals4[k], locals6[k]);
                threads[i++].start();
            }
        }

        // --- Database Thread ---
        Runnable runnable = createDatabaseRunnable(outQueue);
        Thread dbThread = new Thread(runnable);
        dbThread.start();

        // --- Get Nameservers to test ---
        String sql = sqlProvider.getSql(getClass(), "find_list_to_search");
        try (Connection con = database.getConnection();
             Statement stmt = con.createStatement()) {

            stmt.setFetchSize(config.getDatabaseStatementFetchSize());
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    if (SpecialIpBlock.isSpecial(ip)) {
                        SmtpTesterResult result = new SmtpTesterResult(ip, 'S');
                        outQueue.put(result);
                        logger.info("Skipping Private/No Global IP Address: " + ip);
                    } else if (config.excludeIp(ip)) {
                        SmtpTesterResult result = new SmtpTesterResult(ip, 'X');
                        outQueue.put(result);
                        logger.info("Excluded IP Address: " + ip);
                    } else {
                        inQueue.put(ip);
                    }
                }
            }
        }

        // --- Wait for threads ---
        for (int j = 0; j < threads.length; j++) {
            inQueue.put(Constants.POISON_PILL);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        sentinel.turnoff();
        outQueue.put(new SmtpTesterResult());
        dbThread.join();
    }

    private Runnable createDatabaseRunnable(SynchronousQueue<SmtpTesterResult> outQueue) {
        return () -> {
            long limit = config.getDatabaseBatchSize();
            long count = 0;
            long wait = config.getSmtpTesterThreadsWait().toMillis();
            String sql = sqlProvider.getSql(getClass(), "update_ip_results");
            try (Connection con = database.getConnection();
                 PreparedStatement stmt = con.prepareStatement(sql)) {

                while (true) {
                    // --- Takes a list of records to store from the queue ---
                    SmtpTesterResult result = outQueue.poll(wait, TimeUnit.MILLISECONDS);
                    if (result == null || result.getServer() == null) {
                        break;
                    }

                    // --- Check the results ---
                    stmt.setString(1, result.getHeader());
                    stmt.setString(2, "" + result.getStatus());
                    stmt.setString(3, result.getEhlo());
                    stmt.setString(4, result.getEhloResult());
                    stmt.setString(5, result.getAscii());
                    stmt.setString(6, result.getAsciiResult());
                    stmt.setString(7, result.getIdn());
                    stmt.setString(8, result.getIdnResult());
                    stmt.setString(9, result.getServer());
                    stmt.addBatch();
                    if (count++ > limit) {
                        stmt.executeBatch();
                        con.commit();
                        count = 0;
                    }
                }

                // --- Stores and commit remaining data ---
                stmt.executeBatch();
                con.commit();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Unexpected error updating the database", t);
                Context.poisonPill();
            }
        };
    }
}
