package aria.ui;

import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public class HelpPanel {

    private final Stage owner;

    public HelpPanel(Stage owner) {
        this.owner = owner;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.NONE);
        dialog.initOwner(owner);
        dialog.setTitle("ARIA — Help & Features");
        dialog.setWidth(720);
        dialog.setHeight(600);
        dialog.setResizable(true);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            buildShortcutsTab(),
            buildFeaturesTab(),
            buildProvidersTab(),
            buildTipsTab()
        );

        Scene scene = new Scene(tabs);
        if (owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.setScene(scene);
        dialog.show();
    }

    // ─── SHORTCUTS ─────────────────────────────────────────────────────────────

    private Tab buildShortcutsTab() {
        Tab tab = new Tab("⌨  Shortcuts");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(24));
        grid.setHgap(28);
        grid.setVgap(10);

        grid.add(bold("Key"), 0, 0);
        grid.add(bold("Action"), 1, 0);
        Separator sep = new Separator();
        GridPane.setColumnSpan(sep, 2);
        grid.add(sep, 0, 1);

        String[][] shortcuts = {
            {"Enter",           "Send message"},
            {"Shift + Enter",   "New line in input"},
            {"/",               "Open slash-command menu"},
            {"Ctrl + K",        "Open Settings"},
            {"Ctrl + L",        "Smart clear (summarises first)"},
            {"Ctrl + /",        "Focus input field"},
            {"Escape",          "Stop TTS voice output"},
        };

        for (int i = 0; i < shortcuts.length; i++) {
            Label key = new Label(shortcuts[i][0]);
            key.setStyle(
                "-fx-font-family:'Consolas','Courier New',monospace;" +
                "-fx-background-color:-color-bg-inset;" +
                "-fx-padding:2 8 2 8;-fx-background-radius:4;" +
                "-fx-border-color:-color-border-default;-fx-border-radius:4;");
            grid.add(key, 0, i + 2);
            grid.add(new Label(shortcuts[i][1]), 1, i + 2);
        }

        ScrollPane sp = scroll(grid);
        tab.setContent(sp);
        return tab;
    }

    // ─── FEATURES ──────────────────────────────────────────────────────────────

    private Tab buildFeaturesTab() {
        Tab tab = new Tab("✦  Features");
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        String[][] features = {
            {"💬  Natural Chat",
             "Talk like a person. No stiff bullet-point replies. ARIA matches your energy — brief, deep, casual, or technical."},
            {"📐  Math Rendering",
             "Write $x^2$ for inline math or $$\\frac{a}{b}$$ for display. Equations render with KaTeX. Step-by-step solutions get styled step cards with equation numbers."},
            {"🌍  World Context + NPC Mode",
             "Define a world and ARIA becomes a fully immersive native of it — no mention of AI, no breaking character, no matter how hard you try."},
            {"🔮  ORACLE Predictions",
             "Activate ORACLE mode and ask about future events. ARIA grounds every prediction in live DuckDuckGo search, Wikipedia, and real-time weather."},
            {"🧠  Episodic Memory",
             "ARIA remembers past sessions and naturally references them. Smart clear summarises the conversation into memory before wiping."},
            {"🖼  Image Generation",
             "Say 'draw ...' or 'generate an image of ...' — no API key needed. Powered by Pollinations.AI. Save images with one click."},
            {"🔊  TTS Voice",
             "Enable Kokoro TTS in Settings. ARIA speaks responses aloud. Configurable speed and style. Press Escape to stop."},
            {"⚡  Streaming Responses",
             "Responses appear token-by-token in real time — no waiting for the full reply. Works with Groq, OpenAI, Gemini, Ollama, DeepSeek, Claude, and HuggingFace."},
            {"📌  Pinned Messages",
             "Pin any ARIA response with the 📌 button. Pinned messages persist across sessions and clears. Access them from the sidebar."},
            {"📁  File Drop",
             "Drag a .txt, .py, .java, .md, .json, or .csv file onto the chat. ARIA reads it and starts a conversation about it immediately."},
            {"👎  Regenerate",
             "Not happy with a response? Click 👎 to regenerate with a different angle. The response is replaced in place."},
            {"↓  Continue",
             "If ARIA's response looks cut off, a Continue button appears. Click it to seamlessly extend the response."},
            {"🔒  Creator Memory",
             "A private access code unlocks a hidden memory layer. Only the creator knows the code."},
        };

        for (String[] f : features) {
            vbox.getChildren().add(buildCard(f[0], f[1]));
        }
        tab.setContent(scroll(vbox));
        return tab;
    }

    // ─── PROVIDERS ─────────────────────────────────────────────────────────────

    private Tab buildProvidersTab() {
        Tab tab = new Tab("⚡  Providers");
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label intro = new Label("Choose your LLM in Settings → Provider. All free options work great to start:");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill:-color-fg-muted;-fx-font-size:12px;");
        vbox.getChildren().addAll(intro, new Separator());

        String[][] providers = {
            {"Groq",        "Free · Very fast",    "#3fb950",
             "Best for casual chat and quick answers. Llama 3.3 70B. Sign up at console.groq.com."},
            {"Gemini",      "Free · Balanced",     "#3fb950",
             "Strong reasoning and long context. gemini-1.5-flash is fast. Sign up at aistudio.google.com."},
            {"HuggingFace", "Free · Varies",       "#3fb950",
             "Open source models. Quality depends on model. Good for experimenting."},
            {"Ollama",      "Free · 100% Local",   "#3fb950",
             "Fully offline and private. Install Ollama from ollama.ai and pull any model."},
            {"DeepSeek",    "Paid · Strong math",  "#e3b341",
             "Exceptional at reasoning, math, and code. Very affordable API pricing."},
            {"Claude",      "Paid · Premium",      "#e3b341",
             "Best for nuance, long documents, and precise instruction-following."},
            {"OpenAI",      "Paid · Familiar",     "#e3b341",
             "GPT-4o-mini is a reliable all-rounder. Works everywhere."},
        };

        for (String[] p : providers) {
            HBox row = new HBox(12);
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setStyle("-fx-background-color:-color-bg-subtle;-fx-background-radius:6;");
            row.setAlignment(Pos.CENTER_LEFT);

            VBox nameBox = new VBox(2);
            nameBox.setMinWidth(120);
            Label name = new Label(p[0]);
            name.setStyle("-fx-font-weight:bold;-fx-font-size:13px;");
            Label badge = new Label(p[1]);
            badge.setStyle("-fx-font-size:10px;-fx-text-fill:" + p[2] + ";");
            nameBox.getChildren().addAll(name, badge);

            Label desc = new Label(p[3]);
            desc.setWrapText(true);
            desc.setStyle("-fx-font-size:12px;");
            HBox.setHgrow(desc, Priority.ALWAYS);

            row.getChildren().addAll(nameBox, desc);
            vbox.getChildren().add(row);
        }
        tab.setContent(scroll(vbox));
        return tab;
    }

    // ─── TIPS ──────────────────────────────────────────────────────────────────

    private Tab buildTipsTab() {
        Tab tab = new Tab("💡  Tips");
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        String[] tips = {
            "Type / in the input to open the slash-command menu. Navigate with arrows, pick with Enter.",
            "Drag any text, code, or data file onto the chat window — ARIA reads it instantly.",
            "For math, write $\\pi r^2$ inline or $$\\frac{a}{b} = c$$ on its own line for a numbered display equation.",
            "Enable World Context then NPC Mode to simulate any fictional setting — games, novels, D&D worlds.",
            "ORACLE mode uses live DuckDuckGo + Wikipedia + real-time weather to ground its predictions.",
            "Shift+Enter inserts a newline — great for multi-paragraph prompts or pasting code.",
            "The 📌 pin button saves any ARIA response permanently — it survives chat clears.",
            "The 👎 button on any response regenerates it with a different angle, replacing it in-place.",
            "If a response looks cut off, click 'Continue ↓' to seamlessly extend it.",
            "Ctrl+L now summarises the session into memory before clearing — you never lose context.",
            "The ☀/🌙 button in the top bar switches theme live without restarting.",
            "ARIA adapts to your tone. Short messages get short replies. Long ones get depth.",
            "Your unsent draft is saved automatically — it's restored the next time you open ARIA.",
        };

        for (String tip : tips) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.TOP_LEFT);
            Label arrow = new Label("›");
            arrow.setStyle("-fx-text-fill:-color-accent-fg;-fx-font-size:16px;-fx-font-weight:bold;");
            arrow.setMinWidth(14);
            Label text = new Label(tip);
            text.setWrapText(true);
            text.setStyle("-fx-font-size:13px;");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(arrow, text);
            vbox.getChildren().add(row);
        }
        tab.setContent(scroll(vbox));
        return tab;
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private VBox buildCard(String title, String desc) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle(
            "-fx-background-color:-color-bg-subtle;-fx-background-radius:6;" +
            "-fx-border-color:-color-border-default;-fx-border-radius:6;");
        Label t = new Label(title);
        t.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-font-size:12px;-fx-text-fill:-color-fg-muted;");
        card.getChildren().addAll(t, d);
        return card;
    }

    private Label bold(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight:bold;");
        return l;
    }

    private ScrollPane scroll(javafx.scene.Node node) {
        ScrollPane sp = new ScrollPane(node);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        return sp;
    }
}
