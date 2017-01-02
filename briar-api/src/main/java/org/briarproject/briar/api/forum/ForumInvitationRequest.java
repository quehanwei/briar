package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ForumInvitationRequest extends InvitationRequest {

	private final String forumName;

	public ForumInvitationRequest(MessageId id, SessionId sessionId,
			GroupId groupId, ContactId contactId, GroupId forumId,
			String forumName, @Nullable String message, boolean available,
			boolean canBeOpened, long time, boolean local, boolean sent,
			boolean seen, boolean read) {

		super(id, sessionId, groupId, contactId, message, forumId, available,
				canBeOpened, time, local, sent, seen, read);
		this.forumName = forumName;
	}

	public String getForumName() {
		return forumName;
	}

}