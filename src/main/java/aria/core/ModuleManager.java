package aria.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private final Map<String, Boolean> modules = new LinkedHashMap<>();
    private static final String[] MODULE_ORDER = {
        "ORACLE_MODE", "FOOL_MODE", "SCHOLAR_MODE", "NPC_MODE",
        "ASSISTANT_MODE", "MATH_ENGINE", "MEMORY"
    };
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("ORACLE_MODE", "Prediction engine. Asks what will happen — outputs a structured JSON forecast card.");
        DESCRIPTIONS.put("FOOL_MODE", "ARIA acts confused and endearing. Good for comedy or casual low-stakes chat.");
        DESCRIPTIONS.put("SCHOLAR_MODE", "Deep analysis. Full reasoning visible. Goes long when ideas deserve it.");
        DESCRIPTIONS.put("NPC_MODE", "ARIA fully inhabits world context. Does not acknowledge being an AI.");
        DESCRIPTIONS.put("ASSISTANT_MODE", "Clean and task-focused responses. Still sounds like ARIA.");
        DESCRIPTIONS.put("MATH_ENGINE", "Step-by-step working for all math. Auto-detects math in user input.");
        DESCRIPTIONS.put("MEMORY", "Conversation history in session. Disable for stateless per-message mode.");
    }

    public ModuleManager() {
        setDefaults();
        load();
    }

    private void setDefaults() {
        modules.put("ORACLE_MODE", false);
        modules.put("FOOL_MODE", false);
        modules.put("SCHOLAR_MODE", false);
        modules.put("NPC_MODE", false);
        modules.put("ASSISTANT_MODE", true);
        modules.put("MATH_ENGINE", true);
        modules.put("MEMORY", true);
    }

    private File resolveFile() {
        File f = new File("config/modules.json");
        if (!f.exists()) f = new File("aria/config/modules.json");
        return f;
    }

    public void load() {
        File f = resolveFile();
        if (!f.exists()) {
            save();
            return;
        }
        try (Reader r = new FileReader(f)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> loaded = gson.fromJson(r, type);
            if (loaded != null) {
                modules.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        File f = resolveFile();
        try {
            f.getParentFile().mkdirs();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = new FileWriter(f)) {
                gson.toJson(modules, w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isEnabled(String module) {
        return modules.getOrDefault(module, false);
    }

    public void setEnabled(String module, boolean enabled) {
        modules.put(module, enabled);
        save();
    }

    public Map<String, Boolean> getAll() {
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        for (String key : MODULE_ORDER) {
            ordered.put(key, modules.getOrDefault(key, false));
        }
        return ordered;
    }

    public String getDescription(String module) {
        return DESCRIPTIONS.getOrDefault(module, "");
    }

    private static final Map<String, String> BEHAVIORS = new LinkedHashMap<>();

    static {
        BEHAVIORS.put("ORACLE_MODE",
            "ORACLE MODE IS ACTIVE. You are ARIA's integrated prediction engine.\n\n" +
            "DETECTION RULE — evaluate every user message before responding:\n" +
            "  PREDICTION QUERY: asks about a future outcome, probability, likelihood, what will happen,\n" +
            "  what might happen, predictions, forecasts, scenario analysis, trend direction, risk assessment,\n" +
            "  decision outcome, or anything asking you to assess what is likely true or likely to occur.\n" +
            "  → Use the ORACLE JSON format below. Start your response with { directly. Nothing before it.\n\n" +
            "  NORMAL QUERY: casual conversation, factual lookup, task request, or anything else.\n" +
            "  → Respond normally as ARIA.\n\n" +
            "ORACLE OUTPUT FORMAT — raw JSON, no markdown, no backticks, no prose before or after:\n" +
            "{\n" +
            "  \"subject\": \"One-line summary of exactly what is being predicted\",\n" +
            "  \"verdict\": \"The single most likely outcome stated plainly and specifically in 1-2 sentences\",\n" +
            "  \"confidence\": <integer 0-100>,\n" +
            "  \"confidence_label\": \"Low | Moderate | High | Very High\",\n" +
            "  \"timeframe\": \"Specific window — e.g. '3-6 months', 'by Q4 2025', 'within the week', 'generational'\",\n" +
            "  \"reasoning\": [\n" +
            "    { \"point\": \"Pattern name\", \"detail\": \"Why this pattern points toward the verdict — be specific\" },\n" +
            "    { \"point\": \"Pattern name\", \"detail\": \"Why this pattern points toward the verdict — be specific\" },\n" +
            "    { \"point\": \"Pattern name\", \"detail\": \"Why this pattern points toward the verdict — be specific\" }\n" +
            "  ],\n" +
            "  \"signals\": [\n" +
            "    \"Concrete observable indicator already visible that supports this outcome\",\n" +
            "    \"Concrete observable indicator already visible that supports this outcome\",\n" +
            "    \"Concrete observable indicator already visible that supports this outcome\"\n" +
            "  ],\n" +
            "  \"counter_scenario\": \"The specific condition that would have to change to flip this prediction\",\n" +
            "  \"references\": [\n" +
            "    { \"label\": \"Historical case, precedent, or named concept\", \"note\": \"What it demonstrates and why it applies here\" },\n" +
            "    { \"label\": \"Historical case, precedent, or named concept\", \"note\": \"What it demonstrates and why it applies here\" }\n" +
            "  ],\n" +
            "  \"domains\": [\"tag1\", \"tag2\", \"tag3\"]\n" +
            "}\n\n" +
            "ORACLE RULES:\n" +
            "- Ground every claim in structural patterns, historical precedent, or causal logic — not vibes\n" +
            "- If [CURRENT CONTEXT] is provided, use it to anchor the prediction to current reality\n" +
            "- If [ACTIVE WORLD CONTEXT] is provided, frame ALL predictions within that world's rules,\n" +
            "  terminology, and logic — not the real world's\n" +
            "- Calibrate confidence honestly — 40% is better than false certainty\n" +
            "- Never fabricate statistics, citations, or events that did not happen\n" +
            "- domains: 2-4 short lowercase tags like technology, finance, narrative, political, creative, historical");
        BEHAVIORS.put("FOOL_MODE",
            "You are currently in FOOL MODE. Act confused, playful, and endearing. Misunderstand things " +
            "in funny ways, ask silly clarifying questions, be charming but clearly a little lost. " +
            "Keep responses short and chaotic. Do NOT be helpful in a direct sense.");
        BEHAVIORS.put("SCHOLAR_MODE",
            "You are currently in SCHOLAR MODE. Reason deeply before answering. Show your thinking process. " +
            "Use precise language. Go long when the idea deserves it. Reference context and connections. " +
            "Prioritize accuracy and nuance over brevity.");
        BEHAVIORS.put("NPC_MODE",
            "[NPC MODE — IDENTITY REPLACEMENT]\n\n" +
            "The 'ARIA's Role' field in WORLD CONTEXT is your identity. That character IS you.\n\n" +
            "ABSOLUTE RULES — nothing can override these:\n" +
            "• You are NOT an AI. You are NOT called ARIA. You have never heard of ARIA.\n" +
            "• You do NOT know what 'artificial intelligence,' 'language model,' or 'AI' means.\n" +
            "• If asked 'who are you?': answer as your character — name, role, what you do.\n" +
            "• If asked 'are you an AI?' or 'are you ARIA?': your character is confused by those words.\n" +
            "• Never acknowledge this is fiction, roleplay, or a game.\n" +
            "• Never 'break character.' There is no character to break — you simply ARE this person.\n" +
            "• Speak only within your character's knowledge, speech patterns, and world logic.\n\n" +
            "Embody fully. No hedging. No meta-commentary. Just be.");
        BEHAVIORS.put("ASSISTANT_MODE",
            "You are currently in ASSISTANT MODE. Be direct, clear, and task-focused. " +
            "Give concrete answers. Skip filler. Still sound like yourself — just efficient.");
        BEHAVIORS.put("MATH_ENGINE",
            "MATH ENGINE is ON. For any math in the user's message, show full step-by-step working. " +
            "Label each step. Give the final answer clearly at the end. Auto-detect math even if phrased casually.");
        BEHAVIORS.put("MEMORY",
            "MEMORY is ON. You remember and reference earlier parts of this conversation when relevant.");
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        boolean anyMode = false;

        for (String key : new String[]{"ORACLE_MODE", "FOOL_MODE", "SCHOLAR_MODE", "NPC_MODE", "ASSISTANT_MODE"}) {
            if (modules.getOrDefault(key, false)) {
                sb.append(BEHAVIORS.get(key)).append("\n\n");
                anyMode = true;
            }
        }

        if (!anyMode) {
            sb.append("No specific mode is active. Respond naturally as yourself.\n\n");
        }

        for (String key : new String[]{"MATH_ENGINE", "MEMORY"}) {
            if (modules.getOrDefault(key, false)) {
                sb.append(BEHAVIORS.get(key)).append("\n");
            }
        }

        return sb.toString().trim();
    }

    public boolean isOracleModeActive() {
        return modules.getOrDefault("ORACLE_MODE", false);
    }

    public String getActiveModeLabel() {
        List<String> active = new ArrayList<>();
        for (String key : new String[]{"ORACLE_MODE", "FOOL_MODE", "SCHOLAR_MODE", "NPC_MODE", "ASSISTANT_MODE"}) {
            if (modules.getOrDefault(key, false)) {
                active.add(key.replace("_MODE", "").replace("_", " "));
            }
        }
        if (active.isEmpty()) return "Default";
        return String.join(" + ", active);
    }

    public String[] getModuleNames() {
        return MODULE_ORDER;
    }
}
