plugins {
    //can't be simple jvm plugin because of broken resources
    //https://youtrack.jetbrains.com/issue/KTIJ-16582/Consumer-Kotlin-JVM-library-cannot-access-a-Kotlin-Multiplatform-JVM-target-resources-in-multi-module-Gradle-project
    id("org.jetbrains.kotlin.multiplatform")
    id("com.jibru.kostra.resources")
}

apply(from = "${rootProject.projectDir}/../build-ktlint.gradle")

kotlin {
    jvm {
    }

    sourceSets {
        commonMain {
            resources.srcDir("../shared/src/commonMain/resources")
            dependencies {
                implementation(libs.kostra.common)
                implementation(project(":shared-lib2"))
            }
        }

        val jvmMain by getting

        jvmTest {
            dependencies {
                implementation(libs.bundles.unittest.jvm)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kostra {
    androidResources {
        keyMapper { key, _ -> key.toCamelCase() }
        resourceDirs.add(file("../shared/src/commonMain/resources_strings"))
    }
}

fun String.toCamelCase(): String = split("_")
    .map { it.lowercase() }
    .filter { it.isNotEmpty() }
    .takeIf { it.isNotEmpty() }
    ?.mapIndexed { index, s -> if (index > 0) s[0].uppercase() + s.drop(1) else s }
    ?.joinToString(separator = "")
//just return anything original, this is naive toCamelCase implementation
    ?: this
