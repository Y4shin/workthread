// wiring is a named module, so the client's dependency chain can be linked into
// an image. The descriptor is the single source for this module's name, its
// inward edges, and the package it publishes.
module io.github.y4shin.workthread.wiring {
    requires io.github.y4shin.workthread.contract;

    exports io.github.y4shin.workthread.wiring;
}
