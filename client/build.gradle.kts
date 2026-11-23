// Three plugins, one route each. `application` names the module and the class
// the launcher runs: the image is modular, so `jlink` links the module named
// here and the start scripts point at the same pair. `javafx` owns the JavaFX
// coordinates and the host's platform classifier, and the version comes from
// the catalogue. `jlink` owns the image and its launcher.
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

// The packaging proof: a stripped, compressed runtime image, one launcher named
// `workthread`. Both options are the measured ones: `--strip-debug` drops the
// native and class debug sections and `--compress zip-6` takes the image from
// about 338 MB to about 68 MB. `client/build/image` holds the result, and
// `docs/dev-env.md` records the size, the launch and the runtime prerequisites.
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
