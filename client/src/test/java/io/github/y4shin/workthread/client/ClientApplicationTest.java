package io.github.y4shin.workthread.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import javafx.application.Application;
import org.junit.jupiter.api.Test;

/**
 * The image's launcher runs the client's application class through JavaFX, so
 * the suite holds the shape that launcher depends on: a concrete, public
 * subclass of {@link Application} in the exported package. This stays
 * toolkit-free: reading the class's shape does not start the platform, and no
 * display is needed.
 */
class ClientApplicationTest {

    @Test
    void theClientRunsAConcreteJavaFxApplication() {
        assertThat(Application.class)
                .as("the client's window class, as the launcher constructs it")
                .isAssignableFrom(WorkthreadApplication.class);
        assertThat(Modifier.isPublic(WorkthreadApplication.class.getModifiers()))
                .as("a class javafx.graphics can construct from another module")
                .isTrue();
        assertThat(Modifier.isAbstract(WorkthreadApplication.class.getModifiers()))
                .as("javafx.graphics cannot construct an abstract class")
                .isFalse();
    }
}
