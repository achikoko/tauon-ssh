plugins {
    java
}

group = "tauon-app"
// version is defined in the root gradle.properties

val mainClassName = "tauon.app.App"

dependencies {
    // Built from the tauon-jediterm submodule (see settings.gradle.kts); the version is ignored.
    implementation("org.jetbrains.jediterm:jediterm-core:SNAPSHOT")
    implementation("org.jetbrains.jediterm:jediterm-ui:SNAPSHOT")

    implementation("org.jetbrains.pty4j:pty4j:0.13.2")
    implementation("ch.qos.logback:logback-classic:1.5.19")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("commons-net:commons-net:3.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70") // used in App via Security.addProvider(...)
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.tukaani:xz:1.9")
    implementation("org.jvnet.com4j:com4j:2.1")
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("com.jcraft:jsch:0.1.54")
    implementation("com.github.markusbernhardt:proxy-vole:1.0.1")
    implementation("com.kohlschutter.junixsocket:junixsocket-core:2.9.0") // pom-only aggregator module
    implementation("org.jetbrains:annotations:26.0.1")

    // Uncomment to do the text editor
    // implementation("com.fifesoft:rsyntaxtextarea:2.6.1")
    // implementation("com.fifesoft:rstaui:3.0.6")
    // Uncomment to do VNC (needs the jcenter repository)
    // implementation("com.shinyhut:vernacular:1.6")

    testImplementation("junit:junit:4.13.2") // was 3.8.1 in Maven; Gradle needs JUnit 4 to run (JUnit 3 style tests still work)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
}

// Replaces templating-maven-plugin: expands ${project.*} placeholders in src/main/java-templates.
// Unknown placeholders are left verbatim, as Maven did.
val generateTemplateSources = tasks.register<Copy>("generateTemplateSources") {
    val tokens = mapOf(
        "project.version" to project.version.toString(),
        "project.groupId" to project.group.toString(),
    )
    inputs.properties(tokens)
    from("src/main/java-templates")
    into(layout.buildDirectory.dir("generated/sources/java-templates"))
    filter { line -> tokens.entries.fold(line) { acc, (key, value) -> acc.replace("\${$key}", value) } }
}

sourceSets.main {
    java.srcDir(generateTemplateSources)
}

tasks.test {
    useJUnit()
}

// Runs the app from the compiled classes: ./gradlew run
// (A plain JavaExec instead of the `application` plugin, which would also add distZip/distTar to every build.)
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs Tauon from the compiled classes"
    mainClass = mainClassName
    classpath = sourceSets.main.get().runtimeClasspath
}

// Replaces maven-assembly-plugin (jar-with-dependencies): build/libs/tauonssh.jar, uncompressed.
tasks.jar {
    archiveFileName = "tauonssh.jar"
    entryCompression = ZipEntryCompression.STORED
    manifest {
        attributes("Main-Class" to mainClassName)
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // first one wins, like the assembly plugin
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

// Replaces jdeb-maven-plugin: build/distributions/tauonssh_<version>.deb
val jdeb: Configuration = configurations.create("jdeb")
dependencies {
    jdeb("org.vafer:jdeb:1.15")
}

// Expands [[version]] in the Debian control file
val debControl = tasks.register<Copy>("debControl") {
    val packageVersion = project.version.toString()
    inputs.property("version", packageVersion)
    from("src/deb/control")
    into(layout.buildDirectory.dir("deb/control"))
    filter { line -> line.replace("[[version]]", packageVersion) }
}

val deb = tasks.register("deb") {
    group = "distribution"
    description = "Builds the Debian package tauonssh_<version>.deb"

    val jarFile = tasks.jar.flatMap { it.archiveFile }
    val debSrc = layout.projectDirectory.dir("src/deb")
    val controlDir = debControl.map { it.destinationDir }
    val debFile = layout.buildDirectory.file("distributions/tauonssh_${project.version}.deb")
    inputs.file(jarFile)
    inputs.dir(debSrc)
    inputs.files(controlDir)
    inputs.files(jdeb)
    outputs.file(debFile)

    doLast {
        val src = debSrc.asFile
        fun GroovyBuilderScope.data(file: File, prefix: String, mode: String) {
            "data"("src" to file, "type" to "file") {
                "mapper"(
                    "type" to "perm", "prefix" to prefix,
                    "user" to "loader", "group" to "loader", "filemode" to mode,
                )
            }
        }
        ant.withGroovyBuilder {
            "taskdef"("name" to "jdeb", "classname" to "org.vafer.jdeb.ant.DebAntTask", "classpath" to jdeb.asPath)
        }
        ant.withGroovyBuilder {
            "jdeb"("destfile" to debFile.get().asFile, "control" to controlDir.get()) {
                data(src.resolve("opt/tauonssh/tauonssh-logo.png"), "/opt/tauonssh", "644")
                data(src.resolve("usr/share/applications/tauonssh.desktop"), "/usr/share/applications", "644")
                data(src.resolve("usr/bin/tauonssh"), "/usr/bin", "755")
                data(jarFile.get().asFile, "/opt/tauonssh", "644")
            }
        }
    }
}

tasks.assemble {
    dependsOn(deb)
}
