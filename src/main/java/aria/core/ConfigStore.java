package aria.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigStore {

    public static final String VERSION = "1.2.0";

    private final Map<String, String> store = new LinkedHashMap<>();
    private File envFile;

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("ANTHROPIC_API_KEY", "");
        DEFAULTS.put("OPENAI_API_KEY", "");
        DEFAULTS.put("GROQ_API_KEY", "");
        DEFAULTS.put("LLM_PROVIDER", "groq");
        DEFAULTS.put("CLAUDE_MODEL", "claude-haiku-4-5-20251001");
        DEFAULTS.put("OPENAI_MODEL", "gpt-4o-mini");
        DEFAULTS.put("GROQ_MODEL", "llama-3.3-70b-versatile");
        DEFAULTS.put("MAX_TOKENS", "1000");
        DEFAULTS.put("PERSIST_HISTORY", "false");
        DEFAULTS.put("THEME", "dark");
        DEFAULTS.put("AUTHOR_NAME", "Natt");
    }

    public ConfigStore() {
        store.putAll(DEFAULTS);
        loadEnv();
    }

    private void loadEnv() {
        String workDir = System.getProperty("user.dir");

        List<File> candidates = new ArrayList<>();
        candidates.add(new File(workDir, ".env"));
        candidates.add(new File(".env"));
        candidates.add(new File("aria", ".env"));
        candidates.add(new File(workDir).getParentFile() != null
            ? new File(new File(workDir).getParentFile(), ".env") : null);

        for (File f : candidates) {
            if (f != null && f.exists() && f.isFile()) {
                envFile = f;
                System.out.println("[ARIA] Found .env: " + f.getAbsolutePath());
                parseEnvFile(f);
                return;
            }
        }

        System.out.println("[ARIA] .env not found. Creating template at: " + workDir);
        envFile = new File(workDir, ".env");
        try {
            saveToFile();
        } catch (Exception e) {
            System.err.println("[ARIA] Could not create .env: " + e.getMessage());
        }
        loadFromSystemEnv();
    }

    private void parseEnvFile(File f) {
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (!value.isEmpty()) {
                    store.put(key, value);
                }
            }
            System.out.println("[ARIA] Config loaded. Provider: " + store.get("LLM_PROVIDER"));
        } catch (IOException e) {
            System.err.println("[ARIA] Failed to read .env: " + e.getMessage());
        }
    }

    private void loadFromSystemEnv() {
        for (String key : DEFAULTS.keySet()) {
            String val = System.getenv(key);
            if (val != null && !val.isEmpty()) {
                store.put(key, val);
            }
        }
    }

    public String get(String key, String defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    public void set(String key, String value) {
        store.put(key, value);
    }

    public void saveToFile() {
        if (envFile == null) {
            envFile = new File(System.getProperty("user.dir"), ".env");
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : store.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
            Files.writeString(envFile.toPath(), sb.toString());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(store);
    }
}
