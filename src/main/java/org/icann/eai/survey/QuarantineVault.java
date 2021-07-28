package org.icann.eai.survey;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quarantine class that protects consulting the an IP on the same block concurrently.
 */
public class QuarantineVault extends Thread {
    private static final BigInteger TWO_COMPLEMENT_REF = BigInteger.ONE.shiftLeft(64);

    private final Map<BigInteger, Data> map;
    private long period;
    private long timeout;
    private long expire;
    private int cidr4;
    private int cidr6;

    public QuarantineVault() {
        super.setDaemon(true);
        this.map = new HashMap<>();
        this.period = 1000; // 1 sec.
        this.timeout = 1000; // 1 Sec.
        this.expire = 10_000; // 10 sec.
        this.cidr4 = 24;
        this.cidr6 = 56;
    }

    public synchronized boolean lock(String value) {
        BigInteger i = calculateBase(value);
        if (map.containsKey(i)) {
            return false;
        }
        map.put(i, new Data());
        return true;
    }

    public synchronized void unlock(String value) {
        BigInteger i = calculateBase(value);
        Data data = map.get(i);
        if (data == null) {
            return;
        }
        data.unlocked = System.currentTimeMillis();
    }

    private BigInteger calculateBase(String value) {
        try {
            InetAddress ip = InetAddress.getByName(value);
            int cidr = (ip instanceof Inet4Address) ? 32 - cidr4 : 128 - cidr6;
            BigInteger i = new BigInteger(ip.getAddress());
            if (i.compareTo(BigInteger.ZERO) < 0) i = i.add(TWO_COMPLEMENT_REF);
            return i.shiftRight(cidr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (super.isAlive()) {
            synchronized (this) {
                long now = System.currentTimeMillis();
                List<BigInteger> keys = new ArrayList<>(map.keySet());
                for (BigInteger key : keys) {
                    Data data = map.get(key);
                    if (data.unlocked != -1 && (now - data.unlocked) >= timeout) {
                        map.remove(key);
                    } else if (data.unlocked == -1 && (now - data.locked) >= expire) {
                        map.remove(key);
                    }
                }
            }
            try {
                Thread.sleep(period);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // --- Setters ---
    public void setPeriod(long period) {
        this.period = period;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public void setCidr4(int cidr4) {
        this.cidr4 = cidr4;
    }

    public void setCidr6(int cidr6) {
        this.cidr6 = cidr6;
    }

    private static final class Data {
        private final long locked;
        private long unlocked;

        private Data() {
            locked = System.currentTimeMillis();
            unlocked = -1;
        }
    }
}
