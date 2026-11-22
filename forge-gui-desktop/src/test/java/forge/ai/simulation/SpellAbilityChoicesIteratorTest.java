package forge.ai.simulation;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class SpellAbilityChoicesIteratorTest {
    @Test
    public void combinationsKeepForgeModeOrder() {
        Iterator<int[]> combinations = SpellAbilityChoicesIterator.combinationsIterator(4, 2);
        List<List<Integer>> result = new ArrayList<>();
        combinations.forEachRemaining(values -> result.add(List.of(values[0], values[1])));

        Assert.assertEquals(result, List.of(
                List.of(0, 1),
                List.of(0, 2),
                List.of(1, 2),
                List.of(0, 3),
                List.of(1, 3),
                List.of(2, 3)));
        Assert.expectThrows(NoSuchElementException.class, combinations::next);
    }

    @Test
    public void combinationsHandleBounds() {
        Assert.assertEquals(SpellAbilityChoicesIterator.combinationsIterator(0, 0).next(), new int[0]);
        Assert.assertEquals(SpellAbilityChoicesIterator.combinationsIterator(3, 3).next(), new int[]{0, 1, 2});
        Assert.expectThrows(IllegalArgumentException.class,
                () -> SpellAbilityChoicesIterator.combinationsIterator(2, 3));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> SpellAbilityChoicesIterator.combinationsIterator(-1, 0));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> SpellAbilityChoicesIterator.combinationsIterator(1, -1));
    }
}
