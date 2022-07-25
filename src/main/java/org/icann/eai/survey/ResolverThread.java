package org.icann.eai.survey;

import org.xbill.DNS.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread that makes DNS queries to the DNS resolver.
 */
public class ResolverThread extends Thread {
    private static final Logger logger = Logger.getLogger(ResolverThread.class.getCanonicalName());

    private final InetSocketAddress address;
    private final int type;
    private final SynchronousQueue<String> inQueue;
    private final SynchronousQueue<Message> outQueue;
    private final Sentinel sentinel;
    private final InetAddress local;
    private final long duration;
    private final Duration timeout;
    private final int retries;
    private final long wait;

    public ResolverThread(InetSocketAddress address, int type, SynchronousQueue<String> inQueue, SynchronousQueue<Message> outQueue, Sentinel sentinel, InetAddress local) {
        Configuration config = Context.getInstance().getConfig();
        super.setDaemon(true);
        this.address = address;
        this.type = type;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
        this.sentinel = sentinel;
        this.local = local;
        this.duration = config.getResolverDuration().toMillis();
        this.timeout = config.getResolverTimeout();
        this.retries = config.getResolverRetries();
        this.wait = config.getResolverWait().toMillis();
    }

    @Override
    public void run() {
        // --- Resolve the nameserver ---
        SimpleResolver simple = new SimpleResolver(address);
        if (local != null) {
            simple.setLocalAddress(local);
        }
        ExtendedResolver resolver = new ExtendedResolver(new Resolver[]{simple});
        resolver.setTimeout(timeout);
        resolver.setRetries(retries);

        while (super.isAlive()) {
            String owner = null;
            long sleep = duration;
            try {
                // --- Takes a element from the queue ---
                owner = inQueue.poll(wait, TimeUnit.MILLISECONDS);
                if (owner == null || owner.equals(Constants.POISON_PILL)) {
                    break;
                }

                // --- Makes the request ---
                long now = System.currentTimeMillis();
                Name name = Name.fromString(owner);
                Record query = Record.newRecord(name, type, DClass.IN);
                Message message = resolver.send(Message.newQuery(query));
                outQueue.put(message);

                // --- Report to the sentinel ---
                long time = System.currentTimeMillis() - now;
                sentinel.reportSuccess(time);

                sleep = duration - time;
            } catch (Throwable t) {
                sentinel.reportError();
                logger.log(Level.WARNING, "Error resolving owner: " + owner, t);
            }

            // --- Throttle queries ---
            if (sleep < 0) {
                continue;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "Thread interrupted", e);
            }
        }
    }
}
