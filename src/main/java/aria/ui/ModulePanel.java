package aria.ui;

import aria.core.AriaCore;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.Map;

public class ModulePanel {

    private final AriaCore core;
    private final Stage owner;
    private final MainWindow mainWindow;

    public ModulePanel(AriaCore core, Stage owner, MainWindow mainWindow) {
        this.core = core;
        this.owner = owner;
        this.mainWindow = mainWindow;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Module Control Panel");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: -color-bg-default;");

        VBox header = new VBox(4);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Module Control Panel");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        Label subtitle = new Label("Toggle ARIA's active behaviors. Changes apply immediately.");
        subtitle.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");

        header.getChildren().addAll(title, subtitle);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

        FlowPane cards = new FlowPane(16, 16);
        cards.setPadding(new Insets(20));
        cards.setPrefWrapLength(560);

        Map<String, Boolean> modules = core.getModuleManager().getAll();
        for (Map.Entry<String, Boolean> entry : modules.entrySet()) {
            cards.getChildren().add(buildModuleCard(entry.getKey(), entry.getValue(), dialog));
        }

        scrollPane.setContent(cards);

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(12, 20, 12, 20));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Button closeBtn = new Button("Done");
        closeBtn.getStyleClass().add("accent");
        closeBtn.setOnAction(e -> dialog.close());

        footer.getChildren().add(closeBtn);

        root.getChildren().addAll(header, scrollPane, footer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 620, 500);
        dialog.setScene(scene);
        dialog.setMinWidth(500);
        dialog.setMinHeight(400);
        dialog.show();
    }

    private VBox buildModuleCard(String moduleName, boolean initialState, Stage dialog) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setPrefWidth(270);
        card.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 8; -fx-border-color: -color-border-default; -fx-border-radius: 8; -fx-border-width: 1;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(moduleName.replace("_", " "));
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        boolean[] enabled = {initialState};

        Label statusBadge = new Label(initialState ? "ON" : "OFF");
        statusBadge.setPadding(new Insets(2, 8, 2, 8));
        statusBadge.setStyle(getBadgeStyle(initialState));

        titleRow.getChildren().addAll(nameLabel, statusBadge);

        Label desc = new Label(core.getModuleManager().getDescription(moduleName));
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");

        ToggleButton toggle = new ToggleButton(initialState ? "Enabled" : "Disabled");
        toggle.setSelected(initialState);
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setStyle("-fx-cursor: hand;");

        toggle.setOnAction(e -> {
            boolean newState = toggle.isSelected();
            enabled[0] = newState;
            core.getModuleManager().setEnabled(moduleName, newState);
            toggle.setText(newState ? "Enabled" : "Disabled");
            statusBadge.setText(newState ? "ON" : "OFF");
            statusBadge.setStyle(getBadgeStyle(newState));
            mainWindow.refreshAfterContextChange();
        });

        card.getChildren().addAll(titleRow, desc, toggle);
        return card;
    }

    private String getBadgeStyle(boolean enabled) {
        if (enabled) {
            return "-fx-background-color: #1f6329; -fx-text-fill: #3fb950; -fx-background-radius: 10; -fx-font-size: 10px; -fx-font-weight: bold;";
        } else {
            return "-fx-background-color: -color-bg-inset; -fx-text-fill: -color-fg-muted; -fx-background-radius: 10; -fx-font-size: 10px;";
        }
    }
}
