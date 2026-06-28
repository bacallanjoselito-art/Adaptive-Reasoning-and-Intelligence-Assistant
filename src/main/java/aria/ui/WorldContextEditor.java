package aria.ui;

import aria.core.AriaCore;
import aria.core.ContextManager;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.Map;

public class WorldContextEditor {

    private final AriaCore core;
    private final Stage owner;
    private final MainWindow mainWindow;

    private FlowPane cardPane;
    private Stage dialog;

    public WorldContextEditor(AriaCore core, Stage owner, MainWindow mainWindow) {
        this.core = core;
        this.owner = owner;
        this.mainWindow = mainWindow;
    }

    public void show() {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("World Context");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label title = new Label("World Context");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label subtitle = new Label("Create and activate context profiles. Only one can be active at a time.");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        header.getChildren().addAll(title, subtitle);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        cardPane = new FlowPane(16, 16);
        cardPane.setPadding(new Insets(20));
        cardPane.setPrefWrapLength(580);

        refreshCards();
        scrollPane.setContent(cardPane);

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 20, 12, 20));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button newBtn = new Button("+ New Context");
        newBtn.getStyleClass().add("accent");
        newBtn.setStyle("-fx-cursor: hand;");
        newBtn.setOnAction(e -> openCreateDialog());

        Button deactivateBtn = new Button("Deactivate All");
        deactivateBtn.setStyle("-fx-cursor: hand;");
        deactivateBtn.setOnAction(e -> {
            core.getContextManager().deactivate();
            refreshCards();
            mainWindow.refreshAfterContextChange();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button doneBtn = new Button("Done");
        doneBtn.setStyle("-fx-cursor: hand;");
        doneBtn.setOnAction(e -> dialog.close());

        footer.getChildren().addAll(newBtn, deactivateBtn, spacer, doneBtn);

        root.getChildren().addAll(header, scrollPane, footer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 660, 520);
        dialog.setScene(scene);
        dialog.setMinWidth(520);
        dialog.setMinHeight(400);
        dialog.show();
    }

    private void refreshCards() {
        cardPane.getChildren().clear();
        ContextManager cm = core.getContextManager();
        Map<String, JsonObject> all = cm.getAll();

        if (all.isEmpty()) {
            Label empty = new Label("No contexts yet. Click '+ New Context' to create one.");
            empty.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 13px; -fx-padding: 20;");
            cardPane.getChildren().add(empty);
            return;
        }

        for (Map.Entry<String, JsonObject> entry : all.entrySet()) {
            cardPane.getChildren().add(buildCard(entry.getKey(), entry.getValue()));
        }
    }

    private VBox buildCard(String name, JsonObject ctx) {
        ContextManager cm = core.getContextManager();
        boolean isActive = name.equals(cm.getActiveName());

        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setPrefWidth(290);
        String borderColor = isActive ? "#3fb950" : "-color-border-default";
        card.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8; " +
            "-fx-border-color: " + borderColor + "; -fx-border-radius: 8; -fx-border-width: " + (isActive ? "2" : "1") + ";");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label badge = new Label("ACTIVE");
        badge.setPadding(new Insets(2, 8, 2, 8));
        badge.setStyle("-fx-background-color: #1f6329; -fx-text-fill: #3fb950; " +
            "-fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;");
        badge.setVisible(isActive);
        badge.setManaged(isActive);

        titleRow.getChildren().addAll(nameLabel, badge);

        String desc = ctx.has("world_description") ? ctx.get("world_description").getAsString() : "";
        if (desc.length() > 80) desc = desc.substring(0, 80) + "…";
        Label descLabel = new Label(desc.isEmpty() ? "(no description)" : desc);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");

        String role = ctx.has("aria_role") ? ctx.get("aria_role").getAsString() : "";
        card.getChildren().addAll(titleRow, descLabel);
        if (!role.isEmpty()) {
            Label roleLabel = new Label("Role: " + role);
            roleLabel.setStyle("-fx-text-fill: -color-fg-subtle; -fx-font-size: 11px;");
            roleLabel.setWrapText(true);
            card.getChildren().add(roleLabel);
        }

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Button activateBtn = new Button(isActive ? "Deactivate" : "Activate");
        activateBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(activateBtn, Priority.ALWAYS);
        if (isActive) {
            activateBtn.setStyle("-fx-cursor: hand; -fx-background-color: #1f6329; -fx-text-fill: #3fb950;");
        } else {
            activateBtn.getStyleClass().add("accent");
            activateBtn.setStyle("-fx-cursor: hand;");
        }
        activateBtn.setOnAction(e -> {
            if (isActive) cm.deactivate(); else cm.setActive(name);
            refreshCards();
            mainWindow.refreshAfterContextChange();
        });

        Button editBtn = new Button("Edit");
        editBtn.setStyle("-fx-cursor: hand;");
        editBtn.setOnAction(e -> openEditDialog(name, ctx));

        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle("-fx-cursor: hand; -fx-text-fill: -color-danger-fg;");
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + name + "\"?", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Delete Context");
            confirm.initOwner(dialog);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    cm.delete(name);
                    refreshCards();
                    mainWindow.refreshAfterContextChange();
                }
            });
        });

        buttons.getChildren().addAll(activateBtn, editBtn, deleteBtn);
        card.getChildren().add(buttons);
        return card;
    }

    // ── NEW CONTEXT: describe-first dialog ───────────────────────────────────

    private void openCreateDialog() {
        Stage stage = new Stage();
        stage.initOwner(dialog);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("New Context");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        // Header
        VBox header = new VBox(4);
        header.setPadding(new Insets(14, 20, 12, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");
        Label title = new Label("New World Context");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label subtitle = new Label("Describe your world in plain language — ARIA fills in the details.");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        header.getChildren().addAll(title, subtitle);

        // ── Describe pane ────────────────────────────────────────────────────
        VBox describePane = new VBox(12);
        describePane.setPadding(new Insets(20));

        Label describeHint = new Label("Just type a sentence or two:");
        describeHint.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");

        TextArea describeInput = new TextArea();
        describeInput.setPromptText(
            "e.g. \"We're in a dark sci-fi colony on Mars. ARIA is the station AI who knows " +
            "engineering and survival protocols but has no access to Earth news or political events.\""
        );
        describeInput.setPrefRowCount(5);
        describeInput.setWrapText(true);
        describeInput.setStyle("-fx-font-size: 13px;");

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        statusLabel.setWrapText(true);

        HBox describeButtons = new HBox(10);
        describeButtons.setAlignment(Pos.CENTER_LEFT);

        Button generateBtn = new Button("Generate Fields →");
        generateBtn.getStyleClass().add("accent");
        generateBtn.setStyle("-fx-cursor: hand;");

        Button manualBtn = new Button("Enter manually instead");
        manualBtn.setStyle("-fx-cursor: hand; -fx-background-color: transparent; -fx-text-fill: -color-fg-muted;");

        describeButtons.getChildren().addAll(generateBtn, manualBtn);
        describePane.getChildren().addAll(describeHint, describeInput, statusLabel, describeButtons);

        // ── Review / form pane ───────────────────────────────────────────────
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox formContent = new VBox(14);
        formContent.setPadding(new Insets(20));

        TextField nameField       = field("Context Name (e.g. Mars Colony)", "");
        TextArea  descArea        = textArea("World description, setting, lore…", "", 4);
        TextField roleField       = field("ARIA's role in this world", "");
        TextArea  boundsArea      = textArea("What ARIA knows in this world", "", 3);
        TextArea  limitsArea      = textArea("What ARIA does NOT know", "", 3);

        formContent.getChildren().addAll(
            labeled("Context Name", nameField),
            labeled("World Description", descArea),
            labeled("ARIA's Role", roleField),
            labeled("Knowledge Bounds", boundsArea),
            labeled("Knowledge Limits", limitsArea)
        );
        scroll.setContent(formContent);

        // ── Footer ───────────────────────────────────────────────────────────
        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 20, 12, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-cursor: hand;");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button("Save Context");
        saveBtn.getStyleClass().add("accent");
        saveBtn.setStyle("-fx-cursor: hand;");
        saveBtn.setDisable(true);

        footer.getChildren().addAll(cancelBtn, saveBtn);

        // ── Layout: start in describe mode ───────────────────────────────────
        root.getChildren().addAll(header, describePane, footer);
        VBox.setVgrow(describePane, Priority.ALWAYS);

        // Switch to form view
        Runnable showForm = () -> {
            root.getChildren().remove(describePane);
            if (!root.getChildren().contains(scroll)) {
                root.getChildren().add(1, scroll);
                VBox.setVgrow(scroll, Priority.ALWAYS);
            }
            saveBtn.setDisable(false);
        };

        // Generate button action
        generateBtn.setOnAction(e -> {
            String input = describeInput.getText().trim();
            if (input.isEmpty()) {
                statusLabel.setText("Type a description first.");
                return;
            }
            generateBtn.setDisable(true);
            manualBtn.setDisable(true);
            statusLabel.setText("Converting…");

            Task<JsonObject> task = new Task<>() {
                @Override
                protected JsonObject call() throws Exception {
                    return core.getLLMClient().convertToContext(input);
                }
            };

            task.setOnSucceeded(ev -> {
                JsonObject result = task.getValue();
                nameField.setText(safeGet(result, "world_name"));
                descArea.setText(safeGet(result, "world_description"));
                roleField.setText(safeGet(result, "aria_role"));
                boundsArea.setText(safeGet(result, "knowledge_bounds"));
                limitsArea.setText(safeGet(result, "knowledge_limits"));
                statusLabel.setText("Fields generated — review and edit before saving.");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #3fb950;");
                showForm.run();
                generateBtn.setDisable(false);
                manualBtn.setDisable(false);
            });

            task.setOnFailed(ev -> {
                statusLabel.setText("Generation failed: " + task.getException().getMessage());
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-danger-fg;");
                generateBtn.setDisable(false);
                manualBtn.setDisable(false);
            });

            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        });

        // Manual button action
        manualBtn.setOnAction(e -> {
            showForm.run();
        });

        // Save action
        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                nameField.setStyle("-fx-border-color: red;");
                return;
            }
            JsonObject ctx = new JsonObject();
            ctx.addProperty("world_description", descArea.getText().trim());
            ctx.addProperty("aria_role", roleField.getText().trim());
            ctx.addProperty("knowledge_bounds", boundsArea.getText().trim());
            ctx.addProperty("knowledge_limits", limitsArea.getText().trim());
            core.getContextManager().addOrUpdate(newName, ctx);
            stage.close();
            refreshCards();
            mainWindow.refreshAfterContextChange();
        });

        Scene scene = new Scene(root, 520, 480);
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(380);
        stage.show();
    }

    // ── EDIT CONTEXT: form directly (already has data) ───────────────────────

    private void openEditDialog(String existingName, JsonObject existingCtx) {
        Stage editStage = new Stage();
        editStage.initOwner(dialog);
        editStage.initModality(Modality.WINDOW_MODAL);
        editStage.setTitle("Edit: " + existingName);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        VBox header = new VBox(4);
        header.setPadding(new Insets(14, 20, 12, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");
        Label title = new Label("Edit Context");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        header.getChildren().add(title);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        VBox form = new VBox(14);
        form.setPadding(new Insets(20));

        TextField nameField  = field("Context Name", existingName);
        TextArea  descArea   = textArea("World description, setting, lore…", getField(existingCtx, "world_description"), 4);
        TextField roleField  = field("ARIA's role in this world", getField(existingCtx, "aria_role"));
        TextArea  boundsArea = textArea("What ARIA knows in this world", getField(existingCtx, "knowledge_bounds"), 3);
        TextArea  limitsArea = textArea("What ARIA does NOT know", getField(existingCtx, "knowledge_limits"), 3);

        form.getChildren().addAll(
            labeled("Context Name", nameField),
            labeled("World Description", descArea),
            labeled("ARIA's Role", roleField),
            labeled("Knowledge Bounds", boundsArea),
            labeled("Knowledge Limits", limitsArea)
        );
        scroll.setContent(form);

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 20, 12, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-cursor: hand;");
        cancelBtn.setOnAction(e -> editStage.close());

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("accent");
        saveBtn.setStyle("-fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                nameField.setStyle("-fx-border-color: red;");
                return;
            }
            JsonObject ctx = new JsonObject();
            ctx.addProperty("world_description", descArea.getText().trim());
            ctx.addProperty("aria_role", roleField.getText().trim());
            ctx.addProperty("knowledge_bounds", boundsArea.getText().trim());
            ctx.addProperty("knowledge_limits", limitsArea.getText().trim());

            ContextManager cm = core.getContextManager();
            if (!existingName.equals(newName)) {
                cm.rename(existingName, newName, ctx);
            } else {
                cm.addOrUpdate(newName, ctx);
            }
            editStage.close();
            refreshCards();
            mainWindow.refreshAfterContextChange();
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(header, scroll, footer);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 520, 540);
        editStage.setScene(scene);
        editStage.setMinWidth(400);
        editStage.setMinHeight(400);
        editStage.show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getField(JsonObject ctx, String key) {
        if (ctx == null || !ctx.has(key)) return "";
        return ctx.get(key).getAsString();
    }

    private String safeGet(JsonObject obj, String key) {
        try { return obj.get(key).getAsString(); } catch (Exception e) { return ""; }
    }

    private TextField field(String prompt, String value) {
        TextField f = new TextField(value);
        f.setPromptText(prompt);
        return f;
    }

    private TextArea textArea(String prompt, String value, int rows) {
        TextArea a = new TextArea(value);
        a.setPromptText(prompt);
        a.setPrefRowCount(rows);
        a.setWrapText(true);
        return a;
    }

    private VBox labeled(String label, javafx.scene.Node node) {
        VBox box = new VBox(5);
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -color-fg-muted;");
        box.getChildren().addAll(l, node);
        return box;
    }
}
