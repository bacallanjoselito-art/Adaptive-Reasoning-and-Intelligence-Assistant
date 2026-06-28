package aria.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Persists a list of pinned ARIA responses across sessions.
 * Saved to config/pinned.json.
 */
public class PinnedMessages {

    public record PinnedMessage(String text, String timestamp) {}

    private final List<PinnedMessage> messages = new ArrayList<>();
    private final File saveFile;

    public PinnedMessages() {
        saveFile = resolveSaveFile();
        load();
    }

    public void pin(String text) {
        if (isPinned(text)) return;
        String ts = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm"));
        messages.add(new PinnedMessage(text, ts));
        save();
    }

    public void unpin(String text) {
        messages.removeIf(m -> m.text().equals(text));
        save();
    }

    public void unpinAt(int index) {
        if (index >= 0 && index < messages.size()) {
            messages.remove(index);
            save();
        }
    }

    public boolean isPinned(String text) {
        return messages.stream().anyMatch(m -> m.text().equals(text));
    }

    public List<PinnedMessage> getAll() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isEmpty() { return messages.isEmpty(); }

    // ─── PERSISTENCE ───────────────────────────────────────────────────────────

    private File resolveSaveFile() {
        File f = new File("config/pinned.json");
        if (!f.getParentFile().exists()) {
            f = new File("aria/config/pinned.json");
        }
        f.getParentFile().mkdirs();
        return f;
    }

    private void save() {
        try {
            JsonArray arr = new JsonArray();
            for (PinnedMessage m : messages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("text", m.text());
                obj.addProperty("timestamp", m.timestamp());
                arr.add(obj);
            }
            Files.writeString(saveFile.toPath(),
                new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        } catch (Exception e) {
            System.err.println("[PinnedMessages] Save failed: " + e.getMessage());
        }
    }

    private void load() {
        try {
            if (!saveFile.exists()) return;
            String json = Files.readString(saveFile.toPath());
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                messages.add(new PinnedMessage(
                    obj.get("text").getAsString(),
                    obj.has("timestamp") ? obj.get("timestamp").getAsString() : ""
                ));
            }
        } catch (Exception e) {
            System.err.println("[PinnedMessages] Load failed (non-fatal): " + e.getMessage());
        }
    }
}
