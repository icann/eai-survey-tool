package org.icann.eai.survey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Objects;

/**
 * SQL Provider, this utility class read sql sentences from text files.
 */
public class SqlProvider {
    private final HashMap<Class<?>, HashMap<String, String>> cache;

    /**
     * Constructor.
     */
    public SqlProvider() {
        cache = new HashMap<>();
    }

    /**
     * Gets an SQL statement.
     *
     * @param clazz Class of which is desired the SQL statement.
     * @param key   Key of the  SQL statement.
     * @return A String with the SQL statement.
     */
    public synchronized String getSql(Class<?> clazz, String key) {
        HashMap<String, String> map = cache.get(clazz);
        if (map == null) {
            map = new HashMap<>();
            cache.put(clazz, map);
            String filename = "META-INF/sql/" + clazz.getSimpleName() + ".sql";
            ClassLoader cl = getClass().getClassLoader();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream(filename))))) {
                String id = null;
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("--")) {
                        if (id != null) {
                            map.put(id, sb.toString());
                        }
                        id = line.substring(2).trim();
                        sb.delete(0, sb.length());
                    } else {
                        sb.append(line).append('\n');
                    }
                }
                if (id != null) {
                    map.put(id, sb.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while loading sql statements", e);
            }
        }
        return map.get(key);

    }
}
