package org.hansken.plugin.extraction;

import static java.lang.String.format;

import java.io.IOException;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.DataContext;
import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.RandomAccessData;
import org.hansken.plugin.extraction.api.Trace;
import org.hansken.plugin.extraction.api.transformations.RangedDataTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DataTransformationPlugin implements ExtractionPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DataTransformationPlugin.class);

    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "data";
    private static final String TOOL_NAME = "DataTransformationPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    public static final char LINE_SEPARATOR = '\n';

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("tester@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("1.0.0")
            .description("Example Extraction Plugin: This plugin creates a child trace with a ranged data transformation for each line of a simple made-up chat log.")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("type=file AND $data.mimeClass=text")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    @Override
    public void process(final Trace trace, final DataContext context) throws IOException {
        // log something to the output as an example
        LOG.info("processing trace {} file: {}", trace.get("name"), trace.get("file.name"));

        int offset = 0;
        int length = 0;
        int lineNumber = 0;
        final RandomAccessData data = context.data();
        while (data.remaining() > 0) {
            length++;
            if (getNextChar(data) == LINE_SEPARATOR) {
                lineNumber++;
                buildChild(trace, lineNumber, RangedDataTransformation.builder().addRange(offset, length).build());
                offset += length;
                length = 0;
            }
        }
        // If there is remaining data, this forms the last child. But we don't want a child with data length 0.
        if (length > 0) {
            buildChild(trace, lineNumber + 1, RangedDataTransformation.builder().addRange(offset, length).build());
        }
    }

    private void buildChild(final Trace trace, final int lineNumber, final RangedDataTransformation transformation) throws IOException {
        trace.newChild(format("lineNumber %d", lineNumber), child -> {
            child.setData("raw", transformation);
        });
    }

    private char getNextChar(final RandomAccessData data) throws IOException {
        return (char) data.readNBytes(1)[0];
    }
}