import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.jetbrains.kotlin.gradle.internal.backend.common.serialization.metadata.DynamicTypeDeserializer.id

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

group = "se.nikohei"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.ktor.network)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlinx.serialization.cbor)
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generatedAntlr"))
}

tasks.test {
    useJUnitPlatform()
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(layout.projectDirectory.dir("src/main/antlr")) {
        include("**/*.g4")
    }

    val pkgName = "se.nikohei.kvstore.grammar.generated"
    packageName = pkgName

    arguments = listOf("-visitor")

    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateKotlinGrammarSource)
}