import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.compose") version "1.0.0-alpha3"
}

version = getProjectVersion()

repositories {
    if (project.hasProperty("release")) mavenCentral()
    else maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(compose.desktop.currentOs)
    implementation("com.typesafe:config:1.4.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.4")
    implementation("com.squareup.okhttp3:okhttp:3.12.12")
    implementation("com.deepoove:poi-tl:1.10.0")

    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.slf4j:jul-to-slf4j:1.7.25")
    implementation("org.slf4j:jcl-over-slf4j:1.7.25")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = if (project.hasProperty("release")) "16" else "11"
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            includeAllModules = true
            packageName = "ExtraHoursExporter"
            packageVersion = "${project.version}"
        }
    }
}

fun isVersionFileExists(): Boolean = file("version.txt").exists()

fun getVersionFromFile(): String = file("version.txt").readText().removePrefix("refs/tags/v").trim()

fun getProjectVersion(): String {
    if (isVersionFileExists())
        return getVersionFromFile()
    return "1.0.0"
}
