package forge.gamemodes.limited;

import forge.card.ColorSet;
import forge.item.PaperCard;

import java.util.List;

public final class DraftPickContext {
    private final int playerIndex;
    private final int pickNumber;
    private final List<PaperCard> pack;
    private final List<PaperCard> pool;
    private final ColorSet chosenColors;
    private final boolean canAddMoreColors;

    public DraftPickContext(
            final int playerIndex,
            final int pickNumber,
            final List<PaperCard> pack,
            final List<PaperCard> pool,
            final ColorSet chosenColors,
            final boolean canAddMoreColors
    ) {
        this.playerIndex = playerIndex;
        this.pickNumber = pickNumber;
        this.pack = List.copyOf(pack);
        this.pool = List.copyOf(pool);
        this.chosenColors = chosenColors;
        this.canAddMoreColors = canAddMoreColors;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public int getPickNumber() {
        return pickNumber;
    }

    public List<PaperCard> getPack() {
        return pack;
    }

    public List<PaperCard> getPool() {
        return pool;
    }

    public ColorSet getChosenColors() {
        return chosenColors;
    }

    public boolean canAddMoreColors() {
        return canAddMoreColors;
    }
}
