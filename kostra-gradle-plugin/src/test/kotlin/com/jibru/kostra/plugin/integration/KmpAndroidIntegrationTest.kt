package com.jibru.kostra.plugin.integration

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

@EnabledIf("androidSdkAvailable")
class KmpAndroidIntegrationTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `kostra wires generated db files into Android KMP variant processJavaRes`() {
        writeFixture()

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withGradleVersion(GradleVersionUnderTest)
            .withArguments(
                ":processAndroidMainJavaRes",
                "--stacktrace",
                "--no-configuration-cache",
            )
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val generateDatabases = result.task(":generateDatabases")
        val generateResources = result.task(":generateResources")
        val processJavaRes = result.task(":processAndroidMainJavaRes")

        assertAll(
            { assertThat(generateDatabases).isNotNull() },
            { assertThat(generateDatabases?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
            { assertThat(generateResources).isNotNull() },
            { assertThat(generateResources?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
            { assertThat(processJavaRes).isNotNull() },
            { assertThat(processJavaRes?.outcome).isAnyOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE) },
        )

        // Wiring proof #1: generateDatabases must appear before processAndroidMainJavaRes in task graph.
        val executedTasks = result.tasks.map { it.path }
        val genDbIdx = executedTasks.indexOf(":generateDatabases")
        val processIdx = executedTasks.indexOf(":processAndroidMainJavaRes")
        assertThat(genDbIdx).isGreaterThan(-1)
        assertThat(processIdx).isGreaterThan(genDbIdx)

        // Wiring proof #2: the generated .db files exist where the plugin places them.
        val generatedResources = File(testProjectDir, "build/generated/kostra/resources")
        assertThat(generatedResources.exists()).isTrue()
        val dbFiles = generatedResources.walkTopDown().filter { it.extension == "db" }.toList()
        assertThat(dbFiles).isNotEmpty()

        // Wiring proof #3: the .db files are bundled into the variant's processed Java resources.
        val mergedRes = File(testProjectDir, "build/intermediates/java_res/androidMain")
        val mergedDbs = mergedRes.walkTopDown().filter { it.extension == "db" }.toList()
        assertThat(mergedDbs).isNotEmpty()
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
            rootProject.name = "kostra-it"
            """.trimIndent(),
        )

        val sdkDir = readHostSdkDir()
        File(testProjectDir, "local.properties").writeText(
            "sdk.dir=${sdkDir.replace("\\", "\\\\")}",
        )

        File(testProjectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m
            kotlin.code.style=official
            android.useAndroidX=true
            """.trimIndent(),
        )

        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.android.kotlin.multiplatform.library")
                id("com.jibru.kostra.resources")
            }

            kotlin {
                jvm()
                android {
                    namespace = "com.test.kostra"
                    compileSdk = $TestCompileSdk
                    minSdk = $TestMinSdk
                    withHostTestBuilder {}
                }
                jvmToolchain($TestJvmToolchain)

                sourceSets {
                    val commonMain by getting {
                        dependencies {
                            implementation("com.jibru.kostra:kostra-common:$kostraVersion")
                        }
                    }
                }
            }

            kostra {
                kClassName.set("com.test.kostra.K")
            }
            """.trimIndent(),
        )

        val values = File(testProjectDir, "src/commonMain/resources/values").apply { mkdirs() }
        File(values, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="hello">Hello</string>
                <string name="world">World</string>
            </resources>
            """.trimIndent(),
        )

        File(testProjectDir, "src/commonMain/kotlin").mkdirs()
    }

    private val kostraVersion: String =
        System.getProperty("kostra.test.version")?.takeIf { it.isNotBlank() }
            ?: error("kostra.test.version system property not set (configured in kostra-gradle-plugin/build.gradle)")

    private fun readHostSdkDir(): String {
        val sysProp = System.getProperty("kostra.test.android.sdk")
        if (!sysProp.isNullOrBlank()) return sysProp
        val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!env.isNullOrBlank()) return env
        val localProps = File(KostraRoot, "local.properties")
        if (localProps.exists()) {
            val props = Properties().apply { localProps.inputStream().use { load(it) } }
            val dir = props.getProperty("sdk.dir")
            if (!dir.isNullOrBlank()) return dir
        }
        error("Android SDK location not found (set kostra.test.android.sdk, ANDROID_HOME, or sdk.dir in $localProps)")
    }

    companion object {
        private const val GradleVersionUnderTest = "9.5.1"
        private const val TestCompileSdk = 36
        private const val TestMinSdk = 24
        private const val TestJvmToolchain = 21

        // The plugin module's working directory at test time is kostra-gradle-plugin/.
        // The project root (which holds local.properties with sdk.dir) is its parent.
        private val KostraRoot: File = File(System.getProperty("user.dir")).parentFile

        @Suppress("unused") // referenced by @EnabledIf
        @JvmStatic
        fun androidSdkAvailable(): Boolean {
            if (!System.getProperty("kostra.test.android.sdk").isNullOrBlank()) return true
            if (!System.getenv("ANDROID_HOME").isNullOrBlank()) return true
            if (!System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()) return true
            val localProps = File(KostraRoot, "local.properties")
            if (!localProps.exists()) return false
            return Properties().also { localProps.inputStream().use(it::load) }
                .getProperty("sdk.dir")
                ?.takeIf { it.isNotBlank() } != null
        }
    }
}
