package org.hansken.plugin.extraction;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.DataContext;
import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.RandomAccessData;
import org.hansken.plugin.extraction.api.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DataDigestPlugin implements ExtractionPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DataDigestPlugin.class);
    private static final int CHUNK_SIZE = 1024 * 1024; // 1 MiB

    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "digest";
    private static final String TOOL_NAME = "DataDigestPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("tester@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("1.0.0")
            .description("Example Extraction Plugin: Data digest plugin (reads the data in chunks and calculates the hash)")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("$data.type=*")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    @Override
    public void process(final Trace trace, final DataContext dataContext) throws IOException {
        final MessageDigest messageDigest = sha256MessageDigest();
        final RandomAccessData data = dataContext.data();
        final String dataType = dataContext.dataType();
        final long size = data.size();

        // calculate total chunks, including the last chunk which can be smaller than the chunk size
        // formula to calculate total chunks: (a + b - 1) / b
        // for example 5 / 2 = 3 -> (5 + 2 - 1) / 2 = 3
        final long totalChunks = (size + CHUNK_SIZE - 1) / CHUNK_SIZE;

        for (int currentChunk = 0; currentChunk < totalChunks; currentChunk++) {
            final long position = (long) currentChunk * CHUNK_SIZE;
            final byte[] bytes = data.remaining() < CHUNK_SIZE
                ? data.readNBytes((int) (size - position))
                : data.readNBytes(CHUNK_SIZE);

            messageDigest.update(bytes);
            LOG.info("Processed chunk {}/{}", currentChunk + 1, totalChunks);
        }

        final String digest = bytesToHex(messageDigest.digest());
        trace.addType("data").set(String.format("data.%s.hash.sha256", dataType), digest);
    }

    private MessageDigest sha256MessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Invalid algorithm", e);
        }
    }

    private String bytesToHex(final byte[] digest) {
        final StringBuilder hexString = new StringBuilder(2 * digest.length);
        for (final byte b : digest) {
            final String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
