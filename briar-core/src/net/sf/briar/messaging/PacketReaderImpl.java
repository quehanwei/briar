package net.sf.briar.messaging;

import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.Types.ACK;
import static net.sf.briar.api.messaging.Types.MESSAGE;
import static net.sf.briar.api.messaging.Types.OFFER;
import static net.sf.briar.api.messaging.Types.REQUEST;
import static net.sf.briar.api.messaging.Types.RETENTION_ACK;
import static net.sf.briar.api.messaging.Types.RETENTION_UPDATE;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_ACK;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_UPDATE;
import static net.sf.briar.api.messaging.Types.TRANSPORT_ACK;
import static net.sf.briar.api.messaging.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.UniqueId;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.PacketReader;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

// This class is not thread-safe
class PacketReaderImpl implements PacketReader {

	private final StructReader<UnverifiedMessage> messageReader;
	private final StructReader<SubscriptionUpdate> subscriptionUpdateReader;
	private final Reader r;

	PacketReaderImpl(ReaderFactory readerFactory,
			StructReader<UnverifiedMessage> messageReader,
			StructReader<SubscriptionUpdate> subscriptionUpdateReader,
			InputStream in) {
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
		r = readerFactory.createReader(in);
	}

	public boolean eof() throws IOException {
		return r.eof();
	}

	public boolean hasAck() throws IOException {
		return r.hasStruct(ACK);
	}

	public Ack readAck() throws IOException {
		// Set up the reader
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		// Read the start of the struct
		r.readStructStart(ACK);
		// Read the message IDs
		List<MessageId> acked = new ArrayList<MessageId>();
		r.readListStart();
		while(!r.hasListEnd()) {
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH)
				throw new FormatException();
			acked.add(new MessageId(b));
		}
		if(acked.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the struct
		r.readStructEnd();
		// Reset the reader
		r.removeConsumer(counting);
		// Build and return the ack
		return new Ack(Collections.unmodifiableList(acked));
	}

	public boolean hasMessage() throws IOException {
		return r.hasStruct(MESSAGE);
	}

	public UnverifiedMessage readMessage() throws IOException {
		return messageReader.readStruct(r);
	}

	public boolean hasOffer() throws IOException {
		return r.hasStruct(OFFER);
	}

	public Offer readOffer() throws IOException {
		// Set up the reader
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		// Read the start of the struct
		r.readStructStart(OFFER);
		// Read the message IDs
		List<MessageId> offered = new ArrayList<MessageId>();
		r.readListStart();
		while(!r.hasListEnd()) {
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH)
				throw new FormatException();
			offered.add(new MessageId(b));
		}
		if(offered.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the struct
		r.readStructEnd();
		// Reset the reader
		r.removeConsumer(counting);
		// Build and return the offer
		return new Offer(Collections.unmodifiableList(offered));
	}

	public boolean hasRequest() throws IOException {
		return r.hasStruct(REQUEST);
	}

	public Request readRequest() throws IOException {
		// Set up the reader
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		// Read the start of the struct
		r.readStructStart(REQUEST);
		// Read the message IDs
		List<MessageId> requested = new ArrayList<MessageId>();
		r.readListStart();
		while(!r.hasListEnd()) {
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH)
				throw new FormatException();
			requested.add(new MessageId(b));
		}
		if(requested.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the struct
		r.readStructEnd();
		// Reset the reader
		r.removeConsumer(counting);
		// Build and return the request
		return new Request(Collections.unmodifiableList(requested));
	}

	public boolean hasRetentionAck() throws IOException {
		return r.hasStruct(RETENTION_ACK);
	}

	public RetentionAck readRetentionAck() throws IOException {
		r.readStructStart(RETENTION_ACK);
		long version = r.readIntAny();
		if(version < 0) throw new FormatException();
		r.readStructEnd();
		return new RetentionAck(version);
	}

	public boolean hasRetentionUpdate() throws IOException {
		return r.hasStruct(RETENTION_UPDATE);
	}

	public RetentionUpdate readRetentionUpdate() throws IOException {
		r.readStructStart(RETENTION_UPDATE);
		long retention = r.readIntAny();
		if(retention < 0) throw new FormatException();
		long version = r.readIntAny();
		if(version < 0) throw new FormatException();
		r.readStructEnd();
		return new RetentionUpdate(retention, version);
	}

	public boolean hasSubscriptionAck() throws IOException {
		return r.hasStruct(SUBSCRIPTION_ACK);
	}

	public SubscriptionAck readSubscriptionAck() throws IOException {
		r.readStructStart(SUBSCRIPTION_ACK);
		long version = r.readIntAny();
		if(version < 0) throw new FormatException();
		r.readStructEnd();
		return new SubscriptionAck(version);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return r.hasStruct(SUBSCRIPTION_UPDATE);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		return subscriptionUpdateReader.readStruct(r);
	}

	public boolean hasTransportAck() throws IOException {
		return r.hasStruct(TRANSPORT_ACK);
	}

	public TransportAck readTransportAck() throws IOException {
		r.readStructStart(TRANSPORT_ACK);
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		long version = r.readIntAny();
		if(version < 0) throw new FormatException();
		r.readStructEnd();
		return new TransportAck(new TransportId(b), version);
	}

	public boolean hasTransportUpdate() throws IOException {
		return r.hasStruct(TRANSPORT_UPDATE);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		// Set up the reader
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		// Read the start of the struct
		r.readStructStart(TRANSPORT_UPDATE);
		// Read the transport ID
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		TransportId id = new TransportId(b);
		// Read the transport properties
		Map<String, String> p = new HashMap<String, String>();
		r.readMapStart();
		for(int i = 0; !r.hasMapEnd(); i++) {
			if(i == MAX_PROPERTIES_PER_TRANSPORT)
				throw new FormatException();
			String key = r.readString(MAX_PROPERTY_LENGTH);
			String value = r.readString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		r.readMapEnd();
		// Read the version number
		long version = r.readIntAny();
		if(version < 0) throw new FormatException();
		// Read the end of the struct
		r.readStructEnd();
		// Reset the reader
		r.removeConsumer(counting);
		// Build and return the transport update
		return new TransportUpdate(id, new TransportProperties(p), version);
	}
}
