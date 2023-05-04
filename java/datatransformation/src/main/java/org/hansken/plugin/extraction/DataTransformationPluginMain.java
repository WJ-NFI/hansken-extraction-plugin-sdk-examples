package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public class DataTransformationPluginMain {
    public static void main(String... args) {
        ExtractionPluginServerMain.runMain(DataTransformationPlugin::new, args);
    }
}
