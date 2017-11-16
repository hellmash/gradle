/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IncrementalCompileFilesFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileFilesFactory.class);
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileHasher hasher;

    public IncrementalCompileFilesFactory(SourceIncludesParser sourceIncludesParser, SourceIncludesResolver sourceIncludesResolver, FileHasher hasher) {
        this.sourceIncludesParser = sourceIncludesParser;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.hasher = hasher;
    }

    public IncrementalCompileFiles filesFor(CompilationState previousCompileState) {
        return new DefaultIncrementalCompileFiles(previousCompileState);
    }

    private class DefaultIncrementalCompileFiles implements IncrementalCompileFiles {

        private final CompilationState previous;

        @Override
        public CompilationState getCurrent() {
            return current.snapshot();
        }

        private final BuildableCompilationState current = new BuildableCompilationState();

        private final List<File> toRecompile = new ArrayList<File>();
        private final Set<File> discoveredInputs = Sets.newHashSet();
        private final Set<File> existingHeaders = Sets.newHashSet();

        private boolean sourceFilesUseMacroIncludes;

        DefaultIncrementalCompileFiles(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        @Override
        public void processSource(File sourceFile) {
            current.addSourceInput(sourceFile);
            if (visitSourceFile(sourceFile)) {
                toRecompile.add(sourceFile);
            }
        }

        private boolean visitSourceFile(File sourceFile) {
            List<IncludeDirectives> included = new ArrayList<IncludeDirectives>();
            Set<File> visited = new HashSet<File>();
            return visitFile(sourceFile, included, visited) || !previous.getSourceInputs().contains(sourceFile);
        }

        private boolean visitFile(File file, List<IncludeDirectives> included, Set<File> visited) {
            if (!visited.add(file)) {
                // A cycle, treat as unchanged here
                return false;
            }
            if (!file.isFile()) {
                return true;
            }

            boolean changed = false;
            CompilationFileState previousState = previous.getState(file);
            HashCode newHash = hasher.hash(file);
            IncludeDirectives includeDirectives;
            if (sameHash(previousState, newHash)) {
                includeDirectives = previousState.getIncludeDirectives();
            } else {
                changed = true;
                // TODO - parse file once only
                includeDirectives = sourceIncludesParser.parseIncludes(file);
            }

            included.add(includeDirectives);
            SourceIncludesResolver.ResolvedSourceIncludes resolutionResult = sourceIncludesResolver.resolveIncludes(file, includeDirectives, included);
            // TODO - collect the resolved files only in the source file
            CompilationFileState newState = new CompilationFileState(newHash, includeDirectives, ImmutableSet.copyOf(resolutionResult.getResolvedIncludeFiles()));

            // TODO - when file has no macro includes directly or indirectly, remember the result and skip visiting it for other source files
            current.setState(file, newState);
            discoveredInputs.addAll(resolutionResult.getCheckedLocations());
            for (ResolvedInclude resolvedInclude : resolutionResult.getResolvedIncludes()) {
                if (resolvedInclude.isUnknown()) {
                    sourceFilesUseMacroIncludes = true;
                }
                if (!resolvedInclude.isUnknown()) {
                    existingHeaders.add(resolvedInclude.getFile());
                }
            }

            // Compare the previous resolved includes with resolving now.
            if (!sameResolved(previousState, newState)) {
                changed = true;
            }

            for (ResolvedInclude dep : resolutionResult.getResolvedIncludes()) {
                if (dep.isUnknown()) {
                    LOGGER.info("Cannot determine changed state of included '{}' in source file '{}'. Assuming changed.", dep.getInclude(), file.getName());
                    changed = true;
                } else {
                    boolean depChanged = visitFile(dep.getFile(), included, visited);
                    changed = changed || depChanged;
                }
            }

            return changed;
        }

        private boolean sameHash(CompilationFileState previousState, HashCode newHash) {
            return previousState != null && newHash.equals(previousState.getHash());
        }

        private boolean sameResolved(CompilationFileState previousState, CompilationFileState newState) {
            return previousState != null && newState.getResolvedIncludes().equals(previousState.getResolvedIncludes());
        }

        @Override
        public List<File> getModifiedSources() {
            return toRecompile;
        }

        @Override
        public List<File> getRemovedSources() {
            List<File> removed = new ArrayList<File>();
            for (File previousSource : previous.getSourceInputs()) {
                if (!current.getSourceInputs().contains(previousSource)) {
                    removed.add(previousSource);
                }
            }
            return removed;
        }

        @Override
        public Set<File> getDiscoveredInputs() {
            return discoveredInputs;
        }

        @Override
        public Set<File> getExistingHeaders() {
            return existingHeaders;
        }

        @Override
        public boolean isSourceFilesUseMacroIncludes() {
            return sourceFilesUseMacroIncludes;
        }
    }

}