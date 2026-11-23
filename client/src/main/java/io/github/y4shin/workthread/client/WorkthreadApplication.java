package io.github.y4shin.workthread.client;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The client's JavaFX entry point, and the packaging proof: it shows one empty
 * window and holds no state. The image's launcher runs this class, so the
 * window is how the linked runtime is observed rather than described.
 *
 * <p>The window is the ticket's fixed shape: the title {@code workthread}, a
 * 1024x768 scene, and one centred label reading {@code workthread 0.1.0}. The
 * label's text is the window's content, not a second version source; the
 * project version stays in {@code gradle.properties}.
 */
public final class WorkthreadApplication extends Application {

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label("workthread 0.1.0"));
        stage.setTitle("workthread");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    // One entry point, so the launcher runs this class the same way whether it
    // is started from the image or from a module path.
    public static void main(String[] args) {
        launch(args);
    }
}
