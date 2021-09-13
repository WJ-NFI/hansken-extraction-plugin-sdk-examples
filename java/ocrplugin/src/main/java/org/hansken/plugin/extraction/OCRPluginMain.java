package org.hansken.plugin.extraction;

import static org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain.runMain;

public class OCRPluginMain {
    public static void main(final String[] args) {
        runMain(OCRPlugin::new, args);
    }
}
