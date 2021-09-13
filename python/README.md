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
* *ChatPlugin*: This plugin parses a simple made-up chat logs into a message tree. The ChatPlugin can be found in the `python/chat` directory.
* *DataTransformationPlugin* This plugin creates transformations from simple made-up chat logs. This plugin can be found in the `python/datatransformation`directory.
* *FSEventsPlugin*: This plugin parses Apple's fsevents files. The FSEventsPlugin can be found in the `python/fsevents` directory.
* *LocationPlugin*: This plugin processes the traces produced by the ChatPlugin. It tries to detect pseudonymized locations contained in the chat messages.
  If one is found, the trace is enhanced with GPS information of that location.
  This example does not use data and therefore is a *MetaExtractionPlugin*. The LocationPlugin can be found in the `python/location` directory.
* *SecretsPlugin* This plugin parses messages and writes the pictures contained in these messages as data. This plugin can be found in the `python/secrets`directory.
* *QuickLookPlugin* This plugin extracts thumbnails from thumbnail.data and index.sqlite found in com.apple.QuickLook.thumbnailcache. 
  This plugin can be found in the `python/quicklook`directory. This example is a *DeferredExtractionPlugin*.


Each plugin has a couple of configuration files in order to collect the necessary dependencies, and to build and test the plugin.
They can be used as a reference when implementing your own plugins.

## Quick start

This guide will show how to build, run and test the first python example: 'ChatPlugin'. The example shows how text files (chat records, containing important evidence) can
be processed and produce or enrich Hansken traces (the chat records) and create child traces (chatMessage and contact information!). For the other example 'LocationPlugin' the process is the same.

### Prerequisites

Required:

- python 3.6, 3.7 or 3.8
- java 11 jre (for running the test-framework, which is implemented in java)

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
tox -e integration
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


# Testing (advanced)
By default, the build scripts as described in `Building and running` section above will automatically run tests.
The appropirate commands have been added tot the tox.ini directly.
This section gives a little more detail on the test commands and options.

One can simply create unit tests for a plugin directly. However, we also provide a test runner for testing them over
gRPC. This runner connects to a running instance of a Python plugin, and feeds it input files and compares the results
against an expected result set.


## Standalone
The test runner is a script called `test_plugin` which is available in the SDK.

To get started, `cd` into the directory of the plugin you want to test and run:
```bash
test_plugin --standalone plugin/chat_plugin.py 
```

Note that the argument provided to the option `--standalone` must be the relative path to the plugin `py` file which is 
to be tested. This test accepts input files from the directory `testdata/input` and compares the results to the result 
files in found in `testdata/results`. Use the optional argument `--regenerate` to regenerate the expected results for 
the test when needed. 

This standalone test is also used by the [tox](chat/tox.ini) file to validate the plugin. Simply 
calling `tox` should be enough to install all dependencies and run the test.

## Docker
If there is a docker image available for the plugin you can also test it by executing:

```bash
test_plugin --docker extraction-plugin-examples-chat
```

Replace the 'extraction-plugin-examples-chat' with the docker image you want to test. Run the following command
to see which docker images are available:
```bash
docker images
```

## Manual
The third option for testing is a manually started plugin. Start the plugin service in a terminal by executing:
```bash
serve_plugin plugin/chat_plugin.py
```

This will spin up the chat plugin at port 8999. Here also the argument must be a path to the plugin's `.py` file. In
another terminal window, run the test with:
```bash
test_plugin --manual localhost 8999
```

## Help
Run the following for an overview of all the available options in the test script:
```bash
test_plugin --help
```
