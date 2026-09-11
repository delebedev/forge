package forge;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.util.collect.FCollection;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class FCollectionTest {
    private static final class DirectionalEquality {
        private final String value;
        private final boolean acceptsEqualValue;

        private DirectionalEquality(final String value, final boolean acceptsEqualValue) {
            this.value = value;
            this.acceptsEqualValue = acceptsEqualValue;
        }

        @Override
        public boolean equals(final Object obj) {
            return this == obj || acceptsEqualValue
                    && obj instanceof DirectionalEquality
                    && value.equals(((DirectionalEquality) obj).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    @Test
    void testGetUsesStoredEqualityAndReturnsStoredInstance() {
        final DirectionalEquality stored = new DirectionalEquality("match", true);
        final DirectionalEquality probe = new DirectionalEquality("match", false);
        final DirectionalEquality absent = new DirectionalEquality("absent", false);
        final FCollection<DirectionalEquality> collection = new FCollection<>(stored);

        assertSame(collection.get(probe), stored);
        assertSame(collection.get(absent), absent);
        assertNull(collection.get(null));
    }

    @Test
    void testAnyMatchPreservesEncounterOrderAndShortCircuits() {
        final FCollection<Integer> collection = new FCollection<>(List.of(1, 2, 3));
        final List<Integer> visited = new ArrayList<>();

        assertTrue(collection.anyMatch(value -> {
            visited.add(value);
            return value == 2;
        }));
        assertEquals(visited, List.of(1, 2));
    }

    @Test
    void testAnyMatchChecksEveryElementWhenAbsent() {
        final FCollection<Integer> collection = new FCollection<>(List.of(1, 2, 3));
        final List<Integer> visited = new ArrayList<>();

        assertFalse(collection.anyMatch(value -> {
            visited.add(value);
            return false;
        }));
        assertEquals(visited, List.of(1, 2, 3));
    }

    @Test
    void testAnyMatchNullPredicateBehavior() {
        expectThrows(NullPointerException.class, () -> new FCollection<Integer>().anyMatch(null));
        assertFalse(FCollection.<Integer>getEmpty().anyMatch(null));
    }

    /**
     * Just a quick test for FCollection.
     */
    /*@Test
    void testBadIteratorLogic() {
        List<Card> cards = new ArrayList<>();
        for (int i = 1; i < 5; i++)
            cards.add(new Card(i, null));
        CardCollection cc = new CardCollection(cards);
        Iterator<Card> it = cc.iterator();
        it.next();
        it.remove();
        assertEquals(cc.size(), 3);
    }

    /*@Test
    void testBadIteratorLogicTwo() {
        List<Card> cards = new ArrayList<>();
        for (int i = 1; i <= 10; i++)
            cards.add(new Card(i, null));
        CardCollection cc = new CardCollection(cards);
        int i = 0;
        for (Card c : cc) {
            if (i != 3)
                cc.remove(c);  // throws error if the CardCollection not threadsafe
            i++;
        }
        assertEquals(cc.size(), 1);
    }*/// Commented out since the collection doesn't support modification while iterating over it directly

    /**
     * {@link forge.util.collect.FCollection#threadSafeIterable()} hands out a snapshot, so removing
     * from the collection while looping over it neither throws nor skips an element.
     */
    @Test
    void testRemoveWhileIterating() {
        List<Card> cards = new ArrayList<>();
        for (int i = 1; i < 5; i++)
            cards.add(new Card(i, null));
        CardCollection cc = new CardCollection(cards);
        int seen = 0;
        for (Card c : cc.threadSafeIterable()) {
            seen++;
            if (c.getId() % 2 > 0)
                cc.remove(c);
        }
        assertEquals(seen, 4);
        assertEquals(cc.size(), 2);
        for (Card c : cc)
            assertEquals(c.getId() % 2, 0);
    }
}
