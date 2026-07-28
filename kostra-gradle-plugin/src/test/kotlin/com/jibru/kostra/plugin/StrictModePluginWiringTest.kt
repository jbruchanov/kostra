package com.jibru.kostra.plugin

import com.google.common.truth.Truth.assertThat
import com.jibru.kostra.plugin.task.AnalyseResourcesTask
import com.jibru.kostra.plugin.task.ValidateResourcesTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Verifies the strictMode property is wired end-to-end:
 * - Default value on KostraPluginExtension is false (strict coverage is opt-in on CI via
 *   `validateResources`; see StrictMode docs).
 * - AnalyseResourcesTask picks up the extension value as an input.
 * - User overrides on the extension propagate to the task.
 */
internal class StrictModePluginWiringTest {

    @Test
    fun `strictMode default value is false`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val extension = project.extensions.getByType(KostraPluginExtension::class.java)
        assertThat(extension.strictMode.get()).isFalse()
    }

    @Test
    fun `analyseResources task picks up the default strictMode value`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val task = project.tasks.getByName(KostraPluginConfig.Tasks.AnalyseResources) as AnalyseResourcesTask
        assertThat(task.strictMode.get()).isFalse()
    }

    @Test
    fun `extension override propagates to the task input`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        val extension = project.extensions.getByType(KostraPluginExtension::class.java)
        extension.strictMode.set(true)

        val task = project.tasks.getByName(KostraPluginConfig.Tasks.AnalyseResources) as AnalyseResourcesTask
        assertThat(task.strictMode.get()).isTrue()
    }

    @Test
    fun `validateResources task is registered and wired to the analysis file`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")

        val analyse = project.tasks.getByName(KostraPluginConfig.Tasks.AnalyseResources) as AnalyseResourcesTask
        val validate = project.tasks.getByName(KostraPluginConfig.Tasks.ValidateResources) as ValidateResourcesTask

        assertThat(validate.resourcesAnalysisFile.get().asFile).isEqualTo(analyse.outputFile.get().asFile)
        assertThat(validate.taskDependencies.getDependencies(validate)).contains(analyse)
    }

    @Test
    fun `validateResources ignores the strictMode flag being disabled`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.jibru.kostra.resources")
        project.extensions.getByType(KostraPluginExtension::class.java).strictMode.set(false)

        //Task exists and is enforceable regardless of strictMode; there is no flag input to flip off.
        val validate = project.tasks.getByName(KostraPluginConfig.Tasks.ValidateResources)
        assertThat(validate).isInstanceOf(ValidateResourcesTask::class.java)
    }
}
