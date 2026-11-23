package io.github.y4shin.workthread.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The build's version, read from the resource Gradle generates from the one
 * version property in {@code gradle.properties}. Nothing in this module holds
 * the number itself: a copy here would drift from the build on the next bump,
 * and the window would advertise a version that was never built.
 */
final class WorkthreadVersion {

    private static final String RESOURCE = "/workthread-version.properties";

    private WorkthreadVersion() {
    }

    static String value() {
        try (InputStream resource = WorkthreadVersion.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(resource);
            return properties.getProperty("version", "unknown");
        } catch (IOException unreadable) {
            return "unknown";
        }
    }
}
