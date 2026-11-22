package io.github.y4shin.workthread.core;

/**
 * Stands in for the first real type of the core module. A module descriptor
 * cannot export a package that holds no type, and the exported package is how
 * the rest of the build reads this module. The effort that owns core replaces
 * this type rather than deleting it.
 */
public final class CorePlaceholder {

    private CorePlaceholder() {
    }
}
