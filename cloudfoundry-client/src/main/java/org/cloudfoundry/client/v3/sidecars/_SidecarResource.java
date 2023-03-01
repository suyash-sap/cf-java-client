package org.cloudfoundry.client.v3.sidecars;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

/**
 * The Resource response payload for the List sidecars operation
 */
@JsonDeserialize
@Value.Immutable
abstract class _SidecarResource extends Sidecar {
}
