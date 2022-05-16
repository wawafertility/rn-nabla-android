package com.nabla.sdk.messaging.core.data.stubs

import androidx.test.espresso.idling.CountingIdlingResource
import com.nabla.sdk.core.domain.boundary.Logger
import com.nabla.sdk.core.kotlin.runCatchingCancellable
import com.nabla.sdk.messaging.core.NablaMessagingClient
import com.nabla.sdk.messaging.core.domain.entity.Conversation
import com.nabla.sdk.messaging.core.domain.entity.ConversationId
import com.nabla.sdk.messaging.core.domain.entity.ConversationMessages
import com.nabla.sdk.messaging.core.domain.entity.Message
import com.nabla.sdk.messaging.core.domain.entity.MessageId
import com.nabla.sdk.messaging.core.domain.entity.WatchPaginatedResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NablaMessagingClientStub(
    val idlingRes: CountingIdlingResource = CountingIdlingResource("Stubs Idling Res", true),
) : NablaMessagingClient {

    private val conversationRepository = ConversationRepositoryStub(idlingRes)
    private val messageRepository = MessageRepositoryStub(idlingRes)

    override val logger: Logger = LoggerImpl

    override fun watchConversations(): Flow<WatchPaginatedResponse<List<Conversation>>> {
        val loadMoreCallback = suspend {
            runCatchingCancellable {
                conversationRepository.loadMoreConversations()
            }
        }

        return conversationRepository.watchConversations()
            .map { paginatedConversations ->
                WatchPaginatedResponse(
                    content = paginatedConversations.items,
                    loadMore = if (paginatedConversations.hasMore) {
                        loadMoreCallback
                    } else {
                        null
                    },
                )
            }
    }

    override suspend fun createConversation(): Result<Conversation> {
        return runCatchingCancellable {
            conversationRepository.createConversation()
        }
    }

    override fun watchConversation(conversationId: ConversationId): Flow<Conversation> {
        return conversationRepository.watchConversation(conversationId)
    }

    override fun watchConversationMessages(conversationId: ConversationId): Flow<WatchPaginatedResponse<ConversationMessages>> {
        val loadMoreCallback = suspend {
            runCatchingCancellable {
                messageRepository.loadMoreMessages(conversationId)
            }
        }

        return messageRepository.watchConversationMessages(conversationId)
            .map { paginatedConversationMessages ->
                WatchPaginatedResponse(
                    content = paginatedConversationMessages.conversationMessages,
                    loadMore = if (paginatedConversationMessages.hasMore) {
                        loadMoreCallback
                    } else null
                )
            }
    }

    override suspend fun sendMessage(message: Message): Result<Unit> {
        return runCatchingCancellable {
            messageRepository.sendMessage(message)
        }
    }

    override suspend fun retrySendingMessage(localMessageId: MessageId.Local, conversationId: ConversationId): Result<Unit> {
        return runCatchingCancellable {
            messageRepository.retrySendingMessage(conversationId, localMessageId)
        }
    }

    override suspend fun setTyping(conversationId: ConversationId, isTyping: Boolean): Result<Unit> {
        return runCatchingCancellable {
            messageRepository.setTyping(conversationId, isTyping)
        }
    }

    override suspend fun markConversationAsRead(conversationId: ConversationId): Result<Unit> {
        return runCatchingCancellable {
            conversationRepository.markConversationAsRead(conversationId)
        }
    }

    override suspend fun deleteMessage(conversationId: ConversationId, id: MessageId): Result<Unit> {
        return runCatchingCancellable {
            messageRepository.deleteMessage(conversationId, id)
        }
    }
}