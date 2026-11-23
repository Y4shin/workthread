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

// The version reaches the window the same way it reaches every jar: Gradle
// writes it from the one property in `gradle.properties`. The client reads a
// generated resource, so no source file restates the number and the label
// cannot drift when the version moves.
val generateVersionResource = tasks.register<GenerateVersionResource>("generateVersionResource") {
    version.set(project.version.toString())
    outputDirectory.set(layout.buildDirectory.dir("generated/version-resources"))
}

sourceSets {
    main {
        resources.srcDir(generateVersionResource)
    }
}

// The suite compares the label against the build's own version, so the one
// source's value is a task input rather than a copy in the test.
tasks.named<Test>("test") {
    systemProperty("workthread.version", project.version)
}

/**
 * Writes the window's version resource from the one version property, so the
 * client reads the build's number instead of carrying a copy of it.
 */
abstract class GenerateVersionResource : DefaultTask() {

    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun writeVersionResource() {
        val resource = outputDirectory.get().file("workthread-version.properties").asFile
        resource.parentFile.mkdirs()
        resource.writeText("version=${version.get()}\n")
    }
}
