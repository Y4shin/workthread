// The root build holds the shared module convention. Every module is a Java 21
// library; a module's own build file declares only its dependency edges, so the
// architecture's edge table is readable in one place per module.

import java.io.File
import java.lang.module.ModuleDescriptor
import java.lang.module.ModuleFinder
import java.util.zip.ZipFile
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

// The package root, one subpackage per module. A module's name is its package:
// `contract` is the module `io.github.y4shin.workthread.contract` and exports
// `io.github.y4shin.workthread.contract`. One derivation, so a module's name and
// its one exported package cannot drift apart.
val packageRoot = "io.github.y4shin.workthread"
fun qualifiedNameOf(module: String): String = "$packageRoot.$module"

// Seven modules carry a descriptor. `api` stays on the classpath, because
// Spring Boot's classpath model fights named modules, so its jar carries none.
// One set, so the classpath module is not a special case anywhere else in this
// script.
val modulesOnTheClasspath: Set<String> = setOf("api")

// The client's descriptor must export the package that holds its `Application`
// subclass, or JavaFX cannot construct it from the image. A placeholder type
// stands in for that subclass until the client-image ticket replaces it.
val clientApplicationClass = "$packageRoot.client.WorkthreadApplication"

// The declared stack puts JavaFX in the client alone. The runtime only warns
// when JavaFX sits on the classpath, so the build refuses a JavaFX artifact
// everywhere else.
val moduleRowsCarryingJavaFx: Set<String> = setOf("client")

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

    // The runtime classpath check: a JavaFX artifact on the classpath instead of
    // the module path is a leak the runtime only warns about, so the build
    // refuses it.
    tasks.register<VerifyNoJavaFxOnRuntimeClasspath>("verifyNoJavaFxOnRuntimeClasspath") {
        group = "verification"
        description = "Refuses a JavaFX artifact on a runtime classpath the declared stack does not allow one on."
        moduleName.set(project.name)
        configurationName.set("runtimeClasspath")
        mayCarryJavaFx.set(project.name in moduleRowsCarryingJavaFx)
        runtimeClasspath.from(configurations.named("runtimeClasspath"))
    }
    tasks.named("check") { dependsOn("verifyNoJavaFxOnRuntimeClasspath") }

    // The edge row this module obeys. A module with no row is not part of the
    // architecture, so no row can allow its dependencies and the build stops
    // instead of checking it against nothing.
    val edgeRow = moduleEdgeRows[name]
        ?: error("Module ':${name}' has no row in the architecture's edge table; a new module requires an architecture change first.")

    // Read after the module's own build file has run, so its declarations are
    // in. A plugin that adds coordinates later (the JavaFX plugin does) is the
    // runtime classpath check's business: what a module *declares* is what a
    // reader finds in its build file.
    afterEvaluate {
        val module = name
        val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main").get()
        val declared = listOf(mainSourceSet.compileClasspathConfigurationName, mainSourceSet.runtimeClasspathConfigurationName)
            .flatMap { classpath -> configurations.getByName(classpath).hierarchy }
            .flatMap { configuration -> configuration.dependencies }
            .filterNot { dependency -> dependency.isDependencyPlatform() }
            .distinct()
        val edges = declared
            .filterIsInstance<ProjectDependency>()
            .map { dependency -> dependency.name }
            .distinct()
            .sorted()
        val libraryCoordinates = declared
            .filterIsInstance<ExternalModuleDependency>()
            .map { dependency -> "${dependency.group.orEmpty()}:${dependency.name}" }
            .distinct()
            .sorted()

        tasks.register<VerifyModuleDependencies>("verifyModuleDependencies") {
            group = "verification"
            description = "Refuses a declared dependency the module's edge row does not allow, and Spring or JavaFX in the core."
            moduleName.set(module)
            allowedEdges.set(edgeRow)
            moduleDependencies.set(edges)
            libraries.set(libraryCoordinates)
        }
        tasks.named("check") { dependsOn("verifyModuleDependencies") }
    }

    // The descriptor check: the seven modules that reach the image are named
    // modules and `api` is not. The descriptor is the single source for a
    // module's name and exports, so the build reads the built jar rather than a
    // build script's text.
    tasks.register<VerifyModuleDescriptor>("verifyModuleDescriptor") {
        group = "verification"
        description = "Refuses a module whose descriptor is missing, misnamed, or declares something its edge row does not allow."
        moduleName.set(project.name)
        expectedModuleName.set(if (project.name in modulesOnTheClasspath) "" else qualifiedNameOf(project.name))
        expectedPackage.set(qualifiedNameOf(project.name))
        allowedModuleNames.set(edgeRow.map { dependency -> qualifiedNameOf(dependency) })
        moduleNamespace.set("$packageRoot.")
        applicationClass.set(clientApplicationClass.takeIf { project.name == "client" }.orEmpty())
        descriptorJar.set(tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile })
    }
    tasks.named("check") { dependsOn("verifyModuleDescriptor") }
}

/**
 * A dependency platform carries constraints, not code: `api` imports the Spring
 * Boot BOM that way. The edge table governs what a module reaches, and a
 * platform reaches nothing, so the rule does not judge it.
 */
fun Dependency.isDependencyPlatform(): Boolean {
    val category = (this as? ModuleDependency)?.attributes?.getAttribute(Category.CATEGORY_ATTRIBUTE) ?: return false
    return category.name == Category.REGULAR_PLATFORM || category.name == Category.ENFORCED_PLATFORM
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
// The dependency rules
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
 * names, and the core carries no framework, or standalone mode needs Spring too.
 */
class ModuleDependencyRule(
    private val module: String,
    private val allowedEdges: List<String>,
    private val moduleDependencies: List<String>,
    private val libraries: List<String>,
) : DependencyRule() {

    override fun violations(): List<String> {
        val violations = mutableListOf<String>()
        moduleDependencies
            .filterNot { dependency -> dependency in allowedEdges }
            .forEach { dependency ->
                violations.add("$module declares a dependency on $dependency, which its edge row does not allow; the row allows ${row()}")
            }
        if (module == CORE) {
            libraries.forEach { library ->
                val framework = frameworkOf(library) ?: return@forEach
                violations.add("$module declares $library, which is $framework; the core carries no framework")
            }
        }
        return violations
    }

    private fun row(): String = if (allowedEdges.isEmpty()) "nothing" else allowedEdges.joinToString(separator = ", ")

    // The core's rule is about families, not single artifacts: Spring Boot's
    // artifacts sit under org.springframework.boot and the framework's under
    // org.springframework, and the JavaFX plugin's artifacts under org.openjfx.
    private fun frameworkOf(library: String): String? {
        val group = library.substringBefore(':')
        val artifact = library.substringAfter(':')
        return when {
            group.startsWith("org.springframework") -> "Spring"
            group == "org.openjfx" || artifact.startsWith("javafx-") -> "JavaFX"
            else -> null
        }
    }

    private companion object {
        const val CORE = "core"
    }
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

    @get:Input
    abstract val libraries: ListProperty<String>

    @TaskAction
    fun verifyDeclaredDependencies() {
        ModuleDependencyRule(
            module = moduleName.get(),
            allowedEdges = allowedEdges.get(),
            moduleDependencies = moduleDependencies.get(),
            libraries = libraries.get(),
        ).verify()
    }
}

/**
 * The runtime classpath rule: JavaFX is allowed on the client's classpath and
 * on no other module's, and the refusal names the configuration and the
 * artifact so a reader knows where it came from.
 */
class RuntimeClasspathRule(
    private val module: String,
    private val configuration: String,
    private val artifacts: List<String>,
    private val mayCarryJavaFx: Boolean,
) : DependencyRule() {

    override fun violations(): List<String> {
        if (mayCarryJavaFx) {
            return listOf()
        }
        return artifacts
            .filter { artifact -> artifact.startsWith(JAVA_FX_PREFIX) }
            .distinct()
            .sorted()
            .map { artifact -> "$module:$configuration carries $artifact; JavaFX belongs to the client module alone" }
    }

    private companion object {
        const val JAVA_FX_PREFIX = "javafx-"
    }
}

/**
 * One module's resolved runtime classpath, refused when a JavaFX artifact
 * appears on it. The classpath is read after resolution, not from the declared
 * coordinates: JavaFX reaches a classpath through a transitive dependency as
 * easily as directly.
 */
abstract class VerifyNoJavaFxOnRuntimeClasspath : DefaultTask() {

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val mayCarryJavaFx: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun verifyResolvedClasspath() {
        RuntimeClasspathRule(
            module = moduleName.get(),
            configuration = configurationName.get(),
            artifacts = runtimeClasspath.files.map { jar -> jar.name },
            mayCarryJavaFx = mayCarryJavaFx.get(),
        ).verify()
    }
}

// ---------------------------------------------------------------------------
// The named modules
// ---------------------------------------------------------------------------

/**
 * The descriptor rule: a named module's jar carries a descriptor, a classpath
 * module's jar carries none, and the descriptor agrees with the module's edge
 * row. Every violation names the module, so a reader can fix the build without
 * opening the rule.
 */
class ModuleDescriptorRule(
    private val module: String,
    private val expectedModuleName: String?,
    private val expectedPackage: String,
    private val allowedModuleNames: List<String>,
    private val moduleNamespace: String,
    private val applicationClass: String?,
    private val jar: File,
) : DependencyRule() {

    override fun violations(): List<String> {
        val descriptor = explicitDescriptor()
        if (expectedModuleName == null) {
            return if (descriptor == null) {
                listOf()
            } else {
                listOf("$module runs on the classpath and its jar carries the module descriptor ${descriptor.name()}; only the seven named modules carry one")
            }
        }
        if (descriptor == null) {
            return listOf("$module's jar carries no module descriptor; $expectedModuleName is a named module")
        }
        val violations = mutableListOf<String>()
        if (descriptor.name() != expectedModuleName) {
            violations.add("$module's jar reports the module name ${descriptor.name()}, and its descriptor must declare $expectedModuleName")
        }
        val required = buildModulesRequiredBy(descriptor)
        required.filterNot { name -> name in allowedModuleNames }.forEach { name ->
            violations.add("$module requires $name, which its edge row does not allow; the row allows ${row()}")
        }
        allowedModuleNames.filterNot { name -> name in required }.forEach { name ->
            violations.add("$module does not require $name, and its edge row names it")
        }
        if (expectedPackage !in exportedPackages(descriptor)) {
            violations.add("$module does not export $expectedPackage; its descriptor must export the package its public types live in")
        }
        if (applicationClass != null) {
            val applicationPackage = applicationClass.substringBeforeLast('.')
            if (!jarContains(applicationClass)) {
                violations.add("$module's jar holds no $applicationClass, and that class is the one its exported package must be able to hold")
            }
            if (applicationPackage !in exportedPackages(descriptor)) {
                violations.add("$module does not export $applicationPackage, which holds its application class; a class in a package a module keeps to itself cannot be constructed")
            }
        }
        return violations
    }

    private fun jarContains(type: String): Boolean =
        ZipFile(jar).use { archive -> archive.getEntry(type.replace('.', '/') + ".class") != null }

    // The edge table names modules of this build. A JDK module (`java.logging`)
    // and an external module from the declared stack (`javafx.controls`) are not
    // edges in that table, so the rule judges the build's own modules and leaves
    // the rest to the declared stack's checks.
    private fun buildModulesRequiredBy(descriptor: ModuleDescriptor): List<String> =
        descriptor.requires()
            .map { requires -> requires.name() }
            .filter { name -> name.startsWith(moduleNamespace) }
            .sorted()

    private fun exportedPackages(descriptor: ModuleDescriptor): List<String> =
        descriptor.exports().map { exported -> exported.source() }

    private fun row(): String = if (allowedModuleNames.isEmpty()) "nothing" else allowedModuleNames.joinToString(separator = ", ")

    // An automatic module is derived from a jar's name, not declared, so it does
    // not count as a descriptor a reader can read the exports from.
    private fun explicitDescriptor() =
        ModuleFinder.of(jar.toPath()).findAll().singleOrNull()?.descriptor()?.takeUnless { descriptor -> descriptor.isAutomatic }
}

/** One module's built jar, checked against the module it must declare. */
abstract class VerifyModuleDescriptor : DefaultTask() {

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val expectedModuleName: Property<String>

    @get:Input
    abstract val expectedPackage: Property<String>

    @get:Input
    abstract val allowedModuleNames: ListProperty<String>

    @get:Input
    abstract val moduleNamespace: Property<String>

    @get:Input
    abstract val applicationClass: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val descriptorJar: RegularFileProperty

    @TaskAction
    fun verifyDescriptor() {
        ModuleDescriptorRule(
            module = moduleName.get(),
            expectedModuleName = expectedModuleName.get().ifEmpty { null },
            expectedPackage = expectedPackage.get(),
            allowedModuleNames = allowedModuleNames.get(),
            moduleNamespace = moduleNamespace.get(),
            applicationClass = applicationClass.get().ifEmpty { null },
            jar = descriptorJar.get().asFile,
        ).verify()
    }
}

// ---------------------------------------------------------------------------
// The fixtures: one declared violation per failure, and the refusal it must
// produce. The fixture convention is a plain data list plus one task that runs
// them, so a later check can add its own list and its own task.
// ---------------------------------------------------------------------------

/**
 * A declared dependency and the refusal it must produce. The expected message
 * is written from the architecture's wording, not from a rule's code, and null
 * means the rule must accept the declaration.
 */
class ModuleDependencyFixture(
    val what: String,
    val module: String,
    val allowedEdges: List<String>,
    val moduleDependencies: List<String>,
    val libraries: List<String>,
    val expectedRefusal: String?,
)

/** An artifact list on a runtime classpath, and the refusal it must produce. */
class RuntimeClasspathFixture(
    val what: String,
    val module: String,
    val configuration: String,
    val artifacts: List<String>,
    val mayCarryJavaFx: Boolean,
    val expectedRefusal: String?,
)

// Each fixture declares a violation its rule has to refuse. The accepted ones
// guard the other half: an edge the row names, a library outside the core, and
// the client's own JavaFX have to stay allowed, or the real build fails.
val moduleDependencyFixtures = listOf(
    ModuleDependencyFixture(
        what = "the core declares the cli",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("cli"),
        libraries = listOf(),
        expectedRefusal = "core declares a dependency on cli, which its edge row does not allow; the row allows contract",
    ),
    ModuleDependencyFixture(
        what = "the core declares the contract",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("contract"),
        libraries = listOf(),
        expectedRefusal = null,
    ),
    ModuleDependencyFixture(
        what = "the core declares Spring",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("contract"),
        libraries = listOf("org.springframework:spring-context"),
        expectedRefusal = "core declares org.springframework:spring-context, which is Spring; the core carries no framework",
    ),
    ModuleDependencyFixture(
        what = "the core declares JavaFX",
        module = "core",
        allowedEdges = listOf("contract"),
        moduleDependencies = listOf("contract"),
        libraries = listOf("org.openjfx:javafx-controls"),
        expectedRefusal = "core declares org.openjfx:javafx-controls, which is JavaFX; the core carries no framework",
    ),
    ModuleDependencyFixture(
        what = "a surface module declares a library",
        module = "mcp",
        allowedEdges = listOf("contract", "wiring"),
        moduleDependencies = listOf("contract", "wiring"),
        libraries = listOf("io.modelcontextprotocol.sdk:mcp"),
        expectedRefusal = null,
    ),
)

val runtimeClasspathFixtures = listOf(
    RuntimeClasspathFixture(
        what = "the adapters carry JavaFX",
        module = "adapters",
        configuration = "runtimeClasspath",
        artifacts = listOf("sqlite-jdbc-3.54.1.0.jar", "javafx-controls-21.0.13-linux.jar"),
        mayCarryJavaFx = false,
        expectedRefusal = "adapters:runtimeClasspath carries javafx-controls-21.0.13-linux.jar; JavaFX belongs to the client module alone",
    ),
    RuntimeClasspathFixture(
        what = "the client carries JavaFX",
        module = "client",
        configuration = "runtimeClasspath",
        artifacts = listOf("javafx-controls-21.0.13-linux.jar"),
        mayCarryJavaFx = true,
        expectedRefusal = null,
    ),
)

/** Runs every fixture and reports the ones whose rule did not answer as the fixture says. */
class DependencyRuleFixtures(
    private val moduleDependencyFixtures: List<ModuleDependencyFixture>,
    private val runtimeClasspathFixtures: List<RuntimeClasspathFixture>,
) {

    fun count(): Int = moduleDependencyFixtures.size + runtimeClasspathFixtures.size

    fun problems(): List<String> {
        val problems = mutableListOf<String>()
        moduleDependencyFixtures.forEach { fixture ->
            val refusal = refusalOf(
                ModuleDependencyRule(
                    module = fixture.module,
                    allowedEdges = fixture.allowedEdges,
                    moduleDependencies = fixture.moduleDependencies,
                    libraries = fixture.libraries,
                ),
            )
            expectedProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        runtimeClasspathFixtures.forEach { fixture ->
            val refusal = refusalOf(
                RuntimeClasspathRule(
                    module = fixture.module,
                    configuration = fixture.configuration,
                    artifacts = fixture.artifacts,
                    mayCarryJavaFx = fixture.mayCarryJavaFx,
                ),
            )
            expectedProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        return problems
    }

    private fun expectedProblem(problems: MutableList<String>, what: String, expected: String?, refusal: String?) {
        when {
            expected == null && refusal != null ->
                problems.add("Fixture '$what' was refused, and it must be accepted: $refusal")
            expected != null && refusal == null ->
                problems.add("Fixture '$what' was accepted, and it must be refused with: $expected")
            expected != null && refusal != null && !refusal.contains(expected) ->
                problems.add("Fixture '$what' was refused with '$refusal', and the refusal must carry: $expected")
        }
    }

    private fun refusalOf(rule: DependencyRule): String? =
        try {
            rule.verify()
            null
        } catch (refusal: GradleException) {
            refusal.message
        }
}

tasks.register("verifyDependencyRuleFixtures") {
    group = "verification"
    description = "Proves each dependency rule refuses a declared violation with a message that names it."
    // Plain data, so the action needs no reference to the build script itself.
    val fixtures = DependencyRuleFixtures(moduleDependencyFixtures, runtimeClasspathFixtures)
    doLast {
        val problems = fixtures.problems()
        if (problems.isNotEmpty()) {
            throw GradleException(
                "The dependency rule fixtures did not hold:\n" + problems.joinToString(separator = "\n") { problem -> "  $problem" },
            )
        }
        println("${fixtures.count()} dependency rule fixtures held")
    }
}

// The fixtures are part of the suite: `check` runs them, so a rule that stops
// refusing a declared violation breaks the build rather than going quiet.
tasks.named("check") { dependsOn("verifyDependencyRuleFixtures") }
