package forge.player;

/**
 * Result of an interactive target selection operation.
 * Used by {@link PlayerControllerHuman#selectTargetsInteractively} to communicate
 * user choices back to {@link TargetSelection}.
 *
 * @see TargetSelection#chooseTargets
 */
public class TargetSelectionResult {
    private final boolean chosen;
    private final boolean done;

    public TargetSelectionResult(boolean chosen, boolean done) {
        this.chosen = chosen;
        this.done = done;
    }

    /** True if the user made a selection (did not cancel). */
    public boolean isChosen() {
        return chosen;
    }

    /** True if the user pressed OK / finished targeting (no more targets to pick). */
    public boolean isDone() {
        return done;
    }
}
