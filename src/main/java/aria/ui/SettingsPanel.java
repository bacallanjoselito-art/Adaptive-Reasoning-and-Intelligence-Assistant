package aria.ui;

import aria.core.*;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public class SettingsPanel {

    private final AriaCore core;
    private final Stage owner;
    private final MainWindow mainWindow;

    public SettingsPanel(AriaCore core, Stage owner, MainWindow mainWindow) {
        this.core = core;
        this.owner = owner;
        this.mainWindow = mainWindow;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Settings");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");
        Label title = new Label("Settings");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label subtitle = new Label("Configure providers, models, voice, and appearance.");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        header.getChildren().addAll(title, subtitle);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("floating");

        tabs.getTabs().addAll(
            buildProvidersTab(),
            buildModelsTab(),
            buildOracleTab(),
            buildVoiceTab(),
            buildAppearanceTab()
        );

        HBox footer = buildFooter(dialog, tabs);

        root.getChildren().addAll(header, tabs, footer);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Scene scene = new Scene(root, 560, 700);
        dialog.setScene(scene);
        dialog.setMinWidth(480);
        dialog.setMinHeight(550);
        dialog.show();
    }

    // ─── TAB: PROVIDERS ────────────────────────────────────────────────────────

    private Tab buildProvidersTab() {
        Tab tab = new Tab("Providers");
        ConfigStore config = core.getConfigStore();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        form.getChildren().add(sectionHeader("API Keys"));
        PasswordField groqKey    = maskedField("gsk_...", config.get("GROQ_API_KEY", ""));
        PasswordField geminiKey  = maskedField("AIza...", config.get("GEMINI_API_KEY", ""));
        PasswordField hfKey      = maskedField("hf_...",  config.get("HF_API_KEY", ""));
        PasswordField deepseekKey= maskedField("sk-...",  config.get("DEEPSEEK_API_KEY", ""));
        PasswordField claudeKey  = maskedField("sk-ant-...", config.get("ANTHROPIC_API_KEY", ""));
        PasswordField openaiKey  = maskedField("sk-...",  config.get("OPENAI_API_KEY", ""));

        form.getChildren().addAll(
            labeledNode("Groq API Key",       groqKey,
                "Free tier · console.groq.com"),
            labeledNode("Gemini API Key",      geminiKey,
                "Free tier · aistudio.google.com"),
            labeledNode("HuggingFace API Key", hfKey,
                "Free tier · huggingface.co/settings/tokens"),
            labeledNode("DeepSeek API Key",    deepseekKey,
                "Low cost · platform.deepseek.com"),
            labeledNode("Anthropic API Key",   claudeKey,
                "Paid · console.anthropic.com"),
            labeledNode("OpenAI API Key",      openaiKey,
                "Paid · platform.openai.com")
        );

        form.getChildren().add(sectionHeader("Active Provider"));

        ToggleGroup providerGroup = new ToggleGroup();
        String provider = config.get("LLM_PROVIDER", "groq");

        VBox providerBox = new VBox(4);
        providerBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8; -fx-border-color: -color-border-default; -fx-border-radius: 8;");
        providerBox.setPadding(new Insets(4));

        RadioButton[] radios = {
            providerRadio("Groq",             "groq",        "Free tier · Fast · Recommended default",             providerGroup, provider),
            providerRadio("Google Gemini",     "gemini",      "Free tier · Google · Strong general reasoning",      providerGroup, provider),
            providerRadio("HuggingFace",       "huggingface", "Free tier · Open source model variety",              providerGroup, provider),
            providerRadio("Local (Ollama)",    "ollama",      "Free forever · Offline · Requires Ollama installed", providerGroup, provider),
            providerRadio("DeepSeek",          "deepseek",    "Low cost · Strong structured reasoning",             providerGroup, provider),
            providerRadio("Claude (Anthropic)","claude",      "Paid · Highest quality responses",                   providerGroup, provider),
            providerRadio("OpenAI",            "openai",      "Paid · General purpose",                             providerGroup, provider),
        };
        for (RadioButton r : radios) providerBox.getChildren().add(r);
        form.getChildren().add(providerBox);

        scroll.setContent(form);
        tab.setContent(scroll);

        tab.setUserData(new Runnable() {
            public void run() {
                config.set("GROQ_API_KEY",      groqKey.getText().trim());
                config.set("GEMINI_API_KEY",     geminiKey.getText().trim());
                config.set("HF_API_KEY",         hfKey.getText().trim());
                config.set("DEEPSEEK_API_KEY",   deepseekKey.getText().trim());
                config.set("ANTHROPIC_API_KEY",  claudeKey.getText().trim());
                config.set("OPENAI_API_KEY",     openaiKey.getText().trim());
                Toggle sel = providerGroup.getSelectedToggle();
                if (sel != null) config.set("LLM_PROVIDER", (String) sel.getUserData());
            }
        });

        return tab;
    }

    // ─── TAB: MODELS ───────────────────────────────────────────────────────────

    private Tab buildModelsTab() {
        Tab tab = new Tab("Models");
        ConfigStore config = core.getConfigStore();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        boolean oracleOn  = core.getModuleManager().isEnabled("ORACLE_MODE");
        boolean scholarOn = core.getModuleManager().isEnabled("SCHOLAR_MODE");
        boolean mathOn    = core.getModuleManager().isEnabled("MATH_ENGINE");

        if (oracleOn) {
            Label hint = moduleHint("⬡ ORACLE active — deepseek-reasoner or deepseek-r1 recommended for prediction accuracy");
            form.getChildren().add(hint);
        }
        if (scholarOn) {
            Label hint = moduleHint("◎ SCHOLAR active — larger models recommended for deep analysis");
            form.getChildren().add(hint);
        }
        if (mathOn && !oracleOn && !scholarOn) {
            Label hint = moduleHint("∑ MATH ENGINE active — any model works, math rendering is handled locally by KaTeX");
            form.getChildren().add(hint);
        }

        form.getChildren().add(sectionHeader("Groq Model"));
        ComboBox<String> groqModel = combo(config.get("GROQ_MODEL", "llama-3.3-70b-versatile"),
            "llama-3.3-70b-versatile", "llama-3.1-8b-instant",
            "llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768", "gemma2-9b-it");
        form.getChildren().add(groqModel);

        form.getChildren().add(sectionHeader("Gemini Model"));
        ComboBox<String> geminiModel = combo(config.get("GEMINI_MODEL", "gemini-1.5-flash"),
            "gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash");
        form.getChildren().add(geminiModel);

        form.getChildren().add(sectionHeader("HuggingFace Model"));
        ComboBox<String> hfModel = combo(config.get("HF_MODEL", "meta-llama/Llama-3.2-3B-Instruct"),
            "meta-llama/Llama-3.2-3B-Instruct",
            "mistralai/Mistral-7B-Instruct-v0.3",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B");
        form.getChildren().add(hfModel);

        form.getChildren().add(sectionHeader("Ollama Model (Local)"));
        ComboBox<String> ollamaModel = combo(config.get("OLLAMA_MODEL", "llama3.2"),
            "llama3.2", "llama3.1", "mistral", "deepseek-r1", "gemma2");
        form.getChildren().add(ollamaModel);

        form.getChildren().add(sectionHeader("DeepSeek Model"));
        ComboBox<String> deepseekModel = combo(config.get("DEEPSEEK_MODEL", "deepseek-chat"),
            "deepseek-chat", "deepseek-reasoner");
        form.getChildren().add(deepseekModel);

        form.getChildren().add(sectionHeader("Claude Model"));
        ComboBox<String> claudeModel = combo(config.get("CLAUDE_MODEL", "claude-haiku-4-5-20251001"),
            "claude-haiku-4-5-20251001", "claude-sonnet-4-5-20251101",
            "claude-3-haiku-20240307", "claude-3-sonnet-20240229", "claude-3-5-sonnet-20241022");
        form.getChildren().add(claudeModel);

        form.getChildren().add(sectionHeader("OpenAI Model"));
        ComboBox<String> openaiModel = combo(config.get("OPENAI_MODEL", "gpt-4o-mini"),
            "gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo");
        form.getChildren().add(openaiModel);

        form.getChildren().add(sectionHeader("Max Tokens"));
        Slider tokensSlider = new Slider(100, 4000, intConfig("MAX_TOKENS", 1000));
        tokensSlider.setMajorTickUnit(500);
        tokensSlider.setShowTickMarks(true);
        tokensSlider.setShowTickLabels(true);
        Label tokensValue = new Label((int) tokensSlider.getValue() + " tokens");
        tokensValue.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        tokensSlider.valueProperty().addListener((obs, o, n) ->
            tokensValue.setText((int) n.doubleValue() + " tokens"));
        form.getChildren().addAll(tokensSlider, tokensValue);

        scroll.setContent(form);
        tab.setContent(scroll);

        tab.setUserData(new Runnable() {
            public void run() {
                config.set("GROQ_MODEL",     groqModel.getValue());
                config.set("GEMINI_MODEL",   geminiModel.getValue());
                config.set("HF_MODEL",       hfModel.getValue());
                config.set("OLLAMA_MODEL",   ollamaModel.getValue());
                config.set("DEEPSEEK_MODEL", deepseekModel.getValue());
                config.set("CLAUDE_MODEL",   claudeModel.getValue());
                config.set("OPENAI_MODEL",   openaiModel.getValue());
                config.set("MAX_TOKENS",     String.valueOf((int) tokensSlider.getValue()));
            }
        });

        return tab;
    }

    // ─── TAB: ORACLE ───────────────────────────────────────────────────────────

    private Tab buildOracleTab() {
        Tab tab = new Tab("Oracle");
        ConfigStore config = core.getConfigStore();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        form.getChildren().add(sectionHeader("Weather Grounding (Open-Meteo)"));
        Label weatherDesc = new Label(
            "ORACLE injects live 3-day weather forecasts when you ask about weather-related topics. " +
            "No API key required. Set your location coordinates below.");
        weatherDesc.setWrapText(true);
        weatherDesc.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        form.getChildren().add(weatherDesc);

        TextField latField = new TextField(config.get("HOME_LAT", "8.1500"));
        latField.setPromptText("Latitude (e.g. 8.1500)");
        TextField lonField = new TextField(config.get("HOME_LON", "124.2333"));
        lonField.setPromptText("Longitude (e.g. 124.2333)");

        Label locNote = new Label("Default: MSU-IIT, Iligan City, Philippines (8.1500, 124.2333)");
        locNote.setStyle("-fx-text-fill: -color-fg-subtle; -fx-font-size: 11px;");

        form.getChildren().addAll(
            labeledNode("Latitude",  latField, null),
            labeledNode("Longitude", lonField, null),
            locNote
        );

        form.getChildren().add(sectionHeader("Wikipedia Grounding"));
        Label wikiDesc = new Label(
            "ORACLE automatically fetches a Wikipedia summary for the main topic in your query " +
            "and injects it as context. Always-on. No configuration needed.");
        wikiDesc.setWrapText(true);
        wikiDesc.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        form.getChildren().add(wikiDesc);

        form.getChildren().add(sectionHeader("Context Injection Order"));
        Label orderDesc = new Label(
            "1. DuckDuckGo live search\n" +
            "2. Wikipedia topic summary\n" +
            "3. Open-Meteo weather (if weather keywords detected)\n" +
            "4. Active World Context (if a world is active)");
        orderDesc.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px; -fx-font-family: monospace;");
        form.getChildren().add(orderDesc);

        scroll.setContent(form);
        tab.setContent(scroll);

        tab.setUserData(new Runnable() {
            public void run() {
                String lat = latField.getText().trim();
                String lon = lonField.getText().trim();
                if (!lat.isEmpty()) config.set("HOME_LAT", lat);
                if (!lon.isEmpty()) config.set("HOME_LON", lon);
            }
        });

        return tab;
    }

    // ─── TAB: VOICE ────────────────────────────────────────────────────────────

    private Tab buildVoiceTab() {
        Tab tab = new Tab("Voice");
        ConfigStore config = core.getConfigStore();
        TtsManager tts = core.getTtsManager();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        form.getChildren().add(sectionHeader("Kokoro Text-to-Speech"));

        boolean available = tts.isAvailable();
        String statusText = available
            ? "● Kokoro detected — voice is available"
            : "○ Kokoro not detected — install with: pip install kokoro sounddevice";
        Label statusLabel = new Label(statusText);
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " +
            (available ? "#3fb950" : "-color-fg-muted") + ";");
        form.getChildren().add(statusLabel);

        CheckBox voiceToggle = new CheckBox("Enable voice output");
        voiceToggle.setSelected(Boolean.parseBoolean(config.get("TTS_ENABLED", "false")));
        voiceToggle.setDisable(!available);
        if (!available) {
            Tooltip tooltip = new Tooltip("Install Kokoro to enable voice: pip install kokoro sounddevice");
            Tooltip.install(voiceToggle, tooltip);
        }
        form.getChildren().add(voiceToggle);

        form.getChildren().add(sectionHeader("Voice Speed"));
        double savedSpeed = doubleConfig("TTS_SPEED", 1.0);
        Slider speedSlider = new Slider(0.5, 2.0, savedSpeed);
        speedSlider.setMajorTickUnit(0.25);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setDisable(!available);
        Label speedValue = new Label(String.format("%.1fx", speedSlider.getValue()));
        speedValue.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        speedSlider.valueProperty().addListener((obs, o, n) ->
            speedValue.setText(String.format("%.1fx", n.doubleValue())));
        form.getChildren().addAll(speedSlider, speedValue);

        form.getChildren().add(sectionHeader("Voice Style"));
        ComboBox<String> styleBox = combo(config.get("TTS_STYLE", "neutral"),
            "neutral", "warm", "calm");
        styleBox.setDisable(!available);
        form.getChildren().add(styleBox);

        form.getChildren().add(sectionHeader("Test Voice"));
        Button testBtn = new Button("▶  Speak test phrase");
        testBtn.setDisable(!available);
        testBtn.setStyle("-fx-cursor: hand;");
        testBtn.setOnAction(e -> {
            tts.setSpeed(speedSlider.getValue());
            tts.setStyle(styleBox.getValue());
            tts.setEnabled(true);
            tts.speak("Hello. I am ARIA. Your adaptive reasoning and intelligence assistant.");
        });
        form.getChildren().add(testBtn);

        if (!available) {
            Label installLabel = new Label(
                "To enable voice:\n" +
                "  pip install kokoro sounddevice\n\n" +
                "After installing, restart ARIA to detect Kokoro.");
            installLabel.setWrapText(true);
            installLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px; " +
                "-fx-background-color: -color-bg-subtle; -fx-background-radius: 6; -fx-padding: 10;");
            form.getChildren().add(installLabel);
        }

        scroll.setContent(form);
        tab.setContent(scroll);

        tab.setUserData(new Runnable() {
            public void run() {
                config.set("TTS_ENABLED", String.valueOf(voiceToggle.isSelected()));
                config.set("TTS_SPEED",   String.format("%.1f", speedSlider.getValue()));
                config.set("TTS_STYLE",   styleBox.getValue());
                core.reloadTtsSettings();
            }
        });

        return tab;
    }

    // ─── TAB: APPEARANCE ───────────────────────────────────────────────────────

    private Tab buildAppearanceTab() {
        Tab tab = new Tab("Appearance");
        ConfigStore config = core.getConfigStore();

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        form.getChildren().add(sectionHeader("Theme"));
        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton darkTheme  = new RadioButton("Primer Dark");
        RadioButton lightTheme = new RadioButton("Primer Light");
        darkTheme.setToggleGroup(themeGroup);
        lightTheme.setToggleGroup(themeGroup);
        String currentTheme = config.get("THEME", "dark");
        darkTheme.setSelected("dark".equalsIgnoreCase(currentTheme));
        lightTheme.setSelected("light".equalsIgnoreCase(currentTheme));
        form.getChildren().add(new HBox(16, darkTheme, lightTheme));

        form.getChildren().add(sectionHeader("Author Name"));
        TextField authorField = new TextField(config.get("AUTHOR_NAME", "Natt"));
        authorField.setPromptText("Your name (used in AI Base exports)");
        form.getChildren().add(labeledNode("Author Name", authorField, null));

        form.getChildren().add(sectionHeader("Conversation History"));
        CheckBox persistHistory = new CheckBox("Persist history between sessions");
        persistHistory.setSelected(Boolean.parseBoolean(config.get("PERSIST_HISTORY", "false")));
        form.getChildren().add(persistHistory);

        scroll.setContent(form);
        tab.setContent(scroll);

        tab.setUserData(new Runnable() {
            public void run() {
                String newTheme = darkTheme.isSelected() ? "dark" : "light";
                config.set("THEME", newTheme);
                if ("dark".equals(newTheme)) {
                    Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                } else {
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                }
                config.set("AUTHOR_NAME",     authorField.getText().trim());
                config.set("PERSIST_HISTORY", String.valueOf(persistHistory.isSelected()));
            }
        });

        return tab;
    }

    // ─── FOOTER ────────────────────────────────────────────────────────────────

    private HBox buildFooter(Stage dialog, TabPane tabs) {
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 20, 12, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("accent");
        saveBtn.setStyle("-fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            for (Tab tab : tabs.getTabs()) {
                if (tab.getUserData() instanceof Runnable) {
                    ((Runnable) tab.getUserData()).run();
                }
            }
            core.getConfigStore().saveToFile();
            dialog.close();
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        return footer;
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private RadioButton providerRadio(String label, String value,
                                       String subtext, ToggleGroup group, String current) {
        VBox box = new VBox(1);
        box.setPadding(new Insets(6, 10, 6, 10));
        box.setStyle("-fx-cursor: hand;");

        RadioButton radio = new RadioButton(label);
        radio.setToggleGroup(group);
        radio.setUserData(value);
        radio.setSelected(value.equals(current));

        Label sub = new Label(subtext);
        sub.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px; -fx-padding: 0 0 0 20;");

        box.getChildren().addAll(radio, sub);
        box.setOnMouseClicked(e -> radio.setSelected(true));

        RadioButton wrapper = new RadioButton();
        wrapper.setVisible(false);
        wrapper.setManaged(false);
        radio.setGraphic(null);

        return radio;
    }

    private Label moduleHint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #e3a008; -fx-font-size: 12px; " +
            "-fx-background-color: #92400e22; -fx-background-radius: 6; " +
            "-fx-border-color: #92400e44; -fx-border-radius: 6; -fx-padding: 8 12 8 12;");
        return l;
    }

    private Label sectionHeader(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted; " +
            "-fx-letter-spacing: 1px; -fx-padding: 8 0 0 0;");
        return l;
    }

    private PasswordField maskedField(String prompt, String value) {
        PasswordField f = new PasswordField();
        f.setPromptText(prompt);
        f.setText(value);
        return f;
    }

    private ComboBox<String> combo(String current, String... items) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(items);
        box.setValue(current);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox labeledNode(String label, javafx.scene.Node node, String hint) {
        VBox box = new VBox(4);
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        box.getChildren().addAll(l, node);
        if (hint != null) {
            Label h = new Label(hint);
            h.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 11px;");
            box.getChildren().add(h);
        }
        return box;
    }

    private int intConfig(String key, int def) {
        try { return Integer.parseInt(core.getConfigStore().get(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private double doubleConfig(String key, double def) {
        try { return Double.parseDouble(core.getConfigStore().get(key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }
}
