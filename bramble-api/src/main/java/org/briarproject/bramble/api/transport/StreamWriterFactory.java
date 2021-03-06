package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.OutputStream;

@NotNullByDefault
public interface StreamWriterFactory {

	/**
	 * Creates an {@link OutputStream OutputStream} for writing to a
	 * transport stream
	 */
	StreamWriter createStreamWriter(OutputStream out, StreamContext ctx);

	/**
	 * Creates an {@link OutputStream OutputStream} for writing to a contact
	 * exchange stream.
	 */
	StreamWriter createContactExchangeStreamWriter(OutputStream out,
			SecretKey headerKey);
}
