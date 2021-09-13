package org.hansken.plugin.extraction;

import org.hansken.plugin.extraction.runtime.grpc.server.ExtractionPluginServerMain;

public final class QuickLookPluginMain {
    private QuickLookPluginMain() {
    }

    public static void main(final String... args) {
        ExtractionPluginServerMain.runMain(QuickLookPlugin::new, args);
    }
}
