package com.nabla.sdk.core.injection

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nabla.sdk.core.data.auth.*
import com.nabla.sdk.core.data.local.SecuredKVStorage
import com.nabla.sdk.core.data.logger.AndroidLogger
import com.nabla.sdk.core.data.logger.HttpLoggingInterceptorFactory
import com.nabla.sdk.core.data.logger.LoggerImpl
import com.nabla.sdk.core.data.patient.LocalPatientDataSource
import com.nabla.sdk.core.data.patient.PatientRepositoryImpl
import com.nabla.sdk.core.domain.boundary.Logger
import com.nabla.sdk.core.domain.boundary.PatientRepository
import com.nabla.sdk.core.domain.boundary.SessionTokenProvider
import com.nabla.sdk.core.domain.boundary.TokenRepository
import com.nabla.sdk.core.domain.interactor.LoginInteractor
import com.nabla.sdk.messaging.core.data.ConversationRepositoryImpl
import com.nabla.sdk.messaging.core.domain.boundary.ConversationRepository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class CoreContainer(context: Context, sessionTokenProvider: SessionTokenProvider) {
    private val securedKVStorage = SecuredKVStorage(context)
    private val logger: Logger = LoggerImpl(AndroidLogger())

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(tokenRepository))
            .addInterceptor(HttpLoggingInterceptorFactory.make(logger))
            .authenticator(ApiAuthenticator(tokenRepository))
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val retrofit by lazy {
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api.nabla.com/")
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    private val nablaService: NablaService by lazy { retrofit.create(NablaService::class.java) }

    private val tokenLocalDataSource = TokenLocalDataSource(securedKVStorage)
    private val tokenRemoteDataSource by lazy { TokenRemoteDataSource(nablaService) }
    private val tokenRepository: TokenRepository by lazy {
        TokenRepositoryImpl(
            tokenLocalDataSource,
            tokenRemoteDataSource,
            sessionTokenProvider,
            patientRepository,
            logger
        )
    }
    val conversationRepository: ConversationRepository = ConversationRepositoryImpl()
    private val localPatientDataSource = LocalPatientDataSource(securedKVStorage)
    private val patientRepository: PatientRepository = PatientRepositoryImpl(localPatientDataSource)

    fun loginInteractor() = LoginInteractor(patientRepository, tokenRepository)
}
