package io.github.y4shin.workthread.adapters;

/**
 * Stands in for the first real type of the adapters module. A module descriptor
 * cannot export a package that holds no type, and the exported package is how
 * the rest of the build reads this module. The effort that owns adapters replaces
 * this type rather than deleting it.
 */
public final class AdaptersPlaceholder {

    private AdaptersPlaceholder() {
    }
}
