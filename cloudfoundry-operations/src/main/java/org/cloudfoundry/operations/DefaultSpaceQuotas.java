/*
 * Copyright 2013-2015 the original author or authors.
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

package org.cloudfoundry.operations;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpaceQuotaDefinitionsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpaceQuotaDefinitionsResponse;
import org.cloudfoundry.client.v2.spacequotadefinitions.SpaceQuotaDefinitionEntity;
import org.cloudfoundry.client.v2.spacequotadefinitions.SpaceQuotaDefinitionResource;
import org.cloudfoundry.operations.v2.PageUtils;
import org.reactivestreams.Publisher;
import reactor.fn.Function;
import reactor.fn.Predicate;

final class DefaultSpaceQuotas extends AbstractOperations implements SpaceQuotas {

    private static final Function<SpaceQuotaDefinitionResource, SpaceQuota> toSpaceQuotaFunction = new
            Function<SpaceQuotaDefinitionResource, SpaceQuota>() {
                @Override
                public SpaceQuota apply(SpaceQuotaDefinitionResource spaceQuotaDefinition) {

                    SpaceQuotaDefinitionEntity spaceQuotaDefinitionEntity = spaceQuotaDefinition.getEntity();
                    return SpaceQuota.builder()
                            .id(spaceQuotaDefinition.getMetadata().getId())
                            .instanceMemoryLimit(spaceQuotaDefinitionEntity.getInstanceMemoryLimit())
                            .name(spaceQuotaDefinitionEntity.getName())
                            .organizationId(spaceQuotaDefinitionEntity.getOrganizationId())
                            .paidServicePlans(spaceQuotaDefinitionEntity.getNonBasicServicesAllowed())
                            .totalMemoryLimit(spaceQuotaDefinitionEntity.getMemoryLimit())
                            .totalRoutes(spaceQuotaDefinitionEntity.getTotalRoutes())
                            .totalServiceInstances(spaceQuotaDefinitionEntity.getTotalServices())
                            .build();

                }
            };

    private final CloudFoundryClient cloudFoundryClient;

    private final Function<String, Publisher<SpaceQuotaDefinitionResource>>
            toSpaceQuotaDefinitionStream = new Function<String, Publisher<SpaceQuotaDefinitionResource>>() {

        @Override
        public Publisher<SpaceQuotaDefinitionResource> apply(final String targetedOrganization) {
            return PageUtils.resourceStream(
                    new Function<Integer, Publisher<ListOrganizationSpaceQuotaDefinitionsResponse>>() {

                        @Override
                        public Publisher<ListOrganizationSpaceQuotaDefinitionsResponse> apply(Integer page) {
                            ListOrganizationSpaceQuotaDefinitionsRequest request =
                                    ListOrganizationSpaceQuotaDefinitionsRequest.builder()
                                            .id(targetedOrganization)
                                            .page(page)
                                            .build();

                            return DefaultSpaceQuotas.this.cloudFoundryClient.organizations()
                                    .listSpaceQuotaDefinitions(request);
                        }

                    });
        }

    };

    DefaultSpaceQuotas(CloudFoundryClient cloudFoundryClient, String organizationId) {
        super(organizationId, null);
        this.cloudFoundryClient = cloudFoundryClient;
    }

    @Override
    public Publisher<SpaceQuota> get(final GetSpaceQuotaRequest request) {
        return getTargetedOrganization()
                .flatMap(this.toSpaceQuotaDefinitionStream)
                .filter(new Predicate<SpaceQuotaDefinitionResource>() {
                    @Override
                    public boolean test(SpaceQuotaDefinitionResource spaceQuotaDefinition) {
                        return request.getName().equals(spaceQuotaDefinition.getEntity().getName());
                    }
                })
                .map(toSpaceQuotaFunction);
    }

    @Override
    public Publisher<SpaceQuota> list() {
        return getTargetedOrganization()
                .flatMap(this.toSpaceQuotaDefinitionStream)
                .map(toSpaceQuotaFunction);
    }

}
