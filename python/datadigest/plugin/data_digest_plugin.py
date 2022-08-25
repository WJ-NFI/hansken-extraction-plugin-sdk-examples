import hashlib

from hansken_extraction_plugin.api.extraction_plugin import ExtractionPlugin
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo
from logbook import Logger

log = Logger(__name__)


class DataDigestPlugin(ExtractionPlugin):

    def plugin_info(self):
        log.info('pluginInfo request')
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'digest', 'DataDigestPluginPython'),
            version='1.0.0',
            description='Example Extraction Plugin: Data digest plugin (reads the data in chunks and calculates the hash)',
            author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='$data.type=*',
            license='Apache License 2.0'
        )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace, data_context):
        hash = hashlib.sha256()
        data_size = data_context.data_size
        data_type = data_context.data_type
        bytes_to_read = 1024 * 1024  # 1 MB
        chunks = data_size / bytes_to_read

        log.info(f"reading data with size: {data_size}")

        with trace.open() as reader:
            for block in range(0, int(chunks)):
                position = block * bytes_to_read
                data = reader.read(bytes_to_read) if position >= bytes_to_read else reader.read(data_size - position)
                hash.update(data)

        trace.update(f'data.{data_type}.hash.sha256', hash.hexdigest())
