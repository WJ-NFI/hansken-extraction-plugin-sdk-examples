package org.hansken.plugin.extraction.engine;

import static org.hansken.plugin.extraction.core.data.RandomAccessDatas.asInputStream;
import static org.hansken.plugin.extraction.util.ArgChecks.argNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.hansken.plugin.extraction.api.RandomAccessData;
import org.hansken.plugin.extraction.settings.TesseractSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * OCR Engine implemented using Tesseract-OCR.
 *
 * @author Netherlands Forensic Institute.
 */
public class TesseractOCREngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(TesseractOCREngine.class);

    private final Tesseract _tesseract;

    public TesseractOCREngine(final TesseractSettings tesseractSettings) {
        this(tesseractSettings, new Tesseract());
    }

    private TesseractOCREngine(final TesseractSettings tesseractSettings, final Tesseract tesseract) {
        argNotNull("tesseractSettings", tesseractSettings);
        _tesseract = argNotNull("tesseract", tesseract);
        _tesseract.setDatapath(tesseractSettings.getDataPath());
        _tesseract.setPageSegMode(tesseractSettings.getPageSegmentationMode());
        _tesseract.setLanguage(tesseractSettings.getOcrLanguage());
        _tesseract.setOcrEngineMode(tesseractSettings.getOcrEngineMode());
    }

    public void process(final RandomAccessData data, final String mimeType, final OutputStream outputStream) {
        try (final InputStream inputStream = asInputStream(data)) {
            if (mimeType.equals("application/pdf")) {
                processPdf(inputStream, outputStream);
            }
            else if (mimeType.startsWith("image/")) {
                processImage(inputStream, outputStream);
            }
            else {
                throw new IllegalStateException("Unexpected mime type: " + mimeType);
            }
        }
        catch (final IOException e) {
            LOGGER.error("Failed to read input data", e);
            throw new IllegalStateException("Failed to read input data", e);
        }
    }

    /**
     * When feeding the pdf directly to the Tesseract api, the following will happen:
     * - Every page in the pdf will be exported as png
     * - All png files will be merged into a single (large!) tiff file
     * - The tiff file will be fed to Tesseract.
     * <p>
     * To avoid a large tiff file, feed every page to Tesseract directly
     * <p>
     * {@see net.sourceforge.tess4j.util.ImageIOHelper#getIIOImageList(File)}
     *
     * @param inputStream  pdf input stream
     * @param outputStream stream to write the content to
     */
    private void processPdf(final InputStream inputStream, final OutputStream outputStream) {
        try (final PDDocument pdDocument = PDDocument.load(inputStream)) {
            final PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
            final int numberOfPages = pdDocument.getNumberOfPages();

            for (int i = 0; i < numberOfPages; i++) {
                final BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300.0F);
                final String text = getTextFromImage(image);

                if (!text.isEmpty()) {
                    writeToOutputStream(outputStream, text);
                    if (i < numberOfPages - 1) {
                        writeToOutputStream(outputStream, "\n\n\n");
                    }
                }
            }
        }
        catch (final IOException e) {
            LOGGER.error("Error while loading a PDF document", e);
            throw new IllegalStateException("Error while loading PDF document", e);
        }
    }

    private void processImage(final InputStream inputStream, final OutputStream outputStream) {
        try (final ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            final Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
            if (!imageReaders.hasNext()) {
                throw new IllegalStateException("No image reader(s) found");
            }

            // we use the first available image reader
            final ImageReader imageReader = imageReaders.next();
            imageReader.setInput(imageInputStream);

            final ImageIterator imageIterator = new ImageIterator(imageReader);
            String previousText = "";
            while (imageIterator.hasNext()) {
                final String currentText = getTextFromImage(imageIterator.next());
                if (currentText.isEmpty()) {
                    previousText = currentText;
                    continue;
                }

                if (!previousText.isEmpty()) {
                    writeToOutputStream(outputStream, "\n\n\n");
                }

                writeToOutputStream(outputStream, currentText);
                previousText = currentText;
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException("Error while processing image", e);
        }
    }

    private String getTextFromImage(final BufferedImage image) {
        try {
            return _tesseract.doOCR(image).trim();
        }
        catch (final TesseractException e) {
            throw new IllegalStateException("Error while doing OCR on image", e);
        }
        finally {
            image.flush();
        }
    }

    private void writeToOutputStream(final OutputStream outputStream, final String text) {
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
        }
        catch (final IOException e) {
            throw new IllegalStateException("Error while writing to output stream", e);
        }
    }

    private static final class ImageIterator implements Iterator<BufferedImage> {
        private final ImageReader _imageReader;
        private int _index;
        private BufferedImage _image;

        public ImageIterator(final ImageReader imageReader) {
            _imageReader = imageReader;
            try {
                _image = _imageReader.read(_index++);
            }
            catch (final IOException e) {
                throw new IllegalStateException("Image reader does not contain images");
            }
        }

        @Override
        public boolean hasNext() {
            return _image != null;
        }

        @Override
        public BufferedImage next() {
            if (_image == null) {
                throw new NoSuchElementException();
            }

            final BufferedImage currentImage = _image;

            try {
                _image = _imageReader.read(_index++);
            }
            catch (final IndexOutOfBoundsException e) {
                _image = null;
            }
            catch (final IOException e) {
                throw new IllegalStateException("Error while reading image at index " + _index);
            }

            return currentImage;
        }
    }
}
