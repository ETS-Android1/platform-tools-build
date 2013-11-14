/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.build.gradle.internal.dsl.NdkConfigDsl
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.sdklib.IAndroidTarget
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.io.Files
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.util.PatternSet

/**
 */
class NdkCompile extends BaseTask {

    Set<File> sourceFolders

    @OutputFile
    File generatedMakefile

    @Nested
    NdkConfigDsl ndkConfig

    @Input
    boolean debuggable

    @OutputDirectory
    File soFolder

    @OutputDirectory
    File objFolder

    @InputFiles
    public FileTree getSource() {
        FileTree src = null
        Set<File> sources = getSourceFolders()
        if (!sources.isEmpty()) {
            src = getProject().files(new ArrayList<Object>(sources)).getAsFileTree()
        }
        return src == null ? getProject().files().getAsFileTree() : src
    }

    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) {

        FileTree sourceFileTree = getSource()
        Set<File> sourceFiles = sourceFileTree.matching(new PatternSet().exclude("**/*.h")).files
        File makefile = getGeneratedMakefile()

        if (sourceFiles.isEmpty()) {
            makefile.delete()
            emptyFolder(getSoFolder())
            emptyFolder(getObjFolder())
            return
        }

        File ndkDirectory = getPlugin().ndkDirectory
        if (ndkDirectory == null || !ndkDirectory.isDirectory()) {
            throw new GradleException("NDK not configured")
        }

        boolean generateMakefile = false

        if (!inputs.isIncremental()) {
            project.logger.info("Unable do incremental execution: full task run")
            generateMakefile = true
            emptyFolder(getSoFolder())
            emptyFolder(getObjFolder())
        } else {
            // look for added or removed files *only*

            //noinspection GroovyAssignabilityCheck
            inputs.outOfDate { change ->
                if (change.isAdded()) {
                    generateMakefile = true
                }
            }

            //noinspection GroovyAssignabilityCheck
            inputs.removed { change ->
                generateMakefile = true
            }
        }

        if (generateMakefile) {
            writeMakefile(sourceFiles, makefile)
        }

        // now build
        runNdkBuild(ndkDirectory, makefile)
    }

    private void writeMakefile(@NonNull Set<File> sourceFiles, @NonNull File makefile) {
        NdkConfigDsl ndk = getNdkConfig()

        StringBuilder sb = new StringBuilder()

        sb.append(
                "LOCAL_PATH := \$(call my-dir)\n" +
                "include \$(CLEAR_VARS)\n\n")

        sb.append("LOCAL_MODULE := ").append(ndk.moduleName != null ? ndk.moduleName : project.name).append('\n')

        if (ndk.cFlags != null) {
            sb.append("LOCAL_CFLAGS := ").append(ndk.cFlags).append('\n')
        }

        if (ndk.ldLibs != null) {
            sb.append("LOCAL_LDLIBS := ").append(ndk.ldLibs).append('\n')
        }

        sb.append("LOCAL_SRC_FILES := \\\n")
        for (File sourceFile : sourceFiles) {
            sb.append('\t').append(sourceFile.absolutePath).append(" \\\n")
        }
        sb.append('\n')

        sb.append(
                "\ninclude \$(BUILD_SHARED_LIBRARY)\n")

        Files.write(sb.toString(), makefile, Charsets.UTF_8)
    }

    private void runNdkBuild(@NonNull File ndkLocation, @NonNull File makefile) {
        NdkConfigDsl ndk = getNdkConfig()

        List<String> commands = Lists.newArrayList()

        commands.add(ndkLocation.absolutePath + File.separator + "ndk-build")

        commands.add("NDK_PROJECT_PATH=null")

        commands.add("APP_BUILD_SCRIPT=" + makefile.absolutePath)

        // target
        IAndroidTarget target = getPlugin().loadedSdkParser.target
        if (!target.isPlatform()) {
            target = target.parent
        }
        commands.add("APP_PLATFORM=" + target.hashString())

        // include paths
        for (File sourceFolder : getSourceFolders()) {
            commands.add("-I")
            commands.add(sourceFolder.absolutePath)
        }

        // temp out
        commands.add("NDK_OUT=" + getObjFolder().absolutePath)

        // libs out
        commands.add("NDK_LIBS_OUT=" + getSoFolder().absolutePath)

        // debug builds
        if (getDebuggable()) {
            commands.add("NDK_DEBUG=1")
        }

        Set<String> abiFilters = ndk.abiFilters
        if (abiFilters != null && !abiFilters.isEmpty()) {
            if (abiFilters.size() == 1) {
                commands.add("APP_ABI=" + abiFilters.iterator().next())
            } else {
                Joiner joiner = Joiner.on(',').skipNulls()
                commands.add("APP_ABI=" + joiner.join(abiFilters.iterator()))
            }
        } else {
            commands.add("APP_ABI=all")
        }

        getBuilder().commandLineRunner.runCmdLine(commands, null)
    }
}