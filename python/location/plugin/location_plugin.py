from hansken_extraction_plugin.api.extraction_plugin import MetaExtractionPlugin
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo
from hansken.util import GeographicLocation
from logbook import Logger

log = Logger(__name__)

class LocationPlugin(MetaExtractionPlugin):

    # We have an extensive database of secret location names:
    location_translation_database = {
        "arggh"     : GeographicLocation(52.0448013, 4.3585176),  # NFI
        "Tyrol"     : GeographicLocation(52.3791316, 4.8980833),  # Amsterdam Centraal
        "waterval"  : GeographicLocation(51.9243876, 4.4675636),  # Rotterdam Centraal
        "scrum"     : GeographicLocation(52.077184,  4.3123263),  # Den Haag Centraal
        "die jonge" : GeographicLocation(52.0894436, 5.1077982)   # Utrecht Centraal
    }

    def plugin_info(self):
        log.info('pluginInfo request')
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'gps', 'ChatLocationPluginPython'),
            version='0.0.1',
            description='Example Extraction Plugin: Location extractor for exclusive chats',
            author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='type=chatMessage AND chatMessage.message=*',
            license='Apache License 2.0'
        )
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    # This plugin is a MetaExtractionPlugin: Therefore it can only process trace information. It cannot read/write data.
    def process(self, trace):
        # Extract the message
        message = trace.get('chatMessage.message')

        # Check for every locationName if it occurs in the message. If so, add its GPS info to the trace.
        for locationName in self.location_translation_database:
            if locationName in message:
                trace.update('gps.application', trace.get('chatMessage.application'))
                trace.update('gps.latlong', self.location_translation_database.get(locationName))
