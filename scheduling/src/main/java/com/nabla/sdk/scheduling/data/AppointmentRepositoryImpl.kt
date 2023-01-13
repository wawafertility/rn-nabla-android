package com.nabla.sdk.scheduling.data

import com.nabla.sdk.core.data.exception.GraphQLException
import com.nabla.sdk.core.domain.entity.PaginatedList
import com.nabla.sdk.core.kotlin.SharedSingle
import com.nabla.sdk.core.kotlin.sharedSingleIn
import com.nabla.sdk.scheduling.domain.boundary.AppointmentRepository
import com.nabla.sdk.scheduling.domain.entity.Appointment
import com.nabla.sdk.scheduling.domain.entity.AppointmentCategory
import com.nabla.sdk.scheduling.domain.entity.AppointmentCategoryId
import com.nabla.sdk.scheduling.domain.entity.AppointmentConfirmationConsents
import com.nabla.sdk.scheduling.domain.entity.AppointmentId
import com.nabla.sdk.scheduling.domain.entity.AppointmentLocationType
import com.nabla.sdk.scheduling.domain.entity.AvailabilitySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import java.util.UUID

internal class AppointmentRepositoryImpl(
    private val repoScope: CoroutineScope,
    private val gqlAppointmentDataSource: GqlAppointmentDataSource,
    private val gqlAppointmentCategoryDataSource: GqlAppointmentCategoryDataSource,
    private val gqlAppointmentConfirmConsentsDataSource: GqlAppointmentConfirmConsentsDataSource,
    private val gqlAppointmentLocationDataSource: GqlAppointmentLocationDataSource,
) : AppointmentRepository {

    override fun watchUpcomingAppointments(): Flow<PaginatedList<Appointment>> = gqlAppointmentDataSource.watchUpcomingAppointments()
    override fun watchPastAppointments(): Flow<PaginatedList<Appointment>> = gqlAppointmentDataSource.watchPastAppointments()

    private val loadMoreUpcomingAppointmentsSharedSingle: SharedSingle<Unit, Result<Unit>> =
        sharedSingleIn(repoScope) { gqlAppointmentDataSource.loadMoreUpcomingAppointments() }
    private val loadMorePastAppointmentsSharedSingle: SharedSingle<Unit, Result<Unit>> =
        sharedSingleIn(repoScope) { gqlAppointmentDataSource.loadMorePastAppointments() }

    override suspend fun loadMoreUpcomingAppointments() = loadMoreUpcomingAppointmentsSharedSingle.await()
    override suspend fun loadMorePastAppointments() = loadMorePastAppointmentsSharedSingle.await()

    override suspend fun getCategories(): List<AppointmentCategory> = gqlAppointmentCategoryDataSource.getCategories()

    override suspend fun getLocationTypes(): Set<AppointmentLocationType> {
        return gqlAppointmentLocationDataSource.getAvailableLocationTypes()
    }

    override fun watchAvailabilitySlots(categoryId: AppointmentCategoryId): Flow<PaginatedList<AvailabilitySlot>> =
        gqlAppointmentCategoryDataSource.watchAvailabilitySlots(categoryId)

    private val loadMoreAvailabilitySlotsSharedSingleMap = mutableMapOf<AppointmentCategoryId, SharedSingle<Unit, Result<Unit>>>()
    override suspend fun loadMoreAvailabilitySlots(categoryId: AppointmentCategoryId) = loadMoreAvailabilitySlotsSharedSingleMap
        .getOrPut(categoryId) {
            sharedSingleIn(repoScope) {
                gqlAppointmentCategoryDataSource.loadMoreAvailabilitySlots(categoryId)
            }
        }
        .await()

    override suspend fun getAppointmentConfirmationConsents(
        location: AppointmentLocationType
    ): AppointmentConfirmationConsents {
        return gqlAppointmentConfirmConsentsDataSource.getConfirmConsents(location)
    }

    override suspend fun scheduleAppointment(
        location: AppointmentLocationType,
        categoryId: AppointmentCategoryId,
        providerId: UUID,
        slot: Instant,
    ): Appointment {
        return try {
            gqlAppointmentDataSource.scheduleAppointment(location, categoryId, providerId, slot)
        } catch (gqlException: GraphQLException) {
            try {
                // very likely the input is not valid anymore (e.g. the selected slot is not available anymore)
                gqlAppointmentCategoryDataSource.resetAvailabilitySlotsCache(categoryId)
            } catch (e: Exception) {
                throw e.initCause(gqlException)
            }

            throw gqlException
        }
    }

    override suspend fun cancelAppointment(id: AppointmentId) {
        return gqlAppointmentDataSource.cancelAppointment(id)
    }

    override suspend fun getAppointment(id: AppointmentId): Appointment {
        return gqlAppointmentDataSource.getAppointment(id)
    }
}
