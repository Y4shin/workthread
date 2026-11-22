// JavaFX arrives through the plugin alone: it owns the coordinates and the
// host's platform classifier, and the version comes from the catalogue.
//
// The `application` plugin names the module and the class the launcher runs,
// because the image is modular: `jlink` links the module named here, and the
// start scripts and the image both point at the same pair.
plugins {
    application
    alias(libs.plugins.javafx)
    alias(libs.plugins.jlink)
}

application {
    mainModule.set("io.github.y4shin.workthread.client")
    mainClass.set("io.github.y4shin.workthread.client.WorkthreadApplication")
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
}

// The packaging proof: a stripped, compressed runtime image with one launcher
// named `workthread`. The two options are the measured ones: `--strip-debug`
// drops the debug sections and `--compress zip-6` takes the image from about
// 338 MB to about 68 MB. The launcher name is the command the record documents.
jlink {
    options = listOf("--strip-debug", "--compress", "zip-6")
    launcher {
        name = "workthread"
    }
}

dependencies {
    implementation(project(":contract"))
    implementation(project(":wiring"))
}
