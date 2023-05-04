package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public class SecretsPluginMain {
    public static void main(String... args) throws Exception {
        ExtractionPluginServerMain.runMain(SecretsPlugin::new, args);
    }
}
