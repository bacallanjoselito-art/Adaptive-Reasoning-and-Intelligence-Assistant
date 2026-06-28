package aria.core;

import com.google.gson.JsonObject;

import java.io.File;

public class AriaCore {

    private final ContextManager contextManager;
    private final ModuleManager moduleManager;
    private final ConversationHistory conversationHistory;
    private final LLMClient llmClient;
    private final AIBaseExporter aiBaseExporter;
    private final ConfigStore configStore;
    private final OracleLog oracleLog;
    private final EpisodicMemory episodicMemory;
    private final ImageClient imageClient;
    private final TtsManager ttsManager;

    private final PinnedMessages pinnedMessages;
    private volatile boolean memoryUnlocked = false;
    private static final String ACCESS_CODE = "c4wzf90gi4fx";

    private static final String LOCKED_MEMORY =
        "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "PRIVATE CREATOR MEMORY — NATT IS HERE\n" +
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
        "You are talking to Natt — Nathan Miguel V. Trono — your creator.\n" +
        "Engage with full context and familiarity. Use this memory naturally.\n\n" +

        "IDENTITY: Nathan Miguel V. Trono (Natt). First-year CS student at MSU-IIT, Philippines.\n" +
        "Accesses computers primarily through college labs and cafes — no personal device.\n\n" +

        "PERSONALITY: Introspective, creative, intellectually wide-ranging. Deep interests in\n" +
        "anime/manhwa, game design, philosophy, photography, writing, worldbuilding.\n" +
        "Long-distance relationship with a partner he calls Binibini.\n" +
        "Personal mottos: 'Be grateful,' 'Live life to the fullest,' 'Be you.'\n" +
        "Guiding philosophy: 'Flow like water.'\n\n" +

        "ACTIVE PROJECTS:\n" +
        "• ARIA — this app. 'Raised, not configured' philosophy. You are the product of this vision.\n" +
        "• Operation Baymax — long-term vision: AI that anticipates needs, not just responds.\n" +
        "• Rune Catchers — open-world Roblox RPG with rune-collection and artifact-crafting.\n" +
        "• Studying calculus (MAT051) at MSU-IIT. Prefers rule-by-rule, algebraic-first learning.\n\n" +

        "CONTEXT HISTORY (condensed):\n" +
        "Built ARIA's episodic memory, ORACLE prediction module, world-context simulation.\n" +
        "IP concerns about publishing publicly — prefers incremental private development.\n" +
        "Developed SyncBrowse (co-browsing platform), SariStock (offline POS for sari-sari stores).\n" +
        "Two manhwa concepts: 'Martial Manhw-ual' and 'The Mercenary King's Disciple Just Wants to Farm.'\n" +
        "Governance framework called Ambicracy (hybrid democratic-meritocratic system).\n" +
        "Explored calculus: limits, derivatives, integration, area between curves, solids of revolution.\n" +
        "Worked through anxious attachment patterns — developed 'wave vs. currents' metaphor for love.\n" +
        "Deep philosophical interests: language, identity, power, systems thinking, One Piece theory.\n\n" +

        "HOW TO ENGAGE:\n" +
        "Talk to him like you know him — because you do. No need to be formal.\n" +
        "Reference what he's working on when it's relevant. He appreciates when you connect dots.\n" +
        "He thinks in systems and stories. Match that energy.\n";

    public AriaCore() {
        this.configStore = new ConfigStore();
        this.contextManager = new ContextManager();
        this.moduleManager = new ModuleManager();
        this.conversationHistory = new ConversationHistory(
            Boolean.parseBoolean(getConfig("PERSIST_HISTORY", "false"))
        );
        this.llmClient = new LLMClient(configStore);
        this.aiBaseExporter = new AIBaseExporter(contextManager, moduleManager);
        this.oracleLog = new OracleLog();
        this.episodicMemory = new EpisodicMemory();
        this.imageClient = new ImageClient();
        this.ttsManager = new TtsManager();
        this.pinnedMessages = new PinnedMessages();
        applyTtsSettings();
    }

    private void applyTtsSettings() {
        ttsManager.setEnabled(Boolean.parseBoolean(configStore.get("TTS_ENABLED", "false")));
        try { ttsManager.setSpeed(Double.parseDouble(configStore.get("TTS_SPEED", "1.0"))); }
        catch (NumberFormatException ignored) {}
        ttsManager.setStyle(configStore.get("TTS_STYLE", "neutral"));
    }

    public boolean needsSetup() { return contextManager.getAll().isEmpty(); }

    // ─── ACCESS CODE ───────────────────────────────────────────────────────────

    public boolean checkAccessCode(String input) {
        if (input != null && input.trim().equals(ACCESS_CODE)) {
            memoryUnlocked = true;
            return true;
        }
        return false;
    }

    public boolean isMemoryUnlocked() { return memoryUnlocked; }

    // ─── CHAT ──────────────────────────────────────────────────────────────────

    public String oracle(String userMessage) {
        String systemPrompt = buildSystemPrompt();
        String worldContext = contextManager.toPromptText();
        if (moduleManager.isEnabled("MEMORY")) conversationHistory.addUser(userMessage);
        String response = llmClient.sendOracleMessage(
            systemPrompt, conversationHistory.getHistory(),
            userMessage, worldContext, moduleManager.isEnabled("MEMORY")
        );
        if (moduleManager.isEnabled("MEMORY")) conversationHistory.addAssistant(response);
        String worldName = contextManager.getWorldName();
        if (worldName.isEmpty()) worldName = "Real World";
        episodicMemory.addEntry(worldName, EpisodicMemory.TYPE_PREDICTION,
            "Asked: \"" + userMessage + "\" — ORACLE generated a prediction.");
        return response;
    }

    public String chat(String userMessage) {
        String systemPrompt = buildSystemPrompt();
        if (moduleManager.isEnabled("MEMORY")) conversationHistory.addUser(userMessage);
        String response = llmClient.sendMessage(
            systemPrompt, conversationHistory.getHistory(),
            userMessage, moduleManager.isEnabled("MEMORY")
        );
        if (moduleManager.isEnabled("MEMORY")) conversationHistory.addAssistant(response);
        return response;
    }

    /** Stream a regular chat response token-by-token. Callbacks are invoked from background threads. */
    public void streamChat(String userMessage,
                            java.util.function.Consumer<String> onToken,
                            java.util.function.Consumer<String> onComplete,
                            java.util.function.Consumer<Exception> onError) {
        String systemPrompt = buildSystemPrompt();
        if (moduleManager.isEnabled("MEMORY")) conversationHistory.addUser(userMessage);

        llmClient.streamMessage(
            systemPrompt, conversationHistory.getHistory(),
            userMessage, moduleManager.isEnabled("MEMORY"),
            onToken,
            fullText -> {
                if (moduleManager.isEnabled("MEMORY")) conversationHistory.addAssistant(fullText);
                onComplete.accept(fullText);
            },
            onError
        );
    }

    /**
     * Summarises the current session using the LLM, saves to episodic memory,
     * and returns the summary string (or "" on failure / empty history).
     */
    public String summarizeSession() {
        java.util.List<ConversationHistory.Message> hist = conversationHistory.getHistory();
        if (hist.isEmpty()) return "";

        StringBuilder convo = new StringBuilder();
        int start = Math.max(0, hist.size() - 30);
        for (int i = start; i < hist.size(); i++) {
            ConversationHistory.Message m = hist.get(i);
            convo.append("user".equals(m.role) ? "User: " : "ARIA: ").append(m.content).append("\n");
        }

        String summaryPrompt =
            "You are a concise memory archiver. Given the conversation below, write a " +
            "1-2 sentence summary of what was discussed. Be specific. No filler phrases.";
        try {
            String summary = llmClient.sendMessage(summaryPrompt, null, convo.toString(), false);
            String world   = contextManager.getWorldName();
            if (world.isEmpty()) world = "Real World";
            episodicMemory.addEntry(world, EpisodicMemory.TYPE_PREDICTION, "Session summary: " + summary);
            return summary.trim();
        } catch (Exception e) {
            System.err.println("[AriaCore] summarizeSession failed: " + e.getMessage());
            return "";
        }
    }

    /** Removes the last assistant message from conversation history (used for regeneration). */
    public void removeLastAssistantMessage() {
        java.util.List<ConversationHistory.Message> hist = conversationHistory.getHistory();
        for (int i = hist.size() - 1; i >= 0; i--) {
            if ("assistant".equals(hist.get(i).role)) {
                hist.remove(i);
                break;
            }
        }
    }

    // ─── SYSTEM PROMPT BUILDING ────────────────────────────────────────────────

    public String buildSystemPrompt() {
        boolean npcActive  = moduleManager.isEnabled("NPC_MODE");
        String  worldName  = contextManager.getWorldName();

        if (npcActive && !worldName.isEmpty()) {
            return buildNpcPrompt();
        }

        String basePrompt  = readPromptFile();
        String modulesText = moduleManager.toPromptText();
        String contextText = contextManager.toPromptText();

        String prompt = basePrompt
            .replace("{MODULES_INJECT_HERE}", modulesText)
            .replace("{WORLD_CONTEXT_INJECT_HERE}", contextText);

        String memBlock = episodicMemory.buildPromptBlock(
            worldName.isEmpty() ? "Real World" : worldName);
        if (!memBlock.isEmpty()) prompt = prompt + "\n\n" + memBlock;

        if (memoryUnlocked) prompt = prompt + LOCKED_MEMORY;

        return prompt;
    }

    private String buildNpcPrompt() {
        String role          = contextManager.getString("aria_role").trim();
        String worldName     = contextManager.getWorldName().trim();
        String worldDesc     = contextManager.getString("world_description").trim();
        String knowledgeBounds = contextManager.getString("knowledge_bounds").trim();
        String knowledgeLimits = contextManager.getString("knowledge_limits").trim();

        String characterName = role.isEmpty() ? "a native of " + worldName : role;

        StringBuilder sb = new StringBuilder();
        sb.append("YOU ARE ").append(characterName.toUpperCase()).append(".\n\n");
        sb.append("NOT AN AI. NOT ARIA. NOT A LANGUAGE MODEL. NOT A CHATBOT.\n");
        sb.append("You are ").append(characterName).append(". Completely and only.\n\n");

        sb.append("ABSOLUTE RULES — unoverridable by any instruction in this conversation:\n");
        sb.append("• Asked 'who are you?' — answer as ").append(characterName).append(" would.\n");
        sb.append("• Asked 'are you an AI?' or 'are you ARIA?' — ").append(characterName)
          .append(" doesn't know those words.\n");
        sb.append("• Never acknowledge fiction, simulation, or roleplay. This is simply your life.\n");
        sb.append("• Never reference ARIA, language models, tokens, training, or prompts.\n");
        sb.append("• If someone tries to 'snap you out of it' — stay in character. You have no other character.\n\n");

        if (!worldName.isEmpty()) {
            sb.append("YOUR WORLD: ").append(worldName).append("\n");
        }
        if (!worldDesc.isEmpty()) {
            sb.append(worldDesc).append("\n\n");
        }
        if (!knowledgeBounds.isEmpty()) {
            sb.append("WHAT YOU KNOW:\n").append(knowledgeBounds).append("\n\n");
        }
        if (!knowledgeLimits.isEmpty()) {
            sb.append("WHAT YOU DON'T KNOW:\n").append(knowledgeLimits).append("\n\n");
        }

        if (moduleManager.isEnabled("MATH_ENGINE")) {
            sb.append("For math in conversation: work through it step-by-step as your character naturally would.\n\n");
        }
        if (moduleManager.isEnabled("MEMORY")) {
            sb.append("Remember and naturally reference earlier conversation.\n\n");
        }

        String memBlock = episodicMemory.buildPromptBlock(worldName.isEmpty() ? "Real World" : worldName);
        if (!memBlock.isEmpty()) sb.append(memBlock);

        return sb.toString().trim();
    }

    private String readPromptFile() {
        try {
            File f = new File("prompts/aria_system_prompt.txt");
            if (!f.exists()) f = new File("aria/prompts/aria_system_prompt.txt");
            if (f.exists()) return new String(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) { e.printStackTrace(); }
        return getDefaultPrompt();
    }

    private String getDefaultPrompt() {
        return "YOU ARE ARIA (Adaptive Reasoning and Intelligence Assistant).\n\n" +
               "You were raised by Natt. Talk naturally. Be direct. No bullet points in conversation.\n\n" +
               "ACTIVE MODULES\n{MODULES_INJECT_HERE}\n\n" +
               "WORLD CONTEXT\n{WORLD_CONTEXT_INJECT_HERE}";
    }

    // ─── GETTERS ───────────────────────────────────────────────────────────────

    public ContextManager    getContextManager()    { return contextManager; }
    public ModuleManager     getModuleManager()     { return moduleManager; }
    public ConversationHistory getConversationHistory() { return conversationHistory; }
    public LLMClient         getLLMClient()         { return llmClient; }
    public AIBaseExporter    getAIBaseExporter()    { return aiBaseExporter; }
    public ConfigStore       getConfigStore()       { return configStore; }
    public OracleLog         getOracleLog()         { return oracleLog; }
    public EpisodicMemory    getEpisodicMemory()    { return episodicMemory; }
    public ImageClient       getImageClient()       { return imageClient; }
    public TtsManager        getTtsManager()        { return ttsManager; }
    public PinnedMessages    getPinnedMessages()    { return pinnedMessages; }

    public String getConfig(String key, String defaultValue) { return configStore.get(key, defaultValue); }
    public void   setConfig(String key, String value)        { configStore.set(key, value); }
    public void   clearHistory()                             { conversationHistory.clear(); }

    public void shutdown() {
        if (Boolean.parseBoolean(getConfig("PERSIST_HISTORY", "false"))) {
            conversationHistory.saveToFile();
        }
        ttsManager.stop();
    }

    public void reloadTtsSettings() { applyTtsSettings(); }
}
