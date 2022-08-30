from hansken_extraction_plugin.api.extraction_plugin import ExtractionPlugin
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo
from hansken_extraction_plugin.api.transformation import Range, RangedTransformation
from logbook import Logger

log = Logger(__name__)


class DataTransformationPlugin(ExtractionPlugin):

    def plugin_info(self):
        log.info('pluginInfo request')
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'data', 'DataTransformationPluginPython'),
            version='1.0.0',
            description='Example Extraction Plugin: '
                        'This plugin creates a child trace with a '
                        'ranged data transformation for each line of a simple made-up chat log.',
            author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='type=file AND $data.fileType=\'Text UTF-8\'',
            license='Apache License 2.0'
        )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace, data_context):
        # log something to output as an example
        log.info(f"processing trace {trace.get('name')}")

        offset = 0
        with trace.open() as reader:
            for line_number, line in enumerate(reader):
                line_length = len(line)

                # create a ranged transformation with offset = data position and length = line length
                transformation = RangedTransformation.builder().add_range(offset, line_length).build()

                # create a child trace with a transformation
                trace.child_builder(f"lineNumber {line_number}") \
                    .add_transformation("raw", transformation) \
                    .build()

                offset += line_length
