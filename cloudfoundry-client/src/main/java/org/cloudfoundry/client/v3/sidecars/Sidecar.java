package org.cloudfoundry.client.v3.sidecars;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.cloudfoundry.Nullable;

import java.util.List;

/**
 * Base class for responses that are sidecars
 */
public abstract class Sidecar {

    /**
     * When the resource was created
     */
    @JsonProperty("created_at")
    public abstract String getCreatedAt();

    /**
     * The resource's id
     */
    @JsonProperty("guid")
    public abstract String getId();

    /**
     * When the resource was last updated
     */
    @JsonProperty("updated_at")
    @Nullable
    public abstract String getUpdatedAt();

    /**
     * The name
     */
    @JsonProperty("name")
    @Nullable
    public abstract String getName();

    /**
     * The command
     */
    @JsonProperty("command")
    @Nullable
    public abstract String getCommand();

    /**
     * The process types
     */
    @JsonProperty("process_types")
    @Nullable
    public abstract List<String> getProcessTypes();

    /**
     * The memory in megabytes
     */
    @JsonProperty("memory_in_mb")
    @Nullable
    public abstract Integer getMemoryInMb();

    /**
     * The origin
     */
    @JsonProperty("origin")
    @Nullable
    public abstract String getOrigin();

}
