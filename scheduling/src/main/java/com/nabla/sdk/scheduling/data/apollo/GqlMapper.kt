package com.nabla.sdk.scheduling.data.apollo

import com.nabla.sdk.core.data.apollo.CoreGqlMapper
import com.nabla.sdk.core.domain.boundary.Logger
import com.nabla.sdk.core.domain.entity.InternalException.Companion.throwNablaInternalException
import com.nabla.sdk.scheduling.domain.entity.Address
import com.nabla.sdk.scheduling.domain.entity.Appointment
import com.nabla.sdk.scheduling.domain.entity.AppointmentCategory
import com.nabla.sdk.scheduling.domain.entity.AppointmentCategoryId
import com.nabla.sdk.scheduling.domain.entity.AppointmentConfirmationConsents
import com.nabla.sdk.scheduling.domain.entity.AppointmentId
import com.nabla.sdk.scheduling.domain.entity.AppointmentLocation
import com.nabla.sdk.scheduling.domain.entity.AppointmentLocationType
import com.nabla.sdk.scheduling.domain.entity.AppointmentState
import com.nabla.sdk.scheduling.domain.entity.AvailabilitySlot
import com.nabla.sdk.scheduling.graphql.AppointmentConfirmationConsentsQuery
import com.nabla.sdk.scheduling.graphql.fragment.AppointmentCategoryFragment
import com.nabla.sdk.scheduling.graphql.fragment.AppointmentFragment
import com.nabla.sdk.scheduling.graphql.fragment.AvailabilitySlotFragment
import kotlin.time.Duration.Companion.minutes

internal class GqlMapper(
    private val coreGqlMapper: CoreGqlMapper,
    private val logger: Logger,
) {
    fun mapToAppointment(fragment: AppointmentFragment): Appointment {
        val state = if (fragment.state.onUpcomingAppointment != null) AppointmentState.UPCOMING else AppointmentState.FINALIZED
        val location = mapLocation(fragment.location)
        return Appointment(
            AppointmentId(fragment.id),
            coreGqlMapper.mapToProvider(fragment.provider.providerFragment),
            fragment.scheduledAt,
            state,
            location
        )
    }

    private fun mapLocation(location: AppointmentFragment.Location): AppointmentLocation {
        location.onPhysicalAppointmentLocation?.let {
            val addressFragment = it.address.addressFragment
            return AppointmentLocation.Physical(
                Address(
                    address = addressFragment.address,
                    zipCode = addressFragment.zipCode,
                    city = addressFragment.city,
                    state = addressFragment.state,
                    country = addressFragment.country,
                    extraDetails = addressFragment.extraDetails
                )
            )
        }
        location.onRemoteAppointmentLocation?.let {
            return AppointmentLocation.Remote(
                it.livekitRoom?.livekitRoomFragment?.let { coreGqlMapper.mapToVideoCallRoom(it) }
            )
        }
        throwNablaInternalException("Unknown appointment location mapping for $location")
    }

    fun mapToAppointmentCategory(fragment: AppointmentCategoryFragment): AppointmentCategory {
        return AppointmentCategory(
            AppointmentCategoryId(fragment.id),
            fragment.name,
            fragment.callDurationMinutes.minutes,
        )
    }

    fun mapToAvailabilitySlot(fragment: AvailabilitySlotFragment): AvailabilitySlot {
        return AvailabilitySlot(
            startAt = fragment.startAt,
            providerId = fragment.provider.id,
        )
    }

    fun mapToAppointmentConfirmationConsents(
        gqlData: AppointmentConfirmationConsentsQuery.AppointmentConfirmationConsents,
        locationType: AppointmentLocationType
    ): AppointmentConfirmationConsents {
        val htmlConsents = mutableListOf<String>()
        val checkAndAdd: (String) -> Unit = { htmlString ->
            if (htmlString.isNotBlank()) htmlConsents.add(htmlString)
        }
        when (locationType) {
            AppointmentLocationType.PHYSICAL -> {
                checkAndAdd(gqlData.physicalFirstConsentHtml)
                checkAndAdd(gqlData.physicalSecondConsentHtml)
            }
            AppointmentLocationType.REMOTE -> {
                checkAndAdd(gqlData.firstConsentHtml)
                checkAndAdd(gqlData.secondConsentHtml)
            }
        }
        return AppointmentConfirmationConsents(htmlConsents)
    }
}
