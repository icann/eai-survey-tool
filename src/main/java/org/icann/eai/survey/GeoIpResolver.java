package org.icann.eai.survey;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * GeoIP Resolver class, uses the MaxMind Database to get the country code from the IP addresses.
 */
public class GeoIpResolver {
    private final long limit;
    private final DatabaseReader reader;

    public GeoIpResolver() throws IOException {
        Configuration config = Context.getInstance().getConfig();
        limit = config.getDatabaseBatchSize();
        reader = new DatabaseReader.Builder(config.getMaxmindDatabaseFile())
                .withCache(new CHMCache())
                .build();
    }

    public void execute() throws Exception {
        DataSource database = Context.getInstance().getDatabase();
        SqlProvider provider = Context.getInstance().getSqlProvider();
        String sql1 = provider.getSql(getClass(), "get_all_ip");
        String sql2 = provider.getSql(getClass(), "set_country");
        try (Connection con1 = database.getConnection();
             Connection con2 = database.getConnection();
             Statement stmt1 = con1.createStatement();
             ResultSet rs = stmt1.executeQuery(sql1);
             PreparedStatement stmt2 = con2.prepareStatement(sql2)) {

            long count = 0;
            while (rs.next()) {
                String value = rs.getString("ip");
                if (SpecialIpBlock.isSpecial(value)) continue;

                try {
                    // https://maxmind.github.io/GeoIP2-java/
                    InetAddress ip = Inet4Address.getByName(value);
                    CountryResponse response = reader.country(ip);
                    Country country = response.getCountry();
                    String code = country.getIsoCode();
                    stmt2.setString(1, code);
                    stmt2.setString(2, value);
                    stmt2.addBatch();
                    if (count++ > limit) {
                        stmt2.executeBatch();
                        con2.commit();
                        count = 0;
                    }
                } catch (AddressNotFoundException e) {
                    // Error is ignored.
                }
            }
            stmt2.executeBatch();
            con2.commit();
        }
    }
}
