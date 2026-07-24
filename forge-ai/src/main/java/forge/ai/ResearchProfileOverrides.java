package forge.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import forge.game.player.Player;

/**
 * Per-seat overrides of AI personality properties (AiProps).
 *
 * The seat's resolved profile stays in force; listed properties are answered
 * from this table instead, at the single AiProfileUtil.getProperty funnel.
 * Stock behavior is an empty table.
 *
 * Forge's own profile loader silently ignores a key it cannot resolve, which
 * is exactly wrong for a research arm: a typo would run stock under a
 * candidate's name. Everything here fails loudly instead.
 */
public final class ResearchProfileOverrides {

    private final Map<AiProps, String> overrides;
    private final String sourceSha256;

    private ResearchProfileOverrides(Map<AiProps, String> overrides, String sourceSha256) {
        this.overrides = Collections.unmodifiableMap(overrides);
        this.sourceSha256 = sourceSha256;
    }

    /** The override for this property, or null to fall through to the profile. */
    public String value(AiProps prop) {
        return overrides.get(prop);
    }

    public int propCount() {
        return overrides.size();
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    /** The table bound to this player's seat, or null when it plays stock. */
    public static ResearchProfileOverrides forPlayer(Player p) {
        if (p == null) {
            return null;
        }
        if (!(p.getLobbyPlayer() instanceof LobbyPlayerAi)) {
            return null;
        }
        return ((LobbyPlayerAi) p.getLobbyPlayer()).getProfileOverrides();
    }

    /**
     * Parse an overrides file: '# comment' and blank lines ignored, otherwise
     * 'PROPERTY = value'. Unknown properties, duplicates, empty values, and
     * values whose shape contradicts the property's declared default all fail.
     */
    public static ResearchProfileOverrides parse(List<String> lines, String sourceSha256) {
        Map<AiProps, String> parsed = new EnumMap<>(AiProps.class);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("profile-overrides line has no '=': " + raw);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            final AiProps prop;
            try {
                prop = AiProps.valueOf(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("profile-overrides unknown property: " + key);
            }
            if (value.isEmpty()) {
                // An empty profile value means "use the default" — a silent no-op.
                throw new IllegalArgumentException("profile-overrides empty value for " + key);
            }
            checkShape(prop, value);
            if (parsed.put(prop, value) != null) {
                throw new IllegalArgumentException("profile-overrides duplicate property: " + key);
            }
        }
        return new ResearchProfileOverrides(parsed, sourceSha256);
    }

    /**
     * A value must parse the way the property's own default does. AiProps is
     * untyped, so the declared default is the only available type witness —
     * without this, a bad value surfaces as a NumberFormatException mid-game.
     */
    private static void checkShape(AiProps prop, String value) {
        String declared = prop.getDefault();
        if (isBool(declared)) {
            if (!isBool(value)) {
                throw new IllegalArgumentException(
                        "profile-overrides " + prop.name() + " is boolean, got: " + value);
            }
            return;
        }
        if (isInt(declared)) {
            if (!isInt(value)) {
                throw new IllegalArgumentException(
                        "profile-overrides " + prop.name() + " is integer, got: " + value);
            }
        }
    }

    private static boolean isBool(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static ResearchProfileOverrides load(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            String content = new String(bytes, StandardCharsets.UTF_8);
            return parse(List.of(content.split("\n", -1)), sha256Hex(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("profile-overrides file unreadable: " + path, e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
