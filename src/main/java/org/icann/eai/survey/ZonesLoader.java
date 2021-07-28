package org.icann.eai.survey;

import org.xbill.DNS.Name;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

@Deprecated
public class ZonesLoader {
    private final Logger logger = Logger.getLogger(ZonesLoader.class.getCanonicalName());

    public void execute() throws IOException, SQLException {
        Context context = Context.getInstance();
        Configuration config = context.getConfig();
        DataSource database = context.getDatabase();
        SqlProvider sqlProvider = context.getSqlProvider();

        // --- Zones ---
        File dir = config.getZoneDir();
        File[] files = dir.listFiles(config.getZoneFileFilter());
        if (files == null) {
            throw new IOException(dir.getAbsolutePath() + " is not a directory!");
        }

        // --- SQL String ---
        String sql = sqlProvider.getSql(getClass(), "insert_record");
        long count = 0;
        try (Connection con = database.getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {

            // --- Process each zone file ---
            for (File file : files) {
                String filename = file.getName();
                int len = filename.length() - config.getZoneFilenameSuffix().length() - 8;
                Name name = Name.fromString(filename.substring(0, len) + ".");
                logger.info("Loading zone " + name + " - " + file.getAbsolutePath());

                //Zone zone = new Zone(name, file.getAbsolutePath());
                //Iterator<RRset> iterator = zone.iterator();
                //while (iterator.hasNext()) {
                //RRset set = iterator.next();
                //if (set.getName().equals(name) || set.getType() != Type.NS) {
                //    continue;
                //}

                String owner;
                String zone = name.toString().toLowerCase();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    while ((owner = reader.readLine()) != null) {

                        // --- Insert: record ---
                        //String owner = set.getName().toString().toLowerCase();
                        stmt.setString(1, owner);
                        stmt.setString(2, zone);
                        stmt.addBatch();
                        if (count++ % config.getDatabaseBatchSize() == 0) {
                            stmt.executeBatch();
                            con.commit();
                        }

                        // --- Execute Batches ---
                        stmt.executeBatch();
                        con.commit();
                    }
                }
            }
        }
    }
}
