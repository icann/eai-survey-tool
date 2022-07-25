package org.icann.eai.survey;

/**
 * SMTP tester results.
 */
public class SmtpTesterResult {
    private final String server;
    private final char status;
    private final String header;
    private final Boolean ehlo;
    private final String ehloResult;
    private final Boolean ascii;
    private final String asciiResult;
    private final Boolean idn;
    private final String idnResult;

    public SmtpTesterResult(String server, char status) {
        this(server, status, null, null,  null, null, null, null, null);
    }

    public SmtpTesterResult(String server, char status, String header, Boolean ehlo, String ehloResult, Boolean ascii, String asciiResult, Boolean idn, String idnResult) {
        this.server = server;
        this.status = status;
        this.header = header;
        this.ehlo = ehlo;
        this.ehloResult = ehloResult;
        this.ascii = ascii;
        this.asciiResult = asciiResult;
        this.idn = idn;
        this.idnResult = idnResult;
    }

    public SmtpTesterResult() {
        this.server = null;
        this.status = 'N';
        this.header = null;
        this.ehlo = null;
        this.ehloResult = null;
        this.ascii = null;
        this.asciiResult = null;
        this.idn = null;
        this.idnResult = null;
    }

    // --- Getters ---
    public String getServer() {
        return server;
    }

    public char getStatus() {
        return status;
    }

    public String getHeader() {
        return header;
    }

    public String getEhlo() {
        return getBooleanString(ehlo);
    }

    public String getAscii() {
        return getBooleanString(ascii);
    }

    public String getIdn() {
        return getBooleanString(idn);
    }

    public String getEhloResult() {
        return ehloResult;
    }

    public String getAsciiResult() {
        return asciiResult;
    }

    public String getIdnResult() {
        return idnResult;
    }

    private String getBooleanString(Boolean value) {
        if (value == null) {
            return null;
        }
        if (value) return "Y";
        return "N";
    }
}
