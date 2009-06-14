/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* @author Hans Dockter
*/
public class Compile extends ConventionTask {

    /**
     * The directories with the sources to compile
     */
    private List<File> srcDirs;

    /**
     * The directory where to put the compiled classes (.class files)
     */
    private File destinationDir;

    /**
     * The sourceCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String sourceCompatibility;

    /**
     * The targetCompatibility used by the Java compiler for your code. (e.g. 1.5)
     */
    private String targetCompatibility;

    private FileCollection classpath;

    /**
     * Options for the compiler. The compile is delegated to the ant javac task. This property contains almost
     * all of the properties available for the ant javac task.
     */
    private CompileOptions options = new CompileOptions();

    /**
     * Include pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package1/')).
     */
    private List<String> includes = new ArrayList<String>();

    /**
     * Exclude pattern for which files should be compiled (e.g. '**&#2F;org/gradle/package2/A*.java').
     */
    private List<String> excludes = new ArrayList<String>();

    protected ExistingDirsFilter existentDirsFilter = new ExistingDirsFilter();

    protected AntJavac antCompile = new AntJavac();

    public Compile(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                compile();
            }
        });
    }

    protected void compile() {
        if (antCompile == null) {
            throw new InvalidUserDataException("The ant compile command must be set!");
        }
        getDestinationDir().mkdirs();
        List existingSourceDirs = existentDirsFilter.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(
                getDestinationDir(), getSrcDirs());

        if (!GUtil.isTrue(getSourceCompatibility()) || !GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        antCompile.execute(existingSourceDirs, includes, excludes, getDestinationDir(), getClasspath(),
                getSourceCompatibility(), getTargetCompatibility(), options, getProject().getAnt());
    }

    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection configuration) {
        this.classpath = configuration;
    }

    public Compile include(String... includes) {
        GUtil.flatten(Arrays.asList(includes), this.includes);
        return this;
    }

    public Compile exclude(String... excludes) {
        GUtil.flatten(Arrays.asList(excludes), this.excludes);
        return this;
    }

    public List<File> getSrcDirs() {
        return srcDirs;
    }

    public void setSrcDirs(List<File> srcDirs) {
        this.srcDirs = srcDirs;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getOptions() {
        return options;
    }

    public void setOptions(CompileOptions options) {
        this.options = options;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }
}
