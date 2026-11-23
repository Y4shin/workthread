// The root build holds the shared module convention. Every module is a Java 21
// library; a module's own build file declares only its dependency edges, so the
// architecture's edge table is readable in one place per module.

import java.io.File
import java.lang.module.ModuleDescriptor
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.net.URLClassLoader
import java.lang.module.ModuleFinder
import java.util.jar.JarFile
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

    // Every jar reports the version it was built from, so "which version is this
    // module?" is answered by the artifact rather than by the build script. The
    // root version check reads these two attributes back out of the built jars.
    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
            )
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
// The version
// ---------------------------------------------------------------------------

/**
 * The version rule: `gradle.properties` is the one source, every module reports
 * that number in its own jar, and no source file restates it. A second copy
 * drifts on the next bump, and an artifact that reports another number cannot be
 * traced to a release tag.
 */
class VersionRule(
    private val source: String,
    private val reportedVersions: Map<String, String>,
    private val moduleSources: Map<String, String>,
) : DependencyRule() {

    override fun violations(): List<String> {
        val violations = mutableListOf<String>()
        reportedVersions.toSortedMap().forEach { (module, reported) ->
            when {
                reported.isEmpty() ->
                    violations.add("$module reports no version in its jar; every module reports the version in gradle.properties")
                reported != source ->
                    violations.add("$module's jar reports version $reported, and gradle.properties says $source")
            }
        }
        moduleSources.toSortedMap().forEach { (path, text) ->
            if (restatesSource(text)) {
                violations.add("$path restates the version $source; gradle.properties is the one source")
            }
        }
        return violations
    }

    // A whole number, not a fragment of a longer one: at version 1.0, the JavaFX
    // version 21.0.13 and a note about 10.1.0 would otherwise read as copies.
    private fun restatesSource(text: String): Boolean =
        Regex("(?<![\\d.])" + Regex.escape(source) + "(?![\\d.])").containsMatchIn(text)
}

/**
 * The additivity rule: every element of the committed snapshot has to be in the
 * surface the build exports today, in the same shape. An element the snapshot
 * does not know is additive and passes, because a `0.x` export may still grow; a
 * removed or reshaped one names what changed, so the diff and the failure say
 * the same thing.
 */
class ApiSurfaceRule(
    private val module: String,
    private val snapshotPath: String,
    private val baseline: List<String>?,
    private val current: List<String>,
) : DependencyRule() {

    override fun violations(): List<String> {
        if (baseline == null) {
            return listOf("$snapshotPath is missing; $module's exported surface has no committed snapshot to compare against")
        }
        val held = elements(baseline)
        val built = elements(current)
        val violations = mutableListOf<String>()
        held.forEach { (element, shape) ->
            val now = built[element]
            when {
                now == null ->
                    violations.add("$module's exported surface no longer holds $element; the snapshot holds it as '$shape'")
                now != shape ->
                    violations.add("$module's exported surface changed shape for $element: the snapshot holds '$shape', the build holds '$now'")
            }
        }
        return violations
    }

    // One element per line: its name, a TAB, and its shape. `#` lines are the
    // snapshot's header and hold no element.
    private fun elements(lines: List<String>): Map<String, String> =
        lines
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .associate { line -> line.substringBefore('\t').trim() to line.substringAfter('\t', "").trim() }
}

/**
 * Every module's built jar and every module's sources, checked against the one
 * version property. The jar is read rather than the build script, because the
 * artifact is where a consumer reads a module's version.
 *
 * `reportedVersions` holds the number each jar reports, and an empty string for
 * a jar that reports none: a map cannot hold a missing value, and "reports
 * nothing" is a refusal of its own.
 */
abstract class VerifyVersion : DefaultTask() {

    @get:Input
    abstract val sourceVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleJars: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleSources: ConfigurableFileCollection

    // The paths only format the refusal; the sources are fingerprinted relative
    // to their roots, so the check stays relocatable.
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verifyReportedVersions() {
        val root = rootDirectory.get().asFile
        VersionRule(
            source = sourceVersion.get(),
            reportedVersions = moduleJars.files.associate { jar -> reportedVersionOf(jar) },
            moduleSources = moduleSources.asFileTree.files.associate { file -> pathOf(file, root) to file.readText() },
        ).verify()
    }

    // `Implementation-Title` names the module and `Implementation-Version` is the
    // number its artifact reports; a jar without them reports nothing.
    private fun reportedVersionOf(jar: File): Pair<String, String> =
        JarFile(jar).use { archive ->
            val attributes = archive.manifest?.mainAttributes
            val module = attributes?.getValue("Implementation-Title") ?: jar.name
            module to attributes?.getValue("Implementation-Version").orEmpty()
        }

    private fun pathOf(file: File, root: File): String =
        try {
            file.relativeTo(root).path
        } catch (outsideTheRoot: IllegalArgumentException) {
            file.path
        }
}

// The root build hosts the version check, because the one property is the root
// project's: every module inherits it from here, and the check reads the built
// jars back to prove each module reports it.
tasks.register<VerifyVersion>("verifyVersion") {
    group = "verification"
    description = "Refuses a module whose jar reports another version than gradle.properties holds, and a source file that restates it."
    sourceVersion.set(project.version.toString())
    rootDirectory.set(layout.projectDirectory)
    moduleJars.from(subprojects.map { subproject -> subproject.tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile } })
    moduleSources.from(subprojects.map { subproject -> subproject.file("src") })
}
tasks.named("check") { dependsOn("verifyVersion") }

// ---------------------------------------------------------------------------
// The contract API snapshot
// ---------------------------------------------------------------------------

/**
 * The exported surface of one jar: its exported packages, and the public and
 * protected types and members in them, one element per line as
 * `<element>` TAB `<shape>`.
 *
 * The text is the committed snapshot's format, so a review reads a change to the
 * API as a diff and the comparison runs on strings. Only public and protected
 * members are the surface: a private constructor or a package-private type is
 * the module's own business, not a promise to its consumers.
 */
object ExportedSurface {

    fun read(jar: File): List<String> {
        val module = ModuleFinder.of(jar.toPath()).findAll().singleOrNull()
            ?: throw GradleException("${jar.name} carries no module descriptor, and an exported surface is read from one")
        val exportedPackages = module.descriptor().exports().map { export -> export.source() }.distinct().sorted()
        val elements = exportedPackages.map { name -> "package $name" to "exported" }.toMutableList()
        URLClassLoader(arrayOf(jar.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            classNamesIn(jar, exportedPackages).forEach { name ->
                val type = load(name, loader)
                if (Modifier.isPublic(type.modifiers) || Modifier.isProtected(type.modifiers)) {
                    elements.add("type ${type.name}" to shapeOf(type))
                    elements.addAll(membersOf(type))
                }
            }
        }
        return elements.sortedBy { element -> element.first }.map { (element, shape) -> "$element\t$shape" }
    }

    // Every class file in an exported package, nested types included: a nested
    // type a module exports is part of its surface like any other.
    private fun classNamesIn(jar: File, exportedPackages: List<String>): List<String> =
        ZipFile(jar).use { archive ->
            archive.entries().asSequence()
                .map { entry -> entry.name }
                .filter { name -> name.endsWith(".class") }
                .filter { name -> name != "module-info.class" }
                .filter { name -> name.substringAfterLast('/') != "package-info.class" }
                .filter { name -> exportedPackages.any { exported -> name.startsWith(exported.replace('.', '/') + "/") } }
                .map { name -> name.removeSuffix(".class").replace('/', '.') }
                .sorted()
                .toList()
        }

    private fun load(name: String, loader: ClassLoader): Class<*> =
        try {
            Class.forName(name, false, loader)
        } catch (unloadable: ClassNotFoundException) {
            throw GradleException("$name is in the contract jar and its surface cannot be read", unloadable)
        }

    private fun shapeOf(type: Class<*>): String {
        val declaration = buildString {
            val modifiers = Modifier.toString(type.modifiers)
            if (modifiers.isNotEmpty()) {
                append(modifiers).append(' ')
            }
            append(kindOf(type))
            if (type.typeParameters.isNotEmpty()) {
                append('<').append(type.typeParameters.joinToString(separator = ", ") { parameter -> parameter.name }).append('>')
            }
        }
        val supertypes = mutableListOf<String>()
        if (!type.isInterface) {
            type.genericSuperclass?.let { superclass -> supertypes.add("extends ${superclass.typeName}") }
        }
        val interfaces = type.genericInterfaces.map { implemented -> implemented.typeName }.sorted()
        if (interfaces.isNotEmpty()) {
            val word = if (type.isInterface) "extends" else "implements"
            supertypes.add("$word ${interfaces.joinToString(separator = ", ")}")
        }
        return listOf(declaration).plus(supertypes).joinToString(separator = " ")
    }

    private fun kindOf(type: Class<*>): String =
        when {
            type.isAnnotation -> "annotation"
            type.isInterface -> "interface"
            type.isEnum -> "enum"
            type.isRecord -> "record"
            else -> "class"
        }

    private fun membersOf(type: Class<*>): List<Pair<String, String>> {
        val members = mutableListOf<Pair<String, String>>()
        type.declaredConstructors
            .filter { constructor -> Modifier.isPublic(constructor.modifiers) || Modifier.isProtected(constructor.modifiers) }
            .forEach { constructor ->
                members.add(
                    "constructor ${type.name}(${parametersOf(constructor.genericParameterTypes)})" to
                        (modifiersOf(constructor.modifiers) + throwsClauseOf(constructor.genericExceptionTypes)).trim(),
                )
            }
        type.declaredMethods
            .filter { method -> (Modifier.isPublic(method.modifiers) || Modifier.isProtected(method.modifiers)) && !method.isSynthetic && !method.isBridge }
            .forEach { method ->
                members.add(
                    "method ${type.name}#${method.name}(${parametersOf(method.genericParameterTypes)})" to
                        "${modifiersOf(method.modifiers)}returns ${method.genericReturnType.typeName}${throwsClauseOf(method.genericExceptionTypes)}",
                )
            }
        type.declaredFields
            .filter { field -> Modifier.isPublic(field.modifiers) || Modifier.isProtected(field.modifiers) }
            .forEach { field ->
                members.add("field ${type.name}#${field.name}" to "${modifiersOf(field.modifiers)}${field.genericType.typeName}")
            }
        return members
    }

    private fun parametersOf(types: Array<Type>): String =
        types.joinToString(separator = ", ") { parameter -> parameter.typeName }

    private fun throwsClauseOf(types: Array<Type>): String =
        if (types.isEmpty()) "" else " throws " + types.map { thrown -> thrown.typeName }.sorted().joinToString(separator = ", ")

    private fun modifiersOf(modifiers: Int): String =
        Modifier.toString(modifiers).takeIf { words -> words.isNotEmpty() }?.plus(" ").orEmpty()
}

/** Reads the contract jar's exported surface, so the snapshot and the check agree on what it is. */
abstract class ContractSurfaceTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val contractJar: RegularFileProperty

    protected fun surface(): List<String> = ExportedSurface.read(contractJar.get().asFile)
}

/**
 * The snapshot in `check`: every committed element has to survive in the surface
 * the contract jar exports today, so a consumer never meets a removal the build
 * let through.
 */
abstract class VerifyContractApiSnapshot : ContractSurfaceTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val snapshot: ConfigurableFileCollection

    @TaskAction
    fun verifySurface() {
        val file = snapshot.files.singleOrNull()
        ApiSurfaceRule(
            module = CONTRACT,
            snapshotPath = SNAPSHOT_PATH,
            baseline = file?.takeIf { existing -> existing.exists() }?.readLines(),
            current = surface(),
        ).verify()
    }

    private companion object {
        const val CONTRACT = "contract"
        const val SNAPSHOT_PATH = "contract/api-snapshot.txt"
    }
}

/**
 * The deliberate rewrite: the snapshot is committed, so a changed surface is
 * written by hand, reviewed as a diff, and only then committed.
 */
abstract class GenerateContractApiSnapshot : ContractSurfaceTask() {

    @get:OutputFile
    abstract val snapshot: RegularFileProperty

    @TaskAction
    fun writeSurface() {
        val file = snapshot.get().asFile
        file.parentFile.mkdirs()
        file.writeText(HEADER + surface().joinToString(separator = "\n", postfix = "\n"))
    }

    private companion object {
        val HEADER = """
            # The contract module's exported surface: every exported package, and the
            # public and protected types and members in it, one element per line as
            # `<element>` TAB `<shape>`.
            #
            # `./gradlew check` compares this file with the built contract jar and fails
            # when an element disappears or changes shape. `./gradlew
            # generateContractApiSnapshot` rewrites it after a change the release means
            # to ship, and the diff is then the review.
            """.trimIndent() + "\n"
    }
}

// The root build hosts the snapshot task, as the architecture says, and the
// committed file is in `contract` so a reader of that module finds it.
val contractApiSnapshot = rootProject.file("contract/api-snapshot.txt")

tasks.register<VerifyContractApiSnapshot>("verifyContractApiSnapshot") {
    group = "verification"
    description = "Refuses a change that removes or reshapes an element of contract's committed exported surface."
    contractJar.set(project(":contract").tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile })
    snapshot.from(contractApiSnapshot)
}
tasks.named("check") { dependsOn("verifyContractApiSnapshot") }

tasks.register<GenerateContractApiSnapshot>("generateContractApiSnapshot") {
    group = "verification"
    description = "Rewrites contract/api-snapshot.txt from the built jar after an additive change to the contract API."
    contractJar.set(project(":contract").tasks.named<Jar>("jar").flatMap { jar -> jar.archiveFile })
    snapshot.set(contractApiSnapshot)
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
) : FixtureChecks() {

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
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
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
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        return problems
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

// ---------------------------------------------------------------------------
// The version and surface fixtures
// ---------------------------------------------------------------------------

/**
 * A version declaration and the refusal it must produce: the version the
 * property holds, the version each module's jar reports (empty for none), the
 * text of each source file, and null when the rule must accept all of it.
 */
class VersionFixture(
    val what: String,
    val source: String,
    val reportedVersions: Map<String, String>,
    val moduleSources: Map<String, String>,
    val expectedRefusal: String?,
)

// Each fixture is one case the version rule has to answer. The refusals are
// written from the architecture's rule ("the version is one property, and every
// module reports it"), not from the rule's code.
val versionFixtures = listOf(
    VersionFixture(
        what = "a module reports another version",
        source = "0.1.0",
        reportedVersions = mapOf("contract" to "0.1.0", "client" to "0.2.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "new Label(windowLabel())",
        ),
        expectedRefusal = "client's jar reports version 0.2.0, and gradle.properties says 0.1.0",
    ),
    VersionFixture(
        what = "a module reports no version",
        source = "0.1.0",
        reportedVersions = mapOf("contract" to "0.1.0", "api" to ""),
        moduleSources = mapOf(),
        expectedRefusal = "api reports no version in its jar; every module reports the version in gradle.properties",
    ),
    VersionFixture(
        what = "a source file restates the version",
        source = "0.1.0",
        reportedVersions = mapOf("client" to "0.1.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "StackPane root = new StackPane(new Label(\"workthread 0.1.0\"));",
        ),
        expectedRefusal = "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java " +
            "restates the version 0.1.0; gradle.properties is the one source",
    ),
    VersionFixture(
        what = "every module reports the property's version",
        source = "0.1.0",
        reportedVersions = mapOf("contract" to "0.1.0", "client" to "0.1.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "new Label(windowLabel())",
        ),
        expectedRefusal = null,
    ),
    VersionFixture(
        what = "a source file carries another number",
        source = "0.1.0",
        reportedVersions = mapOf("client" to "0.1.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "// 1024x768, measured on 2026-11-23",
        ),
        expectedRefusal = null,
    ),
    VersionFixture(
        what = "a source file carries a longer number",
        source = "1.0",
        reportedVersions = mapOf("client" to "1.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "// javafx 21.0.13, and the test ran on 10.1.0",
        ),
        expectedRefusal = null,
    ),
    VersionFixture(
        what = "a source file carries the version inside another number",
        source = "1.0",
        reportedVersions = mapOf("client" to "1.0"),
        moduleSources = mapOf(
            "client/src/main/java/io/github/y4shin/workthread/client/WorkthreadApplication.java" to
                "// pinned at 11.0.4 and 1.05",
        ),
        expectedRefusal = null,
    ),
)

/**
 * An exported surface and the refusal it must produce: the committed snapshot's
 * lines (null for a file that is not there), the surface the built jar holds,
 * and the refusal each one must carry, or null when the change is additive.
 */
class ApiSurfaceFixture(
    val what: String,
    val snapshot: List<String>?,
    val surface: List<String>,
    val expectedRefusal: String?,
)

// The snapshot's own format, element TAB shape, as the committed file holds it.
private val CONTRACT_PACKAGE = "package io.github.y4shin.workthread.contract\texported"
private val CONTRACT_PLACEHOLDER =
    "type io.github.y4shin.workthread.contract.ContractPlaceholder\tpublic final class extends java.lang.Object"
private val PORT_TYPE = "type io.github.y4shin.workthread.contract.ForThinking\tpublic abstract interface extends java.lang.Object"
private val PORT_METHOD =
    "method io.github.y4shin.workthread.contract.ForThinking#start(java.lang.String)\tpublic abstract returns void"

// Each fixture is one surface change a consumer can feel. The removals and the
// reshape are refusals, because the snapshot is the promise; an addition is not,
// because adding to an exported surface is what `0.x` may still do.
val apiSurfaceFixtures = listOf(
    ApiSurfaceFixture(
        what = "a type is removed",
        snapshot = listOf(CONTRACT_PACKAGE, CONTRACT_PLACEHOLDER),
        surface = listOf(CONTRACT_PACKAGE),
        expectedRefusal = "contract's exported surface no longer holds " +
            "type io.github.y4shin.workthread.contract.ContractPlaceholder; " +
            "the snapshot holds it as 'public final class extends java.lang.Object'",
    ),
    ApiSurfaceFixture(
        what = "a type changes shape",
        snapshot = listOf(CONTRACT_PACKAGE, CONTRACT_PLACEHOLDER),
        surface = listOf(CONTRACT_PACKAGE, "type io.github.y4shin.workthread.contract.ContractPlaceholder\tpublic class extends java.lang.Object"),
        expectedRefusal = "contract's exported surface changed shape for " +
            "type io.github.y4shin.workthread.contract.ContractPlaceholder: " +
            "the snapshot holds 'public final class extends java.lang.Object', the build holds 'public class extends java.lang.Object'",
    ),
    ApiSurfaceFixture(
        what = "an exported package is removed",
        snapshot = listOf(CONTRACT_PACKAGE, CONTRACT_PLACEHOLDER),
        surface = listOf(CONTRACT_PLACEHOLDER),
        expectedRefusal = "contract's exported surface no longer holds package io.github.y4shin.workthread.contract; " +
            "the snapshot holds it as 'exported'",
    ),
    ApiSurfaceFixture(
        what = "a member is removed",
        snapshot = listOf(CONTRACT_PACKAGE, PORT_TYPE, PORT_METHOD),
        surface = listOf(CONTRACT_PACKAGE, PORT_TYPE),
        expectedRefusal = "contract's exported surface no longer holds " +
            "method io.github.y4shin.workthread.contract.ForThinking#start(java.lang.String)",
    ),
    ApiSurfaceFixture(
        what = "a member is added",
        snapshot = listOf(CONTRACT_PACKAGE, PORT_TYPE),
        surface = listOf(CONTRACT_PACKAGE, PORT_TYPE, PORT_METHOD),
        expectedRefusal = null,
    ),
    ApiSurfaceFixture(
        what = "a type is added",
        snapshot = listOf(CONTRACT_PACKAGE),
        surface = listOf(CONTRACT_PACKAGE, CONTRACT_PLACEHOLDER),
        expectedRefusal = null,
    ),
    ApiSurfaceFixture(
        what = "the committed snapshot is missing",
        snapshot = null,
        surface = listOf(CONTRACT_PACKAGE, CONTRACT_PLACEHOLDER),
        expectedRefusal = "contract/api-snapshot.txt is missing; contract's exported surface has no committed snapshot to compare against",
    ),
)

/**
 * Runs the version and surface fixtures, and reports the ones whose rule did not
 * answer as the fixture says. One runner for this ticket's fixtures: each list
 * is plain data, so a later ticket adds its own list and its own runner.
 */
class VersionAndSurfaceFixtures(
    private val versionFixtures: List<VersionFixture>,
    private val apiSurfaceFixtures: List<ApiSurfaceFixture>,
) : FixtureChecks() {

    fun count(): Int = versionFixtures.size + apiSurfaceFixtures.size

    fun problems(): List<String> {
        val problems = mutableListOf<String>()
        versionFixtures.forEach { fixture ->
            val refusal = refusalOf(
                VersionRule(
                    source = fixture.source,
                    reportedVersions = fixture.reportedVersions,
                    moduleSources = fixture.moduleSources,
                ),
            )
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        apiSurfaceFixtures.forEach { fixture ->
            val refusal = refusalOf(
                ApiSurfaceRule(
                    module = "contract",
                    snapshotPath = "contract/api-snapshot.txt",
                    baseline = fixture.snapshot,
                    current = fixture.surface,
                ),
            )
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        return problems
    }
}

/**
 * One fixture's disagreement with its rule. A base class rather than a top-level
 * function, because a script-level function pulls the build script into every
 * object that calls it, and the configuration cache cannot serialize that.
 */
abstract class FixtureChecks {

    /** The refusal a rule produces, or null when it accepts what it was given. */
    protected fun refusalOf(rule: DependencyRule): String? =
        try {
            rule.verify()
            null
        } catch (refusal: GradleException) {
            refusal.message
        }

    /** Adds the problem, worded so the failure names the fixture and what was expected. */
    protected fun fixtureProblem(problems: MutableList<String>, what: String, expected: String?, refusal: String?) {
        when {
            expected == null && refusal != null ->
                problems.add("Fixture '$what' was refused, and it must be accepted: $refusal")
            expected != null && refusal == null ->
                problems.add("Fixture '$what' was accepted, and it must be refused with: $expected")
            expected != null && refusal != null && !refusal.contains(expected) ->
                problems.add("Fixture '$what' was refused with '$refusal', and the refusal must carry: $expected")
        }
    }
}

tasks.register("verifyVersionAndSurfaceFixtures") {
    group = "verification"
    description = "Proves the version rule refuses a module that reports another version or a source that restates it, and the surface rule refuses a removed or reshaped export."
    // Plain data, built here so the action holds no reference to the build script.
    val fixtures = VersionAndSurfaceFixtures(versionFixtures, apiSurfaceFixtures)
    doLast {
        val problems = fixtures.problems()
        if (problems.isNotEmpty()) {
            throw GradleException(
                "The version and surface fixtures did not hold:\n" + problems.joinToString(separator = "\n") { problem -> "  $problem" },
            )
        }
        println("${fixtures.count()} version and surface fixtures held")
    }
}

tasks.named("check") { dependsOn("verifyVersionAndSurfaceFixtures") }

// ---------------------------------------------------------------------------
// The pipeline: `.github/workflows/ci.yml` is the pipeline, so the check reads
// it as the committed file rather than trusting a description of it.
// ---------------------------------------------------------------------------

/**
 * The pipeline's shape, read from the committed workflow file: three pinned
 * legs, an explicit JDK, the build, the client image task, the uploaded images,
 * and the run policy that keeps a superseded run from burning minutes. The
 * architecture's CI section is the source of every refusal, and a refusal names
 * what the workflow must carry.
 *
 * A comment is not a step: the body is read with its comments removed, so the
 * workflow can say why it needs no display without the check reading that
 * sentence as a step.
 */
class CiPipelineRule(
    private val path: String,
    private val lines: List<String>?,
) : DependencyRule() {

    override fun violations(): List<String> {
        if (lines == null) {
            return listOf("$path is missing; the pipeline is a committed workflow file, and three legs build from it")
        }
        val body = bodyOf(lines)
        val violations = mutableListOf<String>()
        checkRunnerLabels(body, violations)
        checkToolchain(body, violations)
        checkPipelineSteps(body, violations)
        checkUploadedImages(body, violations)
        checkRunPolicy(body, violations)
        return violations
    }

    /**
     * When the pipeline runs, what it is allowed to do, and what happens to a run
     * a newer commit supersedes. Read-only permissions are the default a pipeline
     * should have to argue its way out of, and a superseded run that keeps
     * running burns minutes for a result nobody will read.
     */
    private fun checkRunPolicy(body: List<String>, violations: MutableList<String>) {
        if (body.none { line -> line.contains("pull_request:") }) {
            violations.add("the pipeline is not triggered by a pull request")
        }
        if (body.none { line -> line.trim() == "push:" } || body.none { line -> line.contains("branches:") && line.contains("main") }) {
            violations.add("the pipeline is not triggered by a push to main")
        }
        if (body.none { line -> line.contains("workflow_dispatch:") }) {
            violations.add("the pipeline is not triggered by workflow_dispatch; a rerun must be possible without a commit")
        }
        if (body.none { line -> line.trim() == "concurrency:" }) {
            violations.add("the pipeline has no concurrency group; a superseded run keeps burning runner minutes")
        } else if (body.none { line -> line.contains("cancel-in-progress: true") }) {
            violations.add("the pipeline's concurrency group does not set cancel-in-progress: true; a superseded run keeps burning runner minutes")
        }
        if (body.none { line -> line.contains("contents: read") } || body.any { line -> WRITE_PERMISSION.containsMatchIn(line) }) {
            violations.add("the pipeline's permissions are not read-only; the build needs contents: read and nothing more")
        }
    }

    /**
     * The image, uploaded per leg and kept for a week. A delivered artifact loses
     * its permissions, so it is evidence for inspection and for the manual launch
     * check, not the shipped image; three images per run still run out the storage
     * allowance, which is what the short retention is for.
     */
    private fun checkUploadedImages(body: List<String>, violations: MutableList<String>) {
        val upload = stepLines(body, "actions/upload-artifact")
        if (upload == null) {
            violations.add("the pipeline uploads no image; the three artifacts are what the manual launch checks download")
            return
        }
        if (upload.none { line -> line.contains("path: client/build/image") }) {
            violations.add("the artifact upload does not point at client/build/image; that directory is the image")
        }
        if (upload.none { line -> line.contains("retention-days: 7") }) {
            violations.add("the artifact upload keeps no seven days; three images per run exhaust the free storage otherwise")
        }
        if (upload.none { line -> line.contains("name: image-") && line.contains("matrix.os") }) {
            violations.add("the artifact upload names no per-leg artifact; three legs with one name upload one artifact")
        }
        if (upload.none { line -> line.contains("if-no-files-found: error") }) {
            violations.add("the artifact upload tolerates a missing image; if-no-files-found: error fails the leg instead of shipping nothing")
        }
    }

    /**
     * The two commands every leg runs: the build, which carries every check, and
     * the client's image task, which is the packaging artifact.
     */
    private fun checkPipelineSteps(body: List<String>, violations: MutableList<String>) {
        if (body.none { line -> line.contains("./gradlew build") }) {
            violations.add("the pipeline runs no ./gradlew build; the build is the gate every leg meets, and every check rides it")
        }
        if (body.none { line -> line.contains(":client:jlink") }) {
            violations.add("the pipeline runs no ./gradlew :client:jlink; each leg builds its own image, and nothing cross-builds")
        }
    }

    /** The workflow's lines with their comments removed, so a sentence is not a step. */
    private fun bodyOf(lines: List<String>): List<String> =
        lines.map { line -> line.substringBefore('#').trimEnd() }.filter { line -> line.isNotEmpty() }

    /**
     * The JDK the leg builds with, and the Gradle user home the next run restores.
     * The patch is pinned, not the major version: `jlink` links the JDK's own
     * modules, so a floating patch links a different image on each leg.
     */
    private fun checkToolchain(body: List<String>, violations: MutableList<String>) {
        val javaStep = stepLines(body, "actions/setup-java")
        if (javaStep == null) {
            violations.add("the pipeline sets up no JDK; ubuntu and windows default to Java 17, and jlink links the JDK the leg runs")
        } else {
            if (javaStep.none { line -> line.contains("distribution: temurin") }) {
                violations.add("the pipeline's JDK step names no Temurin distribution; the image is linked from that JDK")
            }
            if (javaStep.none { line -> PINNED_JDK.containsMatchIn(line) }) {
                violations.add("the pipeline's JDK step names no 21.0.13; an unpinned patch links a different image on each leg")
            }
        }
        if (body.none { line -> line.contains("gradle/actions/setup-gradle") }) {
            violations.add(
                "the pipeline uses no gradle/actions/setup-gradle; " +
                    "it validates the wrapper and restores the Gradle user home, so a wrapper-validation step of its own is redundant",
            )
        }
    }

    /**
     * One step of the workflow: the line that names the action and the lines
     * indented under it. A step ends where the next line indents no deeper, which
     * is where the next step begins.
     */
    private fun stepLines(body: List<String>, action: String): List<String>? {
        val start = body.indexOfFirst { line -> line.contains(action) }
        if (start < 0) {
            return null
        }
        val indent = indentOf(body[start])
        val end = (start + 1 until body.size).firstOrNull { index -> indentOf(body[index]) <= indent } ?: body.size
        return body.subList(start, end)
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart().length

    private fun checkRunnerLabels(body: List<String>, violations: MutableList<String>) {
        val legs = RUNNER_LABELS.joinToString(separator = ", ")
        RUNNER_LABELS.filterNot { label -> body.any { line -> line.contains(label) } }.forEach { label ->
            violations.add("the pipeline's matrix carries no $label; the three legs are $legs, pinned to exact labels")
        }
        if (body.none { line -> line.contains("runs-on: \${{ matrix.os }}") }) {
            violations.add("the pipeline's job runs on no matrix label; the three labels are three hosts only if the job runs on them")
        }
        body.forEach { line ->
            FLOATING_RUNNER_LABEL.find(line)?.let { floating ->
                violations.add("the pipeline runs on ${floating.value}; a -latest label moves the OS baseline, and the JavaFX classifier with it")
            }
        }
    }

    companion object {
        const val PATH = ".github/workflows/ci.yml"
        val RUNNER_LABELS = listOf("ubuntu-24.04", "macos-26", "windows-2025")
        val FLOATING_RUNNER_LABEL = Regex("(ubuntu|macos|windows)-latest")
        val PINNED_JDK = Regex("java-version:\\s*['\"]?21\\.0\\.13['\"]?")
        val WRITE_PERMISSION = Regex("permissions:\\s*write|:\\s*write")
    }
}

/**
 * The prerequisites the pipeline must not need. Nothing in the pipeline launches
 * the client: the build links the image, the checks are toolkit-free, and the
 * launch happens by hand. Xvfb, GTK, and jpackage would only appear if something
 * started a toolkit or built an installer.
 *
 * A comment is not a step, so the workflow can say why it needs none of them
 * without this rule reading that sentence as a step.
 */
class ForbiddenCiStepRule(private val lines: List<String>?) : DependencyRule() {

    override fun violations(): List<String> {
        // A missing workflow is the pipeline rule's refusal, and there is nothing
        // here to read.
        val body = (lines ?: listOf())
            .map { line -> line.substringBefore('#').trimEnd() }
            .filter { line -> line.isNotEmpty() }
        return FORBIDDEN_STEPS
            .filter { (token, _) -> body.any { line -> line.contains(token, ignoreCase = true) } }
            .map { (_, reason) -> "the pipeline $reason" }
    }

    companion object {
        // Each token names the tool and the reason it does not belong. `wix` is
        // the Windows installer toolchain, `fakeroot` and `dpkg-deb` the Linux
        // one: all three serve jpackage, and the pipeline builds an image.
        val FORBIDDEN_STEPS = listOf(
            "xvfb" to "uses xvfb; nothing in the pipeline launches a toolkit, so a display is not needed",
            "gtk" to "installs GTK; JavaFX needs it only when the toolkit initialises, and nothing in the pipeline launches",
            "jpackage" to "uses jpackage; the jlink image is the shipping artifact and the pipeline builds no installer",
            "fakeroot" to "provisions fakeroot; that is a jpackage installer prerequisite, and the pipeline builds an image",
            "dpkg-deb" to "provisions dpkg-deb; that is a jpackage installer prerequisite, and the pipeline builds an image",
            "wix" to "provisions wix; that is a jpackage installer prerequisite, and the pipeline builds an image",
        )
    }
}

/** The committed workflow file, checked as the pipeline it declares. */
abstract class VerifyCiWorkflow : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val workflow: ConfigurableFileCollection

    @TaskAction
    fun verifyPipeline() {
        val file = workflow.files.singleOrNull()
        val lines = file?.takeIf { existing -> existing.exists() }?.readLines()
        CiPipelineRule(path = CiPipelineRule.PATH, lines = lines).verify()
        ForbiddenCiStepRule(lines = lines).verify()
    }
}

// The workflow is the pipeline's only committed artifact, so the check reads it
// from the root project: a leg runs `./gradlew build`, and this check rides it.
val ciWorkflow = rootProject.file(CiPipelineRule.PATH)

tasks.register<VerifyCiWorkflow>("verifyCiWorkflow") {
    group = "verification"
    description = "Refuses a workflow file that drops a pinned leg, a floating runner label, an explicit JDK, the build, the image task, or the uploaded images."
    workflow.from(ciWorkflow)
}
tasks.named("check") { dependsOn("verifyCiWorkflow") }

/**
 * A workflow fragment and the refusal it must produce. The accepted side of the
 * pipeline rule is the committed file itself, which `verifyCiWorkflow` reads: a
 * fragment cannot stand in for a whole pipeline, and a second copy of the real
 * workflow in the build script would drift from it.
 */
class CiPipelineFixture(
    val what: String,
    val lines: List<String>,
    val expectedRefusal: String,
)

// Each fixture is one way the pipeline can stop keeping the promise. The
// fragments are short on purpose: the rule names every violation it finds, so a
// fragment reports the one under test among the ones its shortness causes.
val ciPipelineFixtures = listOf(
    CiPipelineFixture(
        what = "a leg is missing",
        lines = listOf("        os: [ubuntu-24.04, windows-2025]"),
        expectedRefusal = "the pipeline's matrix carries no macos-26; " +
            "the three legs are ubuntu-24.04, macos-26, windows-2025, pinned to exact labels",
    ),
    CiPipelineFixture(
        what = "a floating runner label",
        lines = listOf("    runs-on: ubuntu-latest"),
        expectedRefusal = "the pipeline runs on ubuntu-latest; a -latest label moves the OS baseline, and the JavaFX classifier with it",
    ),
    CiPipelineFixture(
        what = "no JDK step",
        lines = listOf("      - uses: actions/checkout@v7"),
        expectedRefusal = "the pipeline sets up no JDK; ubuntu and windows default to Java 17, and jlink links the JDK the leg runs",
    ),
    CiPipelineFixture(
        what = "an unpinned JDK patch",
        lines = listOf(
            "      - uses: actions/setup-java@v6",
            "        with:",
            "          distribution: temurin",
            "          java-version: '21'",
        ),
        expectedRefusal = "the pipeline's JDK step names no 21.0.13; an unpinned patch links a different image on each leg",
    ),
    CiPipelineFixture(
        what = "no Gradle setup step",
        lines = listOf(
            "      - uses: actions/setup-java@v6",
            "        with:",
            "          distribution: temurin",
            "          java-version: '21.0.13'",
        ),
        expectedRefusal = "the pipeline uses no gradle/actions/setup-gradle; " +
            "it validates the wrapper and restores the Gradle user home, so a wrapper-validation step of its own is redundant",
    ),
    CiPipelineFixture(
        what = "no build step",
        lines = listOf("      - uses: actions/checkout@v7"),
        expectedRefusal = "the pipeline runs no ./gradlew build; the build is the gate every leg meets, and every check rides it",
    ),
    CiPipelineFixture(
        what = "no client image task",
        lines = listOf("      - run: ./gradlew build"),
        expectedRefusal = "the pipeline runs no ./gradlew :client:jlink; each leg builds its own image, and nothing cross-builds",
    ),
    CiPipelineFixture(
        what = "no artifact upload",
        lines = listOf("      - run: ./gradlew build", "      - run: ./gradlew :client:jlink"),
        expectedRefusal = "the pipeline uploads no image; the three artifacts are what the manual launch checks download",
    ),
    CiPipelineFixture(
        what = "the artifact upload points at another directory",
        lines = listOf(
            "      - uses: actions/upload-artifact@v7",
            "        with:",
            "          name: image-\${{ matrix.os }}",
            "          path: client/build/libs/",
            "          retention-days: 7",
            "          if-no-files-found: error",
        ),
        expectedRefusal = "the artifact upload does not point at client/build/image; that directory is the image",
    ),
    CiPipelineFixture(
        what = "the artifacts are kept for ninety days",
        lines = listOf(
            "      - uses: actions/upload-artifact@v7",
            "        with:",
            "          name: image-\${{ matrix.os }}",
            "          path: client/build/image/",
            "          retention-days: 90",
            "          if-no-files-found: error",
        ),
        expectedRefusal = "the artifact upload keeps no seven days; three images per run exhaust the free storage otherwise",
    ),
    CiPipelineFixture(
        what = "three legs upload one artifact name",
        lines = listOf(
            "      - uses: actions/upload-artifact@v7",
            "        with:",
            "          name: image",
            "          path: client/build/image/",
            "          retention-days: 7",
            "          if-no-files-found: error",
        ),
        expectedRefusal = "the artifact upload names no per-leg artifact; three legs with one name upload one artifact",
    ),
    CiPipelineFixture(
        what = "the upload tolerates a missing image",
        lines = listOf(
            "      - uses: actions/upload-artifact@v7",
            "        with:",
            "          name: image-\${{ matrix.os }}",
            "          path: client/build/image/",
            "          retention-days: 7",
            "          if-no-files-found: warn",
        ),
        expectedRefusal = "the artifact upload tolerates a missing image; if-no-files-found: error fails the leg instead of shipping nothing",
    ),
    CiPipelineFixture(
        what = "no pull request trigger",
        lines = listOf("on:", "  push:", "    branches: [main]", "  workflow_dispatch:"),
        expectedRefusal = "the pipeline is not triggered by a pull request",
    ),
    CiPipelineFixture(
        what = "no push to the main branch",
        lines = listOf("on:", "  pull_request:", "  workflow_dispatch:"),
        expectedRefusal = "the pipeline is not triggered by a push to main",
    ),
    CiPipelineFixture(
        what = "no manual dispatch",
        lines = listOf("on:", "  push:", "    branches: [main]", "  pull_request:"),
        expectedRefusal = "the pipeline is not triggered by workflow_dispatch; a rerun must be possible without a commit",
    ),
    CiPipelineFixture(
        what = "no concurrency group",
        lines = listOf("permissions:", "  contents: read"),
        expectedRefusal = "the pipeline has no concurrency group; a superseded run keeps burning runner minutes",
    ),
    CiPipelineFixture(
        what = "a superseded run is not cancelled",
        lines = listOf(
            "concurrency:",
            "  group: \${{ github.workflow }}-\${{ github.ref }}",
            "  cancel-in-progress: false",
        ),
        expectedRefusal = "the pipeline's concurrency group does not set cancel-in-progress: true; a superseded run keeps burning runner minutes",
    ),
    CiPipelineFixture(
        what = "the permissions are not read-only",
        lines = listOf("permissions: write-all"),
        expectedRefusal = "the pipeline's permissions are not read-only; the build needs contents: read and nothing more",
    ),
)

/**
 * A workflow fragment and the refusal it must produce, or null when the fragment
 * is permitted. The comment case is the permitted one: the workflow may say why
 * it needs no display, and a sentence is not a step.
 */
class ForbiddenCiStepFixture(
    val what: String,
    val lines: List<String>,
    val expectedRefusal: String?,
)

val forbiddenCiStepFixtures = listOf(
    ForbiddenCiStepFixture(
        what = "a display server is installed",
        lines = listOf("      - run: sudo apt-get install -y xvfb"),
        expectedRefusal = "the pipeline uses xvfb; nothing in the pipeline launches a toolkit, so a display is not needed",
    ),
    ForbiddenCiStepFixture(
        what = "the GTK libraries are installed",
        lines = listOf("      - run: sudo apt-get install -y libgtk-3-0"),
        expectedRefusal = "the pipeline installs GTK; JavaFX needs it only when the toolkit initialises, and nothing in the pipeline launches",
    ),
    ForbiddenCiStepFixture(
        what = "an installer is built",
        lines = listOf("      - run: jpackage --type deb --input client/build/image"),
        expectedRefusal = "the pipeline uses jpackage; the jlink image is the shipping artifact and the pipeline builds no installer",
    ),
    ForbiddenCiStepFixture(
        what = "an installer prerequisite is provisioned",
        lines = listOf("      - run: sudo apt-get install -y fakeroot dpkg-deb"),
        expectedRefusal = "the pipeline provisions fakeroot; that is a jpackage installer prerequisite, and the pipeline builds an image",
    ),
    ForbiddenCiStepFixture(
        what = "a comment names the steps the pipeline does not run",
        lines = listOf(
            "# Nothing here starts a toolkit: no Xvfb, no GTK, no jpackage.",
            "      - uses: actions/checkout@v7",
        ),
        expectedRefusal = null,
    ),
)

/** Runs the pipeline fixtures and reports the ones whose rule did not answer as the fixture says. */
class CiWorkflowFixtures(
    private val ciPipelineFixtures: List<CiPipelineFixture>,
    private val forbiddenCiStepFixtures: List<ForbiddenCiStepFixture>,
) : FixtureChecks() {

    fun count(): Int = ciPipelineFixtures.size + forbiddenCiStepFixtures.size

    fun problems(): List<String> {
        val problems = mutableListOf<String>()
        ciPipelineFixtures.forEach { fixture ->
            val refusal = refusalOf(CiPipelineRule(path = CiPipelineRule.PATH, lines = fixture.lines))
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        forbiddenCiStepFixtures.forEach { fixture ->
            val refusal = refusalOf(ForbiddenCiStepRule(lines = fixture.lines))
            fixtureProblem(problems, fixture.what, fixture.expectedRefusal, refusal)
        }
        return problems
    }
}

tasks.register("verifyCiWorkflowFixtures") {
    group = "verification"
    description = "Proves the pipeline rules refuse a dropped leg, a floating label, an unpinned JDK, a missing image upload, or a display step, with messages that name them."
    // Plain data, so the action needs no reference to the build script itself.
    val fixtures = CiWorkflowFixtures(ciPipelineFixtures, forbiddenCiStepFixtures)
    doLast {
        val problems = fixtures.problems()
        if (problems.isNotEmpty()) {
            throw GradleException(
                "The workflow fixtures did not hold:\n" + problems.joinToString(separator = "\n") { problem -> "  $problem" },
            )
        }
        println("${fixtures.count()} workflow fixtures held")
    }
}

tasks.named("check") { dependsOn("verifyCiWorkflowFixtures") }
