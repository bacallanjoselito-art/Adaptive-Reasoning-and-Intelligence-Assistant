package aria.core;

import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class AIBaseExporter {

    private final ContextManager contextManager;
    private final ModuleManager moduleManager;

    public static class AriaBaseExport {
        public String aria_base_version = "1.0";
        public Metadata metadata = new Metadata();
        public Personality personality = new Personality();
        public WorldContext world_context = new WorldContext();
        public Map<String, Boolean> modules = new LinkedHashMap<>();
        public SystemPrompt system_prompt = new SystemPrompt();
        public QuickStart quick_start = new QuickStart();
    }

    public static class Metadata {
        public String name = "";
        public String description = "";
        public String author = "Natt";
        public String created_at = Instant.now().toString();
        public String last_updated = Instant.now().toString();
        public List<String> tags = new ArrayList<>();
        public String intended_use = "";
    }

    public static class Personality {
        public String base = "ARIA";
        public String tone = "warm, dry humor, direct";
        public List<String> speech_patterns = Arrays.asList(
            "casual default, matches user energy",
            "short sentences in casual contexts",
            "no bullet points in conversation",
            "uses I think or not sure but when uncertain"
        );
        public List<String> never_says = Arrays.asList("certainly", "of course", "great question");
    }

    public static class WorldContext {
        public String world_name = "";
        public String description = "";
        public String aria_role = "";
        public String knowledge_bounds = "";
        public String knowledge_limits = "";
    }

    public static class SystemPrompt {
        public String full_text = "";
        public String injection_note = "This prompt is pre-assembled. Paste as system prompt in any OpenAI, Anthropic, or compatible API call.";
    }

    public static class QuickStart {
        public String recommended_llm = "claude-haiku-4-5-20251001 or gpt-4o-mini";
        public int max_tokens = 1000;
        public double temperature = 0.8;
        public String usage_note = "Drop system_prompt.full_text as your system message. Send user messages normally. Works with any OpenAI-compatible API.";
    }

    public static class ExportHistoryItem {
        public String name;
        public String filePath;
        public String timestamp;
        public String worldName;
    }

    public AIBaseExporter(ContextManager contextManager, ModuleManager moduleManager) {
        this.contextManager = contextManager;
        this.moduleManager = moduleManager;
    }

    public String assembleSystemPrompt(String basePromptText) {
        String modulesText = moduleManager.toPromptText();
        String contextText = contextManager.toPromptText();
        return basePromptText
            .replace("{MODULES_INJECT_HERE}", modulesText)
            .replace("{WORLD_CONTEXT_INJECT_HERE}", contextText);
    }

    public AriaBaseExport buildExportPackage(String name, String description, List<String> tags, String intendedUse, String author, String assembledSystemPrompt) {
        AriaBaseExport pkg = new AriaBaseExport();

        JsonObject ctx = contextManager.getWorldContext();

        pkg.metadata.name = name;
        pkg.metadata.description = description;
        pkg.metadata.author = author;
        pkg.metadata.created_at = Instant.now().toString();
        pkg.metadata.last_updated = Instant.now().toString();
        pkg.metadata.tags = tags;
        pkg.metadata.intended_use = intendedUse;

        if (ctx != null) {
            if (ctx.has("world_name")) pkg.world_context.world_name = ctx.get("world_name").getAsString();
            if (ctx.has("world_description")) pkg.world_context.description = ctx.get("world_description").getAsString();
            if (ctx.has("aria_role")) pkg.world_context.aria_role = ctx.get("aria_role").getAsString();
            if (ctx.has("knowledge_bounds")) pkg.world_context.knowledge_bounds = ctx.get("knowledge_bounds").getAsString();
            if (ctx.has("knowledge_limits")) pkg.world_context.knowledge_limits = ctx.get("knowledge_limits").getAsString();
        }

        pkg.modules = new LinkedHashMap<>(moduleManager.getAll());
        pkg.system_prompt.full_text = assembledSystemPrompt;

        return pkg;
    }

    public String saveToFile(AriaBaseExport export, String filename) throws IOException {
        File exportsDir = resolveExportsDir();
        exportsDir.mkdirs();

        String safeName = filename.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (!safeName.endsWith("_aria_base.json")) {
            safeName = safeName + "_aria_base.json";
        }

        File outFile = new File(exportsDir, safeName);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = new FileWriter(outFile)) {
            gson.toJson(export, w);
        }
        return outFile.getAbsolutePath();
    }

    public String exportAsText(AriaBaseExport export, String filename) throws IOException {
        File exportsDir = resolveExportsDir();
        exportsDir.mkdirs();

        String safeName = filename.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (!safeName.endsWith("_system_prompt.txt")) {
            safeName = safeName + "_system_prompt.txt";
        }

        File outFile = new File(exportsDir, safeName);
        Files.writeString(outFile.toPath(), export.system_prompt.full_text);
        return outFile.getAbsolutePath();
    }

    public AriaBaseExport importFromFile(File file) throws IOException {
        try (Reader r = new FileReader(file)) {
            Gson gson = new Gson();
            return gson.fromJson(r, AriaBaseExport.class);
        }
    }

    public List<ExportHistoryItem> loadExportHistory() {
        List<ExportHistoryItem> items = new ArrayList<>();
        File exportsDir = resolveExportsDir();
        if (!exportsDir.exists()) return items;

        File[] files = exportsDir.listFiles((dir, name) -> name.endsWith("_aria_base.json"));
        if (files == null) return items;

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        Gson gson = new Gson();
        for (File f : files) {
            try (Reader r = new FileReader(f)) {
                AriaBaseExport pkg = gson.fromJson(r, AriaBaseExport.class);
                ExportHistoryItem item = new ExportHistoryItem();
                item.name = pkg.metadata != null ? pkg.metadata.name : f.getName();
                item.filePath = f.getAbsolutePath();
                item.timestamp = pkg.metadata != null ? pkg.metadata.created_at : "";
                item.worldName = pkg.world_context != null ? pkg.world_context.world_name : "";
                items.add(item);
            } catch (Exception e) {
                ExportHistoryItem item = new ExportHistoryItem();
                item.name = f.getName();
                item.filePath = f.getAbsolutePath();
                item.timestamp = "";
                item.worldName = "";
                items.add(item);
            }
        }
        return items;
    }

    private File resolveExportsDir() {
        File f = new File("exports");
        if (!f.exists()) {
            File alt = new File("aria/exports");
            if (alt.exists() || new File("aria").exists()) return alt;
        }
        return f;
    }
}
