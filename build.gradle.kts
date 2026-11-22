// The root build holds the shared module convention. Every module is a Java 21
// library; a module's own build file declares only its dependency edges, so the
// architecture's edge table is readable in one place per module.

import java.io.File
import java.lang.module.ModuleFinder
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    base
}

// The test libraries every module runs, taken from the one version catalogue.
// Read here rather than inside the subprojects block, where the catalogue
// accessor resolves against the subproject.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val junitBom = catalog.findLibrary("junit-bom").get()
val junitJupiter = catalog.findLibrary("junit-jupiter").get()
val junitPlatformLauncher = catalog.findLibrary("junit-platform-launcher").get()
val assertjCore = catalog.findLibrary("assertj-core").get()

// The pinned jars, and the closure jars the dependency notes name, so their
// shape is confirmed on the jar rather than inferred from a POM. Artifact names
// only: the versions live in the catalogue.
val pinnedJarArtifactNames = listOf(
    "javafx-base",
    "javafx-controls",
    "javafx-graphics",
    "spring-boot",
    "spring-boot-starter-webmvc",
    "mcp",
    "mcp-json-jackson3",
    "sqlite-jdbc",
    "mcp-core",
    "json-schema-validator",
    "slf4j-api",
    "reactor-core",
    "jackson-annotations",
    "jackson-databind",
    "junit-jupiter-api",
    "assertj-core",
)

subprojects {
    apply(plugin = "java-library")

    // One source: the group and version properties in gradle.properties.
    group = rootProject.group
    version = rootProject.version

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    // JUnit 5 and AssertJ in every module, so no module can forget its tests.
    dependencies {
        add("testImplementation", platform(junitBom))
        add("testImplementation", junitJupiter)
        add("testRuntimeOnly", junitPlatformLauncher)
        add("testImplementation", assertjCore)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // One printing task per module, so a name is always read against the module
    // whose classpath resolved it: the same artifact can resolve at two versions
    // in two modules, and the record must say which.
    tasks.register<PrintPinnedModuleNames>("printPinnedModuleNames") {
        moduleName.set(project.name)
        artifactNames.set(pinnedJarArtifactNames)
        resolvedJars.from(configurations.named("testRuntimeClasspath"))
        report.set(layout.buildDirectory.file("reports/declared-stack/module-names.tsv"))
    }
}

// One recorded command for the declared stack: it prints the module name each
// pinned jar carries once Gradle has resolved it, grouped by the module that
// resolved it. The architecture's dependency notes record the expected names;
// this task turns them into a fact of the build rather than a claim about a
// POM.
//
// Artifact names only, no versions: every version comes from the catalogue.
abstract class PrintPinnedModuleNames : DefaultTask() {

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val artifactNames: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resolvedJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun writeModuleNames() {
        val patterns = artifactNames.get().map { artifact -> Regex(Regex.escape(artifact) + "-[0-9].*\\.jar") }
        val lines = resolvedJars.files
            .filter { jar -> patterns.any { pattern -> pattern.matches(jar.name) } }
            .distinctBy { jar -> jar.name }
            .sortedBy { jar -> jar.name }
            .map { jar -> "${moduleName.get()}\t${describe(jar)}" }
        report.get().asFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun describe(jar: File): String {
        val module = ModuleFinder.of(jar.toPath()).findAll().firstOrNull()
        if (module == null) {
            return "classpath-only\t${jar.name}"
        }
        val shape = if (module.descriptor().isAutomatic) "automatic" else "named"
        return "${module.descriptor().name()}\t$shape\t${jar.name}"
    }
}

tasks.register("printPinnedModuleNames") {
    group = "help"
    description = "Prints the module name of every pinned jar on the resolved test runtime classpaths."
    val reports = subprojects.map { subproject ->
        subproject.tasks.named<PrintPinnedModuleNames>("printPinnedModuleNames").flatMap { task -> task.report }
    }
    dependsOn(reports)
    // The root task prints, so the record is ordered by module and not by
    // whichever task finished first.
    doLast {
        reports.forEach { report ->
            val text = report.get().asFile.readText().trimEnd()
            if (text.isNotEmpty()) {
                println(text)
            }
        }
    }
}
