package org.icann.eai.survey;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

@Deprecated
public class IpTest {
    private static final BigInteger TWO_COMPLEMENT_REF = BigInteger.ONE.shiftLeft(64);
    private static final SpecialIpBlock SPECIAL = new SpecialIpBlock();
    private static final int BASE_CIDR_4 = 24;
    private static final int BASE_CIDR_6 = 64;

    public static void main(String[] args) throws Exception {
        check("/dev/shm/ipv4.txt", 4);
    }

    private static void check(String filename, int version) throws Exception {
        ClassLoader cl = SpecialIpBlock.class.getClassLoader();
        Set<BigInteger> list = new HashSet<>();
        int cidr = (version == 4) ? 32 - BASE_CIDR_4 : 128 - BASE_CIDR_6;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                InetAddress ip = InetAddress.getByName(line);
                BigInteger i = new BigInteger(ip.getAddress());
                if (i.compareTo(BigInteger.ZERO) < 0) i = i.add(TWO_COMPLEMENT_REF);
                list.add(i.shiftRight(cidr));
            }
        }

        System.out.println("n. " + list.size());
        for (int i = 0; i < 8; i++) {
            Set<BigInteger> set = new HashSet<>();
            for(BigInteger n: list) {
                set.add(n.shiftRight(1));
            }
            System.out.println(i + ". " + set.size());
            list = set;
        }
    }
}
