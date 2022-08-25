package org.hansken.plugin.extraction;

import java.io.IOException;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.MetaExtractionPlugin;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.Trace;
import org.hansken.plugin.extraction.api.Vector;

/**
 * Example plugin that stores the width and height of a picture as a vector.
 *
 * @author Netherlands Forensic Institute
 */
public class VectorPlugin extends MetaExtractionPlugin {
    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "imaging";
    private static final String TOOL_NAME = "VectorPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Greenary")
            .email("example@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("1.0.0")
            .description("Example Extraction Plugin: Picture vectors")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("picture.width>0 picture.height>0")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    @Override
    public void process(final Trace trace) throws IOException {
        // adds a vector consisting of the picture dimensions (width and height)
        final Number width = trace.get("picture.width");
        final Number height = trace.get("picture.height");
        trace.addTracelet("prediction", tracelet -> tracelet
            .set("type", "example-vector")
            .set("embedding", Vector.of(width.floatValue(),
                                        height.floatValue())));
    }
}
