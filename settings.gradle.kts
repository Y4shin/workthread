rootProject.name = "workthread"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// The architecture's "May depend on" table, one row per module. This is the one
// place a module's name is spelled: the projects are these eight keys, and the
// root build reads the same map for its dependency, descriptor and packaging
// checks. A module with no row is not part of the architecture, so a new module
// requires an architecture change before it appears here.
val moduleEdgeRows: Map<String, List<String>> = mapOf(
    "contract" to listOf(),
    "core" to listOf("contract"),
    "adapters" to listOf("contract"),
    "wiring" to listOf("contract"),
    "cli" to listOf("contract", "wiring"),
    "mcp" to listOf("contract", "wiring"),
    "api" to listOf("contract", "wiring"),
    "client" to listOf("contract", "wiring"),
)

include(*moduleEdgeRows.keys.toTypedArray())

// One project per row, and one row per project. The root build reads this copy
// through `gradle.ext`, because a build script cannot read a settings value.
gradle.extensions.extraProperties.set("moduleEdgeRows", moduleEdgeRows)
