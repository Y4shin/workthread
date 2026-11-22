// JavaFX arrives through the plugin alone: it owns the coordinates and the
// host's platform classifier, and the version comes from the catalogue.
plugins {
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":contract"))
    implementation(project(":wiring"))
}
