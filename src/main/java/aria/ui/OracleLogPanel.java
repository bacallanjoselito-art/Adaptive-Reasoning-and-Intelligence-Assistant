package aria.ui;

import aria.core.AriaCore;
import aria.core.EpisodicMemory;
import aria.core.OracleLog;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;

import java.util.List;
import java.util.Map;

public class OracleLogPanel {

    private final AriaCore core;
    private final Stage owner;
    private Stage stage;

    private VBox openList;
    private VBox resolvedList;
    private VBox statsBox;

    public OracleLogPanel(AriaCore core, Stage owner) {
        this.core  = core;
        this.owner = owner;
    }

    public void show() {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("ORACLE · Prediction Log");
        stage.setMinWidth(720);
        stage.setMinHeight(560);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0d1117;");

        root.setTop(buildHeader());
        root.setCenter(buildContent());

        Scene scene = new Scene(root, 760, 600);
        stage.setScene(scene);
        stage.show();
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setStyle("-fx-background-color: #0d1117; -fx-border-color: #1e1b4b; -fx-border-width: 0 0 1 0;");

        Label icon = new Label("⬡");
        icon.setStyle("-fx-font-size: 18px; -fx-text-fill: #6366f1;");

        Label title = new Label("ORACLE PREDICTION LOG");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0; -fx-letter-spacing: 1px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        OracleLog log = core.getOracleLog();
        int[] acc = log.getOverallAccuracy();
        String accText = acc[1] == 0 ? "no resolved predictions yet"
            : acc[0] + "/" + acc[1] + " correct  (" + Math.round((acc[0] * 100.0) / acc[1]) + "%)";

        Label accuracy = new Label("Overall: " + accText);
        accuracy.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        header.getChildren().addAll(icon, title, spacer, accuracy);
        return header;
    }

    private TabPane buildContent() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: #0d1117;");

        Tab openTab = new Tab("Open  (" + core.getOracleLog().openCount() + ")");
        openList = new VBox(8);
        openList.setPadding(new Insets(16));
        ScrollPane openScroll = new ScrollPane(openList);
        openScroll.setFitToWidth(true);
        openScroll.setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");
        openTab.setContent(openScroll);
        populateOpen();

        Tab resolvedTab = new Tab("Resolved  (" + core.getOracleLog().resolvedCount() + ")");
        resolvedList = new VBox(8);
        resolvedList.setPadding(new Insets(16));
        ScrollPane resolvedScroll = new ScrollPane(resolvedList);
        resolvedScroll.setFitToWidth(true);
        resolvedScroll.setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");
        resolvedTab.setContent(resolvedScroll);
        populateResolved();

        Tab statsTab = new Tab("Accuracy Stats");
        statsBox = new VBox(12);
        statsBox.setPadding(new Insets(16));
        ScrollPane statsScroll = new ScrollPane(statsBox);
        statsScroll.setFitToWidth(true);
        statsScroll.setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");
        statsTab.setContent(statsScroll);
        populateStats();

        Tab memoryTab = new Tab("Episodic Memory");
        VBox memoryBox = new VBox(8);
        memoryBox.setPadding(new Insets(16));
        ScrollPane memScroll = new ScrollPane(memoryBox);
        memScroll.setFitToWidth(true);
        memScroll.setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");
        memoryTab.setContent(memScroll);
        populateMemory(memoryBox);

        tabs.getTabs().addAll(openTab, resolvedTab, statsTab, memoryTab);
        return tabs;
    }

    private void populateOpen() {
        openList.getChildren().clear();
        List<OracleLog.Entry> entries = core.getOracleLog().getOpen();
        if (entries.isEmpty()) {
            openList.getChildren().add(emptyState("No open predictions.\nMake a prediction in ORACLE mode to start tracking."));
            return;
        }
        for (OracleLog.Entry e : entries) {
            openList.getChildren().add(buildOpenCard(e));
        }
    }

    private void populateResolved() {
        resolvedList.getChildren().clear();
        List<OracleLog.Entry> entries = core.getOracleLog().getResolved();
        if (entries.isEmpty()) {
            resolvedList.getChildren().add(emptyState("No resolved predictions yet.\nUse the 'Mark Outcome' button on open predictions."));
            return;
        }
        for (OracleLog.Entry e : entries) {
            resolvedList.getChildren().add(buildResolvedCard(e));
        }
    }

    private void populateStats() {
        statsBox.getChildren().clear();

        OracleLog log = core.getOracleLog();
        int[] overall = log.getOverallAccuracy();

        VBox overallCard = card();
        Label overallTitle = sectionLabel("OVERALL ACCURACY");
        if (overall[1] == 0) {
            overallCard.getChildren().addAll(overallTitle,
                muted("No resolved predictions yet."));
        } else {
            int pct = (int) Math.round((overall[0] * 100.0) / overall[1]);
            Label pctLabel = new Label(pct + "%");
            pctLabel.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: " + accuracyColor(pct) + ";");
            Label detail = muted(overall[0] + " correct or partial out of " + overall[1] + " resolved predictions");
            overallCard.getChildren().addAll(overallTitle, pctLabel, detail);
        }
        statsBox.getChildren().add(overallCard);

        Map<String, int[]> byDomain = log.getAccuracyByDomain();
        if (!byDomain.isEmpty()) {
            VBox domainCard = card();
            domainCard.getChildren().add(sectionLabel("BY DOMAIN"));
            for (Map.Entry<String, int[]> entry : byDomain.entrySet()) {
                int[] s = entry.getValue();
                int pct = (int) Math.round((s[0] * 100.0) / s[1]);
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Label domainLabel = new Label(entry.getKey());
                domainLabel.setMinWidth(140);
                domainLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #c9d1d9;");

                ProgressBar bar = new ProgressBar(pct / 100.0);
                bar.setPrefWidth(160);
                bar.setStyle("-fx-accent: " + accuracyColor(pct) + ";");

                Label pctLabel = new Label(pct + "% (" + s[0] + "/" + s[1] + ")");
                pctLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

                row.getChildren().addAll(domainLabel, bar, pctLabel);
                domainCard.getChildren().add(row);
            }
            statsBox.getChildren().add(domainCard);
        }

        Label goalLabel = new Label("Target accuracy: 75%+");
        goalLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151; -fx-padding: 8 0 0 4;");
        statsBox.getChildren().add(goalLabel);
    }

    private void populateMemory(VBox box) {
        box.getChildren().clear();
        EpisodicMemory mem = core.getEpisodicMemory();
        String worldContext = core.getContextManager().getWorldName();
        if (worldContext.isEmpty()) worldContext = "Real World";

        Label worldLabel = sectionLabel("WORLD: " + worldContext.toUpperCase());
        Label countLabel = muted(mem.countForWorld(worldContext) + " memory entries");
        box.getChildren().addAll(worldLabel, countLabel);

        List<EpisodicMemory.MemoryEntry> entries = mem.getForWorld(worldContext);
        if (entries.isEmpty()) {
            box.getChildren().add(emptyState("No memories yet for this world.\nThey build automatically as you use ARIA."));
            return;
        }

        VBox memCard = card();
        for (int i = 0; i < entries.size(); i++) {
            EpisodicMemory.MemoryEntry e = entries.get(i);
            HBox row = new HBox(10);
            row.setAlignment(Pos.TOP_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));

            Label dateLabel = new Label(e.date);
            dateLabel.setMinWidth(90);
            dateLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");

            String typeColor = switch (e.type) {
                case EpisodicMemory.TYPE_PREDICTION -> "#6366f1";
                case EpisodicMemory.TYPE_OUTCOME    -> "#3fb950";
                case EpisodicMemory.TYPE_NOTE       -> "#f59e0b";
                default -> "#6b7280";
            };
            Label typeTag = new Label(e.type.toUpperCase());
            typeTag.setPadding(new Insets(1, 6, 1, 6));
            typeTag.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + typeColor +
                "; -fx-border-color: " + typeColor + "; -fx-border-radius: 3; -fx-background-radius: 3;");

            Label summaryLabel = new Label(e.summary);
            summaryLabel.setWrapText(true);
            summaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #c9d1d9;");
            HBox.setHgrow(summaryLabel, Priority.ALWAYS);

            final int idx = i;
            final String wc = worldContext;
            Button del = new Button("×");
            del.setStyle("-fx-background-color: transparent; -fx-text-fill: #374151; -fx-cursor: hand; -fx-font-size: 13px;");
            del.setOnAction(ev -> {
                mem.deleteEntry(wc, idx);
                populateMemory(box);
            });

            row.getChildren().addAll(dateLabel, typeTag, summaryLabel, del);
            memCard.getChildren().add(row);

            if (i < entries.size() - 1) {
                HBox divider = new HBox();
                divider.setMinHeight(1);
                divider.setMaxHeight(1);
                divider.setStyle("-fx-background-color: #1e1b4b;");
                memCard.getChildren().add(divider);
            }
        }
        box.getChildren().add(memCard);
    }

    private VBox buildOpenCard(OracleLog.Entry e) {
        VBox card = card();

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("#" + e.id);
        idLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");

        Label dateLabel = new Label(e.shortDate());
        dateLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");

        Label worldLabel = new Label(e.worldContext);
        worldLabel.setPadding(new Insets(1, 6, 1, 6));
        worldLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #6366f1; -fx-border-color: #6366f1; -fx-border-radius: 3; -fx-background-radius: 3;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label confLabel = new Label(e.confidence + "% " + e.confidenceLabel);
        confLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + confidenceColor(e.confidence) + ";");

        topRow.getChildren().addAll(idLabel, dateLabel, worldLabel, sp, confLabel);

        Label subject = new Label(e.subject);
        subject.setWrapText(true);
        subject.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        Label verdict = new Label(e.verdict);
        verdict.setWrapText(true);
        verdict.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(8, 0, 0, 0));

        Button correct   = outcomeBtn("✓ Correct",   OracleLog.OUTCOME_CORRECT,   "#3fb950", e);
        Button partial   = outcomeBtn("~ Partial",   OracleLog.OUTCOME_PARTIAL,   "#f59e0b", e);
        Button incorrect = outcomeBtn("✗ Incorrect", OracleLog.OUTCOME_INCORRECT, "#f85149", e);

        actions.getChildren().addAll(incorrect, partial, correct);
        card.getChildren().addAll(topRow, subject, verdict, actions);
        return card;
    }

    private Button outcomeBtn(String label, String outcome, String color, OracleLog.Entry entry) {
        Button btn = new Button(label);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + color +
            "; -fx-border-color: " + color + "; -fx-border-radius: 4; -fx-background-radius: 4;" +
            " -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 3 10 3 10;");
        btn.setOnAction(e -> showResolveDialog(entry, outcome));
        return btn;
    }

    private void showResolveDialog(OracleLog.Entry entry, String outcome) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Mark Outcome");
        dialog.setResizable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #161b22;");

        Label title = new Label("What actually happened?");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        Label subject = new Label("Prediction: " + entry.subject);
        subject.setWrapText(true);
        subject.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");

        TextArea note = new TextArea();
        note.setPromptText("Optional: what happened, what was different, any useful detail...");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 12px;");

        Button save = new Button("Save Outcome");
        save.getStyleClass().add("accent");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setStyle("-fx-cursor: hand;");
        save.setOnAction(ev -> {
            String noteText = note.getText().trim();
            core.getOracleLog().resolveEntry(entry.id, outcome, noteText);

            String outcomeVerb = switch (outcome) {
                case OracleLog.OUTCOME_CORRECT   -> "was correct";
                case OracleLog.OUTCOME_PARTIAL   -> "was partially correct";
                case OracleLog.OUTCOME_INCORRECT -> "was incorrect";
                default -> "resolved";
            };
            String memorySummary = "ORACLE predicted: \"" + entry.subject + "\" — outcome " + outcomeVerb + "." +
                (noteText.isEmpty() ? "" : " Note: " + noteText);
            core.getEpisodicMemory().addEntry(entry.worldContext, EpisodicMemory.TYPE_OUTCOME, memorySummary);

            dialog.close();
            refresh();
        });

        root.getChildren().addAll(title, subject, note, save);
        dialog.setScene(new Scene(root, 420, 220));
        dialog.show();
    }

    private VBox buildResolvedCard(OracleLog.Entry e) {
        VBox card = card();

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("#" + e.id);
        idLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");

        Label dateLabel = new Label(e.shortDate());
        dateLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #374151;");

        String outcomeColor = switch (e.outcome) {
            case OracleLog.OUTCOME_CORRECT   -> "#3fb950";
            case OracleLog.OUTCOME_PARTIAL   -> "#f59e0b";
            case OracleLog.OUTCOME_INCORRECT -> "#f85149";
            default -> "#6b7280";
        };
        String outcomeDisplay = switch (e.outcome) {
            case OracleLog.OUTCOME_CORRECT   -> "✓ CORRECT";
            case OracleLog.OUTCOME_PARTIAL   -> "~ PARTIAL";
            case OracleLog.OUTCOME_INCORRECT -> "✗ INCORRECT";
            default -> e.outcome.toUpperCase();
        };
        Label outcomeLabel = new Label(outcomeDisplay);
        outcomeLabel.setPadding(new Insets(1, 8, 1, 8));
        outcomeLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + outcomeColor +
            "; -fx-background-color: " + outcomeColor + "22; -fx-background-radius: 4;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label confLabel = new Label(e.confidence + "%");
        confLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");

        topRow.getChildren().addAll(idLabel, dateLabel, outcomeLabel, sp, confLabel);

        Label subject = new Label(e.subject);
        subject.setWrapText(true);
        subject.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");

        card.getChildren().addAll(topRow, subject);

        if (e.outcomeNote != null && !e.outcomeNote.isEmpty()) {
            Label noteLabel = new Label("\"" + e.outcomeNote + "\"");
            noteLabel.setWrapText(true);
            noteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280; -fx-font-style: italic;");
            card.getChildren().add(noteLabel);
        }

        return card;
    }

    private void refresh() {
        populateOpen();
        populateResolved();
        populateStats();
    }

    private VBox card() {
        VBox v = new VBox(6);
        v.setPadding(new Insets(12, 14, 12, 14));
        v.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8;" +
            " -fx-border-color: #1e1b4b; -fx-border-radius: 8; -fx-border-width: 1;");
        return v;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #374151; -fx-letter-spacing: 1px;");
        return l;
    }

    private Label muted(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        l.setWrapText(true);
        return l;
    }

    private VBox emptyState(String text) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        l.setTextAlignment(TextAlignment.CENTER);
        l.setWrapText(true);
        box.getChildren().add(l);
        return box;
    }

    private String confidenceColor(int confidence) {
        if (confidence >= 75) return "#3fb950";
        if (confidence >= 55) return "#f59e0b";
        if (confidence >= 35) return "#e07b39";
        return "#f85149";
    }

    private String accuracyColor(int pct) {
        if (pct >= 75) return "#3fb950";
        if (pct >= 50) return "#f59e0b";
        return "#f85149";
    }
}
