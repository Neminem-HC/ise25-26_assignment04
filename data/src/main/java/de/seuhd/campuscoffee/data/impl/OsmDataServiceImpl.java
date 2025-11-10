package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OSM import service.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {

    // Use the official OSM API host (api.openstreetmap.org) which returns raw XML for the node endpoint.
    private static final String OSM_NODE_URL = "https://api.openstreetmap.org/api/0.6/node/%d";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node {} from OpenStreetMap API", nodeId);

        String url = String.format(OSM_NODE_URL, nodeId);
        ResponseEntity<String> resp = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            // OSM requires a sensible User-Agent; anonymous requests without one may be rejected or redirected.
            headers.set("User-Agent", "CampusCoffee/0.0.1 (import-script)");
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));

            resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                var contentType = resp.getHeaders().getContentType();
                log.debug("OSM response content-type: {}", contentType);

                // If the response is not XML, log the body and treat as not found/error. This avoids XML parser
                // fatal errors when an HTML error page or redirect is returned.
                if (contentType == null || (!MediaType.APPLICATION_XML.isCompatibleWith(contentType) && !MediaType.TEXT_XML.isCompatibleWith(contentType) && !MediaType.ALL.isCompatibleWith(contentType))) {
                    String body = resp.getBody();
                    log.error("Unexpected non-XML response when fetching OSM node {}: content-type={}", nodeId, contentType);
                    if (body != null) {
                        log.error("Response body (start): {}", body.substring(0, Math.min(body.length(), 1000)));
                    }
                    throw new OsmNodeNotFoundException(nodeId);
                }

                return parseOsmNodeXml(nodeId, resp.getBody());
            } else if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new OsmNodeNotFoundException(nodeId);
            } else {
                log.error("Unexpected response when fetching OSM node {}: {}", nodeId, resp.getStatusCode());
                if (resp.getBody() != null) {
                    log.debug("Response body: {}", resp.getBody());
                }
                throw new OsmNodeNotFoundException(nodeId);
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new OsmNodeNotFoundException(nodeId);
        } catch (Exception e) {
            // If parsing failed because the content wasn't XML (e.g. HTML page), log the body to help debugging
            String body = resp != null ? resp.getBody() : null;
            if (body != null) {
                log.error("Error fetching or parsing OSM node {}: {} - response body starts with: {}", nodeId, e.getMessage(), body.substring(0, Math.min(body.length(), 200)));
            } else {
                log.error("Error fetching or parsing OSM node {}: {}", nodeId, e.getMessage());
            }
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    private OsmNode parseOsmNodeXml(Long nodeId, String xml) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        var db = dbf.newDocumentBuilder();
        var doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        var nodeList = doc.getElementsByTagName("node");
        if (nodeList.getLength() == 0) {
            // No node element found
            throw new OsmNodeNotFoundException(nodeId);
        }

        var nodeElem = nodeList.item(0);
        var attrs = nodeElem.getAttributes();
        String latStr = attrs.getNamedItem("lat") != null ? attrs.getNamedItem("lat").getNodeValue() : null;
        String lonStr = attrs.getNamedItem("lon") != null ? attrs.getNamedItem("lon").getNodeValue() : null;

        Double lat = latStr != null ? Double.parseDouble(latStr) : null;
        Double lon = lonStr != null ? Double.parseDouble(lonStr) : null;

        Map<String, String> tags = new HashMap<>();
        var tagNodes = doc.getElementsByTagName("tag");
        for (int i = 0; i < tagNodes.getLength(); i++) {
            var tag = tagNodes.item(i);
            var tAttrs = tag.getAttributes();
            var k = tAttrs.getNamedItem("k");
            var v = tAttrs.getNamedItem("v");
            if (k != null && v != null) {
                tags.put(k.getNodeValue(), v.getNodeValue());
            }
        }

        return OsmNode.builder()
                .nodeId(nodeId)
                .lat(lat)
                .lon(lon)
                .tags(tags)
                .build();
    }
}
