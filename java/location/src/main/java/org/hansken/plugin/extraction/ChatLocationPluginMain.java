package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public class ChatLocationPluginMain {
    public static void main(String... args) throws Exception {
        ExtractionPluginServerMain.runMain(ChatLocationPlugin::new, args);
    }
}
