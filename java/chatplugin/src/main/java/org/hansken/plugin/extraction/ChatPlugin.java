package org.hansken.plugin.extraction;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;

import java.io.IOException;

import org.hansken.plugin.extraction.api.Author;
import org.hansken.plugin.extraction.api.DataContext;
import org.hansken.plugin.extraction.api.ExtractionPlugin;
import org.hansken.plugin.extraction.api.MaturityLevel;
import org.hansken.plugin.extraction.api.PluginId;
import org.hansken.plugin.extraction.api.PluginInfo;
import org.hansken.plugin.extraction.api.RandomAccessData;
import org.hansken.plugin.extraction.api.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatPlugin implements ExtractionPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ChatPlugin.class);

    private static final String TOOL_DOMAIN = "nfi.nl";
    private static final String TOOL_CATEGORY = "chat";
    private static final String TOOL_NAME = "ChatPluginJava";
    private static final String TOOL_LICENSE = "Apache License, Version 2.0";

    @Override
    public PluginInfo pluginInfo() {
        final Author author = Author.builder()
            .name("The Externals")
            .email("tester@holmes.nl")
            .organisation("NFI")
            .build();

        return PluginInfo.builderFor(this)
            .pluginVersion("1.0.0")
            .description("Example Extraction Plugin: Exclusive Chat format file parser")
            .author(author)
            .maturityLevel(MaturityLevel.PROOF_OF_CONCEPT)
            .webpageUrl("https://hansken.org")
            .hqlMatcher("file.extension=txt")
            .id(new PluginId(TOOL_DOMAIN, TOOL_CATEGORY, TOOL_NAME))
            .license(TOOL_LICENSE)
            .build();
    }

    @Override
    public void process(final Trace trace, final DataContext dataContext) throws IOException {
        // get the name of the file
        final String fileName = trace.get("file.name");
        // log something to the output as an example
        LOG.error("processing trace " + trace.get("name"));
        // set the chat application property on the trace
        trace.addType("chatConversation").set("chatConversation.application", format("DemoApp %s", fileName));

        final RandomAccessData data = dataContext.data();
        final String[] chatMessages = new String(data.readNBytes((int) data.remaining()), UTF_8).split("\n");

        // each message has the format 'sender:receiver message'
        for (int index = 0; index < chatMessages.length; index++) {
            // split contacts and message
            final String[] contactsAndMessage = chatMessages[index].split(" ", 2);
            // split sender and receiver
            final String[] senderAndReceiver = contactsAndMessage[0].split(":");

            final String sender = senderAndReceiver[0];
            final String receiver = senderAndReceiver[1];
            final String message = contactsAndMessage[1];
            final String conversationId = sender.compareTo(receiver) < 0 ?
                sender + "-" + receiver :
                receiver + "-" + sender;

            // add chat message
            trace.newChild(format("message %d", index), messageTrace -> {
                messageTrace.addType("chatMessage")
                    .set("chatMessage.application", "DemoApp")
                    .set("chatMessage.from", sender)
                    .set("chatMessage.to", singletonList(receiver))  // list, because there can be multiple receivers
                    .set("chatMessage.message", message);

                // add a collection (tracelet of type FVT, see tracemodel for typing information)
                messageTrace.addTracelet("collection", tracelet -> tracelet
                    .set("name", conversationId)
                    .set("type", "chatConversation"));

                // add two entities (tracelet of type MVT, see tracemodel for typing information)
                // (!) works with Hansken 45.19.0 or higher
                messageTrace.addTracelet("entity", tracelet -> tracelet
                    .set("confidence", 0.76)
                    .set("type", "name")
                    .set("value", sender));

                messageTrace.addTracelet("entity", tracelet -> tracelet
                    .set("confidence", 0.79)
                    .set("type", "name")
                    .set("value", receiver));


                // add contacts as children of each message (they are the same for each message in the log,
                // but it just shows an example)
                messageTrace.newChild(sender, contactTrace -> {
                    contactTrace.addType("contact")
                        .set("contact.application", "DemoApp")
                        .set("contact.name", sender);
                });
                messageTrace.newChild(receiver, contactTrace -> {
                    contactTrace.addType("contact")
                        .set("contact.application", "DemoApp")
                        .set("contact.name", receiver);
                });
            });
        }
    }
}
