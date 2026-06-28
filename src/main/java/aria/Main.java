package aria;

import aria.core.AriaCore;
import aria.ui.MainWindow;
import aria.ui.SetupWizard;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    private AriaCore core;

    @Override
    public void start(Stage primaryStage) throws Exception {
        core = new AriaCore();

        String theme = core.getConfig("THEME", "dark");
        if ("light".equalsIgnoreCase(theme)) {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        }

        if (core.needsSetup()) {
            SetupWizard wizard = new SetupWizard(core, primaryStage);
            wizard.show();
        } else {
            MainWindow mainWindow = new MainWindow(core, primaryStage);
            mainWindow.show();
        }
    }

    @Override
    public void stop() throws Exception {
        if (core != null) {
            core.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
