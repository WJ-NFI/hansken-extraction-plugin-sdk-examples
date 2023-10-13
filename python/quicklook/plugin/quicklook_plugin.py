import os
from datetime import datetime
from io import BytesIO
from pathlib import Path
import re
from typing import List, Mapping
import plistlib

import pytz
from PIL import Image

from hansken_extraction_plugin.api.extraction_plugin import DeferredExtractionPlugin
from hansken_extraction_plugin.api.extraction_trace import ExtractionTrace, SearchTrace
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginInfo, PluginId
from hansken_extraction_plugin.api.trace_searcher import TraceSearcher

from logbook import Logger

log = Logger(__name__)

DATABASE_NAME = "index.sqlite"
BITMAP_DATA_OFFSET = "bitmapdata_location"
BITMAP_DATA_LENGTH = "bitmapdata_length"
BITS_PER_PIXEL = "bitsperpixel"
BYTES_PER_ROW = "bytesperrow"
THUMB_HEIGHT = "height"
MAC_ABSOLUTE_TIME_EPOCH = 978307200  # Unix Seconds (UTC) 2001-01-01 00:00:00


def get_date_from_mac_absolute_time(timestamp: int):
    """
    Takes as input an integer containing a timestamp. This timestamp is Mac Absolute Time in number
    of seconds since January 1, 2001.
    Then adds {@code MAC_ABSOLUTE_TIME_EPOCH} to the timestamp and converts it to a datetime with timezone set to UTC.
    :param timestamp: a numeric value containing the timestamp in seconds
    :return: a UTC formatted date
    """
    return datetime.fromtimestamp(MAC_ABSOLUTE_TIME_EPOCH + timestamp, tz=pytz.utc)


def add_thumbnail_as_child(trace: ExtractionTrace, thumbnail: Mapping[str, str], file_info: Mapping[str, str],
                           file_info_plist: Mapping[str, str], thumbnail_image: Image, file_index: int):
    child_builder = trace.child_builder(f'thumb-{file_index}')

    child_builder.update({
        'link.target': str(file_info['folder']) + '/' + str(file_info['file_name']),
        'link.targetFileLength': file_info_plist['size'],
        'link.misc.targetModifiedOn': str(get_date_from_mac_absolute_time(int(file_info_plist['date']))),
        'link.misc.plistVersion':  str(file_info['version']),
        'link.misc.generator': str(file_info_plist['gen']),
        'link.misc.fsId':  str(file_info['fs_id'])
    })

    #  For some files there is no thumbnail info present in the cache, so enable skipping this part
    if thumbnail is not None:
        for key, value in thumbnail.items():
            if key == 'last_hit_date':
                child_builder.update(f'picture.misc.{key}', str(get_date_from_mac_absolute_time(int(value))))
            else:
                child_builder.update(f'picture.misc.{key}', str(value))

    #  For some files there is no thumbnail image present in the cache, so enable skipping this part
    if thumbnail_image is not None:
        with BytesIO() as buffer:
            thumbnail_image.save(buffer, 'png')
            child_builder.add_data('raw', buffer.getvalue())

    child_builder.build()


def parse_file_info_plist(searcher: TraceSearcher, file_info: Mapping[str, str]) -> Mapping[str, str]:
    plist_name = file_info["version"].replace("<binary ", "").replace(">", "")
    plist_trace = search_for_trace(searcher, f"data.raw.fileType='Binary Plist' AND name='{plist_name}'")

    with plist_trace.open() as plist_data:
        plist_dict = plistlib.loads(plist_data.read())

    return plist_dict


def get_file_info(files: Mapping[int, Mapping[str, str]], thumbnail: Mapping[str, str], unused_file_indexes: List[int]):
    file_id = int(thumbnail["file_id"])
    if file_id in unused_file_indexes:
        unused_file_indexes.remove(file_id)

    return files[file_id]


def create_buffered_image(buffer_rgba: bytes, thumbnail_info: Mapping[str, str]):
    bits_per_pixel = int(thumbnail_info[BITS_PER_PIXEL])
    bytes_per_row = int(thumbnail_info[BYTES_PER_ROW])
    thumb_height = int(thumbnail_info[THUMB_HEIGHT])
    pixels_per_row = int(bytes_per_row / (bits_per_pixel / 8))  # pixelsPerRow is the thumbnail's width

    return Image.frombytes('RGBA', (pixels_per_row, thumb_height), buffer_rgba, decoder_name='raw')


def get_thumbnail_data(thumbnails_data: BytesIO, thumbnail_info: Mapping[str, str]):
    data_offset = int(thumbnail_info[BITMAP_DATA_OFFSET])
    data_length = int(thumbnail_info[BITMAP_DATA_LENGTH])
    thumbnails_data.seek(data_offset)

    return thumbnails_data.read(data_length)


def parse_database_table(search_trace: SearchTrace):
    table_rows = dict()
    with search_trace.open() as table:
        keys = table.readline().decode('UTF-8').rstrip().split(",", -1)

        for index, row in enumerate(table, start=1):  # QuickLook Files table internal row id starts with index 1!
            # Regex matches all commas which are not between quotes
            values = re.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", row.decode('UTF-8').rstrip())
            table_rows[index] = dict(zip(keys, values))

    return table_rows


def get_expected_trace_path(trace: ExtractionTrace, table_name: str):
    #  TODO HANSKEN-15662 (FLITS) trace.path currently returns a string instead of a list of path items
    #  TODO: Use the following solution after FLITS has been modified
    # table_path: List[str] = trace.get("path")[:-1]
    # table_path.extend([DATABASE_NAME, table_name])
    # table_path_str = "/".join(table_path)
    # return f'/{table_path_str}'
    # Translate the list of string items to a path object
    trace_path = Path(str(trace.get("path")).replace("['", "").replace("']", "").replace("', '", os.sep))
    # Deduce path of the table from the current trace's path and return this
    return trace_path.parent.joinpath(DATABASE_NAME).joinpath(table_name).absolute()


def search_for_trace(searcher: TraceSearcher, query: str) -> SearchTrace:
    search_result = searcher.search(query, 1)
    if search_result.total_results() == 0:
        raise ValueError(f'No results found for query {query}')

    return search_result.takeone()


class QuickLookPlugin(DeferredExtractionPlugin):
    """
    This plugin has been developed with information obtained from:
    https://az4n6.blogspot.com/2016/05/quicklook-python-parser-all-your-blobs.html
    https://az4n6.blogspot.com/2016/10/quicklook-thumbnailsdata-parser.html
    """
    def plugin_info(self):
        log.info('pluginInfo request')
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'picture', 'QuickLookPluginPython'),
            version='1.0.0',
            description='Example Extraction Plugin: This plugin extracts thumbnails from thumbnail.data and '
                        'index.sqlite found in com.apple.QuickLook.thumbnailcache.',
            author=Author('MODS', 'hansken-support@nfi.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='file.name=thumbnails.data AND $data.type=raw',
            license='Apache License 2.0'
        )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace: ExtractionTrace, data_context, searcher):
        # TODO: HANSKEN-15611: To keep FLITS happy, first read trace data as otherwise this stream cannot be reached
        # Open and read extraction trace (thumbnail.data) data
        with trace.open() as reader:
            thumbnail_data = BytesIO(reader.read(data_context.data_size))

        # Search for SQLite tables "files" and "thumbnails" where the path matches the current trace
        files_path = get_expected_trace_path(trace, "files")
        files_trace = search_for_trace(searcher, f"(data.raw.fileType='Tab Separated Values' OR data.raw.fileType='Comma Separated Values') AND path='{files_path}'")
        files = parse_database_table(files_trace)  # Parse the contents of the "files"-table

        thumbnails_path = get_expected_trace_path(trace, "thumbnails")
        thumbnails_trace = search_for_trace(searcher,
                                            f"(data.raw.fileType='Tab Separated Values' OR data.raw.fileType='Comma Separated Values') AND path='{thumbnails_path}'")
        thumbnails = parse_database_table(thumbnails_trace)  # Parse the contents of the "thumbnails"-table

        # Keep track of unused file indexes, there may be files which do not have a thumbnail anymore
        unused_file_indexes = list(files.keys())

        # Start adding thumbnails as child-traces to thumbnails.data
        file_index = 0  # This index is unique for each file extracted from the database
        for thumbnail_info in thumbnails.values():
            thumbnail_image = create_buffered_image(get_thumbnail_data(thumbnail_data, thumbnail_info), thumbnail_info)
            file_info = get_file_info(files, thumbnail_info, unused_file_indexes)
            add_thumbnail_as_child(trace, thumbnail_info, file_info, parse_file_info_plist(searcher, file_info),
                                   thumbnail_image, file_index)
            file_index += 1

        # Add files without thumbnail entries as child-traces
        for i in unused_file_indexes:
            file_info = files[i]
            add_thumbnail_as_child(trace, None, file_info, parse_file_info_plist(searcher, file_info), None, file_index)
            file_index += 1
