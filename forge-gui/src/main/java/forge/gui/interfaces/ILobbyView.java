package forge.gui.interfaces;

import forge.deck.Deck;
import forge.interfaces.IPlayerChangeListener;
import forge.interfaces.IUpdateable;
import forge.item.PaperCard;

import java.util.List;

public interface ILobbyView extends IUpdateable {
    void setPlayerChangeListener(IPlayerChangeListener iPlayerChangeListener);

    /** Implementations that participate in network draft/sealed return their handler; others return null. */
    default IDraftEventHandler getDraftHandler() { return null; }
}
