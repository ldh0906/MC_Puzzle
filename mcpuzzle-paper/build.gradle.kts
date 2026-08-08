import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Exec

dependencies {
    implementation(project(":mcpuzzle-core"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-nop:1.7.36")
}

val buildPuzzleResourcePack by tasks.registering(Exec::class) {
    val resourcePackRoot = rootProject.file("resource-pack")
    val powerShellExecutable = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .asSequence()
        .map(::File)
        .map { it.resolve("pwsh.exe") }
        .firstOrNull(File::isFile)
        ?.absolutePath
        ?: "powershell.exe"
    inputs.dir(resourcePackRoot.resolve("source"))
    inputs.file(resourcePackRoot.resolve("build.ps1"))
    outputs.file(resourcePackRoot.resolve("build/MCPuzzle-1.0.0.zip"))
    commandLine(
        powerShellExecutable,
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", resourcePackRoot.resolve("build.ps1").absolutePath
    )
}

tasks.processResources {
    dependsOn(buildPuzzleResourcePack)
    from(rootProject.file("map-packs")) {
        into("map-packs")
        include("schema/map-pack.schema.json")
        include("difficulty-mazes-30/easy.jsonc")
        include("difficulty-mazes-30/normal.jsonc")
        include("difficulty-mazes-30/hard.jsonc")
    }
    from(rootProject.file("resource-pack/build/MCPuzzle-1.0.0.zip")) {
        into("resource-pack")
    }
}

val coreSourceSets = project(":mcpuzzle-core").extensions.getByType<SourceSetContainer>()

tasks.jar {
    dependsOn(project(":mcpuzzle-core").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(coreSourceSets.named("main").map { it.output })
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
