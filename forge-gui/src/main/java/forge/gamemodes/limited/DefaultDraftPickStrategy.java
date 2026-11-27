package forge.gamemodes.limited;

import forge.item.PaperCard;

import java.util.List;

public final class DefaultDraftPickStrategy implements DraftPickStrategy {
    @Override
    public PaperCard choose(final DraftPickContext context) {
        final List<PaperCard> rankedCards = CardRanker.rankCardsInPack(
                context.getPack(),
                context.getPool(),
                context.getChosenColors(),
                context.canAddMoreColors()
        );
        return rankedCards.get(0);
    }
}
