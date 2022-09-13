package com.nabla.sdk.messaging.core

import androidx.annotation.CheckResult
import com.benasher44.uuid.Uuid
import com.nabla.sdk.core.data.exception.catchAndRethrowAsNablaException
import com.nabla.sdk.core.data.exception.mapFailure
import com.nabla.sdk.core.data.exception.mapFailureAsNablaException
import com.nabla.sdk.core.domain.auth.ensureAuthenticatedOrThrow
import com.nabla.sdk.core.domain.auth.throwOnStartIfNotAuthenticated
import com.nabla.sdk.core.domain.boundary.Logger
import com.nabla.sdk.core.domain.entity.ServerException
import com.nabla.sdk.core.domain.entity.WatchPaginatedResponse
import com.nabla.sdk.core.domain.helper.makePaginatedFlow
import com.nabla.sdk.core.injection.CoreContainer
import com.nabla.sdk.core.kotlin.runCatchingCancellable
import com.nabla.sdk.messaging.core.domain.boundary.ConversationContentRepository
import com.nabla.sdk.messaging.core.domain.boundary.ConversationRepository
import com.nabla.sdk.messaging.core.domain.entity.Conversation
import com.nabla.sdk.messaging.core.domain.entity.ConversationId
import com.nabla.sdk.messaging.core.domain.entity.ConversationItem
import com.nabla.sdk.messaging.core.domain.entity.MessageId
import com.nabla.sdk.messaging.core.domain.entity.MessageInput
import com.nabla.sdk.messaging.core.domain.entity.ProviderMissingPermissionException
import com.nabla.sdk.messaging.core.domain.entity.ProviderNotFoundException
import com.nabla.sdk.messaging.core.injection.MessagingContainer
import kotlinx.coroutines.flow.Flow

internal class NablaMessagingClientImpl internal constructor(
    coreContainer: CoreContainer,
) : NablaMessagingClient {

    private val messagingContainer = MessagingContainer(
        coreContainer.logger,
        coreContainer.coreGqlMapper,
        coreContainer.apolloClient,
        coreContainer.fileUploadRepository,
        coreContainer.exceptionMapper,
        coreContainer.sessionClient,
        coreContainer.clock,
        coreContainer.uuidGenerator,
        coreContainer.stringResolver,
        coreContainer.videoCallModule,
    )

    private val conversationRepository: ConversationRepository by lazy {
        messagingContainer.conversationRepository
    }
    private val conversationContentRepository: ConversationContentRepository by lazy {
        messagingContainer.conversationContentRepository
    }

    override val logger: Logger = coreContainer.logger

    override fun watchConversations(): Flow<WatchPaginatedResponse<List<Conversation>>> {
        return makePaginatedFlow(
            conversationRepository.watchConversations(),
            conversationRepository::loadMoreConversations,
            messagingContainer.nablaExceptionMapper,
            messagingContainer.sessionClient
        )
    }

    @CheckResult
    override suspend fun createConversation(
        title: String?,
        providerIds: List<Uuid>?,
        initialMessage: MessageInput?
    ): Result<Conversation> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationRepository.createConversation(title, providerIds, initialMessage)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
            .mapFailure { error ->
                if (error is ServerException) {
                    when (error.code) {
                        ProviderNotFoundException.ERROR_CODE -> ProviderNotFoundException(cause = error)
                        ProviderMissingPermissionException.ERROR_CODE -> ProviderMissingPermissionException(cause = error)
                        else -> error
                    }
                } else error
            }
    }

    override fun createDraftConversation(title: String?, providerIds: List<Uuid>?): ConversationId.Local {
        return conversationRepository.createLocalConversation(title, providerIds)
    }

    override fun watchConversation(conversationId: ConversationId): Flow<Conversation> {
        return conversationRepository.watchConversation(conversationId)
            .throwOnStartIfNotAuthenticated(messagingContainer.sessionClient)
            .catchAndRethrowAsNablaException(messagingContainer.nablaExceptionMapper)
    }

    override fun watchConversationItems(conversationId: ConversationId): Flow<WatchPaginatedResponse<List<ConversationItem>>> {
        return makePaginatedFlow(
            conversationContentRepository.watchConversationItems(conversationId),
            { conversationContentRepository.loadMoreMessages(conversationId) },
            messagingContainer.nablaExceptionMapper,
            messagingContainer.sessionClient
        )
    }

    override suspend fun sendMessage(
        input: MessageInput,
        conversationId: ConversationId,
        replyTo: MessageId.Remote?,
    ): Result<MessageId.Local> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationContentRepository.sendMessage(input, conversationId, replyTo)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
    }

    @CheckResult
    override suspend fun retrySendingMessage(localMessageId: MessageId.Local, conversationId: ConversationId): Result<Unit> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationContentRepository.retrySendingMessage(conversationId, localMessageId)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
    }

    @CheckResult
    override suspend fun setTyping(conversationId: ConversationId, isTyping: Boolean): Result<Unit> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationContentRepository.setTyping(conversationId, isTyping)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
    }

    @CheckResult
    override suspend fun markConversationAsRead(conversationId: ConversationId): Result<Unit> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationRepository.markConversationAsRead(conversationId)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
    }

    @CheckResult
    override suspend fun deleteMessage(conversationId: ConversationId, id: MessageId): Result<Unit> {
        return runCatchingCancellable {
            messagingContainer.sessionClient.ensureAuthenticatedOrThrow()
            conversationContentRepository.deleteMessage(conversationId, id)
        }.mapFailureAsNablaException(messagingContainer.nablaExceptionMapper)
    }
}
