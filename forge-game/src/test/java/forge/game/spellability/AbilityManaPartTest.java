package forge.game.spellability;

import com.google.common.eventbus.Subscribe;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.MagicColor;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.event.GameEventManaAbilityActivated;
import forge.game.event.GameEventManaPool;
import forge.game.player.Player;
import forge.game.replacement.ReplacementHandler;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbilityManaPartTest {
    @BeforeClass
    public void initializeLocalizer() {
        Localizer.getInstance().initialize(
                "en-US",
                Path.of("..", "forge-gui", "res", "languages").toAbsolutePath().toString());
    }

    @Test
    public void productionPublishesOneSourceEventAfterTheWholePoolUpdate() {
        Fixture f = new Fixture();

        Assert.assertEquals(f.mana.produceMana("G G 2", f.player, f.ability), "G G 2");

        Assert.assertEquals(f.events.size(), 1);
        Assert.assertEquals(f.events.get(0).source().getId(), f.source.getId());
        Assert.assertEquals(f.events.get(0).produced(), "G G 2");
        Assert.assertEquals(f.poolSizes, List.of(4));
        Assert.assertEquals(f.chronology, List.of("pool", "production"));
        Assert.assertEquals(f.player.getManaPool().getAmountOfColor(MagicColor.GREEN), 2);
    }

    @Test
    public void convenienceOverloadPublishesOncePerProductionWithoutReplayingPriorMana() {
        Fixture f = new Fixture();

        f.mana.produceMana(f.ability);
        f.mana.produceMana(f.ability);

        Assert.assertEquals(f.events.size(), 2);
        Assert.assertEquals(f.events.stream().map(GameEventManaAbilityActivated::produced).toList(), List.of("G", "G"));
        Assert.assertEquals(f.poolSizes, List.of(1, 2));
        Assert.assertEquals(f.chronology, List.of("pool", "production", "pool", "production"));
    }

    @Test
    public void preventedProductionPublishesNeitherPoolNorSourceEvent() {
        Fixture f = new Fixture();
        f.source.addReplacementEffect(ReplacementHandler.parseReplacement(
                "Event$ ProduceMana | ValidSA$ SpellAbility | Prevent$ True | Layer$ CantHappen",
                f.source, true));

        Assert.assertEquals(f.mana.produceMana("G", f.player, f.ability), "");

        Assert.assertTrue(f.events.isEmpty());
        Assert.assertTrue(f.chronology.isEmpty());
        Assert.assertEquals(f.player.getManaPool().totalMana(), 0);
    }

    public static final class Fixture {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules, new Match(rules, List.of(), "Mana production"));
        final Player player = new Player("Player", game, 1);
        final Card source = new Card(1, new PaperCard(CardRules.fromScript(List.of(
                "Name:Mana Source", "ManaCost:no cost", "Types:Land", "Oracle:Add green mana.")),
                "TST", CardRarity.Common), game);
        final SpellAbility ability = new SpellAbility.EmptySa(source);
        final AbilityManaPart mana = new AbilityManaPart(source, Map.of("Produced", "G"));
        final List<GameEventManaAbilityActivated> events = new ArrayList<>();
        final List<Integer> poolSizes = new ArrayList<>();
        final List<String> chronology = new ArrayList<>();

        Fixture() {
            game.getPlayers().add(player);
            source.setOwner(player);
            source.setController(player, 0);
            player.getZone(ZoneType.Battlefield).add(source);
            ability.setActivatingPlayer(player);
            game.subscribeToEvents(this);
        }

        @Subscribe
        public void onProduction(GameEventManaAbilityActivated event) {
            events.add(event);
            poolSizes.add(player.getManaPool().totalMana());
            chronology.add("production");
        }

        @Subscribe
        public void onPool(GameEventManaPool event) {
            chronology.add("pool");
        }
    }
}
