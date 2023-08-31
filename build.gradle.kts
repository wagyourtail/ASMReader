plugins {
    id("java")
}

group = "xyz.wagyourtail"
version = "1.0-SNAPSHOT"

base {

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

    // commons-io
    implementation("commons-io:commons-io:2.11.0")
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