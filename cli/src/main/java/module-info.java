// cli is a named module. The descriptor is the single source for the module's
// name, its inward edges, and the package it publishes.
module io.github.y4shin.workthread.cli {
    requires io.github.y4shin.workthread.contract;
    requires io.github.y4shin.workthread.wiring;

    exports io.github.y4shin.workthread.cli;
}
