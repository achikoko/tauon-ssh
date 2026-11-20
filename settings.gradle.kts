rootProject.name = "tauon"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("tauon-app")

// JediTerm fork (git submodule, tracking https://github.com/JetBrains/jediterm) is built from source.
// Its own Gradle build is kept untouched; here we just swap the published coordinates for its projects.
includeBuild("tauon-jediterm") {
    dependencySubstitution {
        substitute(module("org.jetbrains.jediterm:jediterm-core")).using(project(":core"))
        substitute(module("org.jetbrains.jediterm:jediterm-ui")).using(project(":ui"))
    }
}
