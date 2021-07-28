package org.icann.eai.survey;

public enum Step {
    STARTED(1),
    SEARCH_MX(2),
    RESOLVE_IPV4_MX(3),
    RESOLVE_IPV6_MX(4),
    PROBE_MX(5),
    RESOLVE_GEOIP(6),
    DONE(7);

    private final int id;

    public static Step findByPrimaryKey(int id) {
        for (Step s : values()) {
            if (s.id == id) {
                return s;
            }
        }
        throw new IllegalArgumentException("Step not found: " + id);
    }

    Step(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
