/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.RuleAction;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ComponentSelectionInternal;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.IvyModuleVersionMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class ComponentSelectionRulesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentSelectionRulesProcessor.class);
    private static final String USER_CODE_ERROR = "Could not apply component selection rule with all().";

    public void apply(ComponentSelection selection, Collection<RuleAction<? super ComponentSelection>> rules, ModuleComponentRepositoryAccess moduleAccess) {
        MetadataProvider metadataProvider = new MetadataProvider(selection, moduleAccess);

        List<RuleAction<? super ComponentSelection>> noInputRules = Lists.newArrayList();
        List<RuleAction<? super ComponentSelection>> inputRules = Lists.newArrayList();
        for (RuleAction<? super ComponentSelection> rule : rules) {
            if (rule.getInputTypes().isEmpty()) {
                noInputRules.add(rule);
            } else {
                inputRules.add(rule);
            }
        }

        if (processRules(noInputRules, selection, metadataProvider)) {
            processRules(inputRules, selection, metadataProvider);
        }
    }

    private boolean processRules(List<RuleAction<? super ComponentSelection>> rules, ComponentSelection selection, MetadataProvider metadataProvider) {
        for (RuleAction<? super ComponentSelection> rule : rules) {
            processRule(selection, metadataProvider, rule);

            if (((ComponentSelectionInternal) selection).isRejected()) {
                LOGGER.info(String.format("Selection of '%s' rejected by component selection rule: %s", selection.getCandidate(), ((ComponentSelectionInternal) selection).getRejectionReason()));
                return false;
            }
        }
        return true;
    }

    private void processRule(ComponentSelection selection, MetadataProvider metadataProvider, RuleAction<? super ComponentSelection> rule) {
        List<Object> inputs = Lists.newArrayList();
        for (Class<?> inputType : rule.getInputTypes()) {
            if (inputType == ModuleVersionMetaData.class) {
                inputs.add(metadataProvider.getMetaData());
                continue;
            }
            if (inputType == ComponentMetadata.class) {
                inputs.add(metadataProvider.getComponentMetadata());
                continue;
            }
            if (inputType == IvyModuleDescriptor.class) {
                IvyModuleDescriptor ivyModuleDescriptor = metadataProvider.getIvyModuleDescriptor();
                if (ivyModuleDescriptor == null) {
                    // Rules that require ivy module descriptor input are not fired for non-ivy modules
                    return;
                }
                inputs.add(ivyModuleDescriptor);
                continue;
            }
            // We've already validated the inputs: should never get here.
            throw new IllegalStateException();
        }

        try {
            rule.execute(selection, inputs);
        } catch (Exception e) {
            throw new InvalidUserCodeException(USER_CODE_ERROR, e);
        }
    }

    private static class MetadataProvider {
        private final ComponentSelection componentSelection;
        private final ModuleComponentRepositoryAccess moduleAccess;
        private MutableModuleVersionMetaData cachedMetaData;

        private MetadataProvider(ComponentSelection componentSelection, ModuleComponentRepositoryAccess moduleAccess) {
            this.componentSelection = componentSelection;
            this.moduleAccess = moduleAccess;
        }

        public ComponentMetadata getComponentMetadata() {
            return new ComponentMetadataDetailsAdapter(getMetaData());
        }

        public IvyModuleDescriptor getIvyModuleDescriptor() {
            ModuleVersionMetaData metaData = getMetaData();
            if (metaData instanceof IvyModuleVersionMetaData) {
                IvyModuleVersionMetaData ivyMetadata = (IvyModuleVersionMetaData) metaData;
                return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
            }
            return null;
        }

        public MutableModuleVersionMetaData getMetaData() {
            if (cachedMetaData == null) {
                cachedMetaData = initMetaData(componentSelection, moduleAccess);
            }
            return cachedMetaData;
        }

        private MutableModuleVersionMetaData initMetaData(ComponentSelection selection, ModuleComponentRepositoryAccess moduleAccess) {
            BuildableModuleVersionMetaDataResolveResult descriptorResult = new DefaultBuildableModuleVersionMetaDataResolveResult();
            DependencyMetaData dependency = ((ComponentSelectionInternal) selection).getDependencyMetaData().withRequestedVersion(selection.getCandidate().getVersion());
            moduleAccess.resolveComponentMetaData(dependency, selection.getCandidate(), descriptorResult);
            return descriptorResult.getMetaData();
        }

    }
}
