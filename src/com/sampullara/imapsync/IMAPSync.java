package com.sampullara.imapsync;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import java.security.Security;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: sam
 * Date: Jul 31, 2006
 * Time: 10:08:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class IMAPSync {

    private Logger l = Logger.getAnonymousLogger();

    @Argument(value = "source", alias = "s", required = true, description = "This is the source mail url")
    private String sourceURL;

    @Argument(value = "dest", alias = "d", required = true, description = "This is the destination mail url")
    private String destURL;

    @Argument(alias = "sf", description = "Source folder")
    private String sourceFolder;

    @Argument(alias = "df", description = "Destination folder")
    private String destFolder;

    @Argument(description = "Disable SSL certificate checking")
    private Boolean ignorecert = false;

    private List<String> folders;

    public static void main(String[] args) throws MessagingException {
        IMAPSync imapsync = new IMAPSync();
        try {
            imapsync.setFolders(Args.parse(imapsync, args));
            imapsync.run();
        } catch (IllegalArgumentException iae) {
            Args.usage(imapsync);
        }
    }

    private void run() throws MessagingException {

        if (ignorecert) {
            Security.setProperty("ssl.SocketFactory.provider", "com.sampullara.imapsync.ssl.DummySSLSocketFactory");
        }

        Session sourceSession = Session.getDefaultInstance(System.getProperties());
        URLName sourceURLName = new URLName(sourceURL);
        Store sourceStore = sourceSession.getStore(sourceURLName);
        l.info("Connecting to source");
        sourceStore.connect();

        l.info("Connecting to destination");
        Session destSession = Session.getDefaultInstance(System.getProperties());
        URLName destURLName = new URLName(destURL);
        Store destStore = destSession.getStore(destURLName);
        destStore.connect();

        Folder sf;
        if (sourceFolder == null) {
            sf = sourceStore.getDefaultFolder();
        } else {
            sf = sourceStore.getFolder(sourceFolder);
        }
        int messageCount = sf.getMessageCount();
        l.info("Source folder: " + sf.getName() + " " + messageCount + " messages");

        Folder df;
        if (destFolder == null) {
            df = destStore.getDefaultFolder();
        } else {
            df = destStore.getFolder(destFolder);
        }
        l.info("Destination folder: " + df.getName());

        if (folders == null || folders.size() == 0) {
            copy(sf, df);
        } else {
            for (String folder : folders) {
                Folder newSF = sf.getFolder(folder);
                Folder newDF = df.getFolder(folder);
                copy(newSF, newDF);
            }
        }

        l.info("Closing connections");
        sourceStore.close();
        destStore.close();
    }

    private void copy(Folder sf, Folder df) throws MessagingException {
        l.info("Opening source for read only");
        sf.open(Folder.READ_ONLY);
        l.info("Opening destination for read write");
        if (!df.exists()) {
            df.create(Folder.HOLDS_MESSAGES);
        }
        df.open(Folder.READ_WRITE);
        l.info("Getting latest message in destination store");
        Message[] destMessages = df.getMessages();
        Date lastReceivedDate = null;
        if (destMessages.length != 0) {
            Message latest = destMessages[destMessages.length - 1];
            lastReceivedDate = latest.getReceivedDate();
        }
        df.addMessageCountListener(new MessageCountAdapter() {
            private int i = 0;

            public void messagesAdded(MessageCountEvent messageCountEvent) {
                l.info("Message added: " + ++i);
            }
        });
        Message[] messages;
        if (lastReceivedDate == null) {
            l.info("Getting all source messages");
            messages = sf.getMessages();
        } else {
            l.info("Getting all source messages newer than " + lastReceivedDate);
            ReceivedDateTerm rdt = new ReceivedDateTerm(ComparisonTerm.GT, lastReceivedDate);
            messages = sf.search(rdt);
        }
        l.info("Copying " + messages.length + " messages");
        for (Message message : messages) {
            if (lastReceivedDate != null) {
                if (lastReceivedDate.getTime() >= message.getReceivedDate().getTime()) {
                    continue;
                }
            }
            df.appendMessages(new Message[] { message });
        }
    }

    public List<String> getFolders() {
        return folders;
    }

    public void setFolders(List<String> folders) {
        this.folders = folders;
    }
}
