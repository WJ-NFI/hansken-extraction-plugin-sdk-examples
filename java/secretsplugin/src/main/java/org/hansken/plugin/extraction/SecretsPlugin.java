package org.hansken.plugin.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.hansken.plugin.extraction.api.MaturityLevel.PROOF_OF_CONCEPT;
import static org.hansken.plugin.extraction.core.data.RandomAccessDatas.asInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Scanner;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.DataContext;
import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.Trace;

/**
 * {@link ExtractionPlugin} which parses a made up example text format. The format
 * is as follows: the lines up to the first empty line represent a single text stream.
 * The lines after the empty line each contain a base 64 encoded picture.
 */
public final class SecretsPlugin implements ExtractionPlugin {

    private static final Decoder BASE_64 = Base64.getDecoder();

    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "crypto";
    private static final String TOOL_NAME = "SecretsPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("tester@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("0.0.1")
            .description("Extractor for .peb secret files")
            .author(author)
            .maturityLevel(PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("$data.type=raw AND file.extension=peb")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    @Override
    public void process(final Trace trace, final DataContext dataContext) throws IOException {
        try (Scanner scanner = new Scanner(asInputStream(dataContext.data()))) {
            // all lines up to the first empty line represent a single text stream,
            // this is to demonstrate adding a text stream by writing chunks of data
            trace.setData("text", stream -> {
                while (scanner.hasNextLine()) {
                    final String line = scanner.nextLine();
                    // stop if we detect the empty line
                    if (line.isBlank()) {
                        return;
                    }
                    stream.write(utf8Bytes(line));
                }
            });

            int pictureNumber = 0;
            while (scanner.hasNextLine()) {
                // now we add each base 64 encoded image as a new child trace,
                // writing the full data at once as a single stream
                trace.newChild("picture-" + pictureNumber++, child -> {
                    child
                        .addType("picture").set("picture.type", "thumbnail")
                        .setData("raw", base64Decode(scanner.nextLine()));
                });
            }
        }
    }

    private static InputStream base64Decode(final String string) {
        return new ByteArrayInputStream(BASE_64.decode(utf8Bytes(string)));
    }

    private static byte[] utf8Bytes(final String string) {
        return string.getBytes(UTF_8);
    }
}
