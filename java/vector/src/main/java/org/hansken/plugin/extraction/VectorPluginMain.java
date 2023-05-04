package org.hansken.plugin.extraction;

import static org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain.runMain;

public class VectorPluginMain {

    public static void main(final String... args) throws Exception {
        runMain(VectorPlugin::new, args);
    }
}
