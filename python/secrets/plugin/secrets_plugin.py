import base64

from hansken_extraction_plugin.api.extraction_plugin import ExtractionPlugin
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo
from logbook import Logger

log = Logger(__name__)


class SecretsPlugin(ExtractionPlugin):
    def plugin_info(self):
        log.info('pluginInfo request')
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'crypto', 'SecretsPluginPython'),
            version='0.0.1',
            description='Example Extraction Plugin: Extractor for .peb secret files',
            author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='$data.size>0 AND file.extension=peb',
            license='Apache License 2.0'
        )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace, data_context):
        with trace.open() as trace_data:
            # Read raw trace data, decode to utf-8
            data = trace_data.read().decode('utf-8')
            # The first part of the input is readable text. The second part are encoded pictures.
            text, pictures = data.split('\n\n')
            # Add the text as data stream to the trace. hansken expect bytes, so we supply hansken with an utf-8 encoded
            # string
            trace.update(data={'text': text.encode('utf-8')})

            # The next lines contain base64 encoded pictures. We will add them as child traces with the pictures as
            # raw data
            for index, picture in enumerate(pictures.split('\n')):
                trace.child_builder(f'picture-{index}')\
                    .update('picture.type', 'thumbnail')\
                    .update(data={'raw': base64.b64decode(picture)})\
                    .build()
