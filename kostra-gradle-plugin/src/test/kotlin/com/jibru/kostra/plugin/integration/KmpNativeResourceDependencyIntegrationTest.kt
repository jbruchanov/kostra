package com.jibru.kostra.plugin.integration

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Verifies `KostraPlugin.tryAddNativeDependencyResources`: a consumer module that lists a Kostra
 * dependency in `kostra { nativeResourceDependencies }` gets that dependency's generated
 * `kostra_resources/<...>` bundled into its native (here iOS-simulator) target's resources, and its
 * `generateDatabases` runs after the dependency's.
 *
 * KMP does not merge a dependency's native resources into a consumer's static framework
 * (compose-multiplatform#3391); this is the wiring that compensates. The proof runs
 * `iosSimulatorArm64ProcessResources` — a plain resource-copy task (no Kotlin/Native toolchain
 * needed) whose output (`build/processedResources/iosSimulatorArm64/main`) is exactly what the iOS
 * framework packaging consumes.
 */
class KmpNativeResourceDependencyIntegrationTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `consumer native resources include dependency kostra resources and generateDatabases is ordered`() {
        writeFixture()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withGradleVersion(GradleVersionUnderTest)
            .withArguments(
                ":consumer:iosSimulatorArm64ProcessResources",
                "--stacktrace",
                "--no-configuration-cache",
            )
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val libGenerateDatabases = result.task(":lib:generateDatabases")
        val consumerGenerateDatabases = result.task(":consumer:generateDatabases")
        val consumerProcessResources = result.task(":consumer:iosSimulatorArm64ProcessResources")

        assertAll(
            { assertThat(libGenerateDatabases).isNotNull() },
            { assertThat(libGenerateDatabases?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
            { assertThat(consumerGenerateDatabases).isNotNull() },
            { assertThat(consumerGenerateDatabases?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
            { assertThat(consumerProcessResources).isNotNull() },
            { assertThat(consumerProcessResources?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
        )

        // Ordering proof: the dependsOn added by tryAddNativeDependencyResources makes the consumer's
        // generateDatabases run after the dependency's (packaging → own generateDatabases → dep).
        val executed = result.tasks.map { it.path }
        val libIdx = executed.indexOf(":lib:generateDatabases")
        val consumerIdx = executed.indexOf(":consumer:generateDatabases")
        assertThat(libIdx).isGreaterThan(-1)
        assertThat(consumerIdx).isGreaterThan(libIdx)

        // srcDir proof: the dependency's prefixed DBs land in the consumer's native processed
        // resources under kostra_resources/ — exactly where the framework packaging reads them.
        val processed = File(testProjectDir, "consumer/build/processedResources/iosSimulatorArm64/main")
        assertThat(processed.exists()).isTrue()
        val depDbs = File(processed, "kostra_resources")
            .walkTopDown()
            .filter { it.isFile && it.name.startsWith("lib_") && it.extension == "db" }
            .map { it.name }
            .toList()
        assertThat(depDbs).isNotEmpty()

        // ...and only under kostra_resources/ (the runtime read path), never at the resources root —
        // guards against regressing to the old root-level-duplicate wiring.
        val depFilesAtRoot = processed.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("lib_") }
        assertThat(depFilesAtRoot).isEmpty()
    }

    private fun writeFixture() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "kostra-native-it"
            include(":lib")
            include(":consumer")
            """.trimIndent(),
        )

        File(testProjectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m
            kotlin.code.style=official
            """.trimIndent(),
        )

        // Dependency module: a module prefix makes its DBs distinguishable (lib_*.db) from the
        // consumer's own, mirroring the multi-module sample.
        writeModule(
            name = "lib",
            kClassName = "com.test.lib.KLib",
            extraKostra = """modulePrefix.set("Lib")""",
        )

        // Consumer module: pulls the dependency's native resources in via the new DSL.
        writeModule(
            name = "consumer",
            kClassName = "com.test.consumer.K",
            extraKostra = """nativeResourceDependencies.set(listOf(":lib"))""",
        )
    }

    private fun writeModule(name: String, kClassName: String, extraKostra: String) {
        val moduleDir = File(testProjectDir, name).apply { mkdirs() }
        File(moduleDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.jibru.kostra.resources")
            }

            kotlin {
                jvm()
                iosSimulatorArm64()

                sourceSets {
                    val commonMain by getting {
                        dependencies {
                            implementation("com.jibru.kostra:kostra-common:$kostraVersion")
                        }
                    }
                }
            }

            kostra {
                kClassName.set("$kClassName")
                strictMode.set(false)
                $extraKostra
            }
            """.trimIndent(),
        )

        File(moduleDir, "src/commonMain/resources/values").apply { mkdirs() }
            .let { values ->
                File(values, "strings.xml").writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <string name="hello">Hello from $name</string>
                    </resources>
                    """.trimIndent(),
                )
            }
        File(moduleDir, "src/commonMain/kotlin").mkdirs()
    }

    private val kostraVersion: String =
        System.getProperty("kostra.test.version")?.takeIf { it.isNotBlank() }
            ?: error("kostra.test.version system property not set (configured in kostra-gradle-plugin/build.gradle)")

    companion object {
        private const val GradleVersionUnderTest = "9.5.1"
    }
}
