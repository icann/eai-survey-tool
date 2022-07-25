package org.icann.eai.survey;

import inet.ipaddr.IPAddressString;
import org.apache.commons.dbcp2.BasicDataSourceFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Properties;

/**
 * Application configuration.
 */
public class Configuration {
    private final InetAddress[] localAddressesIpv4;
    private final InetAddress[] localAddressesIpv6;
    private final File zoneDir;
    private final String zoneFilenameSuffix;
    private final Properties databaseConfig;
    private final int databaseBatchSize;
    private final int databaseStatementFetchSize;
    private final File maxmindDatabaseFile;
    private final IPAddressString[] excludeIps;
    private final float sentinelThreshold;
    private final Duration sentinelPeriod;
    private final long sentinelMin;
    private final InetSocketAddress[] resolverAddresses;
    private final int resolverThreads;
    private final Duration resolverDuration;
    private final Duration resolverTimeout;
    private final int resolverRetries;
    private final Duration resolverWait;
    private final int smtpTesterThreads;
    private final Duration smtpTesterThreadsWait;
    private final Duration smtpTesterTimeoutConnect;
    private final Duration smtpTesterTimeoutIdle;
    private final int smtpTesterRetries;
    private final Duration smtpTesterWait;
    private final String smtpTesterDomain;
    private final String smtpTesterEmailAscii;
    private final String smtpTesterEmailIdn;
    private final Duration smtpTesterQuarantinePeriod;
    private final Duration smtpTesterQuarantineTimeout;
    private final Duration smtpTesterQuarantineExpire;
    private final int smtpTesterQuarantineCidrIpv4;
    private final int smtpTesterQuarantineCidrIpv6;

    /**
     * Constructor.
     *
     * @param p Configuration.
     */
    public Configuration(Properties p) {
        this.localAddressesIpv4 = parseLocalAddresses(p, "local.addresses.ipv4");
        this.localAddressesIpv6 = parseLocalAddresses(p, "local.addresses.ipv6");
        this.zoneDir = null; // getDir(p, "zone.dir");
        this.zoneFilenameSuffix = getString(p, "zone.file.suffix");
        this.databaseConfig = getDatabaseConfig(p);
        this.databaseBatchSize = getInt(p, "db.batch.size");
        this.databaseStatementFetchSize = getInt(p, "db.statement.fetch_size");
        this.maxmindDatabaseFile = new File(getString(p, "maxmind.database.filename"));
        this.excludeIps = parseExcludeIps(p, "ip.exclude");
        this.sentinelThreshold = Float.parseFloat(getString(p, "sentinel.threshold"));
        this.sentinelPeriod = getDuration(p, "sentinel.period");
        this.sentinelMin = Long.parseLong(getString(p, "sentinel.min"));
        this.resolverAddresses = getInetSocketAddresses(p, "resolver.address");
        this.resolverThreads = getInt(p, "resolver.threads");
        this.resolverDuration = getDuration(p, "resolver.duration");
        this.resolverTimeout = getDuration(p, "resolver.timeout");
        this.resolverRetries = getInt(p, "resolver.retries");
        this.resolverWait = getDuration(p, "resolver.wait");
        this.smtpTesterThreads = getInt(p, "smtp.tester.threads");
        this.smtpTesterThreadsWait = getDuration(p, "smtp.tester.threads.wait");
        this.smtpTesterTimeoutConnect = getDuration(p, "smtp.tester.timeout.connect");
        this.smtpTesterTimeoutIdle = getDuration(p, "smtp.tester.timeout.idle");
        this.smtpTesterRetries = getInt(p, "smtp.tester.retries");
        this.smtpTesterWait = getDuration(p, "smtp.tester.wait");
        this.smtpTesterDomain = getString(p, "smtp.tester.domain");
        this.smtpTesterEmailAscii = getString(p, "smtp.tester.email.ascii");
        this.smtpTesterEmailIdn = getString(p, "smtp.tester.email.idn");
        this.smtpTesterQuarantinePeriod = getDuration(p, "smtp.tester.quarantine.period");
        this.smtpTesterQuarantineTimeout = getDuration(p, "smtp.tester.quarantine.timeout");
        this.smtpTesterQuarantineExpire = getDuration(p, "smtp.tester.quarantine.expire");
        this.smtpTesterQuarantineCidrIpv4 = getInt(p, "smtp.tester.quarantine.cidr.ipv4");
        this.smtpTesterQuarantineCidrIpv6 = getInt(p, "smtp.tester.quarantine.cidr.ipv6");

        if (localAddressesIpv4.length != localAddressesIpv6.length ||
                (localAddressesIpv4[0] == null && localAddressesIpv6[0] != null) ||
                (localAddressesIpv4[0] != null && localAddressesIpv6[0] == null)) {
            throw new IllegalArgumentException("Local IPv4 and IPv6 addresses must be on pairs");
        }

        if (!maxmindDatabaseFile.exists() || !maxmindDatabaseFile.isFile()) {
            throw new RuntimeException("MaxMind database not found", new FileNotFoundException(maxmindDatabaseFile.getAbsolutePath()));
        }
    }

    private String getString(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing argument: " + key);
        }
        return value;
    }

    private InetAddress[] parseLocalAddresses(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            return new InetAddress[]{null};
        }
        String[] tokens = value.split("\\s*,\\s*");
        InetAddress[] list = new InetAddress[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                list[i] = InetAddress.getByName(tokens[i]);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Illegal address: " + tokens[i]);
            }
        }
        return list;
    }

    private IPAddressString[] parseExcludeIps(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            return null;
        }
        String[] tokens = value.split("\\s*,\\s*");
        IPAddressString[] list = new IPAddressString[tokens.length];
        for (int i = 0; i < list.length; i++) list[i] = new IPAddressString(tokens[i]);
        return list;
    }

    private int getInt(Properties p, String key) {
        return Integer.parseInt(getString(p, key));
    }

    private InetSocketAddress[] getInetSocketAddresses(Properties p, String key) {
        String[] values = getString(p, key).split("\\s*,\\s*");
        InetSocketAddress[] list = new InetSocketAddress[values.length];
        for (int n = 0; n < values.length; n++) {
            String value = values[n];
            int i = value.indexOf('#');
            if (i == -1) {
                list[n] = new InetSocketAddress(value, 53);
            } else {
                int port = Integer.parseInt(value.substring(i + 1));
                value = value.substring(0, i);
                list[n] = new InetSocketAddress(value, port);
            }
        }
        return list;
    }

    private Duration getDuration(Properties p, String key) {
        return Duration.parse(getString(p, key));
    }

    private Properties getDatabaseConfig(Properties p) {
        Properties config = new Properties();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("db.")) {
                config.setProperty(key.substring(3), p.getProperty(key));
            }
        }
        return config;
    }

    /**
     * Creates a FileFilter for the zone files.
     *
     * @return An FileFilter for the zone files.
     */
    public FileFilter getZoneFileFilter() {
        return (f) -> {
            if (!f.isFile()) return false;
            String filename = f.getName().toLowerCase();
            return filename.endsWith(zoneFilenameSuffix);
            //return filename.substring(0, filename.length() - 3).endsWith(zoneFilenameSuffix);
        };
    }

    /**
     * Creates a DataSource object from the configuration.
     *
     * @return A DataSource to the database.
     * @throws Exception When an error occurs when creating the DataSource.
     */
    public DataSource getDataSource() throws Exception {
        return BasicDataSourceFactory.createDataSource(databaseConfig);
    }

    // --- Getters ---
    public InetAddress[] getLocalAddressesIpv4() {
        return localAddressesIpv4;
    }

    public InetAddress[] getLocalAddressesIpv6() {
        return localAddressesIpv6;
    }

    public File getZoneDir() {
        return zoneDir;
    }

    public String getZoneFilenameSuffix() {
        return zoneFilenameSuffix;
    }

    public int getDatabaseBatchSize() {
        return databaseBatchSize;
    }

    public int getDatabaseStatementFetchSize() {
        return databaseStatementFetchSize;
    }

    public File getMaxmindDatabaseFile() {
        return maxmindDatabaseFile;
    }

    public boolean excludeIp(String value) {
        if (this.excludeIps == null) return false;

        IPAddressString ip = new IPAddressString(value);
        for (IPAddressString block : this.excludeIps) {
            if (block.contains(ip)) return true;
        }
        return false;
    }

    public float getSentinelThreshold() {
        return sentinelThreshold;
    }

    public Duration getSentinelPeriod() {
        return sentinelPeriod;
    }

    public long getSentinelMin() {
        return sentinelMin;
    }

    public InetSocketAddress[] getResolverAddresses() {
        return resolverAddresses;
    }

    public int getResolverThreads() {
        return resolverThreads;
    }

    public Duration getResolverDuration() {
        return resolverDuration;
    }

    public Duration getResolverTimeout() {
        return resolverTimeout;
    }

    public int getResolverRetries() {
        return resolverRetries;
    }

    public Duration getResolverWait() {
        return resolverWait;
    }

    public int getSmtpTesterThreads() {
        return smtpTesterThreads;
    }

    public Duration getSmtpTesterThreadsWait() {
        return smtpTesterThreadsWait;
    }

    public Duration getSmtpTesterTimeoutConnect() {
        return smtpTesterTimeoutConnect;
    }

    public Duration getSmtpTesterTimeoutIdle() {
        return smtpTesterTimeoutIdle;
    }

    public int getSmtpTesterRetries() {
        return smtpTesterRetries;
    }

    public Duration getSmtpTesterWait() {
        return smtpTesterWait;
    }

    public String getSmtpTesterDomain() {
        return smtpTesterDomain;
    }

    public String getSmtpTesterEmailAscii() {
        return smtpTesterEmailAscii;
    }

    public String getSmtpTesterEmailIdn() {
        return smtpTesterEmailIdn;
    }

    public Duration getSmtpTesterQuarantinePeriod() {
        return smtpTesterQuarantinePeriod;
    }

    public Duration getSmtpTesterQuarantineTimeout() {
        return smtpTesterQuarantineTimeout;
    }

    public Duration getSmtpTesterQuarantineExpire() {
        return smtpTesterQuarantineExpire;
    }

    public int getSmtpTesterQuarantineCidrIpv4() {
        return smtpTesterQuarantineCidrIpv4;
    }

    public int getSmtpTesterQuarantineCidrIpv6() {
        return smtpTesterQuarantineCidrIpv6;
    }
}
