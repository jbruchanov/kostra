package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.plugin.task.AnalyseResourcesTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Verifies the strictMode property is wired end-to-end:
 * - Default value on KostraPluginExtension is true.
 * - AnalyseResourcesTask picks up the extension value as an input.
 * - User overrides on the extension propagate to the task.
 */
internal class StrictModePluginWiringTest {

    @Test
    fun `strictMode default value is true`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val extension = project.extensions.getByType(KostraPluginExtension::class.java)
        assertThat(extension.strictMode.get()).isTrue()
    }

    @Test
    fun `analyseResources task picks up the default strictMode value`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val task = project.tasks.getByName(KostraPluginConfig.Tasks.AnalyseResources) as AnalyseResourcesTask
        assertThat(task.strictMode.get()).isTrue()
    }

    @Test
    fun `extension override propagates to the task input`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val extension = project.extensions.getByType(KostraPluginExtension::class.java)
        extension.strictMode.set(false)

        val task = project.tasks.getByName(KostraPluginConfig.Tasks.AnalyseResources) as AnalyseResourcesTask
        assertThat(task.strictMode.get()).isFalse()
    }
}
