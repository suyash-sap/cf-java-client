package org.cloudfoundry.client.v3.processes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.cloudfoundry.client.v3.sidecars.SidecarResource;
import org.immutables.value.Value;

import java.util.List;

/**
 * The response payload for the Get Sidecars for a Process operation
 */

@JsonDeserialize
@Value.Immutable
abstract class _GetProcessSidecarsResponse {

    /**
     * The resources
     */
    @JsonProperty("resources")
    abstract List<SidecarResource> getResources();
}
