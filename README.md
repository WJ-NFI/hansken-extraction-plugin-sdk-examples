# Extraction Plugin SDK Examples

This repository contains example setup and implementations of extraction plugins for Hansken. We have multiple
implementations of the API, for different languages. These are contained in the directories named as that language.
There you can find README files specific to that implementation. Or use the links below:

* [Java Extraction Plugins](java/README.md)

* [Python Extraction Plugins](python/README.md)

## Extraction Plugin SDK

The Extraction Plugin Software Development Kit can be used to develop an extraction plugin that can be run during the
extraction process.

.. note:: The Hansken ExpertUI Documentation contains a section "Extraction Plugins SDK" written for plugin devs, which contains more details, explanations and a great FAQ on how to write, test and use Extraction Plugins.

## How to get started 101

Now, in order to get started with a plugin(Python example here, but the same concepts apply for both Java & Python...and
any future languages), one needs to have a dependency on the extraction plugin sdk (which is simply called
*extraction-plugin - this is specified in `python/chat/setup.py`, or the `java/pom.xml`)*. When done, we can start with
implementing the plugin itself. The first step is to implement the `ExtractionPlugin` abstract base class contained in
the package
`hansken_extraction_plugin.api.extraction_plugin`. This class has two methods, the first being `plugin_info`.

An example implementation for this is:

```python
def plugin_info(self):
    return PluginInfo(
        self,
        name='ChatPlugin',
        version='1.0.0',
        description='Exclusive Chat format file parser',
        author=Author('The Externals', 'tester@holmes.nl', 'NFI'),
        maturity=MaturityLevel.PROOF_OF_CONCEPT,
        webpage_url='https://hansken.org',
        matcher='$data.type=txt'
    )
```

It contains various information about the plugin itself and the author(s) of the plugin:

| Parameter   | Description                                                        |
| ----------- | ------------------------------------------------------------------ |
| self        | this discloses information about the implementation of this plugin |
| name        | the name of this extraction plugin                                 |
| version     | the version of this plugin                                         |
| description | a human readable description of the plugin                         |
| author      | a description of the author(s) of the plugin                       |
| maturity    | indicates the stage of maturity of the plugin                      |
| webpage_url | a link to a web-page of the plugin, e.g. the git repository |
| matcher     | the matcher for this plugin, see below for more information        |

1. An important parameter to take note of is the last one. This is a trace *matcher* and it determines which traces are
   sent to this plugin for processing. In this case, with `$data.type=txt`, we are are requesting all traces with a text
   stream to be sent for processing. The matching can be done on various other (meta)data of a trace. The language used
   for this is called HQL-Lite. The history, syntax and usage can be found [here](HQL-LITE.md).

   __Note:__ if you are matching on data type, it should always be the last part of the matcher, for
   example `-type=chatLog AND $data.type=txt`. This means the trace should not be of type *chatLog*, and have a text
   stream.


2. The second step is to implement `process`. Each trace which matches will be passed to this process method. In fact,
   if we match on multiple data streams, the trace will be passed multiple times, once for each stream. This method has
   two parameters, `ExtractionTrace` and `ExtractionContext`. The first is the trace itself, which can contain
   previously set properties and can be used to read data and enrich the trace with other properties. The context
   describes the current data stream which we are operating on.

A Python example implementation for this is:

```python
def process(self, trace, context):
    trace.update('chatConversation.application', 'DemoApp {}'.format(trace.get('file.name')))

    data = trace.open().read(context.data_size())
    chat_messages = data.decode('utf-8').split('\r\n')

    # each message has the format 'sender:receiver message'
    for index, chat_message in enumerate(chat_messages):
        # split contacts and message
        contacts, message = chat_message.split(' ', 1)
        # split sender and receiver
        sender, receiver = contacts.split(':')

        # add chat message
        child_builder = trace.child_builder('message {}'.format(index))
        child_builder.update({
            'chatMessage.application': 'DemoApp',
            'chatMessage.from': sender,
            'chatMessage.to': [receiver],  # list, because there can be multiple receivers
            'chatMessage.message': message,
        }).build()
```

Here we:

- update the trace by setting a new property using `trace.update`; the name of the application which produced the file.
  Note that a property should exist and be valid as per the trace model in Hansken.
- We also read the current data stream (fully) using `trace.open().read(context.data_size())`, and parse this in order
  to create new child traces, where each trace represents a chat message. In fact, it is possible to add child traces of
  these child traces again. See the `ChatPlugin` example for how to do this.


## Licensing

This repository and all extraction plugin examples are distributed under the Apache License 2.0, see LICENSE for details.
