package com.nabla.sdk.messaging.core.data.stubs

import com.apollographql.apollo3.annotations.ApolloExperimental
import com.benasher44.uuid.uuid4
import com.nabla.sdk.core.domain.entity.MimeType
import com.nabla.sdk.graphql.ConversationEventsSubscription
import com.nabla.sdk.graphql.ConversationItemsQuery
import com.nabla.sdk.graphql.ConversationsEventsSubscription
import com.nabla.sdk.graphql.ConversationsQuery
import com.nabla.sdk.graphql.test.ConversationEventsSubscription_TestBuilder.Data
import com.nabla.sdk.graphql.test.ConversationItemsQuery_TestBuilder
import com.nabla.sdk.graphql.test.ConversationItemsQuery_TestBuilder.Data
import com.nabla.sdk.graphql.test.ConversationsEventsSubscription_TestBuilder.Data
import com.nabla.sdk.graphql.test.ConversationsQuery_TestBuilder
import com.nabla.sdk.graphql.test.ConversationsQuery_TestBuilder.Data
import com.nabla.sdk.messaging.core.domain.entity.ConversationId
import com.nabla.sdk.messaging.core.domain.entity.MessageId
import com.nabla.sdk.test.apollo.CustomTestResolver

@OptIn(ApolloExperimental::class)
internal object GqlData {
    object Conversations {
        fun empty() = ConversationsQuery.Data {
            conversations = conversations {
                conversations = emptyList()
            }
        }

        fun single(block: ConversationsQuery_TestBuilder.ConversationsBuilder.() -> Unit = {}) =
            ConversationsQuery.Data(CustomTestResolver()) {
                conversations = conversations {
                    conversations = listOf(conversation { })
                    block()
                }
            }
    }

    object ConversationItems {
        fun empty(conversationId: ConversationId) =
            ConversationItemsQuery.Data(CustomTestResolver()) {
                conversation = conversation {
                    conversation = conversation {
                        id = conversationId.value.toString()
                        items = items {
                            data = emptyList()
                        }
                    }
                }
            }

        fun single(
            conversationId: ConversationId,
            block: ConversationItemsQuery_TestBuilder.ItemsBuilder.() -> Unit = {}
        ) = ConversationItemsQuery.Data(CustomTestResolver()) {
            conversation = conversation {
                conversation = conversation {
                    id = conversationId.value.toString()
                    items = items {
                        data = listOf(
                            messageData {
                                messageContent = textMessageContentMessageContent { }
                            }
                        )
                        block()
                    }
                }
            }
        }
    }

    object ConversationsEvents {
        fun conversationCreated(conversationId: ConversationId? = null) = ConversationsEventsSubscription.Data(CustomTestResolver()) {
            conversations = conversations {
                event = conversationCreatedEventEvent {
                    conversation = conversation {
                        conversationId?.let { id = conversationId.value.toString() }
                    }
                }
            }
        }
    }

    object ConversationEvents {
        object MessageDeleted {
            fun deletedPatientMessage(
                conversationId: ConversationId,
                localMessageId: MessageId = MessageId.Local(uuid4()),
            ) = ConversationEventsSubscription.Data(CustomTestResolver()) {
                conversation = conversation {
                    event = messageUpdatedEventEvent {
                        message = message {
                            clientId = localMessageId.clientId.toString()
                            messageContent = deletedMessageContentMessageContent { }
                            conversation = conversation {
                                id = conversationId.value.toString()
                            }
                            author = patientAuthor {}
                        }
                    }
                }
            }
        }

        object MessageCreated {
            fun patientTextMessage(
                conversationId: ConversationId,
                localMessageId: MessageId = MessageId.Local(uuid4()),
            ) = ConversationEventsSubscription.Data(CustomTestResolver()) {
                conversation = conversation {
                    event = messageCreatedEventEvent {
                        message = message {
                            clientId = localMessageId.clientId.toString()
                            messageContent = textMessageContentMessageContent { }
                            conversation = conversation {
                                id = conversationId.value.toString()
                            }
                            author = patientAuthor {}
                        }
                    }
                }
            }

            fun patientImageMessage(
                conversationId: ConversationId,
                localMessageId: MessageId = MessageId.Local(uuid4()),
            ) = ConversationEventsSubscription.Data(CustomTestResolver()) {
                conversation = conversation {
                    event = messageCreatedEventEvent {
                        message = message {
                            clientId = localMessageId.clientId.toString()
                            messageContent = imageMessageContentMessageContent {
                                imageFileUpload = imageFileUpload {
                                    mimeType = MimeType.Image.JPEG.stringRepresentation
                                }
                            }
                            conversation = conversation {
                                id = conversationId.value.toString()
                            }
                            author = patientAuthor {}
                        }
                    }
                }
            }

            fun patientDocumentMessage(
                conversationId: ConversationId,
                localMessageId: MessageId = MessageId.Local(uuid4()),
            ) = ConversationEventsSubscription.Data(CustomTestResolver()) {
                conversation = conversation {
                    event = messageCreatedEventEvent {
                        message = message {
                            clientId = localMessageId.clientId.toString()
                            messageContent = documentMessageContentMessageContent {
                                documentFileUpload = documentFileUpload {
                                    mimeType = MimeType.Application.PDF.stringRepresentation
                                    thumbnail = thumbnail {
                                        mimeType = MimeType.Image.JPEG.stringRepresentation
                                    }
                                }
                            }
                            conversation = conversation {
                                id = conversationId.value.toString()
                            }
                            author = patientAuthor {}
                        }
                    }
                }
            }
        }
    }
}
