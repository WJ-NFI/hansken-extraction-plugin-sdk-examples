package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public class DataDigestPluginMain {
    public static void main(final String... args) {
        ExtractionPluginServerMain.runMain(DataDigestPlugin::new, args);
    }
}
