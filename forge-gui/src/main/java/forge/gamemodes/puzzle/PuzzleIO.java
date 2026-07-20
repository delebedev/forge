package forge.gamemodes.puzzle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

import forge.localinstance.properties.ForgeConstants;
import forge.util.FileSection;
import forge.util.FileUtil;

public class PuzzleIO {

    public static final String TXF_PROMPT = "[New Puzzle]";
    public static final String SUFFIX_DATA = ".pzl";
    public static final String SUFFIX_COMPLETE = ".complete";

    public static ArrayList<Puzzle> loadPuzzles(String directory) {
        String[] pList;
        // get list of puzzles
        final File pFolder = new File(directory);
        if (!pFolder.exists()) {
            throw new RuntimeException("Puzzles : folder not found -- folder is " + pFolder.getAbsolutePath());
        }

        if (!pFolder.isDirectory()) {
            throw new RuntimeException("Puzzles : not a folder -- " + pFolder.getAbsolutePath());
        }

        pList = pFolder.list();

        ArrayList<Puzzle> puzzles = Lists.newArrayList();
        for (final String element : pList) {
            if (element.endsWith(SUFFIX_DATA)) {
                puzzles.add(loadPuzzle(new File(directory, element)));
            }
        }
        return puzzles;
    }

    /** Load a single puzzle by file path (headless puzzle mode doesn't scan a directory). */
    public static Puzzle loadPuzzle(File pzlFile) {
        final List<String> pfData = FileUtil.readFile(pzlFile.getPath());

        String filename = pzlFile.getName().replace(SUFFIX_DATA, "");
        boolean completed = FileUtil.doesFileExist(ForgeConstants.USER_PUZZLE_DIR + filename + SUFFIX_COMPLETE);

        // Pass file name into Puzzle so it can save the completed name to match
        return new Puzzle(FileSection.parseSections(pfData), filename, completed);
    }

}
