package com.jobwarden.inbox.service.impl;

import java.io.IOException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import com.jobwarden.inbox.enums.MailProblem;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.CustomLog;
import org.jsoup.Jsoup;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * IMAP over TLS, with Jakarta Mail: read-only, so reading marks nothing as read, and a few seconds' patience with a
 * slow server, never more. TLS from the start, or after STARTTLS for a server that starts without it, like Proton
 * Mail Bridge; never none. A server on this computer, which signs its own certificate, is trusted by that certificate
 * ({@link LocalServerTrust}); any other by its name, as the certificate authorities vouch for it.
 */
@Component
@CustomLog
class ImapMailReader implements MailReader {

	/** The most of an email's text it keeps: news comes first. */
	private static final int TEXT_MAX_LENGTH = 20_000;

	private final String timeoutMillis;

	/**
	 * @param tls false for a server without TLS, only for the tests' own
	 */
	private final boolean tls;

	private final Clock clock;

	ImapMailReader(InboxProperties properties, Clock clock) {
		this.clock = clock;
		this.timeoutMillis = Integer.toString(properties.timeoutMillis());
		this.tls = properties.tls();
	}

	@Override
	public Tested test(MailServer server) {
		try {
			return testOver(server.withStarttls(false));
		} catch (MailFailure failed) {
			if (!this.tls || !speaksWithoutTls(failed)) {
				throw failed;
			}
			log.debug("{} starts without TLS: trying STARTTLS", server);
			return testOver(server.withStarttls(true));
		}
	}

	private Tested testOver(MailServer server) {
		// Connecting a server on this computer trusts the certificate it shows: the one checked from then on
		LocalServerTrust trust = server.isLocal() ? new LocalServerTrust(null) : null;
		try (Store store = connect(server, trust)) {
			Folder folder = store.getFolder(server.folder());
			folder.open(Folder.READ_ONLY);
			folder.close(false);
		} catch (MessagingException failed) {
			throw failure(server, failed);
		}
		return new Tested(server.starttls(), (trust != null) ? trust.seen() : null);
	}

	@Override
	public Fetched fetch(MailServer server, @Nullable Long uidValidity, @Nullable Long lastUid, Instant since, int max) {
		LocalServerTrust trust = (server.isLocal() && server.certificate() != null)
				? new LocalServerTrust(server.certificate()) : null;
		try (Store store = connect(server, trust)) {
			Folder folder = store.getFolder(server.folder());
			folder.open(Folder.READ_ONLY);
			try {
				UIDFolder uids = (UIDFolder) folder;
				long validity = uids.getUIDValidity();
				// The last email read, while the mailbox keeps its numbering (UIDVALIDITY); else it's read anew from since
				Long goOnAfter = (uidValidity != null && uidValidity == validity) ? lastUid : null;
				boolean goOn = goOnAfter != null;
				long after = (goOnAfter != null) ? goOnAfter : 0;
				Message[] found = goOn ? uids.getMessagesByUID(after + 1, UIDFolder.MAXUID)
						: folder.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)));
				FetchProfile profile = new FetchProfile();
				profile.add(FetchProfile.Item.ENVELOPE);
				profile.add(UIDFolder.FetchProfileItem.UID);
				folder.fetch(found, profile);
				// A range from past the last UID still gives the last email: only the newer ones count
				List<Message> fresh = Arrays.stream(found)
					.filter(message -> !goOn || uid(uids, message) > after)
					.sorted(Comparator.comparingLong(message -> uid(uids, message)))
					.toList();
				List<Message> kept = fresh.subList(Math.max(0, fresh.size() - max), fresh.size());
				List<MailMessage> messages = new ArrayList<>();
				for (Message message : kept) {
					messages.add(read(message, validity, uid(uids, message)));
				}
				long newest = fresh.isEmpty() ? newestUid(folder, uids, after) : uid(uids, fresh.getLast());
				log.debug("Read {} new emails from {}", messages.size(), server);
				return new Fetched(validity, newest, messages);
			} finally {
				folder.close(false);
			}
		} catch (MessagingException | IOException failed) {
			throw failure(server, failed);
		}
	}

	/**
	 * @param trust for a server on this computer, what trusts its own certificate; {@code null} to check the server's
	 * certificate and name as for any other
	 */
	private Store connect(MailServer server, @Nullable LocalServerTrust trust) throws MessagingException {
		String protocol = (this.tls && !server.starttls()) ? "imaps" : "imap";
		Properties properties = new Properties();
		properties.put("mail.store.protocol", protocol);
		String own = "mail." + protocol + ".";
		properties.put(own + "connectiontimeout", this.timeoutMillis);
		properties.put(own + "timeout", this.timeoutMillis);
		properties.put(own + "writetimeout", this.timeoutMillis);
		if (this.tls) {
			if (server.starttls()) {
				// Required: a server that doesn't turn TLS on isn't read at all
				properties.put("mail.imap.starttls.enable", "true");
				properties.put("mail.imap.starttls.required", "true");
			}
			if (trust != null) {
				// An object, not a name: put, not setProperty. STARTTLS uses it too. Without the fallback, a
				// certificate it refuses isn't tried again the usual way.
				properties.put(own + "ssl.socketFactory", trust.socketFactory());
				properties.put(own + "socketFactory.fallback", "false");
			} else {
				properties.put(own + "ssl.checkserveridentity", "true");
			}
		}
		Store store = Session.getInstance(properties).getStore(protocol);
		store.connect(server.host(), server.port(), server.username(), server.password());
		return store;
	}

	private MailMessage read(Message message, long validity, long uid) throws MessagingException, IOException {
		String messageId = (message instanceof MimeMessage mime && mime.getMessageID() != null) ? mime.getMessageID()
				: "uid:" + validity + ":" + uid;
		Date received = (message.getReceivedDate() != null) ? message.getReceivedDate() : message.getSentDate();
		InternetAddress from = firstAddress(message.getFrom());
		String text = text(message);
		return new MailMessage(messageId, (received != null) ? received.toInstant() : this.clock.instant(),
				(from != null) ? from.getPersonal() : null, (from != null) ? from.getAddress() : null,
				(message.getSubject() != null) ? message.getSubject() : "",
				(text.length() > TEXT_MAX_LENGTH) ? text.substring(0, TEXT_MAX_LENGTH) : text);
	}

	private static @Nullable InternetAddress firstAddress(Address @Nullable [] addresses) {
		if (addresses == null) {
			return null;
		}
		return Arrays.stream(addresses)
			.filter(InternetAddress.class::isInstance)
			.map(InternetAddress.class::cast)
			.findFirst()
			.orElse(null);
	}

	/**
	 * The plain text of the email, or the text of its HTML when it has none: never its attachments.
	 */
	private static String text(Part part) throws MessagingException, IOException {
		if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
			return "";
		}
		if (part.isMimeType("text/plain")) {
			return String.valueOf(part.getContent());
		}
		if (part.isMimeType("text/html")) {
			return Jsoup.parse(String.valueOf(part.getContent())).wholeText().replaceAll("[ \\t]+", " ")
				.replaceAll("\\n\\s*\\n+", "\n\n");
		}
		if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
			String html = "";
			for (int i = 0; i < multipart.getCount(); i++) {
				Part child = multipart.getBodyPart(i);
				if (child.isMimeType("text/plain") && !Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) {
					return text(child);
				}
				String text = text(child);
				if (html.isEmpty() && !text.isEmpty()) {
					html = text;
				}
			}
			return html;
		}
		return "";
	}

	/** The last UID there is, for a folder with nothing new: where to start next time. */
	private static long newestUid(Folder folder, UIDFolder uids, long otherwise) throws MessagingException {
		int count = folder.getMessageCount();
		return (count > 0) ? Math.max(otherwise, uid(uids, folder.getMessage(count))) : otherwise;
	}

	private static long uid(UIDFolder uids, Message message) {
		try {
			return uids.getUID(message);
		} catch (MessagingException failed) {
			throw new IllegalStateException("No UID", failed);
		}
	}

	/**
	 * Why the server couldn't be read, as it said it, and what that means when JobWarden can tell: nothing answering
	 * on this computer is Proton Mail Bridge not open, more likely than anything.
	 */
	private static MailFailure failure(MailServer server, Exception failed) {
		MailProblem problem = null;
		if (causedBy(failed, LocalServerTrust.CertificateChanged.class)) {
			problem = MailProblem.CERTIFICATE_CHANGED;
		} else if (server.isLocal() && causedBy(failed, ConnectException.class)) {
			problem = MailProblem.LOCAL_SERVER_DOWN;
		}
		return new MailFailure(reason(failed), problem, failed);
	}

	/**
	 * Whether TLS failed before it started, because the server spoke without it: IMAP's greeting where TLS's was
	 * expected. Not when the server's certificate or TLS itself was the trouble.
	 */
	private static boolean speaksWithoutTls(MailFailure failed) {
		return causedBy(failed, SSLException.class) && !causedBy(failed, SSLHandshakeException.class)
				&& !causedBy(failed, CertificateException.class);
	}

	private static boolean causedBy(Throwable failed, Class<? extends Throwable> type) {
		for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause)) {
				return true;
			}
		}
		return false;
	}

	/** What the server said, which says more than the exception's type, e.g. that the password was refused. */
	private static String reason(Exception failed) {
		Throwable cause = failed;
		while (cause.getCause() != null && cause.getMessage() == null) {
			cause = cause.getCause();
		}
		return (cause.getMessage() != null) ? cause.getMessage().strip() : cause.getClass().getSimpleName();
	}

}
