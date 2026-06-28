package aria.ui;

import com.google.gson.*;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class OraclePredictionCard {

    public static VBox build(String jsonText) {
        return build(jsonText, null);
    }

    public static VBox build(String jsonText, java.util.function.Consumer<JsonObject> onTrack) {
        try {
            JsonObject obj = JsonParser.parseString(jsonText.trim()).getAsJsonObject();
            return buildCard(obj, onTrack);
        } catch (Exception e) {
            VBox err = new VBox(6);
            err.setPadding(new Insets(12));
            err.setStyle("-fx-background-color: #2a1a1a; -fx-border-color: #6b2a2a; -fx-border-radius: 10; -fx-background-radius: 10;");
            Label l = new Label("ORACLE: failed to parse prediction — " + e.getMessage());
            l.setWrapText(true);
            l.setStyle("-fx-text-fill: #f87171; -fx-font-size: 12px;");
            err.getChildren().add(l);
            return err;
        }
    }

    private static VBox buildCard(JsonObject obj, java.util.function.Consumer<JsonObject> onTrack) {
        VBox card = new VBox(0);
        card.setMaxWidth(680);
        card.setStyle(
            "-fx-background-color: #0d1117;" +
            "-fx-border-color: #3730a3;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 14;" +
            "-fx-background-radius: 14;"
        );

        card.getChildren().addAll(
            buildHeader(obj),
            buildVerdict(obj),
            buildConfidenceBar(obj),
            buildDivider(),
            buildReasoning(obj),
            buildSignals(obj),
            buildCounterScenario(obj),
            buildReferences(obj),
            buildFooter(obj, onTrack)
        );

        FadeTransition ft = new FadeTransition(Duration.millis(300), card);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        return card;
    }

    private static HBox buildHeader(JsonObject obj) {
        HBox header = new HBox(8);
        header.setPadding(new Insets(14, 18, 10, 18));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-border-color: transparent transparent #1e1b4b transparent; -fx-border-width: 0 0 1 0;");

        Label badge = new Label("⬡  ORACLE · RESEARCH PREDICTION ENGINE");
        badge.setStyle(
            "-fx-font-family: 'Consolas', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: #818cf8;" +
            "-fx-letter-spacing: 0.1em;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String subject = safe(obj, "subject", "Unknown subject");
        Label subjectLabel = new Label(subject);
        subjectLabel.setWrapText(true);
        subjectLabel.setMaxWidth(340);
        subjectLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        header.getChildren().addAll(badge, spacer, subjectLabel);
        return header;
    }

    private static VBox buildVerdict(JsonObject obj) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(14, 18, 10, 18));

        Label heading = new Label("VERDICT");
        heading.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 9px; -fx-text-fill: #374151; -fx-letter-spacing: 0.1em;");

        String verdict = safe(obj, "verdict", "No verdict");
        Label verdictLabel = new Label(verdict);
        verdictLabel.setWrapText(true);
        verdictLabel.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #e2e8f0;" +
            "-fx-line-spacing: 2;"
        );

        String timeframe = safe(obj, "timeframe", "");
        Label timeLabel = new Label("⏱  " + timeframe);
        timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6366f1;");

        section.getChildren().addAll(heading, verdictLabel, timeLabel);
        return section;
    }

    private static HBox buildConfidenceBar(JsonObject obj) {
        HBox section = new HBox(14);
        section.setPadding(new Insets(4, 18, 14, 18));
        section.setAlignment(Pos.CENTER_LEFT);

        int rawConf;
        try { rawConf = obj.get("confidence").getAsInt(); } catch (Exception ignored) { rawConf = 0; }
        final int confidence = rawConf;
        String label = safe(obj, "confidence_label", "");

        String color = confidenceColor(confidence);

        Label pct = new Label(confidence + "%");
        pct.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + "; -fx-font-family: 'Consolas', monospace;");

        VBox labelBox = new VBox(2, pct, lbl);

        StackPane barArea = new StackPane();
        barArea.setAlignment(Pos.CENTER_LEFT);

        Rectangle track = new Rectangle(0, 6);
        track.setFill(Color.web("#1e293b"));
        track.setArcWidth(4);
        track.setArcHeight(4);

        Rectangle fill = new Rectangle(0, 6);
        fill.setFill(Color.web(color));
        fill.setArcWidth(4);
        fill.setArcHeight(4);

        barArea.getChildren().addAll(track, fill);
        HBox.setHgrow(barArea, Priority.ALWAYS);

        barArea.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            track.setWidth(w);
            double target = w * confidence / 100.0;
            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(fill.widthProperty(), 0)),
                new KeyFrame(Duration.millis(900), new KeyValue(fill.widthProperty(), target, Interpolator.EASE_OUT))
            );
            tl.play();
        });

        section.getChildren().addAll(labelBox, barArea);
        return section;
    }

    private static VBox buildReasoning(JsonObject obj) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(12, 18, 12, 18));

        Label heading = mono9Label("REASONING");
        section.getChildren().add(heading);

        try {
            JsonArray arr = obj.getAsJsonArray("reasoning");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject r = arr.get(i).getAsJsonObject();
                String point = r.get("point").getAsString();
                String detail = r.get("detail").getAsString();

                VBox item = new VBox(2);
                Label pointLabel = new Label((i + 1) + ".  " + point);
                pointLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #a5b4fc;");

                Label detailLabel = new Label(detail);
                detailLabel.setWrapText(true);
                detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-padding: 0 0 0 18;");

                item.getChildren().addAll(pointLabel, detailLabel);
                section.getChildren().add(item);
            }
        } catch (Exception ignored) {}

        return section;
    }

    private static VBox buildSignals(JsonObject obj) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(4, 18, 12, 18));
        section.setStyle("-fx-background-color: #0a0f1e; -fx-border-color: transparent;");

        Label heading = mono9Label("KEY SIGNALS");
        section.getChildren().add(heading);

        try {
            JsonArray arr = obj.getAsJsonArray("signals");
            for (JsonElement el : arr) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.TOP_LEFT);
                Label dot = new Label("▸");
                dot.setStyle("-fx-font-size: 11px; -fx-text-fill: #6366f1;");
                Label sig = new Label(el.getAsString());
                sig.setWrapText(true);
                sig.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
                HBox.setHgrow(sig, Priority.ALWAYS);
                row.getChildren().addAll(dot, sig);
                section.getChildren().add(row);
            }
        } catch (Exception ignored) {}

        return section;
    }

    private static VBox buildCounterScenario(JsonObject obj) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(10, 18, 10, 18));
        section.setStyle("-fx-background-color: #12101e; -fx-border-color: transparent;");

        Label heading = mono9Label("COUNTER-SCENARIO");
        String counter = safe(obj, "counter_scenario", "");

        Label text = new Label(counter);
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280; -fx-font-style: italic;");

        section.getChildren().addAll(heading, text);
        return section;
    }

    private static VBox buildReferences(JsonObject obj) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(10, 18, 10, 18));

        try {
            JsonArray arr = obj.getAsJsonArray("references");
            if (arr.size() == 0) return section;

            Label heading = mono9Label("REFERENCES");
            section.getChildren().add(heading);

            for (JsonElement el : arr) {
                JsonObject ref = el.getAsJsonObject();
                String refLabel = ref.get("label").getAsString();
                String note = ref.get("note").getAsString();

                HBox row = new HBox(8);
                Label lbl = new Label(refLabel);
                lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #818cf8; -fx-min-width: 120;");
                Label nl = new Label("— " + note);
                nl.setWrapText(true);
                nl.setStyle("-fx-font-size: 11px; -fx-text-fill: #4b5563;");
                HBox.setHgrow(nl, Priority.ALWAYS);
                row.getChildren().addAll(lbl, nl);
                section.getChildren().add(row);
            }
        } catch (Exception ignored) {}

        return section;
    }

    private static HBox buildFooter(JsonObject obj, java.util.function.Consumer<JsonObject> onTrack) {
        HBox footer = new HBox(6);
        footer.setPadding(new Insets(10, 18, 12, 18));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-border-color: #1e1b4b transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        try {
            JsonArray domains = obj.getAsJsonArray("domains");
            for (JsonElement el : domains) {
                Label tag = new Label(el.getAsString());
                tag.setStyle(
                    "-fx-background-color: rgba(99,102,241,0.12);" +
                    "-fx-border-color: rgba(99,102,241,0.28);" +
                    "-fx-border-radius: 999;" +
                    "-fx-background-radius: 999;" +
                    "-fx-padding: 2 10 2 10;" +
                    "-fx-font-family: 'Consolas', monospace;" +
                    "-fx-font-size: 10px;" +
                    "-fx-text-fill: #a5b4fc;"
                );
                footer.getChildren().add(tag);
            }
        } catch (Exception ignored) {}

        if (onTrack != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button trackBtn = new Button("⊙ Track");
            trackBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #6366f1;" +
                "-fx-border-color: rgba(99,102,241,0.4);" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-font-size: 10px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 2 10 2 10;"
            );
            trackBtn.setOnAction(e -> {
                onTrack.accept(obj);
                trackBtn.setText("⊙ Tracked");
                trackBtn.setDisable(true);
                trackBtn.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-text-fill: #374151;" +
                    "-fx-border-color: #374151;" +
                    "-fx-border-radius: 4;" +
                    "-fx-background-radius: 4;" +
                    "-fx-font-size: 10px;" +
                    "-fx-padding: 2 10 2 10;"
                );
            });
            footer.getChildren().addAll(spacer, trackBtn);
        }

        return footer;
    }

    private static HBox buildDivider() {
        HBox line = new HBox();
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: #1e1b4b;");
        line.setPadding(new Insets(0));
        return line;
    }

    private static Label mono9Label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 9px; -fx-text-fill: #374151; -fx-letter-spacing: 0.1em;");
        return l;
    }

    private static String safe(JsonObject obj, String key, String fallback) {
        try { return obj.get(key).getAsString(); } catch (Exception e) { return fallback; }
    }

    private static String confidenceColor(int score) {
        if (score >= 80) return "#4ade80";
        if (score >= 60) return "#facc15";
        if (score >= 40) return "#fb923c";
        return "#f87171";
    }
}
