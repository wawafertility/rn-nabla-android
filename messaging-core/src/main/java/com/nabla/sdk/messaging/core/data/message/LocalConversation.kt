package com.nabla.sdk.messaging.core.data.message

import com.benasher44.uuid.Uuid
import com.nabla.sdk.core.domain.boundary.StringResolver
import com.nabla.sdk.messaging.core.R
import com.nabla.sdk.messaging.core.domain.entity.Conversation
import com.nabla.sdk.messaging.core.domain.entity.ConversationId
import kotlinx.datetime.Clock

internal data class LocalConversation(
    val localId: ConversationId.Local,
    val creationState: CreationState,
    val title: String?,
    val providerIds: List<Uuid>?,
) {
    fun asConversation(stringResolver: StringResolver): Conversation {
        return Conversation(
            id = localId,
            title = title,
            subtitle = null,
            inboxPreviewTitle = stringResolver.resolve(R.string.nabla_draft_conversation_title_placeholder),
            lastMessagePreview = null,
            lastModified = Clock.System.now(),
            patientUnreadMessageCount = 0,
            providersInConversation = emptyList(),
        )
    }

    sealed interface CreationState {
        object ToBeCreated : CreationState
        object Creating : CreationState
        data class ErrorCreating(val cause: Throwable) : CreationState
        data class Created(val remoteId: ConversationId.Remote) : CreationState
    }
}
