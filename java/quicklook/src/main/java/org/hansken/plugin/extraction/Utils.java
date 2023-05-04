package org.hansken.plugin.extraction;

import static org.hansken.plugin.extraction.util.ArgChecks.argNotNull;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import javax.imageio.ImageIO;

/**
 * Utils class for the {@link QuickLookPlugin} containing the more generic methods.
 */
public final class Utils {
    private static final long MAC_ABSOLUTE_TIME_EPOCH = 978307200000L; // Unix Milliseconds (UTC) 2001-01-01 00:00:00

    private Utils() { }

    /**
     * Creates a {@link BufferedImage} from provided {@code bytes} with width and height.
     *
     * @param bytes bytes of the image
     * @param width image width
     * @param height image height
     * @return {@code BufferedImage} created from bytes with width and height
     */
    static BufferedImage createBufferedImage(final byte[] bytes, final int width, final int height) {
        final BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        bufferedImage.setData(Raster.createRaster(bufferedImage.getSampleModel(),
            new DataBufferByte(bytes, bytes.length), new Point()));

        return bufferedImage;
    }

    /**
     * Takes as input a string of format [value].0000000. Where [value] is Mac Absolute Time in number
     * of seconds since January 1, 2001. Converts this value to type long and then to milliseconds
     * and adds {@code MAC_ABSOLUTE_TIME_EPOCH}.
     *
     * @param macString a string containing the amount of seconds
     * @return a human readable date
     */
    static Date getDateFromMacAbsoluteTime(final String macString) {
        return new Date(MAC_ABSOLUTE_TIME_EPOCH + (Long.parseLong(macString.split("\\.")[0]) * 1000));
    }

    /**
     * Converts a {@link Date} object into a {@link String} whileas keeping the UTC timezone.
     *
     * @param date a date to be converted
     * @return a string version of date formatted in UTC
     */
    static String getDateStringInUTC(final Date date) {
        final String isoFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS zzz";
        final SimpleDateFormat sdf = new SimpleDateFormat(isoFormat);
        final TimeZone utc = TimeZone.getTimeZone("UTC");
        sdf.setTimeZone(utc);

        return sdf.format(date);
    }

    /**
     * Takes a {@link BufferedImage} and writes it as a png. This png is returned as an {@link InputStream}.
     *
     * @param bufferedImage picture to be written as png
     * @return an input stream of the picture
     * @throws IOException when the writing of the png image fails
     */
    static InputStream getPngImageAsInputStream(final BufferedImage bufferedImage) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * Reverses the bytes of an RGBA bitmap.
     *
     * @param srcRGBA source bitmap formatted as RGBA
     * @return bitmap formatted as ABGR
     */
    static byte[] convertRGBAToABGR(final byte[] srcRGBA) {
        final byte[] dstABGR = new byte[srcRGBA.length];
        for (int i = 0; i < srcRGBA.length; i += 4) {
            dstABGR[i] = srcRGBA[i + 3];
            dstABGR[i + 1] = srcRGBA[i + 2];
            dstABGR[i + 2] = srcRGBA[i + 1];
            dstABGR[i + 3] = srcRGBA[i];
        }

        return dstABGR;
    }

    /**
     * Get the integer value of a property in a {@link TableRow}.
     *
     * @param tableRow table row containing properties
     * @param propertyName name of the property to be returned
     * @return integer typed value
     */
    static int getIntProperty(final TableRow tableRow, final String propertyName) {
        return Integer.parseInt(argNotNull(propertyName, tableRow.getProperty(propertyName)));
    }

    /**
     * A class defining the csv table row.
     */
    static class TableRow {
        private final Map<String, String> _properties;

        TableRow(final Map<String, String> properties) {
            _properties = properties;
        }

        Map<String, String> getProperties() {
            return _properties;
        }

        String getProperty(final String key) {
            return _properties.get(key);
        }
    }
}
