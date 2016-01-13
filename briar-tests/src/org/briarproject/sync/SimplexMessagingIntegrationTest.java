package org.briarproject.sync;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageVerifier;
import org.briarproject.api.sync.MessagingSession;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.plugins.ImmediateExecutor;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private static final int MAX_LATENCY = 2 * 60 * 1000; // 2 minutes

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final TransportId transportId = new TransportId("id");
	private final SecretKey master = TestUtils.createSecretKey();
	private final long timestamp = System.currentTimeMillis();

	private Injector alice, bob;

	@Before
	public void setUp() {
		testDir.mkdirs();
		alice = createInjector(aliceDir);
		bob = createInjector(bobDir);
	}

	private Injector createInjector(File dir) {
		return Guice.createInjector(new TestDatabaseModule(dir),
				new TestLifecycleModule(), new TestSystemModule(),
				new ContactModule(), new CryptoModule(), new DatabaseModule(),
				new DataModule(), new EventModule(), new IdentityModule(),
				new SyncModule(), new MessagingModule(), new TransportModule());
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		// Open Alice's database
		DatabaseComponent db = alice.getInstance(DatabaseComponent.class);
		assertFalse(db.open());
		// Add the transport
		db.addTransport(transportId, MAX_LATENCY);
		// Start Alice's key manager
		KeyManager keyManager = alice.getInstance(KeyManager.class);
		keyManager.start();
		// Add an identity for Alice
		AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
		LocalAuthor aliceAuthor = new LocalAuthor(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		IdentityManager identityManager =
				alice.getInstance(IdentityManager.class);
		identityManager.addLocalAuthor(aliceAuthor);
		// Add Bob as a contact
		AuthorId bobId = new AuthorId(TestUtils.getRandomId());
		Author bobAuthor = new Author(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactManager contactManager = alice.getInstance(ContactManager.class);
		ContactId contactId = contactManager.addContact(bobAuthor, aliceId);
		// Create the private conversation
		MessagingManager messagingManager =
				alice.getInstance(MessagingManager.class);
		messagingManager.addContact(contactId, master);
		// Derive and store the transport keys
		keyManager.addContact(contactId, Collections.singletonList(transportId),
				master, timestamp, true);
		// Send Bob a message
		byte[] body = "Hi Bob!".getBytes("UTF-8");
		PrivateMessageFactory messageFactory =
				alice.getInstance(PrivateMessageFactory.class);
		GroupId groupId = messagingManager.getConversationId(contactId);
		PrivateConversation conversation =
				messagingManager.getConversation(groupId);
		Message message = messageFactory.createPrivateMessage(null,
				conversation, "text/plain", timestamp, body);
		messagingManager.addLocalMessage(message);
		// Get a stream context
		StreamContext ctx = keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		// Create a stream writer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamWriterFactory streamWriterFactory =
				alice.getInstance(StreamWriterFactory.class);
		OutputStream streamWriter =
				streamWriterFactory.createStreamWriter(out, ctx);
		// Create an outgoing sync session
		EventBus eventBus = alice.getInstance(EventBus.class);
		PacketWriterFactory packetWriterFactory =
				alice.getInstance(PacketWriterFactory.class);
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(
				streamWriter);
		MessagingSession session = new SimplexOutgoingSession(db,
				new ImmediateExecutor(), eventBus, contactId, transportId,
				MAX_LATENCY, packetWriter);
		// Write whatever needs to be written
		session.run();
		streamWriter.close();
		// Clean up
		keyManager.stop();
		db.close();
		// Return the contents of the stream
		return out.toByteArray();
	}

	private void read(byte[] stream) throws Exception {
		// Open Bob's database
		DatabaseComponent db = bob.getInstance(DatabaseComponent.class);
		assertFalse(db.open());
		// Add the transport
		db.addTransport(transportId, MAX_LATENCY);
		// Start Bob's key manager
		KeyManager keyManager = bob.getInstance(KeyManager.class);
		keyManager.start();
		// Add an identity for Bob
		AuthorId bobId = new AuthorId(TestUtils.getRandomId());
		LocalAuthor bobAuthor = new LocalAuthor(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		IdentityManager identityManager =
				bob.getInstance(IdentityManager.class);
		identityManager.addLocalAuthor(bobAuthor);
		// Add Alice as a contact
		AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
		Author aliceAuthor = new Author(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactManager contactManager = bob.getInstance(ContactManager.class);
		ContactId contactId = contactManager.addContact(aliceAuthor, bobId);
		// Create the private conversation
		MessagingManager messagingManager =
				bob.getInstance(MessagingManager.class);
		messagingManager.addContact(contactId, master);
		// Derive and store the transport keys
		keyManager.addContact(contactId, Collections.singletonList(transportId),
				master, timestamp, false);
		// Set up an event listener
		MessageListener listener = new MessageListener();
		bob.getInstance(EventBus.class).addListener(listener);
		// Read and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		StreamContext ctx = keyManager.getStreamContext(transportId, tag);
		assertNotNull(ctx);
		// Create a stream reader
		StreamReaderFactory streamReaderFactory =
				bob.getInstance(StreamReaderFactory.class);
		InputStream streamReader =
				streamReaderFactory.createStreamReader(in, ctx);
		// Create an incoming sync session
		EventBus eventBus = bob.getInstance(EventBus.class);
		MessageVerifier messageVerifier =
				bob.getInstance(MessageVerifier.class);
		PacketReaderFactory packetReaderFactory =
				bob.getInstance(PacketReaderFactory.class);
		PacketReader packetReader = packetReaderFactory.createPacketReader(
				streamReader);
		MessagingSession session = new IncomingSession(db,
				new ImmediateExecutor(), new ImmediateExecutor(), eventBus,
				messageVerifier, contactId, transportId, packetReader);
		// No messages should have been added yet
		assertFalse(listener.messageAdded);
		// Read whatever needs to be read
		session.run();
		streamReader.close();
		// The private message from Alice should have been added
		assertTrue(listener.messageAdded);
		// Clean up
		keyManager.stop();
		db.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static class MessageListener implements EventListener {

		private volatile boolean messageAdded = false;

		public void eventOccurred(Event e) {
			if (e instanceof MessageAddedEvent) messageAdded = true;
		}
	}
}