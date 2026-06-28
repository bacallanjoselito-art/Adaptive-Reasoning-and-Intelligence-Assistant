package aria.core;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.google.gson.*;
import okhttp3.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LLMClient {

    private final ConfigStore config;
    private final OkHttpClient httpClient;
    private final OkHttpClient quickClient;

    private static final String[] FALLBACK_ORDER = {
        "ollama", "gemini", "groq", "deepseek", "huggingface", "claude", "openai"
    };

    public LLMClient(ConfigStore config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.quickClient = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build();
    }

    // ─── PUBLIC API ────────────────────────────────────────────────────────────

    public String sendMessage(String systemPrompt,
                              List<ConversationHistory.Message> history,
                              String userMessage,
                              boolean includeHistory) {
        String preferred = config.get("LLM_PROVIDER", "groq").toLowerCase().trim();

        List<String> order = new ArrayList<>();
        order.add(preferred);
        for (String p : FALLBACK_ORDER) {
            if (!p.equals(preferred)) order.add(p);
        }

        List<String> tried = new ArrayList<>();
        for (String provider : order) {
            if (!hasKey(provider)) continue;
            try {
                return routeTo(provider, systemPrompt, history, userMessage, includeHistory);
            } catch (Exception e) {
                System.err.println("[LLM] " + provider + " failed: " + e.getMessage());
                tried.add(provider + " (" + e.getMessage() + ")");
            }
        }

        if (tried.isEmpty()) {
            return "No API keys configured. Open Settings and add a Groq key (free) or any other provider.";
        }
        return "All providers failed — " + String.join(", then ", tried)
            + ". Check your API keys in Settings.";
    }

    public String sendOracleMessage(String systemPrompt,
                                    List<ConversationHistory.Message> history,
                                    String userMessage,
                                    String worldContext,
                                    boolean includeHistory) {
        String ddgContext     = searchWeb(userMessage);
        String wikiContext    = fetchWikipedia(userMessage);
        String weatherContext = containsWeatherKeywords(userMessage)
            ? fetchWeather(config.get("HOME_LAT", "8.1500"), config.get("HOME_LON", "124.2333"))
            : "";

        StringBuilder enriched = new StringBuilder(systemPrompt);

        if (!ddgContext.isEmpty()) {
            enriched.append("\n\n[CURRENT CONTEXT — DuckDuckGo live search. ")
                    .append("Use it to ground this prediction in current reality.]:\n")
                    .append(ddgContext);
        }
        if (!wikiContext.isEmpty()) {
            enriched.append("\n\n").append(wikiContext);
        }
        if (!weatherContext.isEmpty()) {
            enriched.append("\n\n").append(weatherContext);
        }
        if (worldContext != null && !worldContext.trim().isEmpty()) {
            enriched.append("\n\n[ACTIVE WORLD CONTEXT — frame ALL predictions within this world's rules]:\n")
                    .append(worldContext);
        }

        return sendMessage(enriched.toString(), history, userMessage, includeHistory);
    }

    public JsonObject convertToContext(String description) throws Exception {
        String systemPrompt =
            "You extract world context fields from a plain text description.\n" +
            "Output ONLY raw JSON — no markdown, no backticks, no explanation — in exactly this format:\n" +
            "{\n" +
            "  \"world_name\": \"Short memorable name, 2-4 words\",\n" +
            "  \"world_description\": \"Full setting, lore, and atmosphere\",\n" +
            "  \"aria_role\": \"ARIA's role or identity in this world\",\n" +
            "  \"knowledge_bounds\": \"What ARIA knows in this world\",\n" +
            "  \"knowledge_limits\": \"What ARIA does NOT know or cannot do\"\n" +
            "}\nBe specific and detailed. If a field cannot be inferred, use an empty string.";

        String result = sendMessage(systemPrompt, null, description, false);
        result = result.trim()
            .replaceAll("(?s)^```json\\s*", "")
            .replaceAll("(?s)^```\\s*", "")
            .replaceAll("```$", "")
            .trim();
        if (!result.startsWith("{")) {
            throw new Exception("No API key configured or call failed: " + result);
        }
        return JsonParser.parseString(result).getAsJsonObject();
    }

    // ─── STREAMING PUBLIC API ──────────────────────────────────────────────────

    /**
     * Stream a chat response token-by-token.
     * onToken is called on the OkHttp I/O thread for each arriving piece of text.
     * onComplete is called (also off-FX thread) with the full assembled response when done.
     * onError is called if no tokens arrived and the call failed entirely.
     */
    public void streamMessage(String systemPrompt,
                              List<ConversationHistory.Message> history,
                              String userMessage, boolean includeHistory,
                              java.util.function.Consumer<String> onToken,
                              java.util.function.Consumer<String> onComplete,
                              java.util.function.Consumer<Exception> onError) {
        String provider = config.get("LLM_PROVIDER", "groq").toLowerCase().trim();
        StringBuilder full = new StringBuilder();
        java.util.function.Consumer<String> accumulate = tok -> { full.append(tok); onToken.accept(tok); };

        new Thread(() -> {
            try {
                switch (provider) {
                    case "groq" -> streamOpenAISSE(
                        "https://api.groq.com/openai/v1/chat/completions",
                        config.get("GROQ_API_KEY", ""),
                        config.get("GROQ_MODEL", "llama-3.3-70b-versatile"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "openai" -> streamOpenAISSE(
                        "https://api.openai.com/v1/chat/completions",
                        config.get("OPENAI_API_KEY", ""),
                        config.get("OPENAI_MODEL", "gpt-4o-mini"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "deepseek" -> streamOpenAISSE(
                        "https://api.deepseek.com/v1/chat/completions",
                        config.get("DEEPSEEK_API_KEY", ""),
                        config.get("DEEPSEEK_MODEL", "deepseek-chat"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "huggingface" -> streamOpenAISSE(
                        "https://api-inference.huggingface.co/models/"
                            + config.get("HF_MODEL", "meta-llama/Llama-3.2-3B-Instruct")
                            + "/v1/chat/completions",
                        config.get("HF_API_KEY", ""),
                        config.get("HF_MODEL", "meta-llama/Llama-3.2-3B-Instruct"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "gemini" -> streamGeminiSSE(
                        config.get("GEMINI_API_KEY", ""),
                        config.get("GEMINI_MODEL", "gemini-1.5-flash"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "ollama" -> streamOllamaNDJSON(
                        config.get("OLLAMA_MODEL", "llama3.2"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    case "claude" -> streamClaudeSSE(
                        config.get("ANTHROPIC_API_KEY", ""),
                        config.get("CLAUDE_MODEL", "claude-haiku-4-5-20251001"),
                        systemPrompt, history, userMessage, includeHistory, accumulate);
                    default -> throw new Exception("Unknown provider: " + provider);
                }
                onComplete.accept(full.toString().trim());
            } catch (Exception e) {
                if (full.length() > 0) {
                    onComplete.accept(full.toString().trim()); // partial is better than nothing
                } else {
                    onError.accept(e);
                }
            }
        }, "aria-stream") {{  setDaemon(true); start(); }};
    }

    // ─── SSE / NDJSON STREAMING PROVIDERS ──────────────────────────────────────

    private void streamOpenAISSE(String url, String apiKey, String model,
                                  String systemPrompt, List<ConversationHistory.Message> history,
                                  String userMessage, boolean includeHistory,
                                  java.util.function.Consumer<String> onToken) throws Exception {
        JsonObject body = buildOpenAIBody(model, systemPrompt, history, userMessage, includeHistory);
        body.addProperty("stream", true);

        RequestBody rb = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder().url(url)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null)
                throw new Exception("Stream error " + r.code());
            okio.BufferedSource src = r.body().source();
            while (!src.exhausted()) {
                String line = src.readUtf8Line();
                if (line == null || line.equals("data: [DONE]")) break;
                if (!line.startsWith("data: ")) continue;
                String json = line.substring(6).trim();
                if (json.isEmpty() || json.equals("[DONE]")) break;
                try {
                    JsonObject chunk = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray choices = chunk.getAsJsonArray("choices");
                    if (choices == null || choices.isEmpty()) continue;
                    JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                    if (delta != null && delta.has("content") && !delta.get("content").isJsonNull()) {
                        String tok = delta.get("content").getAsString();
                        if (!tok.isEmpty()) onToken.accept(tok);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void streamGeminiSSE(String apiKey, String model,
                                  String systemPrompt, List<ConversationHistory.Message> history,
                                  String userMessage, boolean includeHistory,
                                  java.util.function.Consumer<String> onToken) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + model + ":streamGenerateContent?alt=sse&key=" + apiKey;

        JsonObject body = buildGeminiBody(systemPrompt, history, userMessage, includeHistory);
        RequestBody rb = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder().url(url)
            .header("Content-Type", "application/json").post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null)
                throw new Exception("Gemini stream error " + r.code());
            okio.BufferedSource src = r.body().source();
            while (!src.exhausted()) {
                String line = src.readUtf8Line();
                if (line == null) break;
                if (!line.startsWith("data: ")) continue;
                String json = line.substring(6).trim();
                if (json.isEmpty()) continue;
                try {
                    JsonObject chunk = JsonParser.parseString(json).getAsJsonObject();
                    String text = chunk.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
                    if (!text.isEmpty()) onToken.accept(text);
                } catch (Exception ignored) {}
            }
        }
    }

    private void streamOllamaNDJSON(String model, String systemPrompt,
                                    List<ConversationHistory.Message> history,
                                    String userMessage, boolean includeHistory,
                                    java.util.function.Consumer<String> onToken) throws Exception {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject(); sysMsg.addProperty("role","system"); sysMsg.addProperty("content", systemPrompt); messages.add(sysMsg);
        if (includeHistory && history != null) {
            for (ConversationHistory.Message m : history) {
                JsonObject jm = new JsonObject(); jm.addProperty("role", m.role); jm.addProperty("content", m.content); messages.add(jm);
            }
        }
        JsonObject userMsg = new JsonObject(); userMsg.addProperty("role","user"); userMsg.addProperty("content", userMessage); messages.add(userMsg);
        JsonObject body = new JsonObject(); body.addProperty("model", model); body.add("messages", messages); body.addProperty("stream", true);

        RequestBody rb = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder().url("http://localhost:11434/api/chat")
            .header("Content-Type","application/json").post(rb).build();

        OkHttpClient ollamaClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build();

        try (Response r = ollamaClient.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("Ollama error " + r.code());
            okio.BufferedSource src = r.body().source();
            while (!src.exhausted()) {
                String line = src.readUtf8Line();
                if (line == null || line.isEmpty()) continue;
                try {
                    JsonObject chunk = JsonParser.parseString(line).getAsJsonObject();
                    if (chunk.has("message")) {
                        String content = chunk.getAsJsonObject("message").get("content").getAsString();
                        if (!content.isEmpty()) onToken.accept(content);
                    }
                    if (chunk.has("done") && chunk.get("done").getAsBoolean()) break;
                } catch (Exception ignored) {}
            }
        }
    }

    private void streamClaudeSSE(String apiKey, String model,
                                  String systemPrompt, List<ConversationHistory.Message> history,
                                  String userMessage, boolean includeHistory,
                                  java.util.function.Consumer<String> onToken) throws Exception {
        JsonArray messages = buildClaudeJsonMessages(history, userMessage, includeHistory);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", intConfig("MAX_TOKENS", 1000));
        body.addProperty("system", systemPrompt);
        body.add("messages", messages);
        body.addProperty("stream", true);

        RequestBody rb = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) throw new Exception("Claude stream error " + r.code());
            okio.BufferedSource src = r.body().source();
            while (!src.exhausted()) {
                String line = src.readUtf8Line();
                if (line == null) break;
                if (!line.startsWith("data: ")) continue;
                String json = line.substring(6).trim();
                if (json.isEmpty()) continue;
                try {
                    JsonObject chunk = JsonParser.parseString(json).getAsJsonObject();
                    String type = chunk.has("type") ? chunk.get("type").getAsString() : "";
                    if ("content_block_delta".equals(type)) {
                        JsonObject delta = chunk.getAsJsonObject("delta");
                        if ("text_delta".equals(delta.get("type").getAsString())) {
                            String text = delta.get("text").getAsString();
                            if (!text.isEmpty()) onToken.accept(text);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ─── WEB SEARCH ────────────────────────────────────────────────────────────

    public String searchWeb(String subject) {
        try {
            if (subject == null || subject.isBlank()) return "";
            String encoded = URLEncoder.encode(subject, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encoded
                + "&format=json&no_redirect=1&no_html=1&skip_disambig=1";
            Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "ARIA-Companion/1.1")
                .get()
                .build();
            try (Response r = httpClient.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return "";
                String body = r.body().string();
                if (body.isEmpty()) return "";
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                StringBuilder sb = new StringBuilder();
                String abstractText = json.has("AbstractText") ? json.get("AbstractText").getAsString() : "";
                if (!abstractText.isEmpty()) {
                    String src = json.has("AbstractSource") ? json.get("AbstractSource").getAsString() : "Reference";
                    sb.append("[").append(src).append("]: ").append(abstractText).append("\n\n");
                }
                String answer = json.has("Answer") ? json.get("Answer").getAsString() : "";
                if (!answer.isEmpty()) sb.append("[Instant Answer]: ").append(answer).append("\n\n");
                if (json.has("RelatedTopics")) {
                    JsonArray topics = json.getAsJsonArray("RelatedTopics");
                    int count = 0;
                    for (int i = 0; i < topics.size() && count < 4; i++) {
                        JsonElement el = topics.get(i);
                        if (el.isJsonObject()) {
                            JsonObject t = el.getAsJsonObject();
                            if (t.has("Text")) {
                                String text = t.get("Text").getAsString().trim();
                                if (!text.isEmpty()) { sb.append("• ").append(text).append("\n"); count++; }
                            }
                        }
                    }
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            System.err.println("[LLM] Web search failed (non-fatal): " + e.getMessage());
            return "";
        }
    }

    // ─── ORACLE GROUNDING — WIKIPEDIA ──────────────────────────────────────────

    private String fetchWikipedia(String query) {
        try {
            if (query == null || query.isBlank()) return "";
            String topic = extractMainTopic(query);
            if (topic.isBlank()) return "";
            String encoded = URLEncoder.encode(topic, StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded;
            Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "ARIA-Companion/1.1 (aria-project)")
                .get()
                .build();
            try (Response r = httpClient.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return "";
                String body = r.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (!json.has("extract") || !json.has("title")) return "";
                String title   = json.get("title").getAsString();
                String extract = json.get("extract").getAsString();
                if (extract.isBlank()) return "";
                String firstThree = firstNSentences(extract, 3);
                return "[WIKIPEDIA CONTEXT — " + title + "]:\n  " + firstThree;
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String extractMainTopic(String query) {
        if (query == null) return "";
        String q = query.toLowerCase()
            .replaceAll("\\b(will|what|who|when|where|how|why|is|are|do|does|can|should|would|could)\\b", "")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        String[] words = q.split(" ");
        if (words.length == 0) return "";
        if (words.length == 1) return words[0];
        return String.join(" ", Arrays.copyOfRange(words, 0, Math.min(words.length, 3)));
    }

    private String firstNSentences(String text, int n) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, sentences.length); i++) {
            if (i > 0) sb.append(" ");
            sb.append(sentences[i]);
        }
        return sb.toString();
    }

    // ─── ORACLE GROUNDING — OPEN-METEO WEATHER ─────────────────────────────────

    private boolean containsWeatherKeywords(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String kw : new String[]{
            "rain", "weather", "storm", "flood", "temperature", "hot", "cold",
            "typhoon", "sunny", "cloudy", "wind", "humid", "climate", "forecast"
        }) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String fetchWeather(String lat, String lon) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&hourly=temperature_2m,precipitation_probability,weathercode,windspeed_10m"
                + "&forecast_days=3"
                + "&timezone=auto";
            Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "ARIA-Companion/1.1")
                .get()
                .build();
            try (Response r = httpClient.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return "";
                String body = r.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (!json.has("hourly")) return "";
                JsonObject hourly = json.getAsJsonObject("hourly");
                JsonArray temps    = hourly.getAsJsonArray("temperature_2m");
                JsonArray precip   = hourly.getAsJsonArray("precipitation_probability");

                if (temps == null || temps.size() < 48) return "";

                double temp0  = temps.get(12).getAsDouble();
                int    prec0  = precip.get(12).getAsInt();
                double temp1  = temps.get(36).getAsDouble();
                int    prec1  = precip.get(36).getAsInt();
                double temp2  = temps.isJsonNull() || temps.size() < 60 ? temp1 : temps.get(56).getAsDouble();
                int    prec2  = precip.isJsonNull() || precip.size() < 60 ? prec1 : precip.get(56).getAsInt();

                return "[WEATHER CONTEXT — Open-Meteo live forecast for your location]:\n" +
                    "  Today:       " + String.format("%.1f", temp0) + "°C, precipitation probability " + prec0  + "%\n" +
                    "  Tomorrow:    " + String.format("%.1f", temp1) + "°C, precipitation probability " + prec1  + "%\n" +
                    "  Day after:   " + String.format("%.1f", temp2) + "°C, precipitation probability " + prec2  + "%";
            }
        } catch (Exception e) {
            System.err.println("[LLM] Weather fetch failed (non-fatal): " + e.getMessage());
            return "";
        }
    }

    // ─── ROUTING ───────────────────────────────────────────────────────────────

    private String routeTo(String provider, String systemPrompt,
                           List<ConversationHistory.Message> history,
                           String userMessage, boolean includeHistory) throws Exception {
        return switch (provider) {
            case "claude"      -> sendClaude(systemPrompt, history, userMessage, includeHistory);
            case "groq"        -> sendGroq(systemPrompt, history, userMessage, includeHistory);
            case "openai"      -> sendOpenAI(systemPrompt, history, userMessage, includeHistory);
            case "gemini"      -> sendGemini(systemPrompt, history, userMessage, includeHistory);
            case "huggingface" -> sendHuggingFace(systemPrompt, history, userMessage, includeHistory);
            case "ollama"      -> sendOllama(systemPrompt, history, userMessage, includeHistory);
            case "deepseek"    -> sendDeepSeek(systemPrompt, history, userMessage, includeHistory);
            default -> throw new Exception("Unknown provider: " + provider);
        };
    }

    private boolean hasKey(String provider) {
        return switch (provider) {
            case "claude"      -> isValidKey(config.get("ANTHROPIC_API_KEY", ""));
            case "groq"        -> isValidKey(config.get("GROQ_API_KEY", ""));
            case "openai"      -> isValidKey(config.get("OPENAI_API_KEY", ""));
            case "gemini"      -> isValidKey(config.get("GEMINI_API_KEY", ""));
            case "huggingface" -> isValidKey(config.get("HF_API_KEY", ""));
            case "deepseek"    -> isValidKey(config.get("DEEPSEEK_API_KEY", ""));
            case "ollama"      -> isOllamaRunning();
            default -> false;
        };
    }

    private boolean isValidKey(String key) {
        return key != null && !key.isEmpty() && !key.equals("your_key_here");
    }

    private boolean isOllamaRunning() {
        try {
            Request req = new Request.Builder().url("http://localhost:11434").get().build();
            try (Response r = quickClient.newCall(req).execute()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ─── PROVIDERS ─────────────────────────────────────────────────────────────

    private String sendClaude(String systemPrompt, List<ConversationHistory.Message> history,
                               String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("ANTHROPIC_API_KEY", "");
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        String model = config.get("CLAUDE_MODEL", "claude-haiku-4-5-20251001");
        int maxTokens = intConfig("MAX_TOKENS", 1000);
        List<MessageParam> messages = buildAnthropicMessages(history, userMessage, includeHistory);
        Message response = client.messages().create(
            MessageCreateParams.builder().model(model).maxTokens(maxTokens)
                .system(systemPrompt).messages(messages).build()
        );
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> sb.append(t.text()));
        }
        return sb.toString().trim();
    }

    private String sendGroq(String systemPrompt, List<ConversationHistory.Message> history,
                             String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("GROQ_API_KEY", "");
        String model  = config.get("GROQ_MODEL", "llama-3.3-70b-versatile");
        return sendOpenAICompatible("https://api.groq.com/openai/v1/chat/completions",
            apiKey, model, systemPrompt, history, userMessage, includeHistory);
    }

    private String sendOpenAI(String systemPrompt, List<ConversationHistory.Message> history,
                               String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("OPENAI_API_KEY", "");
        String model  = config.get("OPENAI_MODEL", "gpt-4o-mini");
        return sendOpenAICompatible("https://api.openai.com/v1/chat/completions",
            apiKey, model, systemPrompt, history, userMessage, includeHistory);
    }

    private String sendDeepSeek(String systemPrompt, List<ConversationHistory.Message> history,
                                 String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("DEEPSEEK_API_KEY", "");
        String model  = config.get("DEEPSEEK_MODEL", "deepseek-chat");
        return sendOpenAICompatible("https://api.deepseek.com/v1/chat/completions",
            apiKey, model, systemPrompt, history, userMessage, includeHistory);
    }

    private String sendGemini(String systemPrompt, List<ConversationHistory.Message> history,
                               String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("GEMINI_API_KEY", "");
        String model  = config.get("GEMINI_MODEL", "gemini-1.5-flash");
        String url    = "https://generativelanguage.googleapis.com/v1beta/models/"
            + model + ":generateContent?key=" + apiKey;

        JsonObject body = buildGeminiBody(systemPrompt, history, userMessage, includeHistory);
        RequestBody rb  = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req     = new Request.Builder().url(url)
            .header("Content-Type", "application/json").post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            String bodyText = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("Gemini error " + r.code() + ": " + bodyText);
            JsonObject resp = JsonParser.parseString(bodyText).getAsJsonObject();
            return resp.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString().trim();
        }
    }

    /** Shared Gemini request body builder (used by both sync and streaming). */
    private JsonObject buildGeminiBody(String systemPrompt,
                                        List<ConversationHistory.Message> history,
                                        String userMessage, boolean includeHistory) {
        JsonObject body = new JsonObject();

        JsonObject sysInstr = new JsonObject();
        JsonArray  sysParts = new JsonArray();
        JsonObject sysPart  = new JsonObject();
        sysPart.addProperty("text", systemPrompt);
        sysParts.add(sysPart);
        sysInstr.add("parts", sysParts);
        body.add("systemInstruction", sysInstr);

        JsonArray contents = new JsonArray();
        if (includeHistory && history != null) {
            for (ConversationHistory.Message msg : history) {
                JsonObject content = new JsonObject();
                content.addProperty("role", "user".equals(msg.role) ? "user" : "model");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject(); part.addProperty("text", msg.content);
                parts.add(part); content.add("parts", parts); contents.add(content);
            }
        }
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userPart = new JsonObject(); userPart.addProperty("text", userMessage);
        userParts.add(userPart); userContent.add("parts", userParts); contents.add(userContent);
        body.add("contents", contents);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", intConfig("MAX_TOKENS", 1000));
        body.add("generationConfig", genConfig);
        return body;
    }

    private String sendHuggingFace(String systemPrompt, List<ConversationHistory.Message> history,
                                    String userMessage, boolean includeHistory) throws Exception {
        String apiKey = config.get("HF_API_KEY", "");
        String model  = config.get("HF_MODEL", "meta-llama/Llama-3.2-3B-Instruct");
        String url    = "https://api-inference.huggingface.co/models/" + model + "/v1/chat/completions";

        String bodyText = sendOpenAICompatibleRaw(url, apiKey, model,
            systemPrompt, history, userMessage, includeHistory);

        if (bodyText != null) return bodyText;
        return "";
    }

    private String sendHuggingFaceWithRetry(String url, String apiKey, String model,
                                             String systemPrompt,
                                             List<ConversationHistory.Message> history,
                                             String userMessage, boolean includeHistory) throws Exception {
        JsonObject body    = buildOpenAIBody(model, systemPrompt, history, userMessage, includeHistory);
        RequestBody rb     = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req        = new Request.Builder().url(url)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            String respBody = r.body() != null ? r.body().string() : "";
            if (r.code() == 503) {
                try {
                    JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
                    double wait = json.has("estimated_time") ? json.get("estimated_time").getAsDouble() : 20;
                    Thread.sleep((long)(Math.min(wait, 60) * 1000));
                } catch (Exception ignored) {
                    Thread.sleep(20_000);
                }
                try (Response r2 = httpClient.newCall(req).execute()) {
                    String body2 = r2.body() != null ? r2.body().string() : "";
                    if (!r2.isSuccessful()) throw new Exception("HuggingFace error " + r2.code() + ": " + body2);
                    return extractOpenAIResponse(body2);
                }
            }
            if (!r.isSuccessful()) throw new Exception("HuggingFace error " + r.code() + ": " + respBody);
            return extractOpenAIResponse(respBody);
        }
    }

    private String sendOllama(String systemPrompt, List<ConversationHistory.Message> history,
                               String userMessage, boolean includeHistory) throws Exception {
        String model = config.get("OLLAMA_MODEL", "llama3.2");
        String url   = "http://localhost:11434/api/chat";

        JsonArray messages = new JsonArray();
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        if (includeHistory && history != null) {
            for (ConversationHistory.Message msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.role);
                m.addProperty("content", msg.content);
                messages.add(m);
            }
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("stream", false);

        RequestBody rb = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req    = new Request.Builder().url(url)
            .header("Content-Type", "application/json").post(rb).build();

        OkHttpClient ollamaClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

        try (Response r = ollamaClient.newCall(req).execute()) {
            String respBody = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("Ollama error " + r.code() + ": " + respBody);
            JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
            return json.getAsJsonObject("message").get("content").getAsString().trim();
        }
    }

    // ─── SHARED HELPERS ────────────────────────────────────────────────────────

    private String sendOpenAICompatible(String url, String apiKey, String model,
                                         String systemPrompt, List<ConversationHistory.Message> history,
                                         String userMessage, boolean includeHistory) throws Exception {
        JsonObject body = buildOpenAIBody(model, systemPrompt, history, userMessage, includeHistory);
        RequestBody rb  = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request req     = new Request.Builder().url(url)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(rb).build();

        try (Response r = httpClient.newCall(req).execute()) {
            String bodyText = r.body() != null ? r.body().string() : "";
            if (!r.isSuccessful()) throw new Exception("API error " + r.code() + ": " + bodyText);
            if (bodyText.isEmpty()) throw new Exception("Empty response from API");
            return extractOpenAIResponse(bodyText);
        }
    }

    private String sendOpenAICompatibleRaw(String url, String apiKey, String model,
                                            String systemPrompt, List<ConversationHistory.Message> history,
                                            String userMessage, boolean includeHistory) throws Exception {
        return sendHuggingFaceWithRetry(url, apiKey, model,
            systemPrompt, history, userMessage, includeHistory);
    }

    private JsonObject buildOpenAIBody(String model, String systemPrompt,
                                        List<ConversationHistory.Message> history,
                                        String userMessage, boolean includeHistory) {
        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        if (includeHistory && history != null) {
            for (ConversationHistory.Message msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.role);
                m.addProperty("content", msg.content);
                messages.add(m);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("max_tokens", intConfig("MAX_TOKENS", 1000));
        return body;
    }

    private String extractOpenAIResponse(String bodyText) throws Exception {
        JsonObject json = JsonParser.parseString(bodyText).getAsJsonObject();
        return json.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString().trim();
    }

    /** JSON message array for Claude raw-HTTP streaming (not the SDK type). */
    private JsonArray buildClaudeJsonMessages(List<ConversationHistory.Message> history,
                                               String userMessage, boolean includeHistory) {
        JsonArray messages = new JsonArray();
        if (includeHistory && history != null) {
            for (ConversationHistory.Message msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.role);
                m.addProperty("content", msg.content);
                messages.add(m);
            }
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        return messages;
    }

    private List<MessageParam> buildAnthropicMessages(List<ConversationHistory.Message> history,
                                                       String userMessage, boolean includeHistory) {
        List<MessageParam> messages = new ArrayList<>();
        if (includeHistory && history != null) {
            for (ConversationHistory.Message msg : history) {
                if ("user".equals(msg.role)) {
                    messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(msg.content).build());
                } else if ("assistant".equals(msg.role)) {
                    messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(msg.content).build());
                }
            }
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(userMessage).build());
        return messages;
    }

    private int intConfig(String key, int defaultVal) {
        try { return Integer.parseInt(config.get(key, String.valueOf(defaultVal))); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
