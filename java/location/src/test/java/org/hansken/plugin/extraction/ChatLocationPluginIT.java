package org.hansken.plugin.extraction;

import static nl.minvenj.nfi.flits.util.FlitsUtil.srcPath;

import java.nio.file.Path;

import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.test.EmbeddedExtractionPluginFlits;

class ChatLocationPluginIT extends EmbeddedExtractionPluginFlits {

    @Override
    protected ExtractionPlugin pluginToTest() {
        return new ChatLocationPlugin();
    }

    @Override
    public Path testPath() {
        return srcPath("inputs");
    }

    @Override
    public Path resultPath() {
        return srcPath("results");
    }

    @Override
    public boolean regenerate() {
        return false;
    }
}