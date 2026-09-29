plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.3"
}

group = "fr.danakube.danaevent"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Paper API 1.21.1
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Database & Connection Pool
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.mysql:mysql-connector-j:9.0.0")

    // Optional Hooks (compileOnly)
    compileOnly("me.clip:placeholderapi:2.11.6")
    testCompileOnly("me.clip:placeholderapi:2.11.6")

    // Testing Stack (TDD)
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate HikariCP to avoid runtime classpath conflicts
        relocate("com.zaxxer.hikari", "fr.danakube.danaevent.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
