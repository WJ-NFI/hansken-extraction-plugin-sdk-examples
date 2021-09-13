package org.hansken.plugin.extraction;

import static nl.minvenj.nfi.flits.util.FlitsUtil.srcPath;

import java.nio.file.Path;

import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.test.EmbeddedExtractionPluginFlits;

class ChatPluginIT extends EmbeddedExtractionPluginFlits {

    @Override
    protected ExtractionPlugin pluginToTest() {
        return new ChatPlugin();
    }

    @Override
    public Path testPath() {
        return srcPath("integration/inputs");
    }

    @Override
    public Path resultPath() {
        return srcPath("integration/results");
    }

    @Override
    public boolean regenerate() {
        return false;
    }
}
