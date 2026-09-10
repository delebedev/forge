package forge.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class FileSectionTest {
    @Test
    public void getIntUsesDefaultsWithoutChangingParsingSemantics() {
        FileSection section = FileSection.parse(
                List.of("Valid=42", "Invalid=forty-two", "Empty="),
                FileSection.EQUALS_KV_SEPARATOR);

        Assert.assertEquals(section.getInt("Missing", 7), 7);
        Assert.assertEquals(section.getInt("Valid", 7), 42);
        Assert.assertEquals(section.getInt("Invalid", 7), 7);
        Assert.assertEquals(section.getInt("Empty", 7), 7);
    }
}
