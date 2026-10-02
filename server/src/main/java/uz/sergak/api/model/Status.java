package uz.sergak.api.model;

/** Xavf darajasi. Tartib muhim: oxirgisi eng og'ir. */
public enum Status {
    UNKNOWN, CLEAN, SUSPICIOUS, MALICIOUS;

    public boolean worseThan(Status other) {
        return this.ordinal() > other.ordinal();
    }
}
