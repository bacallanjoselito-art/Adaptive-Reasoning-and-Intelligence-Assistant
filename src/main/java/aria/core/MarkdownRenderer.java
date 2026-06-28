package aria.core;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;

import java.util.*;
import java.util.regex.*;

public class MarkdownRenderer {

    private static final Pattern INLINE_PATTERN = Pattern.compile(
        "\\*\\*(.+?)\\*\\*" +           // **bold**
        "|__(.+?)__" +                   // __bold__
        "|\\*(.+?)\\*" +                 // *italic*
        "|_(.+?)_" +                     // _italic_
        "|`([^`]+)`" +                   // `code`
        "|\\[([^\\]]+)\\]\\([^)]+\\)"   // [text](url) → render text
    );

    public static boolean hasMarkdown(String text) {
        if (text == null) return false;
        return text.contains("**") || text.contains("```") || text.contains("__")
            || text.contains("\n# ") || text.contains("\n## ") || text.contains("\n### ")
            || text.startsWith("# ") || text.startsWith("## ")
            || (text.contains("\n- ") && text.indexOf("\n- ") < text.length() - 3)
            || text.contains("\n* ")
            || INLINE_PATTERN.matcher(text).find();
    }

    public static VBox render(String text, double maxWidth, boolean isDark) {
        VBox container = new VBox(3);

        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        boolean inCodeBlock = false;
        String codeLanguage = "";
        StringBuilder codeBuffer = new StringBuilder();
        List<String> paragraphLines = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (!paragraphLines.isEmpty()) {
                    flushParagraph(container, paragraphLines, maxWidth, isDark);
                    paragraphLines.clear();
                }
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLanguage = line.substring(3).trim();
                    codeBuffer = new StringBuilder();
                } else {
                    container.getChildren().add(buildCodeBlock(codeBuffer.toString(), codeLanguage, isDark));
                    inCodeBlock = false;
                    codeLanguage = "";
                    codeBuffer = new StringBuilder();
                }
            } else if (inCodeBlock) {
                if (codeBuffer.length() > 0) codeBuffer.append("\n");
                codeBuffer.append(line);
            } else {
                paragraphLines.add(line);
            }
        }

        if (inCodeBlock && codeBuffer.length() > 0) {
            container.getChildren().add(buildCodeBlock(codeBuffer.toString(), codeLanguage, isDark));
        }
        if (!paragraphLines.isEmpty()) {
            flushParagraph(container, paragraphLines, maxWidth, isDark);
        }

        return container;
    }

    private static void flushParagraph(VBox container, List<String> lines,
                                        double maxWidth, boolean isDark) {
        for (String line : lines) {
            if (line.isBlank()) {
                Region spacer = new Region();
                spacer.setPrefHeight(5);
                container.getChildren().add(spacer);
                continue;
            }

            if (line.startsWith("### ")) {
                container.getChildren().add(buildHeading(line.substring(4), 14, true, isDark));
            } else if (line.startsWith("## ")) {
                container.getChildren().add(buildHeading(line.substring(3), 16, true, isDark));
            } else if (line.startsWith("# ")) {
                container.getChildren().add(buildHeading(line.substring(2), 18, true, isDark));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                container.getChildren().add(buildBullet(line.substring(2), maxWidth, isDark));
            } else if (line.matches("^\\d+\\.\\s.*")) {
                int dot = line.indexOf('.');
                String num = line.substring(0, dot + 1);
                String content = line.substring(dot + 1).trim();
                container.getChildren().add(buildNumberedItem(num, content, maxWidth, isDark));
            } else {
                container.getChildren().add(buildInlineFlow(line, maxWidth, isDark));
            }
        }
    }

    private static Node buildHeading(String text, double size, boolean bold, boolean isDark) {
        TextFlow tf = new TextFlow();
        Text t = new Text(text);
        t.setFont(Font.font("Segoe UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        t.setFill(Color.web(isDark ? "#f0f6fc" : "#0d1117"));
        tf.getChildren().add(t);
        return tf;
    }

    private static Node buildBullet(String content, double maxWidth, boolean isDark) {
        HBox row = new HBox(6);
        Label bullet = new Label("•");
        bullet.setStyle("-fx-text-fill: " + (isDark ? "#6e7681" : "#656d76") + "; -fx-font-size: 14px;");
        bullet.setMinWidth(12);
        TextFlow tf = buildInlineFlow(content, maxWidth - 20, isDark);
        HBox.setHgrow(tf, Priority.ALWAYS);
        row.getChildren().addAll(bullet, tf);
        return row;
    }

    private static Node buildNumberedItem(String num, String content, double maxWidth, boolean isDark) {
        HBox row = new HBox(6);
        Label numLabel = new Label(num);
        numLabel.setStyle("-fx-text-fill: " + (isDark ? "#6e7681" : "#656d76") +
            "; -fx-font-size: 14px; -fx-min-width: 20;");
        TextFlow tf = buildInlineFlow(content, maxWidth - 30, isDark);
        HBox.setHgrow(tf, Priority.ALWAYS);
        row.getChildren().addAll(numLabel, tf);
        return row;
    }

    private static Node buildCodeBlock(String code, String language, boolean isDark) {
        String bg     = isDark ? "#161b22" : "#f6f8fa";
        String fg     = isDark ? "#e6edf3" : "#24292f";
        String border = isDark ? "#30363d" : "#d0d7de";
        String accent = langAccent(language, isDark);

        VBox block = new VBox(0);
        block.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-border-color: " + accent + " " + border + " " + border + " " + border + ";" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 10 12 10 14;"
        );

        if (!language.isEmpty()) {
            Label lang = new Label(language.toUpperCase());
            lang.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 10px;" +
                "-fx-font-weight: bold; -fx-padding: 0 0 6 0;");
            block.getChildren().add(lang);
        }

        Label codeLabel = new Label(code);
        codeLabel.setStyle(
            "-fx-font-family: 'Consolas', 'Courier New', monospace;" +
            "-fx-font-size: 13px; -fx-text-fill: " + fg + ";");
        codeLabel.setWrapText(true);
        block.getChildren().add(codeLabel);

        // ── Copy button overlay ──────────────────────────────────────
        Button copyBtn = new Button("⎘ Copy");
        copyBtn.setStyle(
            "-fx-background-color: " + (isDark ? "#30363d" : "#d0d7de") + ";" +
            "-fx-text-fill: " + (isDark ? "#8b949e" : "#656d76") + ";" +
            "-fx-font-size: 10px; -fx-padding: 2 7 2 7;" +
            "-fx-background-radius: 4; -fx-cursor: hand;");
        copyBtn.setOpacity(0.0);

        final String codeFinal = code;
        copyBtn.setOnAction(ev -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(codeFinal);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("✓ Copied");
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pause.setOnFinished(done -> copyBtn.setText("⎘ Copy"));
            pause.play();
        });

        StackPane stack = new StackPane(block, copyBtn);
        StackPane.setAlignment(copyBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(copyBtn, new javafx.geometry.Insets(6, 8, 0, 0));

        stack.setOnMouseEntered(e -> copyBtn.setOpacity(1.0));
        stack.setOnMouseExited (e -> { if (!copyBtn.getText().equals("✓ Copied")) copyBtn.setOpacity(0.0); });

        return stack;
    }

    private static String langAccent(String language, boolean isDark) {
        return switch (language.toLowerCase()) {
            case "java"       -> "#f89820";
            case "python", "py" -> "#3572A5";
            case "javascript", "js" -> isDark ? "#f7df1e" : "#b8860b";
            case "typescript", "ts" -> "#3178c6";
            case "kotlin"     -> "#7F52FF";
            case "rust"       -> "#dea584";
            case "go"         -> "#00ADD8";
            case "sql"        -> "#e38c00";
            case "bash", "sh", "shell" -> "#3fb950";
            case "css"        -> "#563d7c";
            case "html"       -> "#e44d26";
            case "json"       -> "#cbcb41";
            case "xml"        -> "#e4842d";
            default           -> isDark ? "#8b949e" : "#656d76";
        };
    }

    private static TextFlow buildInlineFlow(String text, double maxWidth, boolean isDark) {
        TextFlow tf = new TextFlow();
        tf.setMaxWidth(maxWidth);
        tf.getChildren().addAll(parseInline(text, isDark));
        return tf;
    }

    private static List<Text> parseInline(String raw, boolean isDark) {
        List<Text> result = new ArrayList<>();
        String fgColor   = isDark ? "#e2e8f0" : "#1a202c";
        String boldColor = isDark ? "#f0f6fc" : "#0d1117";
        String codeColor = isDark ? "#79c0ff" : "#0550ae";

        Matcher m = INLINE_PATTERN.matcher(raw);
        int last = 0;

        while (m.find()) {
            if (m.start() > last) {
                result.add(normal(raw.substring(last, m.start()), fgColor));
            }

            if (m.group(1) != null || m.group(2) != null) {
                String content = m.group(1) != null ? m.group(1) : m.group(2);
                Text t = new Text(content);
                t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
                t.setFill(Color.web(boldColor));
                result.add(t);
            } else if (m.group(3) != null || m.group(4) != null) {
                String content = m.group(3) != null ? m.group(3) : m.group(4);
                Text t = new Text(content);
                t.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 14));
                t.setFill(Color.web(fgColor));
                result.add(t);
            } else if (m.group(5) != null) {
                Text t = new Text(m.group(5));
                t.setFont(Font.font("Consolas", 13));
                t.setFill(Color.web(codeColor));
                result.add(t);
            } else if (m.group(6) != null) {
                result.add(normal(m.group(6), fgColor));
            }
            last = m.end();
        }

        if (last < raw.length()) {
            result.add(normal(raw.substring(last), fgColor));
        }

        if (result.isEmpty()) {
            result.add(normal(raw, fgColor));
        }
        return result;
    }

    private static Text normal(String content, String fgColor) {
        Text t = new Text(content);
        t.setFont(Font.font("Segoe UI", 14));
        t.setFill(Color.web(fgColor));
        return t;
    }
}
