import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads KEY=VALUE pairs from a .env file in the project root so credentials
 * never have to be hardcoded into a .java file. Copy .env.example to .env
 * and fill in your own local values before running anything.
 */
public class EnvConfig {

    private final Map<String, String> values = new HashMap<>();

    public EnvConfig() {
        this(Path.of(".env"));
    }

    public EnvConfig(Path envFile) {
        if (!Files.exists(envFile)) {
            throw new RuntimeException(
                "Missing " + envFile + " — copy .env.example to .env and fill in your DB credentials."
            );
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int eq = trimmed.indexOf('=');
                if (eq == -1) continue;

                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + envFile, e);
        }
    }

    public String get(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new RuntimeException("Missing key '" + key + "' in .env");
        }
        return value;
    }
}
