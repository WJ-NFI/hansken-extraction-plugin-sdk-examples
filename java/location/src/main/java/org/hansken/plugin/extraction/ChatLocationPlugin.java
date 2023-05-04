package org.hansken.plugin.extraction;

import java.util.Map;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.LatLong;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.MetaExtractionPlugin;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.Trace;

// an example plugin using a very advanced algorithm for extracting and pinpointing secret locations
public final class ChatLocationPlugin extends MetaExtractionPlugin {

    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "gps";
    private static final String TOOL_NAME = "ChatLocationPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    // we have an extensive database of secret location names
    private static final Map<String, LatLong> LOCATION_TRANSLATION_DATABASE = Map.of(
        "arggh",     LatLong.of(52.0448013, 4.3585176), // NFI
        "Tyrol",     LatLong.of(52.3791316, 4.8980833), // Amsterdam Centraal
        "waterval",  LatLong.of(51.9243876, 4.4675636), // Rotterdam Centraal
        "scrum",     LatLong.of(52.077184,  4.3123263), // Den Haag Centraal
        "die jonge", LatLong.of(52.0894436, 5.1077982)  // Utrecht Centraal
    );

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("tester@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("0.0.1")
            .description("Example Extraction Plugin: Location extractor for exclusive chats")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("type=chatMessage AND chatMessage.message=*")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    // note that this is not a 'normal' ExtractionPlugin, but a MetaExtractionPlugin
    // the latter only processes trace metadata itself, and as such only receives a trace
    // in contrast with an ExtractionPlugin, which also receives a data stream of that trace
    @Override
    public void process(final Trace trace) {
        // extract the message
        final String message = trace.get("chatMessage.message");

        // try and match the known location on the individual words
        LOCATION_TRANSLATION_DATABASE.forEach((locationName, gpsLocation) -> {
            if (message.contains(locationName)) {
                trace.addType("gps")
                    .set("gps.application", trace.get("chatMessage.application"))
                    .set("gps.latlong", gpsLocation);
            }
        });
    }
}