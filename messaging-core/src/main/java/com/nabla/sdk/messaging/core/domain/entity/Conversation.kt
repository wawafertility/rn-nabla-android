package com.nabla.sdk.messaging.core.domain.entity

import com.benasher44.uuid.Uuid
import kotlinx.datetime.Instant

@JvmInline
value class ConversationId internal constructor(val value: Uuid)

fun Uuid.toConversationId() = ConversationId(this)

data class Conversation(
    val id: ConversationId,
    val inboxPreviewTitle: String,
    val inboxPreviewSubtitle: String,
    val lastModified: Instant,
    val patientUnreadMessageCount: Int,
    val providersInConversation: List<ProviderInConversation>,
) {
    companion object
}
