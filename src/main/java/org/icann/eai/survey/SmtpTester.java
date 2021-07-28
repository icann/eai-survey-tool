package org.icann.eai.survey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs the SMTP test on a server.
 */
public class SmtpTester {
    private static final Logger logger = Logger.getLogger(SmtpTester.class.getCanonicalName());

    private final String server;
    private final InetAddress local;
    private final long wait;
    private final long timeoutConnect;
    private final long timeoutIdle;
    private final int retries;
    private final String domain;
    private final String asciiEmail;
    private final String idnEmail;
    private String header;
    private Boolean ehlo;
    private String ehloResult;
    private Boolean ascii;
    private String asciiResult;
    private Boolean idn;
    private String idnResult;

    public SmtpTester(String server, InetAddress local) {
        Configuration config = Context.getInstance().getConfig();
        this.server = server;
        this.local = local;
        this.wait = config.getSmtpTesterWait().toMillis();
        this.retries = config.getSmtpTesterRetries();
        this.timeoutConnect = config.getSmtpTesterTimeoutConnect().toMillis();
        this.timeoutIdle = config.getSmtpTesterTimeoutIdle().toMillis();
        this.domain = config.getSmtpTesterDomain();
        this.asciiEmail = config.getSmtpTesterEmailAscii();
        this.idnEmail = config.getSmtpTesterEmailIdn();
    }

    public SmtpTesterResult test() {
        header = null;
        ehlo = null;
        ehloResult = null;
        ascii = null;
        asciiResult = null;
        idn = null;
        idnResult = null;

        // --- ASCII Test ---
        for (int i = 0; i < retries; i++) {
            try {
                doTest(asciiEmail, false);
                break;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error while SMTP Testing", e);
                doWait();
            }
        }
        if (ehlo == null || !ehlo) {
            return new SmtpTesterResult(server, header, ehlo, ehloResult, ascii, asciiResult, idn, idnResult);
        }

        // --- Wait ---
        doWait();

        // --- IDN Test ---
        for (int i = 0; i < retries; i++) {
            try {
                doTest(idnEmail, true);
                break;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error while SMTP Testing", e);
                doWait();
            }
        }

        return new SmtpTesterResult(server, header, ehlo, ehloResult, ascii, asciiResult, idn, idnResult);
    }


    private void doTest(String email, boolean utf8) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout((int) timeoutIdle);
            if (local != null) {
                socket.bind(new InetSocketAddress(local, 0));
            }

            InetSocketAddress address = new InetSocketAddress(server, 25);
            socket.connect(address, (int) timeoutConnect);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();


            header = trunc(read(in), 32768);
            write(out, "EHLO " + domain);
            String response = read(in);
            ehlo = response.contains("250 SMTPUTF8") || response.contains("250-SMTPUTF8");
            ehloResult = trunc(response, 4000);
            if (!ehlo) {
                close(in, out);
                return;
            }
            write(out, "MAIL FROM:<" + email + ">" + (utf8 ? " SMTPUTF8" : ""));
            response = read(in);
            close(in, out);
            if (utf8) {
                idn = response.startsWith("250");
                idnResult = trunc(response, 4000);
            } else {
                ascii = response.startsWith("250");
                asciiResult = trunc(response, 4000);
            }
        }
    }

    private void close(BufferedReader in, OutputStream out) throws IOException {
        write(out, "QUIT");
        read(in);
    }

    private String read(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line).append('\n');
            if (line.matches("^\\d+\\s.*$")) break;
        }
        String response = sb.toString().trim();
        logger.info("S: " + response);
        return response;
    }

    private String trunc(String value, int len) {
        if (value.length() < len) return value;
        return value.substring(0, len);
    }

    private void write(OutputStream out, String msg) throws IOException {
        logger.info("C: " + msg);
        byte[] data = (msg + "\r\n").getBytes(StandardCharsets.UTF_8);
        out.write(data);
        out.flush();
    }

    private void doWait() {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            // Ignore error.
        }
    }
}
