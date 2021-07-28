package org.icann.eai.survey;

import inet.ipaddr.IPAddressString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents special IP blocks that shouldn't be on the internet.
 */
public class SpecialIpBlock {
    private static final List<IPAddressString> IPV4;
    private static final List<IPAddressString> IPV6;

    static {
        IPV4 = readFile("META-INF/special_ipv4.txt");
        IPV6 = readFile("META-INF/special_ipv6.txt");
    }

    private static List<IPAddressString> readFile(String filename) {
        ClassLoader cl = SpecialIpBlock.class.getClassLoader();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(cl.getResourceAsStream(filename))))) {
            List<IPAddressString> list = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(new IPAddressString(line));
            }
            return list;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isSpecial(String value) {
        IPAddressString ip = new IPAddressString(value);
        List<IPAddressString> list;
        if (ip.isIPv4()) {
            list = IPV4;
        } else if (ip.isIPv6()) {
            list = IPV6;
        } else {
            throw new UnsupportedOperationException("Unsupported IP version");
        }
        for (IPAddressString block : list) {
            if (block.contains(ip)) return true;
        }
        return false;
    }
}
