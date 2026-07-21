package forge.ai;

/** Per-controller research arm. Candidate behavior is supplied by the pinned artifact. */
public enum AiVariant {
    BASELINE("baseline"),
    CANDIDATE("candidate");

    private final String externalName;

    AiVariant(final String externalName) {
        this.externalName = externalName;
    }

    public static AiVariant fromExternalName(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return BASELINE;
        }
        final String normalized = value.trim();
        for (AiVariant variant : values()) {
            if (variant.externalName.equalsIgnoreCase(normalized)) {
                return variant;
            }
        }
        throw new IllegalArgumentException("Unknown AI variant: " + value);
    }

    public String getExternalName() {
        return externalName;
    }
}
