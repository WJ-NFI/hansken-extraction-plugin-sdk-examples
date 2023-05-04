package org.hansken.plugin.extraction;

import static nl.minvenj.nfi.flits.util.FlitsUtil.srcPath;

import java.nio.file.Path;

import org.hansken.plugin.extraction.test.EmbeddedExtractionPluginFlits;
import org.junit.jupiter.api.Disabled;

@Disabled
// TODO Disabled because Tesseract is not installed in Jenkins (HANSKEN-15230)
class OCRPluginIT extends EmbeddedExtractionPluginFlits {

    @Override
    protected OCRPlugin pluginToTest() {
        return new OCRPlugin();
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
