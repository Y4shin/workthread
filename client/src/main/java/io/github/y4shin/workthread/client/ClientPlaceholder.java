package io.github.y4shin.workthread.client;

/**
 * Stands in for the first real type of the client module. A module descriptor
 * cannot export a package that holds no type, and the exported package is how
 * the rest of the build reads this module. The effort that owns client replaces
 * this type rather than deleting it.
 */
public final class ClientPlaceholder {

    private ClientPlaceholder() {
    }
}
