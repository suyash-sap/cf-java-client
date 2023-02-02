package org.cloudfoundry.client.v3.processes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.cloudfoundry.client.v3.PaginatedRequest;
import org.immutables.value.Value;

/**
 * The request payload for the Get Sidecars for a Process operation
 */
@Value.Immutable
abstract class _ListProcessSidecarsRequest extends PaginatedRequest {

    /**
     * The process id
     */
    @JsonIgnore
    abstract String getProcessId();

}