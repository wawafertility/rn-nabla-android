package com.nabla.sdk.videocall

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabla.sdk.core.kotlin.runCatchingCancellable
import com.nabla.sdk.core.ui.helpers.LiveFlow
import com.nabla.sdk.core.ui.helpers.MutableLiveFlow
import com.nabla.sdk.core.ui.helpers.emitIn
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.util.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class VideoCallViewModel(
    private val videoCallClient: NablaVideoCallClient,
    private val url: String,
    private val token: String,
) : ViewModel() {

    private val mutableLocalParticipantFlow = MutableStateFlow<LocalVideoTrack?>(null)
    private val mutableRemoteParticipantFlow = MutableStateFlow<VideoTrack?>(null)
    private val mutableIsPictureInPictureFlow = MutableStateFlow(false)

    // this could have been avoided if tracks were observable, see https://github.com/livekit/client-sdk-android/issues/101
    private val mutableIsSelfMirrorFlow = MutableStateFlow(true)

    private val mutableFinishFlow = MutableLiveFlow<FinishReason>()
    internal val finishFlow: LiveFlow<FinishReason> = mutableFinishFlow

    private val mutableConnectedRoomFlow = MutableStateFlow<Room?>(null)
    internal val connectedRoomFlow: StateFlow<Room?> = mutableConnectedRoomFlow

    private val mutableConnectionInfoFlow = MutableStateFlow<ConnectionInfoState?>(null)
    internal val connectionInfoFlow: StateFlow<ConnectionInfoState?> = mutableConnectionInfoFlow

    private val mutableControlsFlow = MutableStateFlow<ControlsState?>(null)
    internal val controlsFlow: Flow<ControlsState?> = mutableControlsFlow
        .combine(mutableIsPictureInPictureFlow) { controls, isPip ->
            if (isPip) controls?.copy(controlsVisible = false) else controls
        }

    internal val videoStateFlow = combine(
        mutableLocalParticipantFlow,
        mutableRemoteParticipantFlow,
        mutableIsSelfMirrorFlow,
        mutableIsPictureInPictureFlow,
        mutableControlsFlow.map { it?.cameraEnabled ?: true },
    ) { localTrack, remoteTrack, isSelfMirror, pictureInPictureEnabled, cameraEnabled ->

        when (localTrack) {
            null -> when (remoteTrack) {
                null -> VideoState.None
                else -> VideoState.RemoteOnly(remoteTrack)
            }
            else -> when (remoteTrack) {
                null -> if (cameraEnabled) {
                    VideoState.SelfOnly(localTrack, isSelfMirror)
                } else VideoState.None
                else -> if (cameraEnabled && !pictureInPictureEnabled) {
                    VideoState.Both(localTrack, remoteTrack, isSelfMirror)
                } else VideoState.RemoteOnly(remoteTrack)
            }
        }.also { videoCallClient.logger.debug("new video state: $it", domain = VIDEO_CALL_DOMAIN) }
    }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = VideoState.None)

    fun onPermissionsGranted() {
        if (mutableConnectedRoomFlow.value != null) return

        viewModelScope.launch {
            runCatchingCancellable {
                val room = videoCallClient.connectRoom(url, token)
                val localParticipant = room.localParticipant

                localParticipant.setMicrophoneEnabled(true)
                localParticipant.setCameraEnabled(true)

                mutableConnectedRoomFlow.value = room

                mutableControlsFlow.value = ControlsState(
                    micEnabled = localParticipant.isMicrophoneEnabled(),
                    cameraEnabled = localParticipant.isCameraEnabled(),
                )

                viewModelScope.launch {
                    room.localParticipant::videoTracks.flow
                        .map { tracks ->
                            tracks.map { it.second }.firstNotNullOf { it as? LocalVideoTrack }
                        }
                        .collect(mutableLocalParticipantFlow)
                }

                viewModelScope.launch {
                    room::state.flow.collect { roomState ->
                        videoCallClient.logger.debug(
                            "new state for room ${room.name}: $roomState",
                            domain = VIDEO_CALL_DOMAIN
                        )
                        when (roomState) {
                            Room.State.CONNECTING -> {
                                mutableConnectionInfoFlow.value = ConnectionInfoState.Connecting
                            }
                            Room.State.CONNECTED -> {
                                mutableConnectionInfoFlow.value = ConnectionInfoState.Connected
                            }
                            Room.State.DISCONNECTED -> {
                                hangUp()
                            }
                            Room.State.RECONNECTING -> {
                                mutableConnectionInfoFlow.value = ConnectionInfoState.ReConnecting
                            }
                        }
                    }
                }

                viewModelScope.launch {
                    room.events.collect { event ->
                        videoCallClient.logger.debug(
                            "new event in room ${room.name}: ${event.javaClass.simpleName}",
                            domain = VIDEO_CALL_DOMAIN
                        )
                        when (event) {
                            is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
                            is RoomEvent.TrackUnsubscribed -> onTrackUnsubscribed(event)
                            is RoomEvent.TrackMuted -> {
                                (event.participant as? LocalParticipant)?.updateControlsState()
                            }
                            is RoomEvent.TrackUnmuted -> {
                                (event.participant as? LocalParticipant)?.updateControlsState()
                            }
                            else -> {
                                /* Room events for state like reconnecting are buggy, prefer using room::state for handling state.
                                See https://github.com/livekit/client-sdk-android/issues/105#issuecomment-1250796210 */
                            }
                        }
                    }
                }
            }.onFailure {
                videoCallClient.logger.warn("Failed to join room", domain = VIDEO_CALL_DOMAIN)
                mutableFinishFlow.emit(FinishReason.UnableToConnect)
            }
        }
    }

    private fun LocalParticipant.updateControlsState() {
        ControlsState(
            micEnabled = isMicrophoneEnabled(),
            cameraEnabled = isCameraEnabled(),
        )
    }

    fun onPermissionsRefused() {
        mutableFinishFlow.emitIn(viewModelScope, FinishReason.PermissionsRefused)
    }

    private fun onTrackSubscribed(trackSubscribed: RoomEvent.TrackSubscribed) {
        (trackSubscribed.track as? VideoTrack)?.let { videoTrack ->
            mutableRemoteParticipantFlow.value = videoTrack
        }
    }

    private fun onTrackUnsubscribed(trackUnsubscribed: RoomEvent.TrackUnsubscribed) {
        (trackUnsubscribed.track as? VideoTrack)?.let {
            mutableRemoteParticipantFlow.value = null
        }
    }

    fun onMicClicked() {
        mutableConnectedRoomFlow.value?.localParticipant?.let {
            viewModelScope.launch {
                it.setMicrophoneEnabled(!it.isMicrophoneEnabled())
                mutableControlsFlow.value = mutableControlsFlow.value?.copy(micEnabled = it.isMicrophoneEnabled())
            }
        }
    }

    fun onCameraClicked() {
        mutableConnectedRoomFlow.value?.localParticipant?.let {
            viewModelScope.launch {
                it.setCameraEnabled(!it.isCameraEnabled())
                mutableControlsFlow.value = mutableControlsFlow.value?.copy(cameraEnabled = it.isCameraEnabled())
            }
        }
    }

    fun onFlipCameraClicked() {
        mutableLocalParticipantFlow.value?.let { videoTrack ->

            val newOptions = when (videoTrack.options.position) {
                CameraPosition.FRONT -> LocalVideoTrackOptions(position = CameraPosition.BACK)
                CameraPosition.BACK -> LocalVideoTrackOptions(position = CameraPosition.FRONT)
                null -> LocalVideoTrackOptions()
            }

            videoTrack.restartTrack(newOptions)

            mutableIsSelfMirrorFlow.value = newOptions.position == CameraPosition.FRONT
        }
    }

    fun onFullscreenClicked() {
        val controls = mutableControlsFlow.value
        mutableControlsFlow.value = controls?.copy(controlsVisible = !controls.controlsVisible)
    }

    fun onHangUpClicked() {
        hangUp()
    }

    private fun hangUp() {
        mutableConnectedRoomFlow.value?.disconnect()
        mutableFinishFlow.emitIn(viewModelScope, FinishReason.CallEnded)
    }

    fun onPictureInPictureChanged(inPictureInPictureMode: Boolean) {
        mutableIsPictureInPictureFlow.value = inPictureInPictureMode
    }

    fun onPictureInPictureDismissed() {
        hangUp()
    }

    override fun onCleared() {
        mutableConnectedRoomFlow.value?.disconnect()
        super.onCleared()
    }

    sealed interface VideoState {

        sealed interface SelfVideo : VideoState {
            val selfTrack: LocalVideoTrack
            val isSelfMirror: Boolean
        }

        sealed interface RemoteVideo : VideoState {
            val remoteTrack: VideoTrack
        }

        object None : VideoState

        class SelfOnly(
            override val selfTrack: LocalVideoTrack,
            override val isSelfMirror: Boolean,
        ) : VideoState, SelfVideo

        class RemoteOnly(
            override val remoteTrack: VideoTrack,
        ) : VideoState, RemoteVideo

        class Both(
            override val selfTrack: LocalVideoTrack,
            override val remoteTrack: VideoTrack,
            override val isSelfMirror: Boolean,
        ) : VideoState, SelfVideo, RemoteVideo
    }

    data class ControlsState(
        val micEnabled: Boolean,
        val cameraEnabled: Boolean,
        val controlsVisible: Boolean = true,
    )

    sealed class ErrorAlert(@StringRes val errorMessageRes: Int) {
        object FailedToJoin : ErrorAlert(R.string.nabla_video_call_error_failed_to_join)
    }

    enum class ConnectionInfoState {
        Connecting,
        Connected,
        ReConnecting,
    }

    enum class FinishReason {
        CallEnded,
        PermissionsRefused,
        UnableToConnect,
    }

    companion object {
        internal const val VIDEO_CALL_DOMAIN = "VideoCall"
    }
}
