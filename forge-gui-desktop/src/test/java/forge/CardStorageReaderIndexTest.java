package forge;

import forge.util.Lang;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CardStorageReaderIndexTest {
    private static final Path CARD_SCRIPTS = Path.of("../forge-gui/res/cardsfolder").toAbsolutePath().normalize();

    private Path root;
    private Path cardsfolder;

    @BeforeMethod
    public void createCardFolder() throws IOException {
        Lang.createInstance("en-US");
        root = Files.createTempDirectory("forge-card-index-");
        cardsfolder = Files.createDirectory(root.resolve("cardsfolder"));
    }

    @AfterMethod
    public void removeCardFolder() throws IOException {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    public void generatedIndexPreservesEveryLazyLookupWinner() throws IOException {
        writeArchive(List.of(
                script("a/emeritus.txt", "e/emeritus_of_abundance_regrowth.txt"),
                script("b/regrowth.txt", "r/regrowth.txt"),
                script("c/wear_tear.txt", "w/wear_tear.txt"),
                script("d/with_great_power.txt", "w/with_great_power.txt"),
                script("e/lim_duls_vault.txt", "l/lim_duls_vault.txt"),
                script("f/lightning_bolt.txt", "l/lightning_bolt.txt"),
                script("z/lightning_bolt_duplicate.txt", "l/lightning_bolt.txt")));
        Files.copy(CARD_SCRIPTS.resolve("l/lightning_bolt.txt"), cardsfolder.resolve("loose_bolt.txt"));

        int aliases = CardStorageReader.writeZipNameIndex(
                cardsfolder.toString(), cardsfolder.resolve("cardsfolder.index").toString());
        CardStorageReader reader = new CardStorageReader(cardsfolder.toString(), null, true);

        assertTrue(aliases >= 9);
        assertEquals(reader.attemptToLoadCard("Lightning Bolt").getPath(), "f/lightning_bolt.txt");
        assertEquals(reader.attemptToLoadCard("Wear // Tear").getPath(), "c/wear_tear.txt");
        assertEquals(reader.attemptToLoadCard("Tear").getPath(), "c/wear_tear.txt");
        assertEquals(reader.attemptToLoadCard("Chosen by Valgavoth").getPath(), "d/with_great_power.txt");
        assertEquals(reader.attemptToLoadCard("Lim Duls Vault").getPath(), "e/lim_duls_vault.txt");
        assertEquals(reader.attemptToLoadCard("Regrowth").getPath(), "b/regrowth.txt");
        assertEquals(reader.attemptToLoadCard("Emeritus of Abundance").getPath(), "a/emeritus.txt");
    }

    @Test
    public void invalidIndexFallsBackToArchiveScan() throws IOException {
        writeArchive(List.of(script("l/lightning_bolt.txt", "l/lightning_bolt.txt")));
        Files.writeString(cardsfolder.resolve("cardsfolder.index"), "invalid");

        CardStorageReader reader = new CardStorageReader(cardsfolder.toString(), null, true);

        assertEquals(reader.attemptToLoadCard("Lightning Bolt").getName(), "Lightning Bolt");
    }

    @Test
    public void staleIndexFallsBackToChangedArchive() throws IOException {
        writeArchive(List.of(script("l/lightning_bolt.txt", "l/lightning_bolt.txt")));
        CardStorageReader.writeZipNameIndex(
                cardsfolder.toString(), cardsfolder.resolve("cardsfolder.index").toString());
        writeArchive(List.of(
                script("l/lightning_bolt.txt", "l/lightning_bolt.txt"),
                script("s/shock.txt", "s/shock.txt")));

        CardStorageReader reader = new CardStorageReader(cardsfolder.toString(), null, true);

        assertEquals(reader.attemptToLoadCard("Shock").getName(), "Shock");
    }

    private Script script(String archivePath, String sourcePath) {
        return new Script(archivePath, CARD_SCRIPTS.resolve(sourcePath));
    }

    private void writeArchive(List<Script> scripts) throws IOException {
        try (OutputStream output = Files.newOutputStream(cardsfolder.resolve("cardsfolder.zip"));
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Script script : scripts) {
                zip.putNextEntry(new ZipEntry(script.archivePath));
                Files.copy(script.sourcePath, zip);
                zip.closeEntry();
            }
        }
    }

    private record Script(String archivePath, Path sourcePath) {}
}
