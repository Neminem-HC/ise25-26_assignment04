package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId The OpenStreetMap node ID.
 * @param lat    latitude of the node (nullable)
 * @param lon    longitude of the node (nullable)
 * @param tags   map of OSM tags (k -> v)
 */
@Builder
public record OsmNode(@NonNull Long nodeId,
                      Double lat,
                      Double lon,
                      Map<String, String> tags) {
    // The builder and record provide an immutable holder for the parsed OSM data.
}
