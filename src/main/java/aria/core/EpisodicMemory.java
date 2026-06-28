package aria.core;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EpisodicMemory {

    public static final String TYPE_PREDICTION  = "prediction";
    public static final String TYPE_OUTCOME     = "outcome";
    public static final String TYPE_DISCUSSION  = "discussion";
    public static final String TYPE_NOTE        = "note";

    public static class MemoryEntry {
        public String date;
        public String type;
        public String summary;
        public String worldContext;
    }

    private static final int MAX_PER_WORLD = 60;
    private static final int INJECT_RECENT = 10;

    private final Map<String, List<MemoryEntry>> store = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public EpisodicMemory() {
        load();
    }

    protected File resolveFile() {
        File primary = new File("data/episodic_memory.json");
        File alt     = new File("aria/data/episodic_memory.json");
        if (alt.exists() && !primary.exists()) return alt;
        return primary;
    }

    public void load() {
        File f = resolveFile();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Type type = new TypeToken<Map<String, List<MemoryEntry>>>() {}.getType();
            Map<String, List<MemoryEntry>> loaded = gson.fromJson(r, type);
            if (loaded != null) store.putAll(loaded);
        } catch (Exception e) {
            System.err.println("[EpisodicMemory] load failed (corrupt file, starting fresh): " + e.getMessage());
        }
    }

    public synchronized void save() {
        File f = resolveFile();
        try {
            f.getParentFile().mkdirs();
            try (Writer w = new FileWriter(f)) {
                gson.toJson(store, w);
            }
        } catch (IOException e) {
            System.err.println("[EpisodicMemory] save failed: " + e.getMessage());
        }
    }

    public synchronized void addEntry(String worldContext, String type, String summary) {
        String key = (worldContext == null || worldContext.isBlank()) ? "Real World" : worldContext;
        List<MemoryEntry> list = store.computeIfAbsent(key, k -> new ArrayList<>());

        MemoryEntry entry = new MemoryEntry();
        entry.date         = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        entry.type         = type;
        entry.summary      = summary;
        entry.worldContext = key;
        list.add(entry);

        while (list.size() > MAX_PER_WORLD) list.remove(0);
        save();
    }

    public String buildPromptBlock(String worldContext) {
        String key = (worldContext == null || worldContext.isBlank()) ? "Real World" : worldContext;
        List<MemoryEntry> list = store.getOrDefault(key, Collections.emptyList());
        if (list.isEmpty()) return "";

        int start = Math.max(0, list.size() - INJECT_RECENT);
        List<MemoryEntry> recent = list.subList(start, list.size());

        StringBuilder sb = new StringBuilder();
        sb.append("EPISODIC MEMORY — what ARIA remembers about this world across sessions:\n");
        for (MemoryEntry e : recent) {
            sb.append("  [").append(e.date).append("] [").append(e.type.toUpperCase()).append("] ")
              .append(e.summary).append("\n");
        }
        return sb.toString().trim();
    }

    public List<MemoryEntry> getForWorld(String worldContext) {
        String key = (worldContext == null || worldContext.isBlank()) ? "Real World" : worldContext;
        List<MemoryEntry> list = store.getOrDefault(key, Collections.emptyList());
        List<MemoryEntry> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    public Set<String> getWorldNames() {
        return store.keySet();
    }

    public void deleteEntry(String worldContext, int reversedIndex) {
        String key = (worldContext == null || worldContext.isBlank()) ? "Real World" : worldContext;
        List<MemoryEntry> list = store.get(key);
        if (list == null) return;
        int actualIndex = list.size() - 1 - reversedIndex;
        if (actualIndex >= 0 && actualIndex < list.size()) {
            list.remove(actualIndex);
            save();
        }
    }

    public int countForWorld(String worldContext) {
        String key = (worldContext == null || worldContext.isBlank()) ? "Real World" : worldContext;
        return store.getOrDefault(key, Collections.emptyList()).size();
    }

    public int totalCount() {
        return store.values().stream().mapToInt(List::size).sum();
    }
}
