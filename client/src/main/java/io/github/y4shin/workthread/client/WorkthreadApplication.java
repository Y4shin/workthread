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
 * 1024x768 scene, and one centred label reading {@code workthread <version>}.
 * The label takes its number from {@link WorkthreadVersion}, which reads the
 * resource Gradle generates from {@code gradle.properties}; this class holds no
 * copy of the version.
 *
 * <p>The other seven modules carry an {@code XPlaceholder} type, and this class
 * is deliberately not called {@code ClientPlaceholder}. A placeholder stands in
 * for the first real type of a module that has none yet; this class <em>is</em>
 * the client's entry point, the one the image launcher runs, and the image work
 * replaces it in place instead of deleting a stand-in.
 */
public final class WorkthreadApplication extends Application {

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label(windowLabel()));
        stage.setTitle("workthread");
        stage.setScene(new Scene(root, 1024, 768));
        stage.show();
    }

    // The window's one line of text: the title word and the build's version.
    static String windowLabel() {
        return "workthread " + WorkthreadVersion.value();
    }

    // One entry point, so the launcher runs this class the same way whether it
    // is started from the image or from a module path.
    public static void main(String[] args) {
        launch(args);
    }
}
