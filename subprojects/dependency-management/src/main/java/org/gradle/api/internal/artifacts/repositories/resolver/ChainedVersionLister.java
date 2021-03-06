/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ResourceException;
import org.gradle.internal.resource.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ChainedVersionLister implements VersionLister {
    private final List<VersionLister> versionListers;

    public ChainedVersionLister(VersionLister... delegates) {
        this.versionListers = Arrays.asList(delegates);
    }

    public VersionPatternVisitor newVisitor(final ModuleIdentifier module, final Collection<String> dest, final ResourceAwareResolveResult result)  {
        final List<VersionPatternVisitor> visitors = new ArrayList<VersionPatternVisitor>();
        for (VersionLister lister : versionListers) {
            visitors.add(lister.newVisitor(module, dest, result));
        }
        return new VersionPatternVisitor() {
            public void visit(ResourcePattern pattern, IvyArtifactName artifact) throws ResourceException {
                ResourceNotFoundException failure = null;
                for (VersionPatternVisitor list : visitors) {
                    try {
                        list.visit(pattern, artifact);
                        return;
                    } catch (ResourceNotFoundException e) {
                        // Try next
                        if (failure == null) {
                            failure = e;
                        }
                    } catch (Exception e) {
                        throw new ResourceException(String.format("Failed to list versions for %s.", module), e);
                    }
                }
                throw failure;
            }
        };
    }
}
