from hansken_extraction_plugin.api.extraction_plugin import ExtractionPlugin
from hansken_extraction_plugin.api.extraction_trace import Tracelet
from hansken_extraction_plugin.api.plugin_info import Author, MaturityLevel, PluginId, PluginInfo

from logbook import Logger


log = Logger(__name__)


class ChatPlugin(ExtractionPlugin):

    def plugin_info(self):
        plugin_info = PluginInfo(
            id=PluginId('nfi.nl', 'chat', 'ChatPluginPython'),
            version='1.0.0',
            description='Example Extraction Plugin: Exclusive Chat format file parser',
            author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
            maturity=MaturityLevel.PROOF_OF_CONCEPT,
            webpage_url='https://hansken.org',
            matcher='file.extension=txt',
            license='Apache License 2.0'
        )
        log.info('pluginInfo request')
        log.debug(f'returning plugin info: {plugin_info}')
        return plugin_info

    def process(self, trace, data_context):
        # get the name of the file
        file_name = trace.get('file.name')
        # log something to output as an example
        log.info(f"processing trace {trace.get('name')}")
        # set the chat application property on the trace
        trace.update('chatConversation.application', f'DemoApp {file_name}')

        data = trace.open().read(data_context.data_size)
        chat_messages = data.decode('utf-8').split('\n')
        log.debug(f'found chat messages: {chat_messages}')

        # each message has the format 'sender:receiver message'
        for index, chat_message in enumerate(chat_messages):
            # split contacts and message
            contacts, message = chat_message.split(' ', 1)
            # split sender and receiver
            sender, receiver = contacts.split(':')
            conversationId = [sender, receiver]
            conversationId.sort()
            conversationId = "-".join(conversationId)

            # add chat message
            child_builder = trace.child_builder(f'message {index}')

            # add a collection (tracelet of type FVT, see tracemodel for typing information)
            child_builder.add_tracelet("collection",
                    { "name": conversationId,
                      "type": "chatConversation" })

            # add two entities (tracelet of type MVT, see tracemodel for typing information)
            # (!) works with Hansken 45.19.0 or higher
            child_builder.add_tracelet("entity",
                    { "confidence": 0.76,
                      "type": "name",
                      "value": sender})
            child_builder.add_tracelet("entity",
                    { "confidence": 0.79,
                      "type": "name",
                      "value": receiver})

            child_builder.update({
                'chatMessage.application': 'DemoApp',
                'chatMessage.from': sender,
                'chatMessage.to': [receiver],  # list, because there can be multiple receivers
                'chatMessage.message': message,
            }).build()

            # add contacts as children of each message (they are the same for each message in the log,
            # but it just shows an example)
            child_builder.child_builder(sender).update({
                'contact.application': 'DemoApp',
                'contact.name': sender
            }).build()

            child_builder.child_builder(receiver).update({
                'contact.application': 'DemoApp',
                'contact.name': receiver
            }).build()

            log.debug(f'found chat message ({sender}->{receiver}): {message}')
