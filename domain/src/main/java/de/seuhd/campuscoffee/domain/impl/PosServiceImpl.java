package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        // TODO: Implement the actual conversion (the response is currently hard-coded).
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Note: This is a stub implementation and should be replaced with real mapping logic.
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        var tags = osmNode.tags();

        // Required fields for creating a POS: name and amenity/type and address (street + housenumber)
        String name = tags.get("name");
        String amenity = tags.get("amenity");
        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String postcode = tags.get("addr:postcode");
        String city = tags.get("addr:city");

        if (name == null || street == null || houseNumber == null) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        PosType type = PosType.CAFE; // default
        if (amenity != null) {
            switch (amenity) {
                case "cafe", "coffee_shop" -> type = PosType.CAFE;
                case "vending_machine" -> type = PosType.VENDING_MACHINE;
                case "bakery" -> type = PosType.BAKERY;
                case "restaurant", "canteen" -> type = PosType.CAFETERIA;
                default -> type = PosType.CAFE;
            }
        }

        Integer postal = null;
        try {
            if (postcode != null) postal = Integer.parseInt(postcode);
        } catch (NumberFormatException ignored) {
        }

        // Build POS with available fields; description can use the OSM "description" or fallback to empty string
        String description = tags.getOrDefault("description", "Imported from OpenStreetMap");

        return Pos.builder()
                .name(name)
                .description(description)
                .type(type)
                .campus(CampusType.ALTSTADT) // best-effort default - mapping from OSM to campus not implemented
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postal != null ? postal : 0)
                .city(city != null ? city : "")
                .build();
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
