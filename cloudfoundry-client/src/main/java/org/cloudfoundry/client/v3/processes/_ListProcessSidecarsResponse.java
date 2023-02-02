package org.cloudfoundry.client.v3.processes;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.cloudfoundry.client.v3.PaginatedResponse;
import org.cloudfoundry.client.v3.sidecars.SidecarResource;
import org.immutables.value.Value;

/**
 * The response payload for the Get Sidecars for a Process operation
 */

@JsonDeserialize
@Value.Immutable
abstract class _ListProcessSidecarsResponse extends PaginatedResponse<SidecarResource> {

}
