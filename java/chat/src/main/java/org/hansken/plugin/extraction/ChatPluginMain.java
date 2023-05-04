package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public class ChatPluginMain {
    public static void main(String... args) throws Exception {
        ExtractionPluginServerMain.runMain(ChatPlugin::new, args);
    }
}
