plugins {
    id("java")
    id("application")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}


base {
    group = "xyz.wagyourtail"
    archivesName.set("asm-reader")
    version = "1.0.3"
}

application {
    mainClass.set("xyz.wagyourtail.asmreader.Main")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // asm
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-commons:9.5")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("org.ow2.asm:asm-util:9.5")
}

tasks.compileJava {
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to base.archivesName.get(),
            "Implementation-Version" to project.version,
            "Main-Class" to application.mainClass.get()
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes(
            "Implementation-Title" to base.archivesName.get(),
            "Implementation-Version" to project.version,
            "Main-Class" to application.mainClass.get()
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    repositories {
        maven {
            name = "WagYourMaven"
            url = if (project.hasProperty("version_snapshot")) {
                uri("https://maven.wagyourtail.xyz/snapshots/")
            } else {
                uri("https://maven.wagyourtail.xyz/releases/")
            }
            credentials {
                username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = base.archivesName.get()
            version = project.version as String

            artifact(project.tasks.jar) {}
            artifact(project.tasks.shadowJar) {
                classifier = "all"
            }
        }
    }
}