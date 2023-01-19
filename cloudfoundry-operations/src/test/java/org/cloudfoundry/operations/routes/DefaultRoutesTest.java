/*
 * Copyright 2013-2021 the original author or authors.
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

package org.cloudfoundry.operations.routes;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.client.v2.Metadata;
import org.cloudfoundry.client.v2.applications.ApplicationResource;
import org.cloudfoundry.client.v2.jobs.ErrorDetails;
import org.cloudfoundry.client.v2.jobs.JobEntity;
import org.cloudfoundry.client.v2.organizations.ListOrganizationPrivateDomainsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationPrivateDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
import org.cloudfoundry.client.v2.privatedomains.PrivateDomainResource;
import org.cloudfoundry.client.v2.routes.DeleteRouteResponse;
import org.cloudfoundry.client.v2.routes.ListRouteApplicationsRequest;
import org.cloudfoundry.client.v2.routes.ListRouteApplicationsResponse;
import org.cloudfoundry.client.v2.routes.ListRoutesResponse;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.routes.RouteResource;
import org.cloudfoundry.client.v2.serviceinstances.UnionServiceInstanceEntity;
import org.cloudfoundry.client.v2.serviceinstances.UnionServiceInstanceResource;
import org.cloudfoundry.client.v2.shareddomains.ListSharedDomainsRequest;
import org.cloudfoundry.client.v2.shareddomains.ListSharedDomainsResponse;
import org.cloudfoundry.client.v2.shareddomains.SharedDomainResource;
import org.cloudfoundry.client.v2.spaces.ListSpaceRoutesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpaceRoutesResponse;
import org.cloudfoundry.client.v2.spaces.ListSpaceServiceInstancesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpaceServiceInstancesResponse;
import org.cloudfoundry.client.v2.spaces.SpaceEntity;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.client.v3.BuildpackData;
import org.cloudfoundry.client.v3.ClientV3Exception;
import org.cloudfoundry.client.v3.Error;
import org.cloudfoundry.client.v3.Lifecycle;
import org.cloudfoundry.client.v3.Relationship;
import org.cloudfoundry.client.v3.ToOneRelationship;
import org.cloudfoundry.client.v3.applications.ApplicationState;
import org.cloudfoundry.client.v3.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v3.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v3.domains.CheckReservedRoutesRequest;
import org.cloudfoundry.client.v3.domains.CheckReservedRoutesResponse;
import org.cloudfoundry.client.v3.domains.DomainResource;
import org.cloudfoundry.client.v3.domains.ListDomainsRequest;
import org.cloudfoundry.client.v3.domains.ListDomainsResponse;
import org.cloudfoundry.client.v3.jobs.GetJobRequest;
import org.cloudfoundry.client.v3.jobs.GetJobResponse;
import org.cloudfoundry.client.v3.jobs.JobState;
import org.cloudfoundry.client.v3.routes.Application;
import org.cloudfoundry.client.v3.routes.Destination;
import org.cloudfoundry.client.v3.routes.InsertRouteDestinationsRequest;
import org.cloudfoundry.client.v3.routes.InsertRouteDestinationsResponse;
import org.cloudfoundry.client.v3.routes.RemoveRouteDestinationsRequest;
import org.cloudfoundry.client.v3.routes.RouteRelationships;
import org.cloudfoundry.client.v3.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v3.spaces.ListSpacesResponse;
import org.cloudfoundry.client.v3.spaces.SpacesV3;
import org.cloudfoundry.operations.AbstractOperationsTest;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.client.v3.LifecycleType.BUILDPACK;
import static org.cloudfoundry.operations.TestObjects.fill;
import static org.mockito.Mockito.when;

public final class DefaultRoutesTest extends AbstractOperationsTest {

    private final DefaultRoutes routes = new DefaultRoutes(Mono.just(this.cloudFoundryClient), Mono.just(TEST_ORGANIZATION_ID), Mono.just(TEST_SPACE_ID));

    @Test
    public void checkRouteInvalidDomain() {
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");

        this.routes
            .check(CheckRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .build())
            .as(StepVerifier::create)
            .expectNext(false)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void checkRouteInvalidHost() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRouteExistsFalse(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");

        this.routes
            .check(CheckRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectNext(false)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void checkRoute() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRouteExistsTrue(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");

        this.routes
            .check(CheckRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectNext(true)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRouteAssignedPort() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", null, null, 9999, "test-space-id");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .port(9999)
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .expectNext(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRouteInvalidDomain() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Domain test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRouteInvalidSpace() {
        requestSpacesEmpty(this.cloudFoundryClient, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomains(this.cloudFoundryClient, "test-domain");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Space test-space-name does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRouteNoHost() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", null, "test-path", null, "test-space-id");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .path("test-path")
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .expectNext(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRouteNoPath() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", "test-host", null, null,"test-space-id");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .expectNext(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void createRoute() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path",  null,"test-space-id");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .expectNext(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    // no random port option for V3
    public void createRouteRandomPort() {
        requestSpaces(this.spacesV3, TEST_ORGANIZATION_ID, TEST_SPACE_NAME);
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", null,  null, null, "test-space-id");

        this.routes
            .create(CreateRouteRequest.builder()
                .domain("test-domain")
                .randomPort(true)
                .space(TEST_SPACE_NAME)
                .build())
            .as(StepVerifier::create)
            .expectNext(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteAssignedPort() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", 9999);
        requestDeleteRouteV3(this.cloudFoundryClient, "test-route-id");
        requestJobSuccessV3(this.cloudFoundryClient, "test-job-entity-id");

        StepVerifier.withVirtualTime(() -> this.routes
            .delete(DeleteRouteRequest.builder()
                .domain("test-domain")
                .port(9999)
                .build()))
            .then(() -> VirtualTimeScheduler.get().advanceTimeBy(Duration.ofSeconds(3)))
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteFailure() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");
        requestDeleteRouteV3(this.cloudFoundryClient, "test-route-id");
        requestJobFailureV3(this.cloudFoundryClient, "test-job-entity-id");

        StepVerifier.withVirtualTime(() -> this.routes
            .delete(DeleteRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build()))
            .then(() -> VirtualTimeScheduler.get().advanceTimeBy(Duration.ofSeconds(3)))
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(ClientV3Exception.class).hasMessage("test-error-details-title(1): test-error-details-detail"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteInvalidDomain() {
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");

        this.routes
            .delete(DeleteRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Domain test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteInvalidRoute() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path", null);

        this.routes
            .delete(DeleteRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Route for test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteOrphanedRoutesAssociatedApplication() {
        requestSpaceRoutes(this.cloudFoundryClient, TEST_SPACE_ID);
        requestApplications(this.cloudFoundryClient, "test-route-id");

        this.routes
            .deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder()
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteOrphanedRoutesAssociatedService() {
        requestSpaceRoutesService(this.cloudFoundryClient, TEST_SPACE_ID);

        this.routes
            .deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder()
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteOrphanedRoutesNoAssociations() {
        requestSpaceRoutes(this.cloudFoundryClient, TEST_SPACE_ID);
        requestApplicationsEmpty(this.cloudFoundryClient, "test-route-id");
        requestDeleteRoute(this.cloudFoundryClient, "test-route-id");
        requestJobSuccess(this.cloudFoundryClient, "test-job-entity-id");

        StepVerifier.withVirtualTime(() -> this.routes
            .deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder()
                .build()))
            .then(() -> VirtualTimeScheduler.get().advanceTimeBy(Duration.ofSeconds(3)))
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteOrphanedRoutesNoAssociationsFailure() {
        requestSpaceRoutes(this.cloudFoundryClient, TEST_SPACE_ID);
        requestApplicationsEmpty(this.cloudFoundryClient, "test-route-id");
        requestDeleteRoute(this.cloudFoundryClient, "test-route-id");
        requestJobFailure(this.cloudFoundryClient, "test-job-entity-id");

        StepVerifier.withVirtualTime(() -> this.routes
            .deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder()
                .build()))
            .then(() -> VirtualTimeScheduler.get().advanceTimeBy(Duration.ofSeconds(3)))
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(ClientV2Exception.class).hasMessage("test-error-details-errorCode(1): test-error-details-description"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteOrphanedRoutesNoRoutes() {
        requestSpaceRoutesEmpty(this.cloudFoundryClient, TEST_SPACE_ID);

        this.routes
            .deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder()
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void deleteRoute() {
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");
        requestDeleteRouteV3(this.cloudFoundryClient, "test-route-id");
        requestJobSuccessV3(this.cloudFoundryClient, "test-job-entity-id");

        StepVerifier.withVirtualTime(() -> this.routes
            .delete(DeleteRouteRequest.builder()
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build()))
            .then(() -> VirtualTimeScheduler.get().advanceTimeBy(Duration.ofSeconds(3)))
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void listCurrentOrganizationNoSpace() {
        requestOrganizationsRoutes(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestPrivateDomainsAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestSharedDomainsAll(this.cloudFoundryClient);
        requestSpacesAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestSpaceServiceInstances(this.cloudFoundryClient, "test-route-entity-serviceInstanceId", "test-route-entity-spaceId");
        requestApplications(this.cloudFoundryClient, "test-id");

        this.routes
            .list(ListRoutesRequest.builder()
                .level(Level.ORGANIZATION)
                .build())
            .as(StepVerifier::create)
            .expectNext(Route.builder()
                .application("test-application-name")
                .domain("test-domain-name")
                .host("test-route-entity-host")
                .id("test-id")
                .path("test-route-entity-path")
                .service("test-service-instance-entityname")
                .space("test-space-entity-name")
                .build())
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void listCurrentOrganizationNoSpaceNoRoutes() {
        requestOrganizationsRoutesEmpty(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestPrivateDomainsAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestSharedDomainsAll(this.cloudFoundryClient);
        requestSpacesAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestApplications(this.cloudFoundryClient, "test-id");

        this.routes
            .list(ListRoutesRequest.builder()
                .level(Level.ORGANIZATION)
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void listCurrentSpace() {
        requestSpaceRoutes(this.cloudFoundryClient, TEST_SPACE_ID);
        requestPrivateDomainsAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestSharedDomainsAll(this.cloudFoundryClient);
        requestSpacesAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestApplications(this.cloudFoundryClient, "test-route-id");

        this.routes
            .list(ListRoutesRequest.builder()
                .level(Level.SPACE)
                .build())
            .as(StepVerifier::create)
            .expectNext(Route.builder()
                .application("test-application-name")
                .domain("test-domain-name")
                .host("test-route-entity-host")
                .id("test-route-id")
                .path("test-route-entity-path")
                .space("test-space-entity-name")
                .build())
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void listCurrentSpaceNoPath() {
        requestSpaceRoutesNoPath(this.cloudFoundryClient, TEST_SPACE_ID);
        requestPrivateDomainsAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestSharedDomainsAll(this.cloudFoundryClient);
        requestSpacesAll(this.cloudFoundryClient, TEST_ORGANIZATION_ID);
        requestApplications(this.cloudFoundryClient, "test-route-id");

        this.routes
            .list(ListRoutesRequest.builder()
                .level(Level.SPACE)
                .build())
            .as(StepVerifier::create)
            .expectNext(Route.builder()
                .application("test-application-name")
                .domain("test-domain-name")
                .host("test-route-entity-host")
                .id("test-route-id")
                .space("test-space-entity-name")
                .build())
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteAssignedPort() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", null, null, 9999);
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", null, null, 9999,  TEST_SPACE_ID);
        requestAssociateRoute(this.cloudFoundryClient, "test-application-id", "test-route-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .port(9999)
                .build())
            .as(StepVerifier::create)
            .expectNext(9999)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteExists() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");
        requestAssociateRoute(this.cloudFoundryClient, "test-application-id", "test-route-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteInvalidApplicationName() {
        requestApplicationsEmpty(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path", null);
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", "test-host",  "test-path",  null, "test-space-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Application test-application-name does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteInvalidDomain() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Domain test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteNoHost() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", null, "test-path", null);
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", null, "test-path", null, TEST_SPACE_ID);
        requestAssociateRoute(this.cloudFoundryClient, "test-application-id", "test-route-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRoutePath() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesTwo(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");
        requestAssociateRoute(this.cloudFoundryClient, "test-application-id", "test-route-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void mapRouteDomain() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path", null);
        requestCreateRoute(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path", null,TEST_SPACE_ID);
        requestAssociateRoute(this.cloudFoundryClient, "test-application-id", "test-route-id");

        this.routes
            .map(MapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void unmapRouteAssignedPort() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", 9999);
        requestRemoveRouteFromApplication(this.cloudFoundryClient, "test-route-id", "test-destination-id");

        this.routes
            .unmap(UnmapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .port(9999)
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void unmapRouteInvalidApplicationName() {
        requestApplicationsEmpty(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");

        this.routes
            .unmap(UnmapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Application test-application-name does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void unmapRouteInvalidDomain() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomainsEmpty(this.cloudFoundryClient, "test-domain");

        this.routes
            .unmap(UnmapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Domain test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void unmapRouteInvalidRoute() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutesEmpty(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path", null);

        this.routes
            .unmap(UnmapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .consumeErrorWith(t -> assertThat(t).isInstanceOf(IllegalArgumentException.class).hasMessage("Route for test-domain does not exist"))
            .verify(Duration.ofSeconds(5));
    }

    @Test
    public void unmapRoute() {
        requestApplications(this.cloudFoundryClient, "test-application-name", TEST_SPACE_ID);
        requestDomains(this.cloudFoundryClient, "test-domain");
        requestRoutes(this.cloudFoundryClient, "test-domain-id", "test-host", "test-path");
        requestRemoveRouteFromApplication(this.cloudFoundryClient, "test-route-id", "test-destination-id");

        this.routes
            .unmap(UnmapRouteRequest.builder()
                .applicationName("test-application-name")
                .domain("test-domain")
                .host("test-host")
                .path("test-path")
                .build())
            .as(StepVerifier::create)
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    private static void requestApplications(CloudFoundryClient cloudFoundryClient, String routeId) {
        when(cloudFoundryClient.routes()
            .listApplications(ListRouteApplicationsRequest.builder()
                .page(1)
                .routeId(routeId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListRouteApplicationsResponse.builder())
                    .resource(fill(ApplicationResource.builder(), "application-")
                        .build())
                    .build()));
    }

    private static void requestApplications(CloudFoundryClient cloudFoundryClient, String application, String spaceId) {
        when(cloudFoundryClient.applicationsV3()
            .list(ListApplicationsRequest.builder()
                .name(application)
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListApplicationsResponse.builder())
                    .resource(org.cloudfoundry.client.v3.applications.ApplicationResource.builder()
                        .createdAt("test-created-at")
                        .id("test-application-id")
                        .lifecycle(Lifecycle.builder()
                            .data(BuildpackData.builder()
                                .buildpack("test-buildpack")
                                .build())
                            .type(BUILDPACK)
                            .build())
                        .name("test-name")
                        .state(ApplicationState.STOPPED)
                        .build())
                    .build()));
    }
    private static void requestApplicationsEmpty(CloudFoundryClient cloudFoundryClient, String routeId) {
        when(cloudFoundryClient.routes()
            .listApplications(ListRouteApplicationsRequest.builder()
                .page(1)
                .routeId(routeId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListRouteApplicationsResponse.builder())
                    .build()));
    }

    private static void requestApplicationsEmpty(CloudFoundryClient cloudFoundryClient, String application, String spaceId) {
        when(cloudFoundryClient.applicationsV3()
            .list(ListApplicationsRequest.builder()
                .name(application)
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListApplicationsResponse.builder())
                    .build()));
    }

     private static void requestAssociateRoute(CloudFoundryClient cloudFoundryClient, String applicationId, String routeId) {
        when(cloudFoundryClient.routesV3()
            .insertDestinations(InsertRouteDestinationsRequest.builder()
                .destination(Destination.builder()
                    .application(Application.builder()
                        .applicationId(applicationId)
                        .build()
                    ).build()
                )
                .routeId(routeId)
                .build()))
            .thenReturn(Mono
                .just(fill(InsertRouteDestinationsResponse.builder())
                    .build()));
    }
    

    private static void requestCreateRoute(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path, Integer port, String spaceId) {
        when(cloudFoundryClient.routesV3()
            .create(org.cloudfoundry.client.v3.routes.CreateRouteRequest.builder()
                .host(host)
                .path(path)
                .port(port)
                .relationships(RouteRelationships.builder()
                    .domain(ToOneRelationship.builder()
                        .data(Relationship.builder()
                            .id(domainId)
                            .build())
                        .build())
                    .space(ToOneRelationship.builder()
                        .data(Relationship.builder()
                            .id(spaceId)
                            .build())
                        .build())
                    .build())
                .build()))
            .thenReturn(Mono
                .just(fill(org.cloudfoundry.client.v3.routes.CreateRouteResponse.builder(), "route-")
                    .build()));
    }

    private static void requestDeleteRoute(CloudFoundryClient cloudFoundryClient, String routeId) {
        when(cloudFoundryClient.routes()
            .delete(org.cloudfoundry.client.v2.routes.DeleteRouteRequest.builder()
                .async(true)
                .routeId(routeId)
                .build()))
            .thenReturn(Mono
                .just(fill(DeleteRouteResponse.builder())
                    .entity(fill(JobEntity.builder(), "job-entity-")
                        .build())
                    .build()));
    }

    private static void requestDeleteRouteV3(CloudFoundryClient cloudFoundryClient, String routeId) {
        when(cloudFoundryClient.routesV3()
            .delete(org.cloudfoundry.client.v3.routes.DeleteRouteRequest.builder()
                .routeId(routeId)
                .build()))
            .thenReturn(Mono
                .just("test-job-entity-id"));
    }
    private static void requestJobFailure(CloudFoundryClient cloudFoundryClient, String jobId) {
        when(cloudFoundryClient.jobs()
            .get(org.cloudfoundry.client.v2.jobs.GetJobRequest.builder()
                .jobId(jobId)
                .build()))
            .thenReturn(Mono
                .defer(new Supplier<Mono<org.cloudfoundry.client.v2.jobs.GetJobResponse>>() {

                    private final Queue<org.cloudfoundry.client.v2.jobs.GetJobResponse> responses = new LinkedList<>(Arrays.asList(
                        fill(org.cloudfoundry.client.v2.jobs.GetJobResponse.builder(), "job-")
                            .entity(fill(JobEntity.builder())
                                .status("running")
                                .build())
                            .build(),
                        fill(org.cloudfoundry.client.v2.jobs.GetJobResponse.builder(), "job-")
                            .entity(fill(JobEntity.builder())
                                .errorDetails(fill(ErrorDetails.builder(), "error-details-")
                                    .build())
                                .status("failed")
                                .build())
                            .build()
                    ));

                    @Override
                    public Mono<org.cloudfoundry.client.v2.jobs.GetJobResponse> get() {
                        return Mono.just(this.responses.poll());
                    }

                }));
    }

    private static void requestJobFailureV3(CloudFoundryClient cloudFoundryClient, String jobId) {
        when(cloudFoundryClient.jobsV3()
            .get(GetJobRequest.builder()
                .jobId(jobId)
                .build()))
            .thenReturn(Mono
                .defer(new Supplier<Mono<GetJobResponse>>() {

                    private final Queue<GetJobResponse> responses = new LinkedList<>(Arrays.asList(
                        fill(GetJobResponse.builder(), "job-")
                            .state(JobState.PROCESSING)
                            .build()
                        ,
                        fill(GetJobResponse.builder(), "job-")
                            .state(JobState.FAILED)
                            .error(fill(Error.builder(),"error-details-")
                                .build())
                            .build()
                    ));

                    @Override
                    public Mono<GetJobResponse> get() {
                        return Mono.just(this.responses.poll());
                    }

                }));
    }
    private static void requestJobSuccess(CloudFoundryClient cloudFoundryClient, String jobId) {
        when(cloudFoundryClient.jobs()
            .get(org.cloudfoundry.client.v2.jobs.GetJobRequest.builder()
                .jobId(jobId)
                .build()))
            .thenReturn(Mono
                .defer(new Supplier<Mono<org.cloudfoundry.client.v2.jobs.GetJobResponse>>() {

                    private final Queue<org.cloudfoundry.client.v2.jobs.GetJobResponse> responses = new LinkedList<>(Arrays.asList(
                        fill(org.cloudfoundry.client.v2.jobs.GetJobResponse.builder(), "job-")
                            .entity(fill(JobEntity.builder())
                                .status("running")
                                .build())
                            .build(),
                        fill(org.cloudfoundry.client.v2.jobs.GetJobResponse.builder(), "job-")
                            .entity(fill(JobEntity.builder())
                                .status("finished")
                                .build())
                            .build()
                    ));

                    @Override
                    public Mono<org.cloudfoundry.client.v2.jobs.GetJobResponse> get() {
                        return Mono.just(this.responses.poll());
                    }
                }));
    }

    private static void requestJobSuccessV3(CloudFoundryClient cloudFoundryClient, String jobId) {
        when(cloudFoundryClient.jobsV3()
            .get(GetJobRequest.builder()
                .jobId(jobId)
                .build()))
            .thenReturn(Mono
                .defer(new Supplier<Mono<GetJobResponse>>() {

                    private final Queue<GetJobResponse> responses = new LinkedList<>(Arrays.asList(
                        fill(GetJobResponse.builder(), "job-")
                            .state(JobState.PROCESSING)
                            .build()
                        ,
                        fill(GetJobResponse.builder(), "job-")
                            .state(JobState.COMPLETE)
                            .build()
                    ));

                    @Override
                    public Mono<GetJobResponse> get() {
                        return Mono.just(this.responses.poll());
                    }
                }));
    }

    private static void requestOrganizationsRoutes(CloudFoundryClient cloudFoundryClient, String organizationId) {
        when(cloudFoundryClient.routes()
            .list(org.cloudfoundry.client.v2.routes.ListRoutesRequest.builder()
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListRoutesResponse.builder())
                    .resource(fill(RouteResource.builder())
                        .entity(fill(RouteEntity.builder(), "route-entity-")
                            .domainId("test-domain-id")
                            .build())
                        .build())
                    .build()));
    }

    private static void requestOrganizationsRoutesEmpty(CloudFoundryClient cloudFoundryClient, String organizationId) {
        when(cloudFoundryClient.routes()
            .list(org.cloudfoundry.client.v2.routes.ListRoutesRequest.builder()
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListRoutesResponse.builder())
                    .build()));
    }

    private static void requestPrivateDomainsAll(CloudFoundryClient cloudFoundryClient, String organizationId) {
        when(cloudFoundryClient.organizations()
            .listPrivateDomains(ListOrganizationPrivateDomainsRequest.builder()
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListOrganizationPrivateDomainsResponse.builder())
                    .resource(fill(PrivateDomainResource.builder(), "domain-")
                        .metadata(fill(Metadata.builder(), "domain-metadata-")
                            .build())
                        .build())
                    .build()));
    }

    private static void requestRemoveRouteFromApplication(CloudFoundryClient cloudFoundryClient, String routeId, String destinationId) {
        when(cloudFoundryClient.routesV3()
            .removeDestinations(RemoveRouteDestinationsRequest.builder()
                .destinationId(destinationId)
                .routeId(routeId)
                .build()))
            .thenReturn(Mono.empty());
    }

    private static void requestRouteExistsFalse(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path) {
        when(cloudFoundryClient.domainsV3()
            .checkReservedRoutes(CheckReservedRoutesRequest.builder()
                .domainId(domainId)
                .host(host)
                .path(path)
                .build()))
            .thenReturn(Mono
                .just(fill(CheckReservedRoutesResponse.builder())
                    .matchingRoute(false)
                    .build()));
    }

    private static void requestRouteExistsTrue(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path) {
        when(cloudFoundryClient.domainsV3()
            .checkReservedRoutes(CheckReservedRoutesRequest.builder()
                .domainId(domainId)
                .host(host)
                .path(path)
                .build()))
            .thenReturn(Mono
                    .just(fill(CheckReservedRoutesResponse.builder())
                        .matchingRoute(true)
                    .build()));
    }

    private static void requestRoutes(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path) {
        when(cloudFoundryClient.routesV3()
            .list(org.cloudfoundry.client.v3.routes.ListRoutesRequest.builder()
                .domainId(domainId)
                .hosts(Optional.ofNullable(host).map(Collections::singletonList).orElse(null))
                .paths(Optional.ofNullable(path).map(Collections::singletonList).orElse(null))
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(org.cloudfoundry.client.v3.routes.ListRoutesResponse.builder())
                    .resource(fill(org.cloudfoundry.client.v3.routes.RouteResource.builder(), "route-")
                        .host(host)
                        .path(path == null ? "" : path)
                        .destination(Destination.builder()
                            .application(Application.builder()
                                .applicationId("test-application-id")
                                .build())
                            .destinationId("test-destination-id")
                            .build())
                        .build())
                    .build()));
    }
    private static void requestRoutes(CloudFoundryClient cloudFoundryClient, String domainId, Integer port) {
        when(cloudFoundryClient.routesV3()
            .list(org.cloudfoundry.client.v3.routes.ListRoutesRequest.builder()
                .domainId(domainId)
                .page(1)
                .port(Optional.ofNullable(port).orElse(null))
                .build()))
            .thenReturn(Mono
                .just(fill(org.cloudfoundry.client.v3.routes.ListRoutesResponse.builder())
                    .resource(fill(org.cloudfoundry.client.v3.routes.RouteResource.builder(), "route-")
                        .port(port)
                        .destination(Destination.builder()
                            .application(Application.builder()
                                .applicationId("test-application-id")
                                .build())
                            .destinationId("test-destination-id")
                            .build())
                        .build())
                    .build()));
    }

    private static void requestRoutesEmpty(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path, Integer port) {
        when(cloudFoundryClient.routesV3()
            .list(org.cloudfoundry.client.v3.routes.ListRoutesRequest.builder()
                .domainId(domainId)
                .hosts(Optional.ofNullable(host).map(Collections::singletonList).orElse(Collections.emptyList()))
                .page(1)
                .paths(Optional.ofNullable(path).map(Collections::singletonList).orElse(Collections.emptyList()))
                .ports(Optional.ofNullable(port).map(Collections::singletonList).orElse(Collections.emptyList()))
                .build()))
            .thenReturn(Mono
                .just(fill(org.cloudfoundry.client.v3.routes.ListRoutesResponse.builder())
                    .build()));
    }

    private static void requestRoutesTwo(CloudFoundryClient cloudFoundryClient, String domainId, String host, String path) {
        when(cloudFoundryClient.routesV3()
            .list(org.cloudfoundry.client.v3.routes.ListRoutesRequest.builder()
                .domainId(domainId)
                .hosts(Optional.ofNullable(host).map(Collections::singletonList).orElse(Collections.emptyList()))
                .page(1)
                .paths(Optional.ofNullable(path).map(Collections::singletonList).orElse(Collections.emptyList()))
                .build()))
            .thenReturn(Mono
                .just(fill(org.cloudfoundry.client.v3.routes.ListRoutesResponse.builder())
                    .resource(fill(org.cloudfoundry.client.v3.routes.RouteResource.builder(), "route-")
                        .build())
                    .resource(fill(org.cloudfoundry.client.v3.routes.RouteResource.builder(), "route-")
                        .host(host)
                        .path(path == null ? "" : path)
                        .build())
                    .build()));
    }

    private static void requestDomains(CloudFoundryClient cloudFoundryClient, String domain) {
        when(cloudFoundryClient.domainsV3()
            .list(ListDomainsRequest.builder()
                .name(domain)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListDomainsResponse.builder())
                    .resource(fill(DomainResource.builder(), "domain-")
                        .metadata(fill(org.cloudfoundry.client.v3.Metadata.builder(), "domain-metadata-")
                            .build())
                        .isInternal(false)
                        .build())
                    .build()));
    }

    private static void requestSharedDomainsAll(CloudFoundryClient cloudFoundryClient) {
        when(cloudFoundryClient.sharedDomains()
            .list(ListSharedDomainsRequest.builder()
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSharedDomainsResponse.builder())
                    .resource(fill(SharedDomainResource.builder(), "domain-")
                        .metadata(fill(Metadata.builder(), "domain-metadata-")
                            .id("test-domain-id")
                            .build())
                        .build())
                    .build()));

    }
    
    private static void requestDomainsEmpty(CloudFoundryClient cloudFoundryClient, String domain) {
        when(cloudFoundryClient.domainsV3()
            .list(ListDomainsRequest.builder()
                .name(domain)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListDomainsResponse.builder())
                    .build()));
    }

    private static void requestSpaceRoutes(CloudFoundryClient cloudFoundryClient, String spaceId) {
        when(cloudFoundryClient.spaces()
            .listRoutes(ListSpaceRoutesRequest.builder()
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpaceRoutesResponse.builder())
                    .resource(fill(RouteResource.builder(), "route-")
                        .entity(fill(RouteEntity.builder(), "route-entity-")
                            .domainId("test-domain-id")
                            .serviceInstanceId(null)
                            .build())
                        .build())
                    .build()));
    }

    private static void requestSpaceRoutesEmpty(CloudFoundryClient cloudFoundryClient, String spaceId) {
        when(cloudFoundryClient.spaces()
            .listRoutes(ListSpaceRoutesRequest.builder()
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpaceRoutesResponse.builder())
                    .build()));
    }

    private static void requestSpaceRoutesNoPath(CloudFoundryClient cloudFoundryClient, String spaceId) {
        when(cloudFoundryClient.spaces()
            .listRoutes(ListSpaceRoutesRequest.builder()
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpaceRoutesResponse.builder())
                    .resource(fill(RouteResource.builder(), "route-")
                        .entity(fill(RouteEntity.builder(), "route-entity-")
                            .domainId("test-domain-id")
                            .path(null)
                            .serviceInstanceId(null)
                            .build())
                        .build())
                    .build()));
    }

    private static void requestSpaceRoutesService(CloudFoundryClient cloudFoundryClient, String spaceId) {
        when(cloudFoundryClient.spaces()
            .listRoutes(ListSpaceRoutesRequest.builder()
                .page(1)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpaceRoutesResponse.builder())
                    .resource(fill(RouteResource.builder(), "route-")
                        .entity(fill(RouteEntity.builder(), "route-entity-")
                            .domainId("test-domain-id")
                            .serviceInstanceId("test-service-instance-id")
                            .build())
                        .build())
                    .build()));
    }

    private static void requestSpaceServiceInstances(CloudFoundryClient cloudFoundryClient, String serviceInstanceId, String spaceId) {
        when(cloudFoundryClient.spaces()
            .listServiceInstances(ListSpaceServiceInstancesRequest.builder()
                .page(1)
                .returnUserProvidedServiceInstances(true)
                .spaceId(spaceId)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpaceServiceInstancesResponse.builder())
                    .resource(fill(UnionServiceInstanceResource.builder(), "service-instance-")
                        .metadata(fill(Metadata.builder(), "service-instance-metadata-")
                            .id(serviceInstanceId)
                            .build())
                        .entity(fill(UnionServiceInstanceEntity.builder(), "service-instance-entity")
                            .spaceId(spaceId)
                            .build())
                        .build())
                    .build()));
    }
    

    private static void requestSpaces(SpacesV3 spacesV3, String organizationId, String space) {
        when(spacesV3
            .list(ListSpacesRequest.builder()
                .name(space)
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpacesResponse.builder())
                    .resource(fill(org.cloudfoundry.client.v3.spaces.SpaceResource.builder(), "space-").build())
                    .build()));
    }

    private static void requestSpacesAll(CloudFoundryClient cloudFoundryClient, String organizationId) {
        when(cloudFoundryClient.organizations()
            .listSpaces(ListOrganizationSpacesRequest.builder()
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListOrganizationSpacesResponse.builder())
                    .resource(SpaceResource.builder()
                        .metadata(fill(Metadata.builder(), "space-resource-")
                            .id("test-route-entity-spaceId")
                            .build())
                        .entity(fill(SpaceEntity.builder(), "space-entity-")
                            .name("test-space-entity-name")
                            .organizationId(organizationId)
                            .build())
                        .build())
                    .build()));
    }

    private static void requestSpacesEmpty(CloudFoundryClient cloudFoundryClient, String organizationId, String space) {
        when(cloudFoundryClient.spacesV3()
            .list(ListSpacesRequest.builder()
                .name(space)
                .organizationId(organizationId)
                .page(1)
                .build()))
            .thenReturn(Mono
                .just(fill(ListSpacesResponse.builder())
                    .build()));
    }

}
