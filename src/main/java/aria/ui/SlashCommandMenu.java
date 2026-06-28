package aria.ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.*;
import java.util.function.*;

/**
 * Popup slash-command menu that appears when the user types '/' in the input.
 * Arrow keys to navigate, Enter or click to execute.
 */
public class SlashCommandMenu {

    public record SlashCommand(String name, String description, String fillText, Runnable action) {}

    private final Popup popup;
    private final VBox  list;
    private final TextArea input;
    private final List<SlashCommand> commands;
    private int selectedIndex = 0;

    public SlashCommandMenu(TextArea input, List<SlashCommand> commands) {
        this.input    = input;
        this.commands = commands;

        list = new VBox(0);
        list.setStyle(
            "-fx-background-color:-color-bg-elevated;" +
            "-fx-border-color:-color-border-default;" +
            "-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.35),14,0,0,4);");
        list.setMinWidth(340);

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(list);

        // Show/hide as user types
        input.textProperty().addListener((obs, old, text) -> {
            if (text.startsWith("/")) {
                refresh(text.substring(1).toLowerCase());
                if (!list.getChildren().isEmpty()) positionAndShow();
                else popup.hide();
            } else {
                popup.hide();
            }
        });

        // Keyboard navigation while popup is showing
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!popup.isShowing()) return;
            switch (e.getCode()) {
                case UP -> { selectedIndex = Math.max(0, selectedIndex - 1); highlight(); e.consume(); }
                case DOWN -> { selectedIndex = Math.min(list.getChildren().size() - 1, selectedIndex + 1); highlight(); e.consume(); }
                case ENTER -> { if (popup.isShowing()) { e.consume(); executeAt(selectedIndex); } }
                case ESCAPE -> { popup.hide(); e.consume(); }
                default -> {}
            }
        });
    }

    private void refresh(String filter) {
        list.getChildren().clear();
        selectedIndex = 0;
        List<SlashCommand> matched = commands.stream()
            .filter(c -> c.name().toLowerCase().contains(filter)
                      || c.description().toLowerCase().contains(filter))
            .limit(9)
            .toList();

        for (int i = 0; i < matched.size(); i++) {
            list.getChildren().add(row(matched.get(i), i));
        }
        if (!matched.isEmpty()) highlight();
    }

    private javafx.scene.Node row(SlashCommand cmd, int index) {
        HBox row = new HBox(14);
        row.setPadding(new Insets(9, 16, 9, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-cursor:hand;");

        Label nameLabel = new Label("/" + cmd.name());
        nameLabel.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-min-width:120;");

        Label descLabel = new Label(cmd.description());
        descLabel.setStyle("-fx-text-fill:-color-fg-muted;-fx-font-size:12px;");

        row.getChildren().addAll(nameLabel, descLabel);
        row.setOnMouseEntered(e -> { selectedIndex = index; highlight(); });
        row.setOnMouseClicked(e -> executeAt(index));
        return row;
    }

    private void highlight() {
        for (int i = 0; i < list.getChildren().size(); i++) {
            list.getChildren().get(i).setStyle(
                i == selectedIndex
                    ? "-fx-background-color:-color-accent-emphasis;-fx-background-radius:4;-fx-cursor:hand;"
                    : "-fx-cursor:hand;");
        }
    }

    private void executeAt(int index) {
        String filter = input.getText().startsWith("/") ? input.getText().substring(1).toLowerCase() : "";
        List<SlashCommand> matched = commands.stream()
            .filter(c -> c.name().toLowerCase().contains(filter)
                      || c.description().toLowerCase().contains(filter))
            .limit(9)
            .toList();

        popup.hide();
        if (index < 0 || index >= matched.size()) return;
        SlashCommand cmd = matched.get(index);

        if (cmd.fillText() != null) {
            input.setText(cmd.fillText());
            input.end();
        } else {
            input.clear();
        }
        if (cmd.action() != null) {
            javafx.application.Platform.runLater(cmd.action());
        }
    }

    private void positionAndShow() {
        if (popup.isShowing()) return;
        Bounds b = input.localToScreen(input.getBoundsInLocal());
        if (b == null) return;
        // Show above the input; height of list is unknown until shown, estimate ~250px
        double estimatedHeight = Math.min(list.getChildren().size(), 9) * 42.0 + 8;
        popup.show(input, b.getMinX(), b.getMinY() - estimatedHeight - 6);
    }
}
