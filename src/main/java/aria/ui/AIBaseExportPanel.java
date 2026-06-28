package aria.ui;

import aria.core.AIBaseExporter;
import aria.core.AIBaseExporter.*;
import aria.core.AriaCore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;

public class AIBaseExportPanel {

    private final AriaCore core;
    private final Stage owner;
    private final MainWindow mainWindow;

    private TextField nameField;
    private TextArea descriptionField;
    private TextField tagsField;
    private TextField intendedUseField;
    private TextField authorField;
    private ComboBox<String> formatBox;
    private TextArea previewArea;
    private VBox historyList;
    private Label statusLabel;

    public AIBaseExportPanel(AriaCore core, Stage owner, MainWindow mainWindow) {
        this.core = core;
        this.owner = owner;
        this.mainWindow = mainWindow;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("AI Base Export");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label title = new Label("AI Base Export");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label subtitle = new Label("Package ARIA's current context as a portable AI base file.");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        header.getChildren().addAll(title, subtitle);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.5);

        splitPane.getItems().addAll(buildLeftPanel(), buildRightPanel());

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 12px;");
        statusLabel.setPadding(new Insets(0, 0, 0, 8));

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(10, 16, 10, 16));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button exportBtn = new Button("⇥ Export JSON");
        exportBtn.getStyleClass().add("accent");
        exportBtn.setStyle("-fx-cursor: hand;");
        exportBtn.setOnAction(e -> doExportJSON(dialog));

        Button exportTxtBtn = new Button("Export as TXT");
        exportTxtBtn.setStyle("-fx-cursor: hand;");
        exportTxtBtn.setOnAction(e -> doExportTXT(dialog));

        Button copyBtn = new Button("Copy Prompt");
        copyBtn.setStyle("-fx-cursor: hand;");
        copyBtn.setOnAction(e -> doCopyPrompt());

        Button importBtn = new Button("Import...");
        importBtn.setStyle("-fx-cursor: hand;");
        importBtn.setOnAction(e -> doImport(dialog));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        footer.getChildren().addAll(exportBtn, exportTxtBtn, copyBtn, importBtn, statusLabel, spacer, closeBtn);

        root.getChildren().addAll(header, splitPane, footer);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 960, 640);
        dialog.setScene(scene);
        dialog.setMinWidth(700);
        dialog.setMinHeight(500);
        dialog.show();

        refreshPreview();
        refreshHistory();
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox(14);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: -color-bg-default;");

        Label settingsTitle = new Label("EXPORT SETTINGS");
        settingsTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted; -fx-letter-spacing: 1px;");

        String worldName = core.getContextManager().getWorldName();

        nameField = new TextField(worldName.isEmpty() ? "my_aria_base" : worldName.toLowerCase().replaceAll("\\s+", "_"));
        nameField.setPromptText("Export name (filename)");

        descriptionField = new TextArea();
        descriptionField.setPromptText("Short description of intended use...");
        descriptionField.setPrefRowCount(2);
        descriptionField.setWrapText(true);

        tagsField = new TextField();
        tagsField.setPromptText("Tags (comma-separated): npc, fantasy, assistant...");

        intendedUseField = new TextField();
        intendedUseField.setPromptText("Game NPC, roleplay assistant, creative writing...");

        authorField = new TextField(core.getConfig("AUTHOR_NAME", "Natt"));
        authorField.setPromptText("Author name");

        formatBox = new ComboBox<>();
        formatBox.getItems().addAll("JSON (Full Package)", "TXT (System Prompt Only)");
        formatBox.setValue("JSON (Full Package)");

        Label previewTitle = new Label("ASSEMBLED SYSTEM PROMPT PREVIEW");
        previewTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted; -fx-letter-spacing: 1px; -fx-padding: 8 0 0 0;");

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(8);
        previewArea.setStyle("-fx-font-size: 11px; -fx-font-family: monospace;");

        panel.getChildren().addAll(
            settingsTitle,
            labeled("Export Name", nameField),
            labeled("Description", descriptionField),
            labeled("Tags", tagsField),
            labeled("Intended Use", intendedUseField),
            labeled("Author", authorField),
            labeled("Format", formatBox),
            previewTitle,
            previewArea
        );

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildRightPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 0 1;");

        Label histTitle = new Label("EXPORT HISTORY");
        histTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted; -fx-letter-spacing: 1px;");

        historyList = new VBox(6);

        ScrollPane scroll = new ScrollPane(historyList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(histTitle, scroll);
        return panel;
    }

    private void refreshPreview() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return core.buildSystemPrompt();
            }
        };
        task.setOnSucceeded(e -> {
            if (previewArea != null) {
                previewArea.setText(task.getValue());
            }
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void refreshHistory() {
        if (historyList == null) return;
        historyList.getChildren().clear();
        List<ExportHistoryItem> items = core.getAIBaseExporter().loadExportHistory();
        if (items.isEmpty()) {
            Label empty = new Label("No exports yet.");
            empty.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
            historyList.getChildren().add(empty);
            return;
        }
        for (ExportHistoryItem item : items) {
            historyList.getChildren().add(buildHistoryCard(item));
        }
    }

    private VBox buildHistoryCard(ExportHistoryItem item) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 6; -fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width: 1;");

        Label name = new Label(item.name);
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        name.setWrapText(true);

        String ts = item.timestamp.length() > 10 ? item.timestamp.substring(0, 10) : item.timestamp;
        Label meta = new Label((item.worldName.isEmpty() ? "" : item.worldName + " · ") + ts);
        meta.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        HBox actions = new HBox(6);
        Button reExport = new Button("Re-export");
        reExport.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");
        reExport.setOnAction(e -> {
            if (item.name != null) nameField.setText(item.name.toLowerCase().replaceAll("\\s+", "_"));
            setStatus("Settings loaded. Click Export to re-export.");
        });

        Button copyPath = new Button("Copy Path");
        copyPath.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");
        copyPath.setOnAction(e -> {
            Clipboard cb = Clipboard.getSystemClipboard();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(item.filePath);
            cb.setContent(cc);
            setStatus("Path copied!");
        });

        actions.getChildren().addAll(reExport, copyPath);
        card.getChildren().addAll(name, meta, actions);
        return card;
    }

    private void doExportJSON(Stage dialog) {
        String assembledPrompt = previewArea.getText();
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();
        String tagsRaw = tagsField.getText().trim();
        String intendedUse = intendedUseField.getText().trim();
        String author = authorField.getText().trim();

        List<String> tags = new ArrayList<>();
        if (!tagsRaw.isEmpty()) {
            for (String t : tagsRaw.split(",")) tags.add(t.trim());
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                AIBaseExporter exporter = core.getAIBaseExporter();
                AriaBaseExport pkg = exporter.buildExportPackage(name, description, tags, intendedUse, author, assembledPrompt);
                return exporter.saveToFile(pkg, name);
            }
        };

        task.setOnSucceeded(e -> {
            String path = task.getValue();
            setStatus("Exported to: " + path);
            refreshHistory();
        });

        task.setOnFailed(e -> setStatus("Export failed: " + task.getException().getMessage()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void doExportTXT(Stage dialog) {
        String assembledPrompt = previewArea.getText();
        String name = nameField.getText().trim();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                AIBaseExporter exporter = core.getAIBaseExporter();
                AIBaseExporter.AriaBaseExport pkg = new AIBaseExporter.AriaBaseExport();
                pkg.system_prompt = new AIBaseExporter.SystemPrompt();
                pkg.system_prompt.full_text = assembledPrompt;
                return exporter.exportAsText(pkg, name);
            }
        };

        task.setOnSucceeded(e -> {
            setStatus("TXT exported to: " + task.getValue());
        });

        task.setOnFailed(e -> setStatus("TXT export failed: " + task.getException().getMessage()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void doCopyPrompt() {
        String prompt = previewArea.getText();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(prompt);
        clipboard.setContent(content);
        setStatus("Prompt copied to clipboard!");
    }

    private void doImport(Stage dialog) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import AI Base");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ARIA Base Files", "*_aria_base.json", "*.json"));
        File file = chooser.showOpenDialog(dialog);
        if (file == null) return;

        try {
            AIBaseExporter exporter = core.getAIBaseExporter();
            AriaBaseExport imported = exporter.importFromFile(file);

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Import AI Base");
            confirm.setHeaderText("Import: " + (imported.metadata != null ? imported.metadata.name : file.getName()));
            confirm.setContentText("Apply this context to your current session?");
            confirm.initOwner(dialog);

            ButtonType applyNow = new ButtonType("Apply Now");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            confirm.getButtonTypes().setAll(applyNow, cancel);

            confirm.showAndWait().ifPresent(btn -> {
                if (btn == applyNow) {
                    if (imported.world_context != null) {
                        com.google.gson.JsonObject ctx = new com.google.gson.JsonObject();
                        ctx.addProperty("world_name", imported.world_context.world_name);
                        ctx.addProperty("world_description", imported.world_context.description);
                        ctx.addProperty("aria_role", imported.world_context.aria_role);
                        ctx.addProperty("knowledge_bounds", imported.world_context.knowledge_bounds);
                        ctx.addProperty("knowledge_limits", imported.world_context.knowledge_limits);
                        core.getContextManager().setWorldContext(ctx);
                    }
                    if (imported.modules != null) {
                        imported.modules.forEach((k, v) -> core.getModuleManager().setEnabled(k, v));
                        core.getModuleManager().save();
                    }
                    mainWindow.refreshAfterContextChange();
                    setStatus("Context imported from: " + file.getName());
                    refreshPreview();
                }
            });
        } catch (Exception ex) {
            setStatus("Import failed: " + ex.getMessage());
        }
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private VBox labeled(String label, javafx.scene.Node node) {
        VBox box = new VBox(4);
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        box.getChildren().addAll(l, node);
        return box;
    }
}
