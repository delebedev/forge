package forge.card;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import forge.StaticData;
import forge.gamesimulationtests.util.CardDatabaseHelper;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Every test here loads a card through the lazy database, verifies it is present, then checks the
 * selected printing.
 */
public class CardDbLazyCardLoadingCardMockTestCase extends CardMockTestCase {

    protected CardDb cardDb;

    @Override
    protected void initializeStaticData() {
        // One lazily loaded database of this class's own, indexed once and emptied again before
        // each test, since the checks above need it to start with nothing loaded.
        StaticData data = CardDatabaseHelper.createStaticData("CardDbLazyCardLoadingCardMockTestCase", true);
        data.resetLazyLoadedCards();
        fModelMock.when(FModel::getMagicDb).thenReturn(data);
        this.cardDb = data.getCommonCards();
    }

    @Test
    public void testLoadAndGetBorrowing100_000ArrowsCardFromAllEditions() {
        String cardName = "Borrowing 100,000 Arrows";
        String[] allAvailableEds = new String[] { "PTK", "ME3", "C13", "CMA", "A25", "PLST" };

        assertEquals(this.cardDb.getCardArtPreference(), CardDb.CardArtPreference.LATEST_ART_ALL_EDITIONS);

        // Load the Card (just card name
        FModel.getMagicDb().attemptToLoadCard(cardName);

        assertTrue(this.cardDb.contains(cardName));
        PaperCard borrowingCard = this.cardDb.getCard(cardName);
        assertNotNull(borrowingCard);
        assertEquals(borrowingCard.getName(), cardName);
        assertEquals(borrowingCard.getEdition(), "PLST");

        // Now get card from all the specified editions
        for (String setCode : allAvailableEds) {
            borrowingCard = this.cardDb.getCard(cardName, setCode);
            assertNotNull(borrowingCard);
            assertEquals(borrowingCard.getName(), cardName);
            assertEquals(borrowingCard.getEdition(), setCode);
        }
    }

    @Test
    public void testLoadAndGetAinokBondKinFromKTKWithCaseInsensitiveCardName() {
        String cardName = "aiNOk Bond-kin"; // wrong case
        String expectedCardName = "Ainok Bond-Kin";
        String setCode = "KTK";

        assertEquals(this.cardDb.getCardArtPreference(), CardDb.CardArtPreference.LATEST_ART_ALL_EDITIONS);

        // Load the Card (just card name
        FModel.getMagicDb().attemptToLoadCard(cardName, setCode);

        assertTrue(this.cardDb.contains(cardName));
        PaperCard borrowingCard = this.cardDb.getCard(cardName);
        assertNotNull(borrowingCard);
        assertEquals(borrowingCard.getName(), expectedCardName);
        assertEquals(borrowingCard.getEdition(), setCode);

        // The card is now in the DB so we can update this test
        CardEdition ima = FModel.getMagicDb().getCardEdition("IMA");
        assertNotNull(ima);
        assertNull(this.cardDb.getCardFromSet(expectedCardName, ima, false));
        // And the lenient call falls back to the one printing that was loaded.
        assertEquals(this.cardDb.getCard(cardName, "IMA").getEdition(), setCode);
    }

    @Test
    public void tesLoadAndGetAetherVialWithWrongCase() {
        String cardName = "AEther vial"; // wrong case
        String expectedCardName = "Aether Vial";
        // Load the Card (just card name
        FModel.getMagicDb().attemptToLoadCard(cardName);

        assertTrue(this.cardDb.contains(cardName));
        PaperCard aetherVialCard = this.cardDb.getCard(cardName);
        assertNotNull(aetherVialCard);
        assertEquals(aetherVialCard.getName(), expectedCardName);
    }

    @Test
    public void tesLoadAndGetUnsupportedCardHavingWrongSetCode() {
        String cardName = "Dominating Licid";
        String wrongSetCode = "AA";
        String expectedSetCode = "EXO"; // Exodus
        CardRarity expectedCardRarity = CardRarity.Rare;

        // Load the Card (just card name
        FModel.getMagicDb().attemptToLoadCard(cardName, wrongSetCode);

        assertTrue(this.cardDb.contains(cardName));
        PaperCard dominatingLycidCard = this.cardDb.getCard(cardName);
        assertNotNull(dominatingLycidCard);
        assertEquals(dominatingLycidCard.getName(), cardName);
        assertEquals(dominatingLycidCard.getEdition(), expectedSetCode);
        assertEquals(dominatingLycidCard.getRarity(), expectedCardRarity);
    }
}
