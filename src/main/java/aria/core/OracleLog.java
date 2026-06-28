package aria.core;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class OracleLog {

    public static final String OUTCOME_CORRECT   = "correct";
    public static final String OUTCOME_PARTIAL   = "partial";
    public static final String OUTCOME_INCORRECT = "incorrect";

    public static class Entry {
        public String id;
        public String timestamp;
        public String worldContext;
        public String subject;
        public String verdict;
        public int    confidence;
        public String confidenceLabel;
        public String timeframe;
        public List<String> domains;
        public String outcome;
        public String outcomeNote;
        public String resolvedTimestamp;

        public boolean isResolved() {
            return outcome != null && !outcome.isEmpty();
        }

        public String shortDate() {
            if (timestamp == null) return "";
            return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public OracleLog() {
        load();
    }

    protected File resolveFile() {
        File primary = new File("data/oracle_log.json");
        File alt     = new File("aria/data/oracle_log.json");
        if (alt.exists() && !primary.exists()) return alt;
        return primary;
    }

    public void load() {
        File f = resolveFile();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Type type = new TypeToken<List<Entry>>() {}.getType();
            List<Entry> loaded = gson.fromJson(r, type);
            if (loaded != null) entries.addAll(loaded);
        } catch (Exception e) {
            System.err.println("[OracleLog] load failed (corrupt file, starting fresh): " + e.getMessage());
        }
    }

    public synchronized void save() {
        File f = resolveFile();
        try {
            f.getParentFile().mkdirs();
            try (Writer w = new FileWriter(f)) {
                gson.toJson(entries, w);
            }
        } catch (IOException e) {
            System.err.println("[OracleLog] save failed: " + e.getMessage());
        }
    }

    public synchronized String addEntry(String worldContext, String subject, String verdict,
                                        int confidence, String confidenceLabel,
                                        String timeframe, List<String> domains) {
        Entry e = new Entry();
        e.id               = UUID.randomUUID().toString().substring(0, 8);
        e.timestamp        = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        e.worldContext     = worldContext != null ? worldContext : "Real World";
        e.subject          = subject;
        e.verdict          = verdict;
        e.confidence       = confidence;
        e.confidenceLabel  = confidenceLabel != null ? confidenceLabel : "";
        e.timeframe        = timeframe != null ? timeframe : "";
        e.domains          = domains != null ? new ArrayList<>(domains) : new ArrayList<>();
        entries.add(e);
        save();
        return e.id;
    }

    public synchronized boolean resolveEntry(String id, String outcome, String note) {
        for (Entry e : entries) {
            if (e.id.equals(id)) {
                e.outcome           = outcome;
                e.outcomeNote       = note != null ? note : "";
                e.resolvedTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                save();
                return true;
            }
        }
        return false;
    }

    public List<Entry> getAll() {
        List<Entry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }

    public List<Entry> getOpen() {
        List<Entry> open = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (!entries.get(i).isResolved()) open.add(entries.get(i));
        }
        return open;
    }

    public List<Entry> getResolved() {
        List<Entry> resolved = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).isResolved()) resolved.add(entries.get(i));
        }
        return resolved;
    }

    public int[] getOverallAccuracy() {
        int[] stats = new int[2];
        for (Entry e : entries) {
            if (!e.isResolved()) continue;
            stats[1]++;
            if (OUTCOME_CORRECT.equals(e.outcome) || OUTCOME_PARTIAL.equals(e.outcome)) stats[0]++;
        }
        return stats;
    }

    public Map<String, int[]> getAccuracyByDomain() {
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!e.isResolved()) continue;
            for (String domain : e.domains) {
                stats.computeIfAbsent(domain, k -> new int[2]);
                stats.get(domain)[1]++;
                if (OUTCOME_CORRECT.equals(e.outcome) || OUTCOME_PARTIAL.equals(e.outcome)) {
                    stats.get(domain)[0]++;
                }
            }
        }
        return stats;
    }

    public Optional<Entry> findById(String id) {
        return entries.stream().filter(e -> e.id.equals(id)).findFirst();
    }

    public int totalCount()    { return entries.size(); }
    public int openCount()     { return (int) entries.stream().filter(e -> !e.isResolved()).count(); }
    public int resolvedCount() { return (int) entries.stream().filter(Entry::isResolved).count(); }
}
