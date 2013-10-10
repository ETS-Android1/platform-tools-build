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
import com.android.annotations.Nullable
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.LintGradleClient
import com.android.tools.lint.HtmlReporter
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.Reporter
import com.android.tools.lint.XmlReporter
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.LintUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

public class Lint extends DefaultTask {
    @NonNull private BasePlugin mPlugin
    @Nullable private List<File> mCustomRules
    @Nullable private File mConfigFile
    @Nullable private File mHtmlOutput
    @Nullable private File mXmlOutput
    @Nullable private List<Set<File>> mSourceSets
    @Nullable private String mClassPath
    @Nullable private List<Set<File>> mResourceSets
    private boolean mQuiet

    public void setPlugin(@NonNull BasePlugin plugin) {
        mPlugin = plugin
    }

    public void addCustomRule(@NonNull File jar) {
        if (mCustomRules == null) {
            mCustomRules = new ArrayList<File>()
        }
        mCustomRules.add(jar)
    }

    public void setQuiet() {
        mQuiet = true
    }

    public void setConfig(@NonNull File configFile) {
        mConfigFile = configFile
    }

    public void setHtmlOutput(@NonNull File htmlOutput) {
        mHtmlOutput = htmlOutput
    }

    public void setXmlOutput(@NonNull File xmlOutput) {
        mXmlOutput = xmlOutput;
    }

    /**
     * Adds all files in sourceSets as a source file for lint.
     *
     * @param sourceSets files to be added to sources.
     */
    public void setSources(@NonNull List<Set<File>> sourceSets) {
        mSourceSets = sourceSets
    }

    /**
     * Adds all class files in directory specified by paths for lint.
     *
     * @param paths A set of paths to class files separated with path separators
     */
    public void setClasspath(@NonNull String paths) {
        mClassPath = paths
    }

    /**
     *  Adds all files in resourceSets as a resource file for lint.
     *
     * @param resourceSets files to be added to resources.
     */
    public void setLintResources(@NonNull List<Set<File>> resourceSets) {
        mResourceSets = resourceSets
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    public void lint() {
        IssueRegistry registry = new BuiltinIssueRegistry()
        LintCliFlags flags = new LintCliFlags()
        LintGradleClient client = new LintGradleClient(flags, mPlugin.getSdkDirectory())

        // Configure Reporters

        if (mHtmlOutput != null) {
            mHtmlOutput = mHtmlOutput.getAbsoluteFile()
            if (mHtmlOutput.exists()) {
                boolean delete = mHtmlOutput.delete()
                if (!delete) {
                    throw new GradleException("Could not delete old " + mHtmlOutput)
                }
            }
            if (mHtmlOutput.getParentFile() != null && !mHtmlOutput.getParentFile().canWrite()) {
                throw new GradleException("Cannot write HTML output file " + mHtmlOutput)
            }
            try {
                flags.getReporters().add(new HtmlReporter(client, mHtmlOutput))
            } catch (IOException e) {
                throw new GradleException("HTML invalid argument.", e)
            }
        }

        if (mXmlOutput != null) {
            mXmlOutput = mXmlOutput.getAbsoluteFile()
            if (mXmlOutput.exists()) {
                boolean delete = mXmlOutput.delete();
                if (!delete) {
                    throw new GradleException("Could not delete old " + mXmlOutput)
                }
            }
            if (mXmlOutput.getParentFile() != null && !mXmlOutput.getParentFile().canWrite()) {
                throw new GradleException("Cannot write XML output file " + mXmlOutput)
            }
            try {
                flags.getReporters().add(new XmlReporter(client, mXmlOutput))
            } catch (IOException e) {
                throw new GradleException("XML invalid argument.", e)
            }
        }

        List<Reporter> reporters = flags.getReporters()
        if (reporters.isEmpty()) {
            throw new GradleException("No reporter specified.")
        }

        Map<String, String> map = new HashMap<String, String>(){{
            put("", "file://")
        }}
        for (Reporter reporter : reporters) {
            reporter.setUrlMap(map)
        }

        // Flags

        if (mQuiet) {
            flags.setQuiet(true)
        }
        if (mConfigFile != null) {
            flags.setDefaultConfiguration(client.createConfigurationFromFile(mConfigFile))
        }

        // Flags: sources, resources, classes

        for (Set<File> args : mSourceSets) {
            for (File input : args) {
                if (input.exists()) {
                    List<File> sources = flags.getSourcesOverride()
                    if (sources == null) {
                        sources = new ArrayList<File>()
                        flags.setSourcesOverride(sources)
                    }
                    sources.add(input)
                }
            }
        }

        for (String path : LintUtils.splitPath(mClassPath)) {
            File input = new File(path);
            if (!input.exists()) {
                throw new GradleException("Class path entry " + input + " does not exist.")
            }
            List<File> classes = flags.getClassesOverride();
            if (classes == null) {
                classes = new ArrayList<File>();
                flags.setClassesOverride(classes);
            }
            classes.add(input);
        }

        for (Set<File> args : mResourceSets) {
            for (File input : args) {
                if (input.exists()) {
                    List<File> resources = flags.getResourcesOverride()
                    if (resources == null) {
                        resources = new ArrayList<File>()
                        flags.setResourcesOverride(resources)
                    }
                    resources.add(input)
                }
            }
        }

        // Client setup

        if (mCustomRules != null) {
            client.setCustomRules(mCustomRules)
        }

        // Finally perform lint run

        try {
            client.run(registry, Arrays.asList(project.projectDir));
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e)
        }
    }
}
