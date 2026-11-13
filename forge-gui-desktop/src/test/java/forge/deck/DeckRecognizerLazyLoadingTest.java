package forge.deck;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckRecognizer.TokenType;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

public class DeckRecognizerLazyLoadingTest {
    @Test
    public void importsExactPrintingBeforeCardIsLoaded() throws IOException {
        ImageKeys.initializeDirs("", Collections.emptyMap(), "", "", "", "", "", "", "");
        Path resources = Path.of("../forge-gui/res").toAbsolutePath().normalize();
        CardStorageReader reader = new CardStorageReader(resources.resolve("cardsfolder").toString(), null, true);
        CardStorageReader tokenReader = new CardStorageReader(resources.resolve("tokenscripts").toString(), null, false);
        Path customEditions = Files.createTempDirectory("forge-empty-editions");
        StaticData data;
        try {
            data = new StaticData(reader, tokenReader, null, null, resources.resolve("editions").toString(),
                    customEditions.toString(), resources.resolve("blockdata").toString(), "",
                    "Latest Art All Editions", true, false, false, false);
        } finally {
            Files.delete(customEditions);
        }
        assertFalse(data.getCommonCards().getAllCards().stream()
                .anyMatch(card -> card.getName().equals("Hearthborn Battler")));

        Token token = new DeckRecognizer().recogniseCardToken("4 Hearthborn Battler (BLB) 139", null);

        assertEquals(token.getType(), TokenType.LEGAL_CARD);
        assertEquals(token.getQuantity(), 4);
        assertEquals(token.getCard().getName(), "Hearthborn Battler");
        assertEquals(token.getCard().getEdition(), "BLB");
        assertEquals(token.getCard().getCollectorNumber(), "139");
        assertNotNull(StaticData.instance().getAllTokens().getToken("w_1_1_warrior", "ZZQ"));
    }
}
