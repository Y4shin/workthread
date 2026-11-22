package io.github.y4shin.workthread.mcp;

/**
 * Stands in for the first real type of the mcp module. A module descriptor
 * cannot export a package that holds no type, and the exported package is how
 * the rest of the build reads this module. The effort that owns mcp replaces
 * this type rather than deleting it.
 */
public final class McpPlaceholder {

    private McpPlaceholder() {
    }
}
