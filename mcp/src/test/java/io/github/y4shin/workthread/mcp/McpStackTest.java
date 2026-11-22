package io.github.y4shin.workthread.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The MCP module's declared stack, observed through the resolved test runtime
 * classpath. The seam is the build.
 */
class McpStackTest {

    @Test
    void theAggregatorAndItsPayloadResolveAtThePinnedVersion() {
        assertThat(resolvedJars("mcp-"))
                .as("the MCP SDK aggregator and the two payload jars")
                .contains("mcp-2.1.0.jar", "mcp-core-2.1.0.jar", "mcp-json-jackson3-2.1.0.jar");
    }

    @Test
    void theJsonMapperAndSchemaValidatorClosureIsPresent() {
        assertThat(resolvedJars("json-schema-validator-")).contains("json-schema-validator-3.0.6.jar");
        assertThat(resolvedJars("jackson-databind-"))
                .as("the settled Jackson 3 line, not Jackson 2")
                .isNotEmpty()
                .allSatisfy(jar -> assertThat(jar).matches("jackson-databind-3\\..*\\.jar"));
        assertThat(resolvedJars("jackson-annotations-"))
                .as("the shared Jackson 2.21 annotations artifact")
                .isNotEmpty()
                .allSatisfy(jar -> assertThat(jar).startsWith("jackson-annotations-2.21"));

        assertThat(isLoadableWithoutInitialisation("io.modelcontextprotocol.spec.McpSchema")).isTrue();
        assertThat(isLoadableWithoutInitialisation("io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper"))
                .isTrue();
        assertThat(isLoadableWithoutInitialisation("com.networknt.schema.SchemaRegistry")).isTrue();
        assertThat(isLoadableWithoutInitialisation("tools.jackson.databind.ObjectMapper")).isTrue();
    }

    private static List<String> resolvedJars(String prefix) {
        List<String> names = new ArrayList<>();
        for (String property : List.of("java.class.path", "jdk.module.path")) {
            String value = System.getProperty(property);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String entry : value.split(File.pathSeparator)) {
                String name = Path.of(entry).getFileName().toString();
                if (name.startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static boolean isLoadableWithoutInitialisation(String className) {
        try {
            Class.forName(className, false, McpStackTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
