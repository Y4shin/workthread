rootProject.name = "workthread"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// The eight modules of the architecture's "May depend on" table. The table is
// the authoritative edge list: a new module requires an architecture change
// before it appears here.
include(
    "contract",
    "core",
    "adapters",
    "wiring",
    "cli",
    "mcp",
    "api",
    "client",
)
