package org.hansken.plugin.extraction;

import static java.lang.String.format;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.plist.XMLPropertyListConfiguration;
import org.hansken.plugin.extraction.Utils.TableRow;
import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.DataContext;
import org.hansken.plugin.extraction.api.DeferredExtractionPlugin;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.RandomAccessData;
import org.hansken.plugin.extraction.api.SearchTrace;
import org.hansken.plugin.extraction.api.Trace;
import org.hansken.plugin.extraction.api.TraceSearcher;

import static org.hansken.plugin.extraction.Utils.convertRGBAToABGR;
import static org.hansken.plugin.extraction.Utils.createBufferedImage;
import static org.hansken.plugin.extraction.Utils.getDateFromMacAbsoluteTime;
import static org.hansken.plugin.extraction.Utils.getDateStringInUTC;
import static org.hansken.plugin.extraction.Utils.getIntProperty;
import static org.hansken.plugin.extraction.Utils.getPngImageAsInputStream;
import static org.hansken.plugin.extraction.util.ArgChecks.argNotNull;

/**
 * This plugin has been developed with information obtained from az4n6.blogspot.com.
 * {see https://az4n6.blogspot.com/2016/05/quicklook-python-parser-all-your-blobs.html}
 * {see https://az4n6.blogspot.com/2016/10/quicklook-thumbnailsdata-parser.html}
 */
public final class QuickLookPlugin implements DeferredExtractionPlugin {
    private static final String THUMB_HEIGHT = "height";
    private static final String BITS_PER_PIXEL = "bitsperpixel";
    private static final String BYTES_PER_ROW = "bytesperrow";
    private static final String BITMAP_DATA_OFFSET = "bitmapdata_location";
    private static final String BITMAP_DATA_LENGTH = "bitmapdata_length";
    private static final String DATABASE_NAME = "index.sqlite";

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("hansken-support@nfi.nl")
            .organisation("NFI")
            .build();

        // NOTE: This is the PluginInfo for DeferredExtractionPlugin, with the possibility to set deferredIterations.
        // deferredIterations has a default value of 1, so even when deferredIterations are not set explicitly,
        // the plugin is deferred at least for one extraction cycle *after* all the other extractions have finished.
        return PluginInfo.builderFor(this)
            .id(new PluginId("nfi.nl", "picture", "QuickLookPluginJava"))
            .pluginVersion("1.0.0")
            .description("Example Extraction Plugin: This plugin extracts thumbnails from thumbnail.data and " +
                "index.sqlite found in com.apple.QuickLook.thumbnailcache.")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            // TODO: HANSKEN-15568: Should also match "AND file.path:com.apple.QuickLook.thumbnailcache"
            .hqlMatcher("file.name=thumbnails.data AND $data.type=raw")
            .license("Apache License, Version 2.0")
            .build();
    }

    @Override
    public void process(final Trace trace, final DataContext dataContext, final TraceSearcher searcher)
        throws ExecutionException, InterruptedException, IOException {

        // Search for SQLite tables "files" and "thumbnails" where the path matches the current trace
        final SearchTrace filesTrace =
            searchForTrace(searcher, format("(data.raw.fileType='Tab Separated Values' OR data.raw.fileType='Comma Separated Values') AND path='%s'",
                getExpectedTracePath(trace, "files")));
        final SearchTrace thumbnailsTrace =
            searchForTrace(searcher, format("(data.raw.fileType='Tab Separated Values' OR data.raw.fileType='Comma Separated Values') AND path='%s'",
                getExpectedTracePath(trace, "thumbnails")));

        // Parse the found traces of files and thumbnails and add as child traces
        addChildTraces(trace, searcher, dataContext, parseDatabaseTable(thumbnailsTrace), parseDatabaseTable(filesTrace));
    }

    private SearchTrace searchForTrace(final TraceSearcher searcher, final String query) throws ExecutionException, InterruptedException {
        return searcher.search(query, 1)
            .getTraces()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No search results present for query: " + query));
    }

    private Path getExpectedTracePath(final Trace trace, final String tableName) {
        // TODO: HANSKEN-15693: EP: trace.get("path") returns an non-castable object
        final String path = argNotNull("trace.path", trace.get("path")).toString();
        final String tracePath = path.replace("[", "") // Remove [
            .replace("]", "") // Remove ]
            .replaceAll(", ", System.getProperty("file.separator")); // Replace all commas by forward slashes
        return Path.of(tracePath) // Convert String to Path
            .resolveSibling(DATABASE_NAME) // Get the neighbouring sql database file
            .resolve(tableName) // Append current table's name
            .toAbsolutePath(); // Make sure this is the very absolute path of the trace in the project
    }

    private Map<Integer, TableRow> parseDatabaseTable(final SearchTrace searchTrace) throws IOException {
        final Map<Integer, TableRow> tableRows = new HashMap<>();

        final RandomAccessData data = searchTrace.getData("raw");
        final Scanner scanner = new Scanner(new String(data.readNBytes((int) data.remaining())));
        final String[] keys = scanner.nextLine().split(",");

        int idCounter = 1; // QuickLook Files table internal row id starts with index 1!
        while (scanner.hasNext()) {
            final String nextLine = scanner.nextLine();
            // Regex matches all commas which are not between quotes
            final String[] values = nextLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            final Map<String, String> properties = IntStream.range(0, keys.length)
                .boxed()
                .collect(Collectors.toMap(i -> keys[i], i -> values[i]));

            tableRows.put(idCounter, new TableRow(properties));
            idCounter++;
        }

        return tableRows;
    }

    private void addChildTraces(final Trace trace, final TraceSearcher searcher, final DataContext dataContext,
                                final Map<Integer, TableRow> thumbnails, final Map<Integer, TableRow> files)
        throws IOException, ExecutionException, InterruptedException {
        int childIndex = 0;
        final Set<Integer> unusedFileIndexes = new HashSet<>(files.keySet()); // use this to later add traces w/o thumb data
        final RandomAccessData thumbnailsData = dataContext.data();
        for (final TableRow thumbnailInfo : thumbnails.values()) {
            final TableRow fileInfo = getFileInfo(files, thumbnailInfo, unusedFileIndexes);

            addChildTrace(trace, thumbnailInfo, fileInfo,
                parseFileInfoPlist(searcher, fileInfo),
                getBufferedImage(thumbnailsData, thumbnailInfo), childIndex);

            childIndex++;
        }

        // Apply the values in unusedFileIndexes to add child-trac
        // es which have no thumbnail data available anymore
        for (final Integer index : unusedFileIndexes) {
            final TableRow fileInfo = files.get(index);
            addChildTrace(trace, null, fileInfo, parseFileInfoPlist(searcher, fileInfo), null, childIndex);
            childIndex++;
        }
    }

    private void addChildTrace(final Trace trace, final TableRow thumbnail, final TableRow fileInfo,
                               final Map<String, String> fileInfoPlist, final BufferedImage bufferedImage,
                               final int counter) throws IOException {
        trace.newChild("thumb-" + counter, thumbnailTrace -> {
            if (bufferedImage != null) {
                thumbnailTrace.addType("data")
                    .set("data.raw.mimeClass", "picture")
                    .set("data.raw.mimeType", "image/png");
            }

            thumbnailTrace.addType("link")
                .set("link.target", fileInfo.getProperty("folder").concat("/").concat(fileInfo.getProperty("file_name")))
                .set("link.targetFileLength", Long.valueOf(fileInfoPlist.get("size")))
                .set("link.misc.targetModifiedOn", getDateStringInUTC(getDateFromMacAbsoluteTime(fileInfoPlist.get("date"))))
                .set("link.misc.plistVersion", fileInfo.getProperty("version"))
                .set("link.misc.generator", fileInfoPlist.get("gen"))
                .set("link.misc.fsId", fileInfo.getProperty("fs_id"));

            // For some files there is no thumbnail info present in the cache, so enable skipping this part
            if (thumbnail != null) {
                final Trace picture = thumbnailTrace.addType("picture");
                thumbnail.getProperties().forEach((key, value) -> {
                    picture.set("picture.misc." + key,
                        key.equals("last_hit_date") ? getDateStringInUTC(getDateFromMacAbsoluteTime(value)) : value);
                });
            }

            // For some files there is no thumbnail data present in the cache, so enable skipping this part
            if (bufferedImage != null) {
                thumbnailTrace.setData("raw", getPngImageAsInputStream(bufferedImage));
            }
        });
    }

    private BufferedImage getBufferedImage(final RandomAccessData thumbnailsData, final TableRow thumbnailInfo) throws IOException {
        final int bitsPerPixel = getIntProperty(thumbnailInfo, BITS_PER_PIXEL);
        final int bytesPerRow = getIntProperty(thumbnailInfo, BYTES_PER_ROW);
        final int pixelsPerRow = bytesPerRow / (bitsPerPixel / 8); // pixelsPerRow is the thumbnail's width
        final int thumbHeight = getIntProperty(thumbnailInfo, THUMB_HEIGHT);

        return createBufferedImage(getThumbnailData(thumbnailsData, thumbnailInfo), pixelsPerRow, thumbHeight);
    }

    private byte[] getThumbnailData(final RandomAccessData thumbnailsData, final TableRow thumbnailInfo) throws IOException {
        final int dataOffset = getIntProperty(thumbnailInfo, BITMAP_DATA_OFFSET);
        final int dataLength = getIntProperty(thumbnailInfo, BITMAP_DATA_LENGTH);

        // Read thumbnail data from the bitmap
        final byte[] bufferRGBA = new byte[dataLength];
        thumbnailsData.seek(dataOffset);
        thumbnailsData.read(bufferRGBA, dataLength);

        return convertRGBAToABGR(bufferRGBA);
    }

    private Map<String, String> parseFileInfoPlist(final TraceSearcher searcher, final TableRow fileInfo)
        throws ExecutionException, InterruptedException, IOException {
        final Map<String, String> result = new HashMap<>();

        // Get the plist trace by its name which is known by the fileInfo property "version"
        final String plistName = fileInfo.getProperty("version")
            .replace("<binary ", "")
            .replace(">", "");
        final SearchTrace plistTrace = searchForTrace(searcher,
            format("data.raw.fileType='Binary Plist' AND name='%s'", plistName));
        final RandomAccessData plistData = plistTrace.getData("plain");
        final byte[] buffer = plistData.readNBytes((int) plistData.remaining());

        // Setup the plist parser, parse the xml and add results to the map
        final XMLPropertyListConfiguration plistConfig = new XMLPropertyListConfiguration();
        try {
            plistConfig.read(new InputStreamReader(new ByteArrayInputStream(buffer)));
            result.put("size", plistConfig.getString("size"));
            result.put("date", plistConfig.getString("date"));
            result.put("gen", plistConfig.getString("gen"));
        }
        catch (final ConfigurationException e) {
            throw new IOException("Failed to parse the fileInfo plist. ", e);
        }

        return result;
    }

    static TableRow getFileInfo(final Map<Integer, TableRow> files, final TableRow thumbnailInfo, final Set<Integer> unusedFileIndexes) {
        final int fileId = getIntProperty(thumbnailInfo, "file_id");
        unusedFileIndexes.remove(fileId);

        return files.get(fileId);
    }
}
