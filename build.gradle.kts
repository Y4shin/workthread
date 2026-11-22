// The root build holds the shared module convention. Every module is a Java 21
// library; a module's own build file declares only its dependency edges, so the
// architecture's edge table is readable in one place per module.

import java.io.File
import java.lang.module.ModuleFinder
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Category

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

// The architecture's "May depend on" table, one row per module. This is the
// build's copy of the table, and the dependency check's only source for what a
// module may reach: a module with no row is not part of the architecture, so it
// cannot be checked at all.
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

    // The edge row this module obeys. A module with no row is not part of the
    // architecture, so no row can allow its dependencies and the build stops
    // instead of checking it against nothing.
    val edgeRow = moduleEdgeRows[name]
        ?: error("Module ':${name}' has no row in the architecture's edge table; a new module requires an architecture change first.")

    afterEvaluate {
        val module = name
        val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main").get()
        val declared = listOf(mainSourceSet.compileClasspathConfigurationName, mainSourceSet.runtimeClasspathConfigurationName)
            .flatMap { classpath -> configurations.getByName(classpath).hierarchy }
            .flatMap { configuration -> configuration.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { dependency -> dependency.name }
            .distinct()
            .sorted()

        tasks.register<VerifyModuleDependencies>("verifyModuleDependencies") {
            group = "verification"
            description = "Refuses a declared dependency the module's edge row does not allow."
            moduleName.set(module)
            allowedEdges.set(edgeRow)
            moduleDependencies.set(declared)
        }
        tasks.named("check") { dependsOn("verifyModuleDependencies") }
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

// ---------------------------------------------------------------------------
// The dependency rules, and the fixtures that prove their refusals
// ---------------------------------------------------------------------------

/**
 * A rule that names every violation it finds, then refuses the build. The
 * message is part of the behaviour: it says which module, and what it declared,
 * so a reader can fix the build without opening the rule.
 */
abstract class DependencyRule {

    abstract fun violations(): List<String>

    fun verify() {
        val violations = violations()
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n"))
        }
    }
}

/**
 * The declared-dependency rule: a module reaches only the modules its edge row
 * names.
 */
class ModuleDependencyRule(
    private val module: String,
    private val allowedEdges: List<String>,
    private val moduleDependencies: List<String>,
) : DependencyRule() {

    override fun violations(): List<String> = moduleDependencies
        .filterNot { dependency -> dependency in allowedEdges }
        .map { dependency ->
            "$module declares a dependency on $dependency, which its edge row does not allow; the row allows ${row()}"
        }

    private fun row(): String = if (allowedEdges.isEmpty()) "nothing" else allowedEdges.joinToString(separator = ", ")
}

/**
 * One module's declared dependencies, checked against its edge row. The task
 * reads the declarations the module's own build file made, across both
 * classpath hierarchies, because a module may declare an edge as `api` or as
 * `implementation` and the edge table names the edge, not the configuration.
 */
abstract class VerifyModuleDependencies : DefaultTask() {

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val allowedEdges: ListProperty<String>

    @get:Input
    abstract val moduleDependencies: ListProperty<String>

    @TaskAction
    fun verifyDeclaredDependencies() {
        ModuleDependencyRule(
            module = moduleName.get(),
            allowedEdges = allowedEdges.get(),
            moduleDependencies = moduleDependencies.get(),
        ).verify()
    }
}

/**
 * A declared violation and the refusal it must produce. The expected message is
 * written from the architecture's wording, not from the rule's code, and null
 * means the rule must accept the declaration.
 */
class ModuleDependencyFixture(
    val what: String,
    val module: String,
    val allowedEdges: List<String>,
    val moduleDependencies: List<String>,
    val expectedRefusal: String?,
)

// Each fixture declares a violation the rule has to refuse. The first is
// criterion 1: a dependency outside the module's edge row. The second guards
// the other half: an edge the row names is allowed, or every module would fail.
val moduleDependencyFixtures = listOf(
    ModuleDependencyFixture(
        what = "the core declares the cli",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("cli"),
        expectedRefusal = "core declares a dependency on cli, which its edge row does not allow; the row allows contract",
    ),
    ModuleDependencyFixture(
        what = "the core declares the contract",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("contract"),
        expectedRefusal = null,
    ),
)

/** The message a rule refuses with, or null when it accepts. */
fun refusalOf(rule: DependencyRule): String? =
    try {
        rule.verify()
        null
    } catch (refusal: GradleException) {
        refusal.message
    }

/** Runs every fixture and collects the ones whose rule did not answer as the fixture says. */
fun dependencyRuleFixtureProblems(): List<String> {
    val problems = mutableListOf<String>()
    moduleDependencyFixtures.forEach { fixture ->
        val refusal = refusalOf(
            ModuleDependencyRule(
                module = fixture.module,
                allowedEdges = fixture.allowedEdges,
                moduleDependencies = fixture.moduleDependencies,
            ),
        )
        val expected = fixture.expectedRefusal
        when {
            expected == null && refusal != null ->
                problems.add("Fixture '${fixture.what}' was refused, and it must be accepted: $refusal")
            expected != null && refusal == null ->
                problems.add("Fixture '${fixture.what}' was accepted, and it must be refused with: $expected")
            expected != null && refusal != null && !refusal.contains(expected) ->
                problems.add("Fixture '${fixture.what}' was refused with '$refusal', and the refusal must carry: $expected")
        }
    }
    return problems
}

tasks.register("verifyDependencyRuleFixtures") {
    group = "verification"
    description = "Proves each dependency rule refuses a declared violation with a message that names it."
    doLast {
        val problems = dependencyRuleFixtureProblems()
        if (problems.isNotEmpty()) {
            throw GradleException(
                "The dependency rule fixtures did not hold:\n" + problems.joinToString(separator = "\n") { problem -> "  $problem" },
            )
        }
        println("${moduleDependencyFixtures.size} dependency rule fixtures held")
    }
}
