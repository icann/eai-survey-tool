package org.icann.eai.survey;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TesterThread extends Thread {
    private static final Logger logger = Logger.getLogger(TesterThread.class.getCanonicalName());

    private final SynchronousQueue<String> inQueue;
    private final SynchronousQueue<SmtpTesterResult> outQueue;
    private final QuarantineVault vault;
    private final Sentinel sentinel;
    private final InetAddress local4;
    private final InetAddress local6;
    private final long wait;

    public TesterThread(SynchronousQueue<String> inQueue, SynchronousQueue<SmtpTesterResult> outQueue, QuarantineVault vault, Sentinel sentinel, InetAddress local4, InetAddress local6) {
        super.setDaemon(true);
        this.inQueue = inQueue;
        this.outQueue = outQueue;
        this.sentinel = sentinel;
        this.vault = vault;
        this.local4 = local4;
        this.local6 = local6;

        Configuration config = Context.getInstance().getConfig();
        this.wait = config.getSmtpTesterThreadsWait().toMillis();
    }

    @Override
    public void run() {
        while (super.isAlive()) {
            String server = null;
            try {
                // --- Takes a element from the queue ---
                server = inQueue.poll(wait, TimeUnit.MILLISECONDS);
                if (server == null || server.equals(Constants.POISON_PILL)) {
                    break;
                }

                // --- Quarantine Vault: Lock ---
                while (!vault.lock(server)) {
                    logger.info("Waiting for IP: " + server);
                    Thread.sleep((long) (Math.random() * 1000));
                }

                // --- Test MX Server ---
                long now = System.currentTimeMillis();
                InetAddress local = InetAddress.getByName(server) instanceof Inet4Address ? local4 : local6;

                SmtpTester tester = new SmtpTester(server, local);
                SmtpTesterResult result = tester.test();
                outQueue.put(result);

                // --- Quarantine Value: Unlock --
                vault.unlock(server);

                // --- Report to the sentinel ---
                long time = System.currentTimeMillis() - now;
                sentinel.reportSuccess(time);
            } catch (Throwable t) {
                sentinel.reportError();
                logger.log(Level.WARNING, "Error resolving owner: " + server);
            }
        }
    }
}
