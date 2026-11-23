// client is a named module. The descriptor is the single source for the module's
// name, its inward edges, and the package it publishes.
module io.github.y4shin.workthread.client {
    requires io.github.y4shin.workthread.contract;
    requires io.github.y4shin.workthread.wiring;
    // The declared stack puts JavaFX in the client alone. `javafx.controls`
    // reads `javafx.graphics`, which holds the `Application` subclass's own
    // supertype, so one clause covers the window and its controls.
    requires javafx.controls;

    exports io.github.y4shin.workthread.client;
}
