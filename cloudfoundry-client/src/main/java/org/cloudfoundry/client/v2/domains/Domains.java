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

package org.cloudfoundry.client.v2.domains;

import org.reactivestreams.Publisher;

/**
 * Main entry point to the Cloud Foundry Domains Client API
 */
public interface Domains {

    /**
     * Makes the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_%28deprecated%29/create_a_domain_owned_by_the_given_organization_%28deprecated%29.html">Create a Domain owned by the
     * given Organization</a> request and the the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_%28deprecated%29/create_a_shared_domain_%28deprecated%29.html">Create a Shared
     * Domain</a> request
     *
     * @param request the Create a Domain request
     * @return the response from the Create a Domain request
     */
    Publisher<CreateDomainResponse> create(CreateDomainRequest request);

    /**
     * Makes the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_%28deprecated%29/delete_a_particular_domain_%28deprecated%29.html">Delete a Particular Domain</a> request
     *
     * @param request the Delete a Particular Domain request
     * @return the response from the Delete a Particular Domain request
     */
    Publisher<Void> delete(DeleteDomainRequest request);

    /**
     * Makes the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_(deprecated)/retrieve_a_particular_domain_(deprecated).html">Get Domain</a> request
     *
     * @param request The Get Domain request
     * @return the response from the Get Domain request
     */
    Publisher<GetDomainResponse> get(GetDomainRequest request);

    /**
     * Makes the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_%28deprecated%29/list_all_domains_%28deprecated%29.html">List all Domains</a> request
     *
     * @param request the List all Domains request
     * @return the response from the List all Domains request
     */
    Publisher<ListDomainsResponse> list(ListDomainsRequest request);

    /**
     * Makes the deprecated <a href="http://apidocs.cloudfoundry.org/214/domains_%28deprecated%29/list_all_spaces_for_the_domain_%28deprecated%29.html">List all Spaces for the Domain</a> request
     *
     * @param request the List all Spaces for the Domain request
     * @return the response from the List all Spaces for the Domain request
     */
    Publisher<ListDomainSpacesResponse> listSpaces(ListDomainSpacesRequest request);

}
