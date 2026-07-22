package forge.view;

import forge.game.player.RegisteredPlayer;

/** Calibration-only controls for exercising the external research harness. */
public enum ResearchControl {
    NONE(""),
    SHAM("sham"),
    START_AT_ZERO("start-at-zero"),
    FAIL("fail");

    private final String externalName;

    ResearchControl(final String externalName) {
        this.externalName = externalName;
    }

    public static ResearchControl fromExternalName(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        final String normalized = value.trim();
        for (ResearchControl control : values()) {
            if (control.externalName.equalsIgnoreCase(normalized)) {
                return control;
            }
        }
        throw new IllegalArgumentException("Unknown research control: " + value);
    }

    public void applyTo(final RegisteredPlayer player) {
        switch (this) {
            case START_AT_ZERO:
                player.setStartingLife(0);
                break;
            case NONE:
            case SHAM:
            case FAIL:
                break;
            default:
                throw new IllegalStateException("Unhandled research control: " + this);
        }
    }

    public boolean isForcedFailure() {
        return this == FAIL;
    }
}
