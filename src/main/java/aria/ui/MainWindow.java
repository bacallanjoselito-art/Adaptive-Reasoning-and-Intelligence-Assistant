package aria.ui;

import aria.core.*;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainWindow {

    private final AriaCore core;
    private final Stage    stage;

    private VBox       chatBox;
    private ScrollPane scrollPane;
    private TextArea   inputField;
    private Button     sendButton;
    private HBox       typingIndicator;
    private Label      thinkingLabel;
    private Label      worldContextLabel;
    private VBox       moduleStatusBox;
    private Label      modeBadge;
    private Label      providerBadge;
    private Button     themeToggleBtn;

    private String         lastSentUserMessage = "";
    private SlashCommandMenu slashMenu;
    private static final String DRAFT_KEY = "UNSENT_DRAFT";

    public MainWindow(AriaCore core, Stage stage) {
        this.core  = core;
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("main-window");
        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(buildChatArea());

        Scene scene = new Scene(root, 1100, 720);
        applyStyles(scene);
        installKeyboardShortcuts(scene);

        stage.setScene(scene);
        stage.setTitle("ARIA v" + ConfigStore.VERSION + " — AI Companion");
        stage.setMinWidth(800);
        stage.setMinHeight(550);
        stage.show();

        // Restore unsent draft
        String draft = core.getConfig(DRAFT_KEY, "");
        if (!draft.isEmpty()) {
            inputField.setText(draft);
            inputField.end();
        }

        Platform.runLater(() -> inputField.requestFocus());
    }

    // ─── KEYBOARD SHORTCUTS ────────────────────────────────────────────────────

    private void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown()) {
                switch (e.getCode()) {
                    case L     -> Platform.runLater(this::smartClear);
                    case K     -> Platform.runLater(this::openSettings);
                    case SLASH -> Platform.runLater(() -> { inputField.requestFocus(); inputField.end(); });
                    default    -> {}
                }
            }
            if (e.getCode() == KeyCode.ESCAPE) core.getTtsManager().stop();
        });
    }

    // ─── TOP BAR ───────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label ariaLabel = new Label("ARIA");
        ariaLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");
        Label dash = new Label("—");
        dash.setStyle("-fx-text-fill: -color-fg-muted;");

        String worldName = core.getContextManager().getWorldName();
        worldContextLabel = new Label(worldName.isEmpty() ? "Real World" : worldName);
        worldContextLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 13px;");

        modeBadge = new Label(); updateModeBadge();
        providerBadge = new Label(); updateProviderBadge();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Ctrl+K Settings · Ctrl+L Clear");
        hint.setStyle("-fx-text-fill: -color-fg-subtle; -fx-font-size: 10px;");

        themeToggleBtn = new Button(); updateThemeToggleBtn();
        themeToggleBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14px;");
        themeToggleBtn.setOnAction(e -> toggleTheme());

        Button exportBtn = new Button("⬇ Export");
        exportBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-cursor: hand;");
        exportBtn.setTooltip(new Tooltip("Export chat to Markdown (Ctrl+E)"));
        exportBtn.setOnAction(e -> exportChat());

        Button clearBtn = new Button("✕ Clear");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> smartClear());

        Button settingsBtn = new Button("⚙ Settings");
        settingsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-cursor: hand;");
        settingsBtn.setOnAction(e -> openSettings());

        Button helpBtn = new Button("?");
        helpBtn.setStyle(
            "-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 12px;" +
            "-fx-padding: 2 8 2 8; -fx-border-color: -color-border-default; -fx-border-radius: 10;" +
            "-fx-text-fill: -color-fg-muted;");
        helpBtn.setTooltip(new Tooltip("Help & Features guide"));
        helpBtn.setOnAction(e -> openHelp());

        bar.getChildren().addAll(
            ariaLabel, dash, worldContextLabel, modeBadge, providerBadge,
            spacer, hint, themeToggleBtn, exportBtn, clearBtn, settingsBtn, helpBtn);
        return bar;
    }

    private void updateProviderBadge() {
        if (providerBadge == null) return;
        String provider = core.getConfigStore().get("LLM_PROVIDER", "groq").toLowerCase();
        String display  = switch (provider) {
            case "huggingface" -> "HF";
            case "openai"      -> "GPT";
            case "anthropic"   -> "Claude";
            default            -> provider.substring(0, 1).toUpperCase() + provider.substring(1);
        };
        boolean isLocal = "ollama".equals(provider);
        boolean isFree  = isLocal || List.of("groq", "gemini", "huggingface").contains(provider);
        String  color   = isLocal ? "#3fb950" : isFree ? "#388bfd" : "#e3b341";
        providerBadge.setText(display);
        providerBadge.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 7 2 7;" +
            "-fx-background-radius: 8; -fx-text-fill: " + color + ";" +
            "-fx-background-color: " + color + "22;" +
            "-fx-border-color: " + color + "55; -fx-border-radius: 8;");
    }

    // ─── SIDEBAR ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 1 0 0;");

        VBox avatarArea = new VBox(8);
        avatarArea.setAlignment(Pos.CENTER);
        avatarArea.setPadding(new Insets(24, 12, 16, 12));
        avatarArea.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");
        Label avatar = new Label("◈");
        avatar.setStyle("-fx-font-size: 42px; -fx-text-fill: -color-accent-fg;");
        Label nameLabel = new Label("ARIA");
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label("AI Companion");
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        avatarArea.getChildren().addAll(avatar, nameLabel, subtitleLabel);

        VBox modulesSection = new VBox(0);
        modulesSection.setPadding(new Insets(12, 0, 0, 0));
        Label modulesHeader = new Label("MODULES");
        modulesHeader.setPadding(new Insets(0, 12, 6, 12));
        modulesHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        moduleStatusBox = new VBox(2);
        moduleStatusBox.setPadding(new Insets(0, 8, 0, 8));
        refreshModuleStatus();
        modulesSection.getChildren().addAll(modulesHeader, moduleStatusBox);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox bottomButtons = new VBox(6);
        bottomButtons.setPadding(new Insets(12));
        bottomButtons.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        for (String[] btn : new String[][]{
            {"⊞ Module Control", "modules"},
            {"⊕ World Context",  "world"},
            {"⇥ Export AI Base", "export"},
            {"⬡ Prediction Log", "oracle"},
            {"📌 Pinned",         "pinned"}
        }) {
            Button b = new Button(btn[0]);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setStyle("-fx-cursor: hand;");
            String action = btn[1];
            b.setOnAction(e -> {
                switch (action) {
                    case "modules" -> openModulePanel();
                    case "world"   -> openWorldContextEditor();
                    case "export"  -> openExportPanel();
                    case "oracle"  -> openOracleLog();
                    case "pinned"  -> openPinnedPanel();
                }
            });
            bottomButtons.getChildren().add(b);
        }

        boolean histEnabled = Boolean.parseBoolean(core.getConfigStore().get("PERSIST_HISTORY", "false"));
        Button histBtn = new Button(histEnabled ? "● History  On" : "○ History Off");
        histBtn.setMaxWidth(Double.MAX_VALUE);
        histBtn.setStyle("-fx-cursor: hand; -fx-text-fill: " + (histEnabled ? "-color-accent-fg" : "-color-fg-muted") + ";");
        histBtn.setTooltip(new Tooltip("Persist conversation history across sessions"));
        histBtn.setOnAction(e -> {
            boolean cur  = Boolean.parseBoolean(core.getConfigStore().get("PERSIST_HISTORY", "false"));
            boolean next = !cur;
            core.setConfig("PERSIST_HISTORY", String.valueOf(next));
            histBtn.setText(next ? "● History  On" : "○ History Off");
            histBtn.setStyle("-fx-cursor: hand; -fx-text-fill: " + (next ? "-color-accent-fg" : "-color-fg-muted") + ";");
        });
        bottomButtons.getChildren().add(histBtn);

        sidebar.getChildren().addAll(avatarArea, modulesSection, spacer, bottomButtons);
        return sidebar;
    }

    private void refreshModuleStatus() {
        moduleStatusBox.getChildren().clear();
        core.getModuleManager().getAll().forEach((name, enabled) -> {
            HBox row = new HBox(6);
            row.setPadding(new Insets(3, 6, 3, 6));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-cursor: hand; -fx-background-radius: 4;");
            Label indicator = new Label(enabled ? "●" : "○");
            indicator.setStyle("-fx-font-size: 9px; -fx-text-fill: " + (enabled ? "#3fb950" : "-color-fg-subtle") + ";");
            Label nameLabel = new Label(name.replace("_", " "));
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (enabled ? "-color-fg-default" : "-color-fg-muted") + ";");
            row.getChildren().addAll(indicator, nameLabel);
            row.setOnMouseEntered(e -> row.setStyle("-fx-cursor: hand; -fx-background-color: -color-bg-inset; -fx-background-radius: 4;"));
            row.setOnMouseExited (e -> row.setStyle("-fx-cursor: hand; -fx-background-radius: 4;"));
            row.setOnMouseClicked(e -> {
                core.getModuleManager().setEnabled(name, !core.getModuleManager().isEnabled(name));
                refreshModuleStatus();
                updateModeBadge();
            });
            moduleStatusBox.getChildren().add(row);
        });
    }

    // ─── CHAT AREA ─────────────────────────────────────────────────────────────

    private BorderPane buildChatArea() {
        BorderPane chatPane = new BorderPane();

        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(16));
        chatBox.setFillWidth(true);

        // File drop support
        chatBox.setOnDragOver(e -> {
            if (e.getGestureSource() != chatBox && e.getDragboard().hasFiles())
                e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        chatBox.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) { db.getFiles().forEach(this::handleFileDrop); ok = true; }
            e.setDropCompleted(ok);
            e.consume();
        });

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        typingIndicator = buildTypingIndicator();
        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);

        VBox chatContainer = new VBox();
        chatContainer.getChildren().addAll(scrollPane, typingIndicator);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        chatPane.setCenter(chatContainer);
        chatPane.setBottom(buildInputArea());

        addAriaMessage("hey. I'm here. what's up?");
        return chatPane;
    }

    private HBox buildTypingIndicator() {
        HBox ind = new HBox(8);
        ind.setAlignment(Pos.CENTER_LEFT);
        ind.setPadding(new Insets(8, 16, 8, 16));
        ind.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");
        Label avatarDot = new Label("◈");
        avatarDot.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-accent-fg;");
        Label d1 = new Label("●"), d2 = new Label("●"), d3 = new Label("●");
        for (Label d : new Label[]{d1, d2, d3}) d.setStyle("-fx-font-size: 8px; -fx-text-fill: -color-fg-muted;");
        animateDot(d1, 0); animateDot(d2, 200); animateDot(d3, 400);
        thinkingLabel = new Label();
        thinkingLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        ind.getChildren().addAll(avatarDot, d1, d2, d3, thinkingLabel);
        return ind;
    }

    private void animateDot(Label dot, int delayMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(600), dot);
        ft.setFromValue(0.3); ft.setToValue(1.0);
        ft.setCycleCount(Timeline.INDEFINITE); ft.setAutoReverse(true);
        ft.setDelay(Duration.millis(delayMs)); ft.play();
    }

    private HBox buildInputArea() {
        HBox area = new HBox(10);
        area.setPadding(new Insets(10, 16, 10, 16));
        area.setAlignment(Pos.BOTTOM_CENTER);
        area.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        inputField = new TextArea();
        inputField.setPromptText("Message ARIA...  (Enter to send · Shift+Enter for newline · / for commands)");
        inputField.setStyle("-fx-font-size: 14px;");
        inputField.setPrefRowCount(2);
        inputField.setWrapText(true);
        inputField.setMaxHeight(120);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) { e.consume(); sendMessage(); }
        });
        inputField.focusedProperty().addListener((obs, was, isFocused) -> {
            if (!isFocused) core.setConfig(DRAFT_KEY, inputField.getText());
        });

        sendButton = new Button("Send ↵");
        sendButton.setDefaultButton(false);
        sendButton.setStyle("-fx-cursor: hand;");
        sendButton.getStyleClass().add("accent");
        sendButton.setOnAction(e -> sendMessage());

        area.getChildren().addAll(inputField, sendButton);

        slashMenu = new SlashCommandMenu(inputField, List.of(
            new SlashCommandMenu.SlashCommand("clear",    "Smart clear — summarises before wiping",    null,      this::smartClear),
            new SlashCommandMenu.SlashCommand("export",   "Export chat to Markdown",                   null,      this::exportChat),
            new SlashCommandMenu.SlashCommand("pinned",   "View pinned messages",                      null,      this::openPinnedPanel),
            new SlashCommandMenu.SlashCommand("help",     "Open Help & Features guide",                null,      this::openHelp),
            new SlashCommandMenu.SlashCommand("settings", "Open Settings",                             null,      this::openSettings),
            new SlashCommandMenu.SlashCommand("theme",    "Toggle dark / light theme",                 null,      this::toggleTheme),
            new SlashCommandMenu.SlashCommand("world",    "Open World Context editor",                 null,      this::openWorldContextEditor),
            new SlashCommandMenu.SlashCommand("oracle",   "Open ORACLE prediction log",                null,      this::openOracleLog),
            new SlashCommandMenu.SlashCommand("draw",     "Generate an image — type: /draw [desc]",    "/draw ",  null),
            new SlashCommandMenu.SlashCommand("imagine",  "Generate an image — type: /imagine [desc]", "/imagine ", null)
        ));

        return area;
    }

    // ─── MESSAGE FLOW ──────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        inputField.clear();
        core.setConfig(DRAFT_KEY, "");

        if (core.checkAccessCode(text)) {
            addUserMessage(text.substring(0, 2) + "***");
            addAriaMessage("Memory unlocked. Welcome back, Natt.");
            return;
        }

        inputField.setDisable(true);
        sendButton.setDisable(true);
        lastSentUserMessage = text;
        addUserMessage(text);
        showTypingIndicator(true);

        boolean isOracleActive = core.getModuleManager().isOracleModeActive();
        ImageClient imageClient = core.getImageClient();

        // ── Image generation ─────────────────────────────────────────
        if (imageClient.isImageRequest(text)) {
            String prompt = imageClient.extractPrompt(text);
            Task<byte[]> imgTask = new Task<>() {
                @Override protected byte[] call() { return imageClient.fetchImage(prompt); }
            };
            imgTask.setOnSucceeded(e -> {
                showTypingIndicator(false);
                byte[] bytes = imgTask.getValue();
                if (bytes != null && bytes.length > 0) addImageMessage(bytes, prompt);
                else addAriaMessage("Image generation unavailable right now. Describe what you want and I'll write it instead.");
                resetInput();
            });
            imgTask.setOnFailed(e -> { showTypingIndicator(false); addAriaMessage("Image generation unavailable right now."); resetInput(); });
            new Thread(imgTask) {{ setDaemon(true); }}.start();
            return;
        }

        core.getTtsManager().stop();

        // ── ORACLE mode — full JSON needed, keep Task ─────────────────
        if (isOracleActive) {
            Task<String> task = new Task<>() {
                @Override protected String call() { return core.oracle(text); }
            };
            task.setOnSucceeded(e -> {
                showTypingIndicator(false);
                String response = task.getValue();
                String trimmed  = response.trim();
                if (isOracleJson(trimmed)) { addOracleCard(trimmed); speakOracleVerdict(trimmed); }
                else                       { addAriaMessage(response); speakText(response); }
                resetInput();
            });
            task.setOnFailed(e -> { showTypingIndicator(false); addAriaMessage("ORACLE failed — " + task.getException().getMessage()); resetInput(); });
            new Thread(task) {{ setDaemon(true); }}.start();
            return;
        }

        // ── Regular chat — streaming ──────────────────────────────────
        streamChat(text);
    }

    private void streamChat(String text) {
        final StringBuilder full = new StringBuilder();
        final boolean[] firstToken = {true};

        Label streamLabel = buildStreamingLabel();
        Label avatarLbl   = new Label("◈");
        avatarLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 6 0 0 0;");
        HBox streamWrapper = new HBox(8);
        streamWrapper.setAlignment(Pos.TOP_LEFT);
        streamWrapper.getChildren().addAll(avatarLbl, streamLabel);

        core.streamChat(text,
            token -> Platform.runLater(() -> {
                full.append(token);
                if (firstToken[0]) {
                    firstToken[0] = false;
                    showTypingIndicator(false);
                    chatBox.getChildren().add(streamWrapper);
                    fadeIn(streamWrapper, 120);
                }
                streamLabel.setText(full.toString());
                scrollToBottom();
            }),
            fullText -> Platform.runLater(() -> {
                chatBox.getChildren().remove(streamWrapper);
                addAriaMessage(fullText);
                speakText(fullText);
                resetInput();
            }),
            error -> Platform.runLater(() -> {
                if (!firstToken[0]) chatBox.getChildren().remove(streamWrapper);
                showTypingIndicator(false);
                addAriaMessage("Something went wrong: " + error.getMessage());
                resetInput();
            })
        );
    }

    private void regenMessage(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) return;
        core.removeLastAssistantMessage();
        inputField.setDisable(true);
        sendButton.setDisable(true);
        showTypingIndicator(true);

        final StringBuilder full = new StringBuilder();
        final boolean[] first    = {true};
        Label streamLabel = buildStreamingLabel();
        Label avatarLbl   = new Label("◈");
        avatarLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 6 0 0 0;");
        HBox streamWrapper = new HBox(8);
        streamWrapper.setAlignment(Pos.TOP_LEFT);
        streamWrapper.getChildren().addAll(avatarLbl, streamLabel);

        core.streamChat(userMessage,
            token -> Platform.runLater(() -> {
                full.append(token);
                if (first[0]) { first[0] = false; showTypingIndicator(false); chatBox.getChildren().add(streamWrapper); fadeIn(streamWrapper, 120); }
                streamLabel.setText(full.toString()); scrollToBottom();
            }),
            fullText -> Platform.runLater(() -> {
                chatBox.getChildren().remove(streamWrapper); addAriaMessage(fullText); speakText(fullText); resetInput();
            }),
            error -> Platform.runLater(() -> {
                if (!first[0]) chatBox.getChildren().remove(streamWrapper);
                showTypingIndicator(false); addAriaMessage("Regeneration failed: " + error.getMessage()); resetInput();
            })
        );
    }

    private void sendContinue() {
        inputField.setDisable(true);
        sendButton.setDisable(true);
        showTypingIndicator(true);

        final StringBuilder full = new StringBuilder();
        final boolean[] first    = {true};
        Label streamLabel = buildStreamingLabel();
        Label avatarLbl   = new Label("◈");
        avatarLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 6 0 0 0;");
        HBox streamWrapper = new HBox(8);
        streamWrapper.setAlignment(Pos.TOP_LEFT);
        streamWrapper.getChildren().addAll(avatarLbl, streamLabel);

        core.streamChat("Continue from where you left off.",
            token -> Platform.runLater(() -> {
                full.append(token);
                if (first[0]) { first[0] = false; showTypingIndicator(false); chatBox.getChildren().add(streamWrapper); fadeIn(streamWrapper, 120); }
                streamLabel.setText(full.toString()); scrollToBottom();
            }),
            fullText -> Platform.runLater(() -> {
                chatBox.getChildren().remove(streamWrapper); addAriaMessage(fullText); speakText(fullText); resetInput();
            }),
            error -> Platform.runLater(() -> {
                if (!first[0]) chatBox.getChildren().remove(streamWrapper);
                showTypingIndicator(false); addAriaMessage("Couldn't continue: " + error.getMessage()); resetInput();
            })
        );
    }

    private Label buildStreamingLabel() {
        Label l = new Label();
        l.setWrapText(true);
        l.setMaxWidth(520);
        l.setPadding(new Insets(9, 14, 9, 14));
        l.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4;" +
            "-fx-font-size: 14px; -fx-border-color: -color-border-default;" +
            "-fx-border-radius: 14 14 14 4; -fx-border-width: 1;");
        return l;
    }

    private void resetInput() {
        inputField.setDisable(false);
        sendButton.setDisable(false);
        Platform.runLater(() -> inputField.requestFocus());
    }

    // ─── BUBBLE RENDERERS ──────────────────────────────────────────────────────

    private void addUserMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(500);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setStyle("-fx-background-color: -color-accent-emphasis; -fx-text-fill: white;" +
            "-fx-background-radius: 14 14 4 14; -fx-font-size: 14px;");
        wrapper.getChildren().add(bubble);
        fadeIn(wrapper, 180);
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addAriaMessage(String text) {
        boolean isDark  = "dark".equalsIgnoreCase(core.getConfigStore().get("THEME", "dark"));
        boolean hasMath = MathRenderer.containsMath(text) ||
            (core.getModuleManager().isEnabled("MATH_ENGINE") && MathRenderer.containsStepByStep(text));
        boolean hasMd   = !hasMath && MarkdownRenderer.hasMarkdown(text);
        boolean isPlain = !hasMath && !hasMd;

        Label avatarLabel = new Label("◈");
        avatarLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 6 0 0 0;");

        javafx.scene.Node bubble;
        if (hasMath)    bubble = buildMathBubble(text);
        else if (hasMd) bubble = buildMarkdownBubble(text, isDark);
        else            bubble = buildPlainBubble();

        final String capturedUser = lastSentUserMessage;
        Button copyBtn   = buildCopyButton(text);
        Button thumbsBtn = buildThumbsDownButton(capturedUser);
        Button pinBtn    = buildPinButton(text);

        HBox wrapper = new HBox(6);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.getChildren().addAll(avatarLabel, bubble, copyBtn, thumbsBtn, pinBtn);

        wrapper.setOnMouseEntered(e -> { copyBtn.setOpacity(0.85); thumbsBtn.setOpacity(0.7); pinBtn.setOpacity(core.getPinnedMessages().isPinned(text) ? 1.0 : 0.7); });
        wrapper.setOnMouseExited (e -> { copyBtn.setOpacity(0.0);  thumbsBtn.setOpacity(0.0); pinBtn.setOpacity(core.getPinnedMessages().isPinned(text) ? 1.0 : 0.0); });

        chatBox.getChildren().add(wrapper);

        if (isPlain) animateText((Label) bubble, text);
        else         fadeIn(wrapper, 250);

        if (looksLikeTruncated(text)) chatBox.getChildren().add(buildContinueRow());

        scrollToBottom();
    }

    private Label buildPlainBubble() {
        Label b = new Label();
        b.setWrapText(true);
        b.setMaxWidth(520);
        b.setPadding(new Insets(9, 14, 9, 14));
        b.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4;" +
            "-fx-font-size: 14px; -fx-border-color: -color-border-default;" +
            "-fx-border-radius: 14 14 14 4; -fx-border-width: 1;");
        return b;
    }

    private javafx.scene.Node buildMarkdownBubble(String text, boolean isDark) {
        VBox md = MarkdownRenderer.render(text, 500, isDark);
        md.setPadding(new Insets(10, 14, 10, 14));
        md.setMaxWidth(540);
        md.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4;" +
            "-fx-border-color: -color-border-default; -fx-border-radius: 14 14 14 4; -fx-border-width: 1;");
        return md;
    }

    private javafx.scene.Node buildMathBubble(String text) {
        try {
            boolean isDark = "dark".equalsIgnoreCase(core.getConfigStore().get("THEME", "dark"));
            String html = MathRenderer.buildMathHtml(text, isDark);

            javafx.scene.web.WebView wv = new javafx.scene.web.WebView();
            wv.setMaxWidth(560); wv.setPrefWidth(520); wv.setPrefHeight(200);
            wv.setStyle("-fx-background-color: transparent;");
            wv.getEngine().setJavaScriptEnabled(true);
            wv.getEngine().getLoadWorker().stateProperty().addListener((obs, old, ns) -> {
                if (ns == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Platform.runLater(() -> {
                        try {
                            Object h = wv.getEngine().executeScript("document.body.scrollHeight");
                            if (h instanceof Number) wv.setPrefHeight(Math.min(((Number)h).doubleValue() + 24, 800));
                        } catch (Exception ignored) {}
                        scrollToBottom();
                    });
                }
            });
            wv.getEngine().loadContent(html, "text/html");

            VBox container = new VBox();
            container.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4;" +
                "-fx-border-color: -color-border-default; -fx-border-radius: 14 14 14 4; -fx-border-width: 1;");
            container.getChildren().add(wv);
            return container;
        } catch (Exception e) {
            return buildPlainBubble();
        }
    }

    // ─── ACTION BUTTONS ────────────────────────────────────────────────────────

    private Button buildCopyButton(String text) {
        Button btn = new Button("⎘");
        btn.setOpacity(0.0);
        btn.setStyle("-fx-background-color: -color-bg-inset; -fx-text-fill: -color-fg-muted;" +
            "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12px;" +
            "-fx-padding: 4 8 4 8; -fx-border-color: -color-border-default; -fx-border-radius: 6;");
        btn.setTooltip(new Tooltip("Copy message"));
        btn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent(); cc.putString(text);
            Clipboard.getSystemClipboard().setContent(cc);
            btn.setText("✓");
            new Timeline(new KeyFrame(Duration.millis(1500), ev -> btn.setText("⎘"))).play();
        });
        return btn;
    }

    private Button buildThumbsDownButton(String capturedUserMessage) {
        Button btn = new Button("👎");
        btn.setOpacity(0.0);
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 6 4 6;");
        btn.setTooltip(new Tooltip("Regenerate response"));
        btn.setOnAction(e -> {
            javafx.scene.Node wrapper = btn.getParent();
            if (wrapper != null) {
                int idx = chatBox.getChildren().indexOf(wrapper);
                chatBox.getChildren().remove(wrapper);
                if (idx < chatBox.getChildren().size()) {
                    javafx.scene.Node next = chatBox.getChildren().get(idx);
                    if (next instanceof HBox hb && hb.getStyleClass().contains("continue-row"))
                        chatBox.getChildren().remove(hb);
                }
            }
            regenMessage(capturedUserMessage);
        });
        return btn;
    }

    private Button buildPinButton(String text) {
        PinnedMessages pinned = core.getPinnedMessages();
        boolean alreadyPinned = pinned.isPinned(text);
        Button btn = new Button(alreadyPinned ? "📌" : "🔖");
        btn.setOpacity(alreadyPinned ? 1.0 : 0.0);
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 6 4 6;");
        btn.setTooltip(new Tooltip(alreadyPinned ? "Unpin message" : "Pin message"));
        btn.setOnAction(e -> {
            if (pinned.isPinned(text)) {
                pinned.unpin(text);
                btn.setText("🔖");
                btn.setTooltip(new Tooltip("Pin message"));
                btn.setOpacity(0.0);
            } else {
                pinned.pin(text);
                btn.setText("📌");
                btn.setTooltip(new Tooltip("Unpin message"));
                btn.setOpacity(1.0);
            }
        });
        return btn;
    }

    // ─── CONTINUE BUTTON ───────────────────────────────────────────────────────

    private boolean looksLikeTruncated(String text) {
        if (text == null || text.length() < 600) return false;
        String t = text.stripTrailing();
        if (t.isEmpty()) return false;
        char last = t.charAt(t.length() - 1);
        return last != '.' && last != '!' && last != '?' && last != '"'
            && last != ')' && last != ']' && last != ':' && last != '`' && last != '\'';
    }

    private HBox buildContinueRow() {
        Button btn = new Button("Continue ↓");
        btn.setStyle("-fx-background-color: -color-bg-inset; -fx-cursor: hand;" +
            "-fx-border-color: -color-border-default; -fx-border-radius: 6;" +
            "-fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 4 12 4 12;");
        HBox row = new HBox(btn);
        row.setPadding(new Insets(0, 0, 0, 42));
        row.getStyleClass().add("continue-row");
        btn.setOnAction(e -> { chatBox.getChildren().remove(row); sendContinue(); });
        return row;
    }

    // ─── FILE DROP ─────────────────────────────────────────────────────────────

    private static final java.util.Set<String> ALLOWED_EXTS = java.util.Set.of(
        ".txt", ".md", ".py", ".java", ".js", ".ts", ".json", ".csv",
        ".xml", ".yaml", ".yml", ".sh", ".sql", ".html", ".css", ".kt", ".rb", ".rs"
    );

    private void handleFileDrop(File file) {
        String name    = file.getName().toLowerCase();
        boolean ok     = ALLOWED_EXTS.stream().anyMatch(name::endsWith);
        if (!ok) { addAriaMessage("I can only read text files (.txt, .py, .java, .json, .csv, etc.). Binary files won't work."); return; }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.length() > 12_000) content = content.substring(0, 12_000) + "\n\n[... truncated to 12 000 chars ...]";
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";

            addUserMessage("📁 " + file.getName());

            String prompt = "I just dropped a file called `" + file.getName() + "` into the chat. Here's its content:\n\n```" + ext + "\n" + content + "\n```\n\nRead it and wait for my first question about it.";
            inputField.setDisable(true);
            sendButton.setDisable(true);
            lastSentUserMessage = prompt;
            showTypingIndicator(true);
            streamChat(prompt);
        } catch (Exception e) {
            addAriaMessage("Couldn't read `" + file.getName() + "`: " + e.getMessage());
        }
    }

    // ─── SMART CLEAR ───────────────────────────────────────────────────────────

    private void smartClear() {
        if (core.getConversationHistory().getHistory().isEmpty()) {
            chatBox.getChildren().clear();
            core.clearHistory();
            addAriaMessage("Chat cleared. What's up?");
            return;
        }
        addAriaMessage("Saving this session to memory...");
        inputField.setDisable(true);
        sendButton.setDisable(true);
        new Thread(() -> {
            String summary = core.summarizeSession();
            Platform.runLater(() -> {
                chatBox.getChildren().clear();
                core.clearHistory();
                inputField.setDisable(false);
                sendButton.setDisable(false);
                String msg = summary.isEmpty()
                    ? "All clear. Ready for a new conversation."
                    : "Session saved. Here's what we covered:\n\n" + summary + "\n\nReady.";
                addAriaMessage(msg);
            });
        }) {{ setDaemon(true); start(); }};
    }

    // ─── CHAT EXPORT ───────────────────────────────────────────────────────────

    private void exportChat() {
        try {
            File dir = new File("exports");
            if (!dir.exists()) dir = new File("aria/exports");
            dir.mkdirs();
            String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
            File dest   = new File(dir, "aria-chat-" + ts + ".md");
            var history = core.getConversationHistory().getHistory();
            if (history.isEmpty()) { addAriaMessage("Nothing to export — chat history is empty."); return; }

            StringBuilder sb = new StringBuilder();
            sb.append("# ARIA Chat Export\n**Date:** ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy — HH:mm")))
              .append("\n\n---\n\n");
            for (var msg : history) {
                sb.append("user".equals(msg.role) ? "**You:** " : "**ARIA:** ")
                  .append(msg.content).append("\n\n");
            }
            Files.writeString(dest.toPath(), sb.toString(), StandardCharsets.UTF_8);
            addAriaMessage("Chat exported to:\n`" + dest.getAbsolutePath() + "`");
        } catch (Exception e) {
            addAriaMessage("Export failed: " + e.getMessage());
        }
    }

    // ─── TTS ───────────────────────────────────────────────────────────────────

    private void speakText(String text) {
        TtsManager tts = core.getTtsManager();
        if (!tts.isEnabled()) return;
        new Thread(() -> tts.speak(text)) {{ setDaemon(true); }}.start();
    }

    private void speakOracleVerdict(String json) {
        TtsManager tts = core.getTtsManager();
        if (!tts.isEnabled()) return;
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String verdict   = obj.has("verdict")          ? obj.get("verdict").getAsString()          : "";
            String confLabel = obj.has("confidence_label") ? obj.get("confidence_label").getAsString() : "";
            new Thread(() -> tts.speakOracleVerdict(verdict, confLabel)) {{ setDaemon(true); }}.start();
        } catch (Exception ignored) {}
    }

    // ─── ANIMATION ─────────────────────────────────────────────────────────────

    private void animateText(Label label, String fullText) {
        if (fullText == null || fullText.isEmpty()) { label.setText(""); fadeIn(label, 200); return; }
        int len = fullText.length();
        int msPerChar = len <= 80 ? 22 : len <= 300 ? 12 : len <= 800 ? 7 : 4;
        int totalMs   = Math.min(2500, len * msPerChar);
        if (totalMs < 250 || len < 12) { label.setText(fullText); fadeIn(label, 200); return; }
        int actualMs = Math.max(4, totalMs / len);
        label.setText(""); label.setOpacity(1.0);
        Timeline tl = new Timeline();
        for (int i = 1; i <= len; i++) {
            final int idx = i;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis((long)actualMs * idx),
                ev -> label.setText(fullText.substring(0, idx))));
        }
        tl.setOnFinished(ev -> scrollToBottom());
        tl.play();
    }

    private void addImageMessage(byte[] imageBytes, String prompt) {
        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(imageBytes));
            ImageView iv = new ImageView(image);
            iv.setPreserveRatio(true); iv.setFitWidth(480); iv.setSmooth(true);

            Label attr = new Label("Generated via Pollinations.AI");
            attr.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px; -fx-padding: 4 0 0 0;");
            Label pl = new Label("\"" + prompt + "\"");
            pl.setStyle("-fx-text-fill: -color-fg-subtle; -fx-font-size: 11px; -fx-font-style: italic;");
            pl.setWrapText(true);
            Button saveBtn = new Button("⬇ Save Image");
            saveBtn.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");
            saveBtn.setOnAction(e -> saveImage(imageBytes, prompt));

            VBox bubble = new VBox(8, iv, pl, attr, saveBtn);
            bubble.setPadding(new Insets(10, 12, 10, 12));
            bubble.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4;" +
                "-fx-border-color: -color-border-default; -fx-border-radius: 14 14 14 4; -fx-border-width: 1;");
            bubble.setMaxWidth(520);

            Label avatarLabel = new Label("◈");
            avatarLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 6 0 0 0;");
            HBox wrapper = new HBox(10, avatarLabel, bubble);
            wrapper.setAlignment(Pos.TOP_LEFT);
            fadeIn(wrapper, 300);
            chatBox.getChildren().add(wrapper);
            scrollToBottom();
        } catch (Exception e) {
            addAriaMessage("Image received but couldn't be displayed. Try again.");
        }
    }

    private void saveImage(byte[] imageBytes, String prompt) {
        try {
            File dir = new File("exports/images");
            if (!dir.exists()) dir = new File("aria/exports/images");
            dir.mkdirs();
            File dest = new File(dir, "aria-image-" + System.currentTimeMillis() + ".png");
            Files.write(dest.toPath(), imageBytes);
            addAriaMessage("Saved to: " + dest.getAbsolutePath());
        } catch (Exception e) {
            addAriaMessage("Couldn't save image: " + e.getMessage());
        }
    }

    private boolean isOracleJson(String text) {
        return text.startsWith("{") && text.contains("\"verdict\"") && text.contains("\"confidence\"");
    }

    private void addOracleCard(String json) {
        VBox card = OraclePredictionCard.build(json, obj -> {
            try {
                String subject   = obj.has("subject")          ? obj.get("subject").getAsString()          : "Unknown";
                String verdict   = obj.has("verdict")          ? obj.get("verdict").getAsString()          : "";
                int confidence   = obj.has("confidence")       ? obj.get("confidence").getAsInt()          : 0;
                String confLabel = obj.has("confidence_label") ? obj.get("confidence_label").getAsString() : "";
                String timeframe = obj.has("timeframe")        ? obj.get("timeframe").getAsString()        : "";
                java.util.List<String> domains = new java.util.ArrayList<>();
                if (obj.has("domains"))
                    for (com.google.gson.JsonElement el : obj.getAsJsonArray("domains")) domains.add(el.getAsString());
                String worldCtx = core.getContextManager().getWorldName();
                if (worldCtx.isEmpty()) worldCtx = "Real World";
                String id = core.getOracleLog().addEntry(worldCtx, subject, verdict, confidence, confLabel, timeframe, domains);
                core.getEpisodicMemory().addEntry(worldCtx, EpisodicMemory.TYPE_PREDICTION,
                    "ORACLE predicted: \"" + subject + "\" — " + confidence + "% confidence. Verdict: " + verdict);
                System.out.println("ORACLE tracked: #" + id + " — " + subject);
            } catch (Exception ex) { System.err.println("Failed to track oracle prediction: " + ex.getMessage()); }
        });
        card.setMaxWidth(680);
        Label avatarLabel = new Label("⬡");
        avatarLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #6366f1; -fx-padding: 6 0 0 0;");
        HBox wrapper = new HBox(10, avatarLabel, card);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setPadding(new Insets(4, 0, 4, 0));
        fadeIn(wrapper, 300);
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    // ─── UTILITIES ─────────────────────────────────────────────────────────────

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void showTypingIndicator(boolean show) {
        Platform.runLater(() -> {
            typingIndicator.setVisible(show);
            typingIndicator.setManaged(show);
            if (show && thinkingLabel != null) {
                String provider = core.getConfigStore().get("LLM_PROVIDER", "groq");
                String display  = switch (provider.toLowerCase()) {
                    case "huggingface" -> "HuggingFace";
                    case "openai"      -> "GPT";
                    case "anthropic"   -> "Claude";
                    case "ollama"      -> "Ollama (local)";
                    default -> provider.substring(0, 1).toUpperCase() + provider.substring(1);
                };
                thinkingLabel.setText(display + " is thinking...");
            }
        });
    }

    private void fadeIn(javafx.scene.Node node, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void updateThemeToggleBtn() {
        if (themeToggleBtn == null) return;
        boolean isDark = "dark".equalsIgnoreCase(core.getConfigStore().get("THEME", "dark"));
        themeToggleBtn.setText(isDark ? "☀" : "🌙");
        themeToggleBtn.setTooltip(new Tooltip(isDark ? "Switch to Light theme" : "Switch to Dark theme"));
    }

    private void toggleTheme() {
        boolean isDark  = "dark".equalsIgnoreCase(core.getConfigStore().get("THEME", "dark"));
        String newTheme = isDark ? "light" : "dark";
        core.setConfig("THEME", newTheme);
        Application.setUserAgentStylesheet(newTheme.equals("light")
            ? new PrimerLight().getUserAgentStylesheet()
            : new PrimerDark().getUserAgentStylesheet());
        if (stage.getScene() != null) { stage.getScene().getStylesheets().clear(); applyStyles(stage.getScene()); }
        updateThemeToggleBtn();
    }

    private void updateModeBadge() {
        String label      = core.getModuleManager().getActiveModeLabel();
        boolean isDefault = label.equals("Default");
        modeBadge.setText("[ " + label + " ]");
        modeBadge.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 10; " +
            (isDefault ? "-fx-text-fill: -color-fg-muted; -fx-background-color: -color-bg-inset;"
                       : "-fx-text-fill: #3fb950; -fx-background-color: #1f6329;"));
    }

    public void refreshAfterContextChange() {
        String worldName = core.getContextManager().getWorldName();
        worldContextLabel.setText(worldName.isEmpty() ? "Real World" : worldName);
        refreshModuleStatus();
        updateModeBadge();
        updateProviderBadge();
    }

    private void applyStyles(Scene scene) {
        var res = getClass().getResource("/aria-styles.css");
        if (res != null) scene.getStylesheets().add(res.toExternalForm());
    }

    // ─── PINNED PANEL ──────────────────────────────────────────────────────────

    private void openPinnedPanel() {
        PinnedMessages pinned = core.getPinnedMessages();
        Stage dialog = new Stage();
        dialog.initModality(javafx.stage.Modality.NONE);
        dialog.initOwner(stage);
        dialog.setTitle("📌 Pinned Messages");
        dialog.setWidth(640);
        dialog.setHeight(500);

        VBox list = new VBox(10);
        list.setPadding(new Insets(16));

        var msgs = pinned.getAll();
        if (msgs.isEmpty()) {
            Label empty = new Label("No pinned messages yet.\nHover over any ARIA response and click 🔖 to pin it.");
            empty.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 13px;");
            empty.setWrapText(true);
            list.getChildren().add(empty);
        } else {
            for (int i = 0; i < msgs.size(); i++) {
                var msg      = msgs.get(i);
                final int idx = i;

                Label ts = new Label(msg.timestamp());
                ts.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");

                Label textLabel = new Label(msg.text().length() > 400 ? msg.text().substring(0, 400) + "…" : msg.text());
                textLabel.setWrapText(true);
                textLabel.setStyle("-fx-font-size: 13px;");

                Button unpin = new Button("Unpin");
                unpin.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");
                unpin.setOnAction(ev -> { pinned.unpinAt(idx); dialog.close(); openPinnedPanel(); });

                Button copy = new Button("⎘ Copy");
                copy.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");
                copy.setOnAction(ev -> {
                    ClipboardContent cc = new ClipboardContent(); cc.putString(msg.text());
                    Clipboard.getSystemClipboard().setContent(cc);
                    copy.setText("✓ Copied");
                    new Timeline(new KeyFrame(Duration.millis(1500), e -> copy.setText("⎘ Copy"))).play();
                });

                VBox card = new VBox(6, ts, textLabel, new HBox(8, copy, unpin));
                card.setPadding(new Insets(12));
                card.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8;" +
                    "-fx-border-color: -color-accent-fg; -fx-border-width: 0 0 0 3; -fx-border-radius: 8;");
                list.getChildren().add(card);
            }
        }

        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        Scene scene = new Scene(sp, 640, 500);
        if (stage.getScene() != null) scene.getStylesheets().addAll(stage.getScene().getStylesheets());
        dialog.setScene(scene);
        dialog.show();
    }

    // ─── PANEL OPENERS ─────────────────────────────────────────────────────────

    private void openHelp() { new HelpPanel(stage).show(); }

    private void openSettings() {
        new SettingsPanel(core, stage, this).show();
        stage.focusedProperty().addListener((obs, was, isFocused) -> { if (isFocused) updateProviderBadge(); });
    }

    private void openModulePanel()       { new ModulePanel(core, stage, this).show(); }
    private void openWorldContextEditor(){ new WorldContextEditor(core, stage, this).show(); }
    private void openExportPanel()       { new AIBaseExportPanel(core, stage, this).show(); }
    private void openOracleLog()         { new OracleLogPanel(core, stage).show(); }
}
