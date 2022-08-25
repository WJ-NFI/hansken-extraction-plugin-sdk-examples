# Python Extraction Plugin SDK

The Python SDK also runs against the Hansken *python-api*, which is used to do calls to Hansken over REST after the
extraction process. Because of this, the API is not a direct clone of the Java implementation, but based on it and the
Hansken extraction-plugin-api combined. It is however a subset of the latter, so not everything that the Hansken
python-api offers is available in this SDK.

# Table of Contents

* [Getting started](#getting-started)
  * [Quick start](#quick-start)
    * [Prerequisites](#prerequisites)
    * [Building and running](#building-and-running)
* [Testing](#testing)
  * [Standalone](#standalone)
  * [Docker](#docker)
  * [Manual](#manual)
  * [Help](#help)

The following paragraph describes the workings in the context of an extraction.

# Getting started

Before showing how to get started with a Python plugin, note that a couple of example plugins are provided to show you how the API operates:
* *ChatPlugin*: This plugin parses a simple made-up chat logs into a message tree. This plugin can be found in the `python/chat` directory.
* *DataDigestPlugin*: This plugin reads data in chunks and calculates an SHA-256 hash over the entire data. This plugin can be found in the `python/datadigest` directory.
* *DataTransformationPlugin*: This plugin creates transformations from simple made-up chat logs. This plugin can be found in the `python/datatransformation`directory.
* *FSEventsPlugin*: This plugin parses Apple's fsevents files. This plugin can be found in the `python/fsevents` directory.
* *LocationPlugin*: This plugin processes the traces produced by the ChatPlugin. It tries to detect pseudonymized locations contained in the chat messages.
  If one is found, the trace is enhanced with GPS information of that location.
  This example does not use data and therefore is a *MetaExtractionPlugin*. This plugin can be found in the `python/location` directory.
* *SecretsPlugin*: This plugin parses messages and writes the pictures contained in these messages as data. This plugin can be found in the `python/secrets`directory.
* *QuickLookPlugin*: This plugin extracts thumbnails from thumbnail.data and index.sqlite found in com.apple.QuickLook.thumbnailcache. 
  This plugin can be found in the `python/quicklook`directory. This example is a *DeferredExtractionPlugin*.


Each plugin has a couple of configuration files in order to collect the necessary dependencies, and to build and test the plugin.
They can be used as a reference when implementing your own plugins.

## Quick start

This guide will show how to build, run and test the first python example: 'ChatPlugin'. The example shows how text files (chat records, containing important evidence) can
be processed and produce or enrich Hansken traces (the chat records) and create child traces (chatMessage and contact information!). For the other example 'LocationPlugin' the process is the same.

### Prerequisites

Required:

- python 3.6, 3.7 or 3.8
- java 11 (for running the test-framework, which is implemented in java)

It is recommended you also install:

- docker - for running plugins in containers, required for integration tests
- gzip - for unpacking test data, required when using Flits

This software has only been tested on Linux, you may have luck on other systems. If you do, please let us know (We'll be
flabbergasted).

### Building and running

If you just want to build and test the plugin code, use:
```bash
tox
```

This will show the test results for the plugin and a wheel distribution is created in the `dist/` directory.

To test the plugin running in a docker image:
```bash
tox -e integration-test
```

To serve the plugin manually:

```bash
serve_plugin <file> <port>
```
Where `file` is the Python implementation of the plugin, and `port` is the port on which the plugin is served.


### Regenerate expected test results
The build and test scripts run some integration tests. To update the expected test outcome, the following command can be used:
```
tox -e regenerate
```


## Help
Run the following for an overview of all the available options in the test script:
```bash
test_plugin --help
```
