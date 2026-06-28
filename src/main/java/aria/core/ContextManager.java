package aria.core;

import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ContextManager {

    private final Map<String, JsonObject> contexts = new LinkedHashMap<>();
    private String activeContext = null;

    public ContextManager() {
        migrateOldFormat();
        load();
    }

    private File resolveFile() {
        File f = new File("config/world_contexts.json");
        if (!f.exists()) f = new File("aria/config/world_contexts.json");
        return f;
    }

    private void migrateOldFormat() {
        File old = new File("config/world_context.json");
        if (!old.exists()) old = new File("aria/config/world_context.json");
        if (!old.exists()) return;
        if (resolveFile().exists()) return;

        try (Reader r = new FileReader(old)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el.isJsonObject()) {
                JsonObject ctx = el.getAsJsonObject();
                if (ctx.has("world_name") && !ctx.get("world_name").getAsString().isEmpty()) {
                    String name = ctx.get("world_name").getAsString();
                    contexts.put(name, ctx);
                    activeContext = name;
                    save();
                }
            }
        } catch (Exception ignored) {}
    }

    public void load() {
        File f = resolveFile();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("contexts")) {
                JsonObject all = root.getAsJsonObject("contexts");
                for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
                    contexts.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
            if (root.has("active") && !root.get("active").isJsonNull()) {
                String saved = root.get("active").getAsString();
                if (contexts.containsKey(saved)) activeContext = saved;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        File f = resolveFile();
        try {
            f.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            root.addProperty("active", activeContext != null ? activeContext : "");
            JsonObject all = new JsonObject();
            for (Map.Entry<String, JsonObject> entry : contexts.entrySet()) {
                all.add(entry.getKey(), entry.getValue());
            }
            root.add("contexts", all);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = new FileWriter(f)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, JsonObject> getAll() {
        return Collections.unmodifiableMap(contexts);
    }

    public String getActiveName() {
        return activeContext;
    }

    public JsonObject getActiveContext() {
        if (activeContext == null) return null;
        return contexts.get(activeContext);
    }

    public void setActive(String name) {
        activeContext = name;
        save();
    }

    public void deactivate() {
        activeContext = null;
        save();
    }

    public void addOrUpdate(String name, JsonObject ctx) {
        ctx.addProperty("world_name", name);
        contexts.put(name, ctx);
        save();
    }

    public void rename(String oldName, String newName, JsonObject ctx) {
        contexts.remove(oldName);
        ctx.addProperty("world_name", newName);
        contexts.put(newName, ctx);
        if (oldName.equals(activeContext)) activeContext = newName;
        save();
    }

    public void delete(String name) {
        contexts.remove(name);
        if (name.equals(activeContext)) activeContext = null;
        save();
    }

    public String toPromptText() {
        JsonObject ctx = getActiveContext();
        if (ctx == null || ctx.size() == 0) {
            return "Real world. No special context active.";
        }
        StringBuilder sb = new StringBuilder();
        appendField(sb, "World", ctx, "world_name");
        appendField(sb, "Description", ctx, "world_description");
        appendField(sb, "ARIA's Role", ctx, "aria_role");
        appendField(sb, "Knowledge Bounds", ctx, "knowledge_bounds");
        appendField(sb, "Knowledge Limits", ctx, "knowledge_limits");
        return sb.toString().trim();
    }

    private void appendField(StringBuilder sb, String label, JsonObject ctx, String key) {
        if (ctx.has(key)) {
            String val = ctx.get(key).getAsString().trim();
            if (!val.isEmpty()) sb.append(label).append(": ").append(val).append("\n");
        }
    }

    public String getWorldName() {
        return activeContext != null ? activeContext : "";
    }

    public JsonObject getWorldContext() {
        return getActiveContext();
    }

    public void setWorldContext(JsonObject ctx) {
        String name = ctx.has("world_name") ? ctx.get("world_name").getAsString() : "Unnamed";
        addOrUpdate(name, ctx);
        activeContext = name;
        save();
    }

    public void clear() {
        activeContext = null;
        save();
    }

    public String getString(String key) {
        JsonObject ctx = getActiveContext();
        if (ctx != null && ctx.has(key)) return ctx.get(key).getAsString();
        return "";
    }
}
