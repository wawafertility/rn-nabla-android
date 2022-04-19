package com.nabla.sdk.core.data.logger

import com.nabla.sdk.core.domain.boundary.Logger
import okhttp3.logging.HttpLoggingInterceptor

internal object HttpLoggingInterceptorFactory {
    fun make(logger: Logger): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor { message -> logger.debug(message, tag = Logger.tag("Http")) }
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        return logging
    }
}
