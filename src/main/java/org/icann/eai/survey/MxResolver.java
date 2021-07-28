package org.icann.eai.survey;

import org.xbill.DNS.*;

import javax.sql.DataSource;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves all the domain names of the MX servers.
 */
public class MxResolver {
    private static final Logger logger = Logger.getLogger(MxResolver.class.getCanonicalName());

    private final Configuration config;
    private final DataSource database;
    private final SqlProvider sqlProvider;
    private final int type;

    public MxResolver(int type) {
        Context context = Context.getInstance();
        this.config = context.getConfig();
        this.database = context.getDatabase();
        this.sqlProvider = context.getSqlProvider();
        this.type = type;
    }

    public void execute() throws SQLException, InterruptedException {
        SynchronousQueue<String> inQueue = new SynchronousQueue<>();
        SynchronousQueue<Message> outQueue = new SynchronousQueue<>();
        Sentinel sentinel = new Sentinel();
        sentinel.start();

        // --- Resolver Threads ---
        InetAddress[] locals4 = config.getLocalAddressesIpv4();
        InetAddress[] locals6 = config.getLocalAddressesIpv6();
        InetSocketAddress[] servers = config.getResolverAddresses();
        int count = config.getResolverThreads();
        List<Thread> threads = new ArrayList<>();
        for (InetSocketAddress server : servers) {
            for (InetAddress local : server.getAddress() instanceof Inet4Address ? locals4 : locals6) {
                for (int k = 0; k < count; k++) {
                    Thread thread = new ResolverThread(server, type, inQueue, outQueue, sentinel, local);
                    thread.start();
                    threads.add(thread);
                }
            }
        }

        // --- Database Thread ---
        Runnable runnable = createDatabaseRunnable(outQueue);
        Thread dbThread = new Thread(runnable);
        dbThread.start();

        // --- Get Nameservers to resolve ---
        String sql = sqlProvider.getSql(getClass(), "find_list_to_search");
        try (Connection con = database.getConnection();
             Statement stmt = con.createStatement()) {

            stmt.setFetchSize(config.getDatabaseStatementFetchSize());
            try (ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String owner = rs.getString("mx");
                    inQueue.put(owner);
                }
            }
        }
        sentinel.turnoff();

        // --- Poison Pill Threads ---
        for (int j = 0; j < threads.size(); j++) {
            inQueue.put(Constants.POISON_PILL);
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // --- End DB Thread ---
        outQueue.put(new Message());
        dbThread.join();

        // --- Create ip records ---
        if (type == Type.AAAA) {
            sql = sqlProvider.getSql(getClass(), "create_ip");
            try (Connection con = database.getConnection();
                 Statement stmt = con.createStatement()) {
                stmt.execute(sql);
                con.commit();
            }
        }
    }

    private Runnable createDatabaseRunnable(SynchronousQueue<Message> outQueue) {
        return () -> {
            long limit = config.getDatabaseBatchSize() - 10;
            long count = 0;
            long wait = config.getResolverWait().toMillis();
            String sql1 = sqlProvider.getSql(getClass(), "insert_mx_ip");
            String sql2 = sqlProvider.getSql(getClass(), "update_mx_searched_" + (type == Type.A ? "4" : "6"));
            try (Connection con1 = database.getConnection();
                 Connection con2 = database.getConnection();
                 PreparedStatement stmt1 = con1.prepareStatement(sql1);
                 PreparedStatement stmt2 = con2.prepareStatement(sql2)) {

                while (true) {
                    // --- Takes a list of records to store from the queue ---
                    Message message = outQueue.poll(wait, TimeUnit.MILLISECONDS);
                    if (message == null || message.getQuestion() == null) {
                        break;
                    }

                    // --- Check the results ---
                    Name name = message.getQuestion().getName();
                    if (message.getHeader().getRcode() != Rcode.NOERROR) {
                        String owner = message.getQuestion().getName().toString().toLowerCase();
                        stmt2.setString(1, String.valueOf(message.getHeader().getRcode()));
                        stmt2.setString(2, owner);
                        stmt2.addBatch();
                        continue;
                    }

                    List<Record> list = new ArrayList<>();
                    for (Record r : message.getSection(Section.ANSWER)) {
                        if (r.getName().equals(name) && r.getType() == type) {
                            list.add(r);
                        }
                    }
                    if (list.size() == 0) {
                        String owner = message.getQuestion().getName().toString().toLowerCase();
                        stmt2.setString(1, "X");
                        stmt2.setString(2, owner);
                        stmt2.addBatch();
                        continue;
                    }

                    // --- Stores the list into the database ---
                    boolean send = false;
                    String owner = null;
                    for (Record record : list) {
                        // --- Store name server resolution --
                        owner = record.getName().toString().toLowerCase();
                        String ip;
                        if (type == Type.A) {
                            ip = ((ARecord) record).getAddress().getHostAddress();
                        } else {
                            ip = ((AAAARecord) record).getAddress().getHostAddress();
                        }
                        stmt1.setString(1, owner);
                        stmt1.setString(2, ip);
                        stmt1.addBatch();

                        if (count++ > limit) {
                            send = true;
                        }
                    }

                    // --- Update nameserver status ---
                    stmt2.setString(1, "S");
                    stmt2.setString(2, owner);
                    stmt2.addBatch();

                    // --- Execute batch and commit progress ---
                    if (send) {
                        stmt1.executeBatch();
                        stmt2.executeBatch();
                        con1.commit();
                        con2.commit();
                        count = 0;
                    }
                }

                // --- Stores and commit remaining data ---
                stmt1.executeBatch();
                stmt2.executeBatch();
                con1.commit();
                con2.commit();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Unexpected error updating the database", t);
                Context.poisonPill();
            }
        };
    }
}
