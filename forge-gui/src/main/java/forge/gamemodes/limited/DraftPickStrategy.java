package forge.gamemodes.limited;

import forge.item.PaperCard;

public interface DraftPickStrategy {
    /**
     * Chooses one card from the current pack.
     *
     * Implementations must return a non-null card from {@link DraftPickContext#getPack()}.
     */
    PaperCard choose(DraftPickContext context);
}
