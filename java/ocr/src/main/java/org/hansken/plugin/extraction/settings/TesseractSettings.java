package org.hansken.plugin.extraction.settings;

public class TesseractSettings {

    private final String _dataPath;

    /**
     * Set the language that is used for ocr process.
     * The available choices can be found here: https://github.com/tesseract-ocr/tesseract/blob/master/doc/tesseract.1.asc#languages.
     * The .traineddata file of the language that is being chosen should be added in tessdata folder.
     */
    private final String _ocrLanguage;

    /**
     * Set the page segmentation mode.
     * The available choices can be found here: https://github.com/tesseract-ocr/tesseract/wiki/ImproveQuality#page-segmentation-method.
     */
    private final int _pageSegmentationMode;

    /**
     * Set the ocr engine mode.
     * The available choices are: (see https://github.com/tesseract-ocr/tesseract/wiki#linux)
     * 0    Legacy engine only.
     * 1    Neural nets LSTM engine only.
     * 2    Legacy + LSTM engines.
     * 3    Default, based on what is available.
     */
    private final int _ocrEngineMode;

    public TesseractSettings() {
        this("/usr/share/tesseract-ocr/4.00/tessdata", "eng", 3, 3);
    }

    public TesseractSettings(final String dataPath, final String ocrLanguage, final int pageSegmentationMode, final int ocrEngineMode) {
        _dataPath = dataPath;
        _ocrLanguage = ocrLanguage;
        _pageSegmentationMode = pageSegmentationMode;
        _ocrEngineMode = ocrEngineMode;
    }

    public String getDataPath() {
        return _dataPath;
    }

    public String getOcrLanguage() {
        return _ocrLanguage;
    }

    public int getPageSegmentationMode() {
        return _pageSegmentationMode;
    }

    public int getOcrEngineMode() {
        return _ocrEngineMode;
    }
}
