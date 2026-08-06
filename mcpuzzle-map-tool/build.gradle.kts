plugins {
    application
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.networknt:json-schema-validator:1.0.87")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.mcpuzzle.maptool.MapToolMain")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

tasks.named<Test>("test") {
    systemProperty("repositoryRoot", rootProject.projectDir.absolutePath)
}
