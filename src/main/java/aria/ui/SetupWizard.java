package aria.ui;

import aria.core.AriaCore;
import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

public class SetupWizard {

    private final AriaCore core;
    private final Stage stage;

    private VBox chatBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private Button nextButton;
    private Label stepLabel;
    private ProgressBar progressBar;

    private int currentStep = 0;
    private final List<String> questions = Arrays.asList(
        "who are you? like, what do I call you, and what's your deal?",
        "what world am I existing in right now? real world, a game, a story — what's the context?",
        "so what's my role here? who am I supposed to be in this world?",
        "what do you actually need me for? casual chat, help with something specific, roleplay, what?",
        "last one — is there anything I absolutely should or shouldn't know in this context? limits, boundaries, special knowledge?"
    );

    private final String[] fieldKeys = {
        "user_name", "world_name", "aria_role", "intended_use", "knowledge_bounds"
    };

    private final Map<String, String> collected = new LinkedHashMap<>();
    private boolean waitingForInput = false;

    public SetupWizard(AriaCore core, Stage stage) {
        this.core = core;
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();

        root.setTop(buildHeader());
        root.setCenter(buildChatArea());
        root.setBottom(buildInputArea());

        Scene scene = new Scene(root, 780, 580);
        stage.setScene(scene);
        stage.setTitle("ARIA — First Setup");
        stage.setMinWidth(600);
        stage.setMinHeight(450);
        stage.show();

        Platform.runLater(() -> {
            addAriaMessage("hey. I'm here. I don't really know the context yet.");
            showNextQuestion();
        });
    }

    private VBox buildHeader() {
        VBox header = new VBox(6);
        header.setPadding(new Insets(14, 20, 10, 20));
        header.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        Label title = new Label("ARIA — First Setup");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        stepLabel = new Label("Step 1 of " + questions.size());
        stepLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(5);
        progressBar.setStyle("-fx-accent: -color-accent-fg;");

        header.getChildren().addAll(title, stepLabel, progressBar);
        return header;
    }

    private BorderPane buildChatArea() {
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(16));
        chatBox.setFillWidth(true);

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane center = new BorderPane(scrollPane);
        return center;
    }

    private HBox buildInputArea() {
        HBox inputArea = new HBox(10);
        inputArea.setPadding(new Insets(12, 16, 12, 16));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        inputField = new TextField();
        inputField.setPromptText("Your answer...");
        inputField.setStyle("-fx-font-size: 14px;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> handleInput());

        nextButton = new Button("Next →");
        nextButton.getStyleClass().add("accent");
        nextButton.setStyle("-fx-cursor: hand;");
        nextButton.setOnAction(e -> handleInput());

        inputArea.getChildren().addAll(inputField, nextButton);
        return inputArea;
    }

    private void showNextQuestion() {
        if (currentStep >= questions.size()) {
            finishSetup();
            return;
        }

        String question = questions.get(currentStep);
        stepLabel.setText("Step " + (currentStep + 1) + " of " + questions.size());
        progressBar.setProgress((double) currentStep / questions.size());

        addAriaMessageDelayed(question, 400);
        waitingForInput = true;
        Platform.runLater(() -> inputField.requestFocus());
    }

    private void handleInput() {
        if (!waitingForInput) return;
        String answer = inputField.getText().trim();
        if (answer.isEmpty()) return;

        waitingForInput = false;
        inputField.clear();
        inputField.setDisable(true);
        nextButton.setDisable(true);

        addUserMessage(answer);
        collected.put(fieldKeys[currentStep], answer);

        currentStep++;

        if (currentStep < questions.size()) {
            progressBar.setProgress((double) currentStep / questions.size());
            showBridgeResponse(answer, () -> {
                inputField.setDisable(false);
                nextButton.setDisable(false);
                showNextQuestion();
            });
        } else {
            finishSetup();
        }
    }

    private void showBridgeResponse(String answer, Runnable onDone) {
        String[] bridges = {
            "got it.",
            "okay, makes sense.",
            "noted.",
            "alright.",
            "okay."
        };
        String bridge = bridges[(int)(Math.random() * bridges.length)];
        addAriaMessageDelayed(bridge, 300);
        new Timeline(new KeyFrame(Duration.millis(700), e -> Platform.runLater(onDone))).play();
    }

    private void finishSetup() {
        inputField.setDisable(true);
        nextButton.setDisable(true);
        progressBar.setProgress(1.0);
        stepLabel.setText("Setting up...");

        addAriaMessageDelayed("okay, I think I have enough to go on. give me a second.", 400);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                saveContext();
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            String worldName = collected.getOrDefault("world_name", "this world");
            String role = collected.getOrDefault("aria_role", "your companion");

            String summary = "alright. world: " + worldName + ". my role: " + role + ". I think I've got it. let's go.";
            addAriaMessage(summary);

            new Timeline(new KeyFrame(Duration.millis(1200), ev -> openMainWindow())).play();
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void saveContext() {
        String worldAnswer = collected.getOrDefault("world_name", "Unknown World");
        String[] words = worldAnswer.trim().split("\\s+");
        String shortName = words.length <= 4 ? worldAnswer
            : String.join(" ", java.util.Arrays.copyOfRange(words, 0, 4));

        JsonObject ctx = new JsonObject();
        ctx.addProperty("world_name", shortName);
        ctx.addProperty("world_description", worldAnswer);
        ctx.addProperty("aria_role", collected.getOrDefault("aria_role", "companion"));
        ctx.addProperty("knowledge_bounds", collected.getOrDefault("knowledge_bounds", ""));
        ctx.addProperty("knowledge_limits", "");
        ctx.addProperty("intended_use", collected.getOrDefault("intended_use", "general companion"));

        String userName = collected.getOrDefault("user_name", "");
        if (!userName.isEmpty()) {
            core.setConfig("AUTHOR_NAME", userName);
            core.getConfigStore().saveToFile();
        }

        core.getContextManager().setWorldContext(ctx);
    }

    private void openMainWindow() {
        MainWindow mainWindow = new MainWindow(core, stage);
        mainWindow.show();
    }

    private void addAriaMessage(String text) {
        HBox wrapper = new HBox(10);
        wrapper.setAlignment(Pos.TOP_LEFT);

        Label avatarLabel = new Label("◈");
        avatarLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-accent-fg; -fx-padding: 4 0 0 0;");

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(480);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 14 14 14 4; -fx-font-size: 14px; -fx-border-color: -color-border-default; -fx-border-radius: 14 14 14 4; -fx-border-width: 1;");

        wrapper.getChildren().addAll(avatarLabel, bubble);

        FadeTransition ft = new FadeTransition(Duration.millis(250), wrapper);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        chatBox.getChildren().add(wrapper);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void addAriaMessageDelayed(String text, int delayMs) {
        new Timeline(new KeyFrame(Duration.millis(delayMs), e -> addAriaMessage(text))).play();
    }

    private void addUserMessage(String text) {
        HBox wrapper = new HBox();
        wrapper.setAlignment(Pos.CENTER_RIGHT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(480);
        bubble.setPadding(new Insets(9, 14, 9, 14));
        bubble.setStyle("-fx-background-color: -color-accent-emphasis; -fx-text-fill: white; -fx-background-radius: 14 14 4 14; -fx-font-size: 14px;");

        wrapper.getChildren().add(bubble);

        FadeTransition ft = new FadeTransition(Duration.millis(200), wrapper);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        chatBox.getChildren().add(wrapper);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}
