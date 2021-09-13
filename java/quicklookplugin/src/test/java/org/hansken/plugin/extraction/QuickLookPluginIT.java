package org.hansken.plugin.extraction;

import static nl.minvenj.nfi.flits.util.FlitsUtil.srcPath;

import java.nio.file.Path;

import org.hansken.plugin.extraction.api.DeferredExtractionPlugin;
import org.hansken.plugin.extraction.test.EmbeddedDeferredExtractionPluginFlits;

class QuickLookPluginIT extends EmbeddedDeferredExtractionPluginFlits {

    @Override
    protected DeferredExtractionPlugin pluginToTest() {
        return new QuickLookPlugin();
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
