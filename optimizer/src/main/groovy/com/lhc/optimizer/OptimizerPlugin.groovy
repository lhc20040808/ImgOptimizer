package com.lhc.optimizer

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project


class OptimizerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.afterEvaluate {
            project.android.applicationVariants.all {
                BaseVariant variant ->
                    def task = project.tasks.create("optimize${variant.name.capitalize()}", OptimizerTask) {

                        def processManifest = variant.outputs.first().processManifest
                        def processResources = variant.outputs.first().processResources

                        if (processManifest.properties['manifestOutPutFile'] != null) {
                            manifestFile = processManifest.manifestOutputFile
                        } else if (processResources.properties['manifestFile'] != null) {
                            manifestFile = processResources.manifestFile
                        }

                        res = variant.mergeResources.outputDir
                        apiLevel = variant.mergeResources.minSdk

                        println("hello gradle plugin:" + apiLevel)
                    }

                    variant.outputs.first().processResources.dependsOn task
                    task.dependsOn variant.outputs.first().processManifest
            }
        }

    }
}