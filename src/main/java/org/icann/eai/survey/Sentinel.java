package org.icann.eai.survey;

import java.util.logging.Logger;

/**
 * Helping class that prints an approximation QpS on the console. This class is used for debugging.
 */
public class Sentinel extends Thread {
    private static final Logger logger = Logger.getLogger(Sentinel.class.getCanonicalName());

    private final float threshold;
    private final long period;
    private final long min;
    private long count;
    private long time;
    private long errors;
    private boolean alive;

    public Sentinel() {
        super.setDaemon(true);

        Configuration config = Context.getInstance().getConfig();
        this.threshold = config.getSentinelThreshold();
        this.period = config.getSentinelPeriod().toMillis();
        this.min = config.getSentinelMin();

        this.count = 0;
        this.time = 0;
        this.errors = 0;
        this.alive = true;
    }

    public synchronized void reportError() {
        errors++;
        count++;
    }

    public synchronized void reportSuccess(long t) {
        time += t;
        count++;
    }

    public synchronized void turnoff() {
        alive = false;
    }

    @Override
    public void run() {
        while (alive) {
            long now = System.currentTimeMillis();

            // --- Check values ---
            synchronized (this) {
                if (count < min) {
                    logger.info("[Sentinel] Too few elements: " + count);
                } else {
                    float avg = time * 1.0f / count;
                    float rate = errors * 1.0f / count;
                    String msg = String.format("[Sentinel] qps: %,d, avg: %1.2f, error rate: %1.2f", count, avg, rate);
                    logger.info(msg);

                    if (rate > threshold) {
                        logger.severe("Error rate bigger than threshold: " + rate);
                        Context.poisonPill();
                    }
                }

                // --- Reset values ---
                count = 0;
                time = 0;
                errors = 0;
            }

            // --- Sleep until next period ---
            long sleep = period - (System.currentTimeMillis() - now);
            if (sleep < 0) {
                continue;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
