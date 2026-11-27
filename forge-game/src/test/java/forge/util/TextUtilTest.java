package forge.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TextUtilTest {
    @Test
    public void fastReplacePreservesEscapedCrLfFormatting() {
        String[] inputs = {
                "prefix\\r\\nsuffix\\r\\n",
                "\\r\\n\\r\\n",
                "$\\r\\n\\\\text$\\r\\n",
                "actual\r\nnewline\n$\\\\text",
                "absent $\\\\text"
        };

        for (String input : inputs) {
            String expected = input.replaceAll("\\\\r\\\\n", "\r\n");
            Assert.assertEquals(TextUtil.fastReplace(input, "\\r\\n", "\r\n"), expected);
        }
    }

    @Test
    public void fastReplacePreservesEscapedLfFormatting() {
        String[] inputs = {
                "prefix\\nsuffix\\n",
                "\\n\\n",
                "$\\n\\\\text$\\n",
                "actual\r\nnewline\n$\\\\text",
                "absent $\\\\text"
        };

        for (String input : inputs) {
            String expected = input.replace("\\n", "\r\n\r\n");
            Assert.assertEquals(TextUtil.fastReplace(input, "\\n", "\r\n\r\n"), expected);
        }
    }

    @Test
    public void fastReplacePreservesEscapedLinebreakUnescaping() {
        String[] inputs = {
                "prefix\\r\\nsuffix\\r\\n",
                "\\r\\n\\r\\n",
                "$\\r\\n\\\\text$\\n",
                "actual\r\nnewline\n$\\\\text",
                "absent $\\\\text"
        };

        for (String input : inputs) {
            String expected = input.replace("\\r", "\r").replace("\\n", "\n");
            String actual = TextUtil.fastReplace(TextUtil.fastReplace(input, "\\r", "\r"), "\\n", "\n");
            Assert.assertEquals(actual, expected);
        }
    }
}
