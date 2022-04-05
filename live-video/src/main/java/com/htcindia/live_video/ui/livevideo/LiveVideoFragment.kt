package com.htcindia.live_video.ui.livevideo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.htcindia.live_video.data.model.VideoTrackModel
import com.htcindia.live_video.databinding.FragmentLiveVideoBinding
import com.htcindia.live_video.databinding.LayoutDropDownBinding
import com.htcindia.live_video.ui.adapter.ParticipantsAdapter
import com.htcindia.live_video.ui.adapter.ThumbnailAdapter
import com.htcindia.live_video.R
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import com.twilio.video.*
import com.twilio.video.ktx.Video
import com.twilio.video.ktx.createLocalAudioTrack
import com.twilio.video.ktx.createLocalVideoTrack
import com.twilio.video.ktx.enabled
import tvi.webrtc.VideoSink
import kotlin.properties.Delegates

class LiveVideoFragment(private var accessToken:String, var roomName: String) : Fragment() {

    companion object {

        const val PREF_AUDIO_CODEC_DEFAULT = OpusCodec.NAME
        const val PREF_VIDEO_CODEC_DEFAULT = Vp8Codec.NAME
        const val PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT = "0"
        const val PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT = "0"
        const val PREF_VP8_SIMULCAST_DEFAULT = false
        const val PREF_ENABLE_AUTOMATIC_SUBCRIPTION_DEFAULT = true

    }

    private var tokenExpireRetryCount = 0

    lateinit var adapter: ThumbnailAdapter
    lateinit var participantsAdapter: ParticipantsAdapter

    private val CAMERA_MIC_PERMISSION_REQUEST_CODE = 1
    private val CAMERA_PERMISSION_INDEX = 0
    private val MIC_PERMISSION_INDEX = 1

    private var room: Room? = null
    private var localParticipant: LocalParticipant? = null
    private var cameraFlag: Boolean? = null
    private var cameraBtnFlag: Boolean = false
    var hasLocalParticipant = false
    var participantList = mutableListOf<RemoteParticipant>()

    private var savedVolumeControlStream by Delegates.notNull<Int>()

    private var participantIdentity: String? = null
    private lateinit var localVideoView: VideoSink
    private var disconnectedFromOnDestroy = false

    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
    private var tempVideoTrack: VideoTrackModel? = null

    private var dropDownBuilder: Dialog? = null
    private var dropDownDialogBinding: LayoutDropDownBinding? = null

    private var _binding: FragmentLiveVideoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveVideoBinding.inflate(inflater, container, false)

        adapter = ThumbnailAdapter(requireContext()) { selectedItem: VideoTrackModel ->
            listItemClicked(selectedItem)
        }
        adapter.setHasStableIds(true)
        binding.rvThumbnail.adapter = adapter

        participantsAdapter = ParticipantsAdapter(requireContext())

        dropDownDialogBinding =
            LayoutDropDownBinding.inflate(LayoutInflater.from(requireActivity()))
        dropDownBuilder = Dialog(requireActivity(), R.style.list_dialog_style)
        dropDownBuilder!!.setContentView(dropDownDialogBinding!!.root)

        localVideoView = binding.primaryVideoView
        savedVolumeControlStream = requireActivity().volumeControlStream
        requireActivity().volumeControlStream = AudioManager.STREAM_VOICE_CALL

//        liveVideoViewModel.getLiveVideoToken(
//            TwilioRequest(
//                identity = args.identity
//            )
//        )
//        callTwilioKeyObserver()
        createAudioAndVideoTracks()

        connectToRoom(roomName)

        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraMicrophoneAndBluetooth()
        }

        binding.ivVideo.setOnClickListener {
            if (!hasLocalParticipant) {
                val view = binding.rvThumbnail.findViewHolderForAdapterPosition(0)?.itemView
                val thumbnailView = view?.findViewById<VideoView>(R.id.thumbnailVideoView)
                localVideoTrack?.let {
                    val enable = !it.isEnabled
                    it.enable(enable)
                    val icon: Int
                    if (enable) {
                        icon = R.drawable.ic_videowhite
                        thumbnailView?.background = null
                        localVideoTrack?.addSink(thumbnailView!!)

                        view?.findViewById<ImageView>(R.id.iv_video)?.visibility = View.GONE
                        binding.apply {
                            localAudioTrack?.let { track ->
                                val enableAudio = track.isEnabled
                                val icon1 = if (enableAudio)
                                    R.drawable.ic_audioonwhite
                                else
                                    R.drawable.ic_audiooffwhite
                                view?.findViewById<TextView>(R.id.tv_name)
                                    ?.setCompoundDrawablesWithIntrinsicBounds(icon1, 0, 0, 0)

                            }
                        }
                        cameraBtnFlag = true
                    } else {
                        icon = R.drawable.ic_videooffwhite
                        thumbnailView?.setBackgroundColor(
                            ContextCompat.getColor(requireContext(),
                                R.color.bottomSheetBg))
                        view?.findViewById<ImageView>(R.id.iv_video)?.visibility = View.VISIBLE
                        binding.apply {
                            localAudioTrack?.let { track ->
                                val enableAudio = track.isEnabled
                                val icon1 = if (enableAudio)
                                    R.drawable.ic_audioonwhite
                                else
                                    R.drawable.ic_audiooffwhite
                                view?.findViewById<TextView>(R.id.tv_name)
                                    ?.setCompoundDrawablesWithIntrinsicBounds(icon1, 0, 0, 0)

                            }
                        }
                        cameraBtnFlag = false
                    }
                    binding.ivVideo.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), icon)
                    )
                }
            } else {
                localVideoTrack?.let {
                    val enable = !it.isEnabled
                    it.enable(enable)
                    val icon: Int
                    if (enable) {
                        icon = R.drawable.ic_videowhite
                        binding.primaryVideoView.background = null
                        binding.apply {
                            tvAgentName.visibility = View.GONE
                            tvAgentYou.visibility = View.GONE
                            ivAudioMode.visibility = View.GONE
                            localAudioTrack?.let { track ->
                                val enableAudio = track.isEnabled
                                val icon1 = if (enableAudio)
                                    R.drawable.ic_audioonwhite
                                else
                                    R.drawable.ic_audiooffwhite
                                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                                    icon1,
                                    0,
                                    0,
                                    0
                                )
                                binding.ivAudioMode.setImageDrawable(
                                    ContextCompat.getDrawable(
                                        requireContext(), icon
                                    )
                                )
                            }

                            tvTopInfo.visibility = View.VISIBLE
                        }
                        cameraBtnFlag = true
                    } else {
                        icon = R.drawable.ic_videooffwhite
                        binding.primaryVideoView.setBackgroundColor(
                            ContextCompat.getColor(requireContext(),
                                R.color.bottomSheetBg))
                        binding.apply {
                            tvAgentName.visibility = View.VISIBLE
                            localAudioTrack?.let { track ->
                                val enableAudio = track.isEnabled
                                val icon1 = if (enableAudio)
                                    R.drawable.ic_audioonwhite
                                else
                                    R.drawable.ic_audiooffwhite
                                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                                    icon1,
                                    0,
                                    0,
                                    0
                                )
                                binding.ivAudioMode.setImageDrawable(
                                    ContextCompat.getDrawable(
                                        requireContext(), icon
                                    )
                                )
                            }
                            tvAgentYou.visibility = View.VISIBLE
                            ivAudioMode.visibility = View.VISIBLE
                            tvTopInfo.visibility = View.GONE
                        }
                        cameraBtnFlag = false
                    }
                    binding.ivVideo.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), icon)
                    )
                }
            }
        }

        binding.ivCamera.setOnClickListener {
            val cameraSource = cameraCapturerCompat.cameraSource
            cameraCapturerCompat.switchCamera()
            binding.primaryVideoView.mirror =
                cameraSource == CameraCapturerCompat.Source.BACK_CAMERA
        }

        binding.ivAudio.setOnClickListener {
            if (!hasLocalParticipant) {
                val view = binding.rvThumbnail.findViewHolderForAdapterPosition(0)?.itemView
                val textView = view?.findViewById<TextView>(R.id.tv_name)
                localAudioTrack?.let { track ->
                    val enable = !track.isEnabled
                    track.enable(enable)
                    val icon = if (enable)
                        R.drawable.ic_audioonwhite
                    else
                        R.drawable.ic_audiooffwhite
                    binding.ivAudio.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(), icon
                        )
                    )
                    textView?.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                }

            } else {
                localAudioTrack?.let { track ->
                    val enable = !track.isEnabled
                    track.enable(enable)
                    val icon = if (enable)
                        R.drawable.ic_audioonwhite
                    else
                        R.drawable.ic_audiooffwhite
                    binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                    binding.ivAudio.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(), icon
                        )
                    )
                    binding.ivAudioMode.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(), icon
                        )
                    )
                }

            }
        }

        binding.btnLiveStreamBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.apply {
            ivAudio.isClickable = false
            ivVideo.isClickable = false
            ivCamera.isClickable = false
            ivParticipants.isEnabled = false
        }

        binding.ivParticipants.setOnClickListener {
            openParticipantDialog(participantList)
        }

        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    fun listItemClicked(videoTrack: VideoTrackModel) {
        adapter.removeParticipant(videoTrack)
        binding.primaryVideoView.setBackgroundColor(
            ContextCompat.getColor(requireContext(),
                R.color.bottomSheetBg))

        if (hasLocalParticipant) {
            localVideoTrack!!.removeSink(binding.primaryVideoView)
            binding.primaryVideoView.background = null
            binding.primaryVideoView.mirror = false
            if (!videoTrack.hasInternet.equals(NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO)) {
                if (!videoTrack.videoTrack.isEnabled) {
                    binding.tvAgentName.visibility = View.VISIBLE
                    binding.ivAudioMode.visibility = View.VISIBLE
                    binding.tvAgentName.text = getUserName(videoTrack.identity)
                    binding.tvAgentYou.visibility = View.GONE
                    binding.tvTopInfo.text = getUserName(videoTrack.identity)
                    binding.tvTopInfo.visibility = View.GONE
                    binding.primaryVideoView.setBackgroundColor(
                        ContextCompat.getColor(requireContext(),
                            R.color.bottomSheetBg))
                } else {
                    binding.ivAudioMode.visibility = View.GONE
                    binding.tvAgentName.visibility = View.GONE
                    binding.tvTopInfo.visibility = View.VISIBLE
                    binding.tvAgentName.text = getUserName(videoTrack.identity)
                    binding.tvAgentYou.visibility = View.GONE
                    binding.tvTopInfo.text = getUserName(videoTrack.identity)
                    binding.primaryVideoView.background = null
                    videoTrack.videoTrack.addSink(binding.primaryVideoView)
                }
            } else {
                binding.tvAgentName.visibility = View.VISIBLE
                binding.ivAudioMode.visibility = View.VISIBLE
                binding.tvTopInfo.visibility = View.GONE
                binding.primaryVideoView.setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                        R.color.bottomSheetBg))
                binding.tvAgentName.text = "${getUserName(videoTrack.identity)} lost connection.."
            }

            adapter.addParticipant(
                VideoTrackModel(
                    videoTrack = localVideoTrack!!,
                    videoMode = localVideoTrack!!.isEnabled,
                    audioMode = localAudioTrack!!.isEnabled,
                    identity = getUserName(localParticipant!!.identity),
                    isLocalParticipant = true,
                    mParticipant = localParticipant!!,
                    sid = localParticipant!!.sid,
                    hasInternet = localParticipant!!.networkQualityLevel
                )
            )

            if (getAudioStatus(videoTrack.mParticipant)) {
                binding.ivAudioMode.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_audioonwhite)
                )
                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_audioonwhite,
                    0,
                    0,
                    0
                )

            } else {
                binding.ivAudioMode.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_audiooffwhite)
                )
                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_audiooffwhite,
                    0,
                    0,
                    0
                )

            }
            hasLocalParticipant = false
            tempVideoTrack = videoTrack
        } else {
            if (videoTrack.isLocalParticipant) {
                tempVideoTrack?.videoTrack?.removeSink(binding.primaryVideoView)
                binding.primaryVideoView.background = null
                if (!videoTrack.videoTrack.isEnabled) {
                    binding.tvAgentName.visibility = View.VISIBLE
                    binding.tvAgentYou.visibility = View.VISIBLE
                    binding.ivAudioMode.visibility = View.VISIBLE
                    binding.primaryVideoView.setBackgroundColor(
                        ContextCompat.getColor(requireContext(),
                            R.color.bottomSheetBg))
                    binding.tvTopInfo.visibility = View.GONE

                } else {
                    binding.tvAgentName.visibility = View.GONE
                    binding.tvAgentYou.visibility = View.GONE
                    binding.tvTopInfo.visibility = View.VISIBLE
                    binding.ivAudioMode.visibility = View.GONE
                    binding.primaryVideoView.background = null
                }
                videoTrack.videoTrack.addSink(binding.primaryVideoView)
                binding.tvAgentName.text = getUserName(videoTrack.identity)
                binding.tvTopInfo.text = getUserName(videoTrack.identity)

                adapter.addParticipant(
                    VideoTrackModel(
                        videoTrack = tempVideoTrack!!.videoTrack,
                        videoMode = tempVideoTrack!!.videoTrack.isEnabled,
                        audioMode = getAudioStatus(tempVideoTrack!!.mParticipant),
                        identity = getUserName(tempVideoTrack!!.identity),
                        isLocalParticipant = false,
                        mParticipant = tempVideoTrack!!.mParticipant,
                        hasInternet = tempVideoTrack!!.mParticipant.networkQualityLevel,
                        sid = tempVideoTrack!!.sid
                    )
                )

                if (getAudioStatus(videoTrack.mParticipant)) {
                    binding.ivAudioMode.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_audioonwhite)
                    )
                    binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_audioonwhite,
                        0,
                        0,
                        0
                    )

                } else {
                    binding.ivAudioMode.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_audiooffwhite)
                    )
                    binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_audiooffwhite,
                        0,
                        0,
                        0
                    )

                }
                hasLocalParticipant = true
            } else {
                tempVideoTrack?.videoTrack?.removeSink(binding.primaryVideoView)
                binding.primaryVideoView.background = null
                if (!videoTrack.hasInternet.equals(NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO)) {
                    binding.tvAgentName.text = getUserName(videoTrack.identity)
                    binding.tvTopInfo.text = getUserName(videoTrack.identity)
                    if (!videoTrack.videoTrack.isEnabled) {
                        binding.tvAgentName.visibility = View.VISIBLE
                        binding.ivAudioMode.visibility = View.VISIBLE
                        binding.primaryVideoView.setBackgroundColor(
                            ContextCompat.getColor(requireContext(),
                                R.color.bottomSheetBg))
                        binding.tvTopInfo.visibility = View.GONE
                    } else {
                        binding.tvAgentName.visibility = View.GONE
                        binding.tvTopInfo.visibility = View.VISIBLE
                        binding.ivAudioMode.visibility = View.GONE
                        binding.primaryVideoView.background = null
                        videoTrack.videoTrack.addSink(binding.primaryVideoView)

                    }
                } else {
                    binding.tvAgentName.visibility = View.VISIBLE
                    binding.ivAudioMode.visibility = View.VISIBLE
                    binding.primaryVideoView.setBackgroundColor(
                        ContextCompat.getColor(requireContext(),
                            R.color.bottomSheetBg))
                    binding.tvTopInfo.visibility = View.GONE
                    binding.tvAgentName.text =
                        getUserName(videoTrack.identity) + " lost connection..."

                }
                adapter.addParticipant(
                    VideoTrackModel(
                        videoTrack = tempVideoTrack!!.videoTrack,
                        videoMode = tempVideoTrack!!.videoTrack.isEnabled,
                        audioMode = getAudioStatus(tempVideoTrack!!.mParticipant),
                        identity = getUserName(tempVideoTrack!!.identity),
                        isLocalParticipant = false,
                        mParticipant = tempVideoTrack!!.mParticipant,
                        hasInternet = tempVideoTrack!!.mParticipant.networkQualityLevel,
                        sid = tempVideoTrack!!.sid

                    )
                )



                if (getAudioStatus(videoTrack.mParticipant)) {
                    binding.ivAudioMode.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_audioonwhite)
                    )
                    binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_audioonwhite,
                        0,
                        0,
                        0
                    )

                } else {
                    binding.ivAudioMode.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_audiooffwhite)
                    )
                    binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_audiooffwhite,
                        0,
                        0,
                        0
                    )

                }
                hasLocalParticipant = false
            }
            tempVideoTrack = videoTrack
        }
        adapter.notifyDataSetChanged()
    }

    fun getAudioStatus(participant: Participant): Boolean {
        var flag = false
        participant.audioTracks.forEach {
            flag = it.audioTrack?.isEnabled!!

        }
        return flag
    }

//    private val audioSwitch by lazy {
//        AudioSwitch(
//            requireContext(), preferredDeviceList = listOf(
//                AudioDevice.BluetoothHeadset::class.java,
//                AudioDevice.WiredHeadset::class.java,
//                AudioDevice.Speakerphone::class.java,
//                AudioDevice.Earpiece::class.java
//            )
//        )
//    }

    private val cameraCapturerCompat by lazy {
        CameraCapturerCompat(requireContext(), CameraCapturerCompat.Source.FRONT_CAMERA)
    }

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun openParticipantDialog(list:List<RemoteParticipant>) {
        dropDownBuilder!!.show()
        dropDownDialogBinding!!.tvDropDownTitle.text = "Participants (${list.size + 1})"
        dropDownDialogBinding!!.btnIncidentCategorySearch.visibility = View.GONE
        dropDownDialogBinding!!.rclrViewIncidentCategory.setHasFixedSize(true)
        dropDownDialogBinding!!.rclrViewIncidentCategory.adapter = participantsAdapter
        dropDownDialogBinding!!.btnIncidentCategoryBack.setOnClickListener {
            dropDownBuilder!!.dismiss()
        }
        participantsAdapter.resetCount()
        participantsAdapter.setParticipantList(list,localParticipant!!)
        participantsAdapter.notifyDataSetChanged()
    }

    private val roomListener = object : Room.Listener {
        @SuppressLint("SetTextI18n")
        override fun onConnected(room: Room) {
            localParticipant = room.localParticipant
            binding.tvTopInfo.text = getUserName(room.localParticipant?.identity!!).plus(" (You)")
            binding.tvAgentName.text = getUserName(room.localParticipant?.identity!!)
            binding.primaryVideoView.mirror = cameraCapturerCompat.cameraSource ==
                    CameraCapturerCompat.Source.FRONT_CAMERA
            localVideoTrack?.addSink(binding.primaryVideoView)
            hasLocalParticipant = true
            participantList.clear()
            participantList.addAll(room.remoteParticipants)
            binding.tvParticipantsCount.text = "${room.remoteParticipants.size + 1}"
            binding.apply {
                tvAgentYou.visibility = View.VISIBLE
                ivAudio.isClickable = true
                ivVideo.isClickable = true
                ivCamera.isClickable = true
                ivParticipants.isEnabled = true
            }
            participantsAdapter.setParticipantList(room.remoteParticipants,localParticipant!!)

            room.remoteParticipants.forEach {
                binding.tvWaiting.visibility = View.GONE
                addRemoteParticipant(it)
            }

        }

        override fun onReconnected(room: Room) {

        }

        override fun onReconnecting(room: Room, twilioException: TwilioException) {

        }

        override fun onConnectFailure(room: Room, e: TwilioException) {
            Toast.makeText(requireActivity(), "Connection Failed", Toast.LENGTH_SHORT).show()
//            audioSwitch.deactivate()
            requireActivity().onBackPressed()
        }

        override fun onDisconnected(room: Room, e: TwilioException?) {
            localParticipant = null
            this@LiveVideoFragment.room = null
            // Only reinitialize the UI if disconnect was not called from onDestroy()
            if (!disconnectedFromOnDestroy) {
//                audioSwitch.deactivate()
                requireActivity().onBackPressed()

            }
        }

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        override fun onParticipantConnected(room: Room, participant: RemoteParticipant) {
            binding.tvWaiting.visibility = View.GONE
            binding.tvParticipantsCount.text = "${room.remoteParticipants.size + 1}"
            participantList.clear()
            participantList.addAll(room.remoteParticipants)
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(room.remoteParticipants,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()
            binding.apply {
                tvParticipant.visibility = View.VISIBLE
                tvParticipant.text = "${getUserName(participant.identity)} joined"
                tvParticipant.postDelayed({
                    tvParticipant.visibility = View.GONE
                },2000)
            }

            dropDownDialogBinding!!.tvDropDownTitle.text = "Participants (${room.remoteParticipants.size + 1})"
            addRemoteParticipant(participant)
        }

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        override fun onParticipantDisconnected(room: Room, participant: RemoteParticipant) {
            removeRemoteParticipant(participant)
            dropDownDialogBinding!!.tvDropDownTitle.text = "Participants (${room.remoteParticipants.size + 1})"
            binding.tvParticipantsCount.text = "${room.remoteParticipants.size + 1}"
            participantList.clear()
            participantList.addAll(room.remoteParticipants)
            binding.apply {
                tvParticipant.visibility = View.VISIBLE
                tvParticipant.text = "${getUserName(participant.identity)} left"
                tvParticipant.postDelayed({
                    tvParticipant.visibility = View.GONE
                },2000)
            }
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(room.remoteParticipants,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()
            if (adapter.itemCount == 0) {
                binding.tvWaiting.visibility = View.VISIBLE
            }
        }

        override fun onRecordingStarted(room: Room) {
        }

        override fun onRecordingStopped(room: Room) {
        }
    }

    private val participantListener = object : RemoteParticipant.Listener {
        override fun onAudioTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {


        }

        override fun onAudioTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {

        }

        override fun onDataTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication
        ) {


        }

        override fun onDataTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication
        ) {


        }

        override fun onVideoTrackPublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {

        }

        override fun onVideoTrackUnpublished(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {

        }

        override fun onAudioTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack
        ) {


        }

        override fun onAudioTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            remoteAudioTrack: RemoteAudioTrack
        ) {

        }

        override fun onAudioTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication,
            twilioException: TwilioException
        ) {

        }

        override fun onDataTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack
        ) {

        }

        override fun onDataTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            remoteDataTrack: RemoteDataTrack
        ) {

        }

        override fun onDataTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteDataTrackPublication: RemoteDataTrackPublication,
            twilioException: TwilioException
        ) {

        }

        override fun onVideoTrackSubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack
        ) {
            var audioMode = false

            remoteParticipant.remoteAudioTracks.forEach {
                if (it.isTrackSubscribed) {
                    it.remoteAudioTrack?.let { track ->
                        audioMode = it.isTrackEnabled
                    }
                }
            }

            addRemoteParticipantVideo(
                remoteVideoTrack,
                remoteVideoTrack.isEnabled,
                audioMode,
                getUserName(remoteParticipant.identity),
                remoteParticipant
            )
        }

        override fun onVideoTrackUnsubscribed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            remoteVideoTrack: RemoteVideoTrack
        ) {
            var audioMode = false

            remoteParticipant.remoteAudioTracks.forEach {
                if (it.isTrackSubscribed) {
                    it.remoteAudioTrack?.let { track ->
                        audioMode = it.isTrackEnabled
                    }
                }
            }

            if (localVideoTrack != null) {
                removeParticipantVideo(
                    remoteVideoTrack,
                    remoteVideoTrack.isEnabled,
                    audioMode,
                    getUserName(remoteParticipant.identity),
                    remoteParticipant
                )
            }
        }

        override fun onVideoTrackSubscriptionFailed(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication,
            twilioException: TwilioException
        ) {

        }

        @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
        override fun onNetworkQualityLevelChanged(
            remoteParticipant: RemoteParticipant,
            networkQualityLevel: NetworkQualityLevel
        ) {

            if (networkQualityLevel.equals(NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO)) {
                if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                    binding.tvAgentName.visibility = View.VISIBLE
                    binding.ivAudioMode.visibility = View.VISIBLE
                    binding.tvTopInfo.visibility = View.GONE
                    binding.tvAgentYou.visibility = View.GONE
                    binding.tvAgentName.text =
                        "${getUserName(tempVideoTrack!!.identity)} lost connection..."
                    binding.primaryVideoView.setBackgroundColor(
                        ContextCompat.getColor(requireContext(),
                            R.color.bottomSheetBg))
                    tempVideoTrack!!.hasInternet = networkQualityLevel
                } else {
                    adapter.changeInternetStatus(remoteParticipant.sid, networkQualityLevel)
                    adapter.notifyDataSetChanged()
                }
            } else {
                if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                    binding.tvAgentName.visibility = View.GONE
                    binding.ivAudioMode.visibility = View.GONE
                    binding.tvTopInfo.visibility = View.VISIBLE
                    binding.tvTopInfo.text = getUserName(tempVideoTrack!!.identity)
                    binding.tvAgentName.text = getUserName(tempVideoTrack!!.identity)
                    tempVideoTrack?.videoTrack?.addSink(binding.primaryVideoView)
                    binding.primaryVideoView.background = null
                    tempVideoTrack!!.hasInternet = networkQualityLevel
                } else {
                    adapter.changeInternetStatus(remoteParticipant.sid, networkQualityLevel)
                    adapter.notifyDataSetChanged()
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onAudioTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {

            if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                binding.ivAudioMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_audioonwhite
                    )
                )
                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_audioonwhite,
                    0,
                    0,
                    0
                )
            } else {
                adapter.changeAudioStatus(remoteParticipant.sid, true)
                adapter.notifyDataSetChanged()
            }
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(participantList,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()

        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onVideoTrackEnabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {
            if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                binding.tvAgentName.visibility = View.GONE
                binding.ivAudioMode.visibility = View.GONE
                binding.tvTopInfo.visibility = View.VISIBLE
                binding.tvTopInfo.text = getUserName(tempVideoTrack!!.identity)
                binding.tvAgentName.text = getUserName(tempVideoTrack!!.identity)
                tempVideoTrack?.videoTrack?.addSink(binding.primaryVideoView)
                binding.primaryVideoView.background = null
            } else {
                adapter.changeVideoStatus(remoteParticipant.sid, true)
                adapter.notifyDataSetChanged()
            }
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(participantList,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()

        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onVideoTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteVideoTrackPublication: RemoteVideoTrackPublication
        ) {

            if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                binding.tvAgentName.visibility = View.VISIBLE
                binding.ivAudioMode.visibility = View.VISIBLE
                binding.tvTopInfo.visibility = View.GONE
                binding.primaryVideoView.setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                        R.color.bottomSheetBg))
            } else {
                adapter.changeVideoStatus(remoteParticipant.sid, false)
                adapter.notifyDataSetChanged()
            }
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(participantList,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()

        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onAudioTrackDisabled(
            remoteParticipant: RemoteParticipant,
            remoteAudioTrackPublication: RemoteAudioTrackPublication
        ) {

            if (tempVideoTrack != null && tempVideoTrack!!.sid.equals(remoteParticipant.sid)) {
                binding.ivAudioMode.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_audiooffwhite
                    )
                )
                binding.tvTopInfo.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_audiooffwhite,
                    0,
                    0,
                    0
                )

            } else {
                adapter.changeAudioStatus(remoteParticipant.sid, false)
                adapter.notifyDataSetChanged()
            }
            participantsAdapter.resetCount()
            participantsAdapter.setParticipantList(participantList,localParticipant!!)
            participantsAdapter.notifyDataSetChanged()

        }
    }

    private fun connectToRoom(roomName: String) {
//        audioSwitch.activate()

        val configuration = NetworkQualityConfiguration(
            NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL,
            NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL
        )

        room = Video.connect(requireContext(), accessToken, roomListener) {
            roomName(roomName)
            enableNetworkQuality(true)
            networkQualityConfiguration(configuration)
            region("gll")
            /*
             * Add local audio track to connect options to share with participants.
             */
            audioTracks(listOf(localAudioTrack))
            /*
             * Add local video track to connect options to share with participants.
             */
            videoTracks(listOf(localVideoTrack))

            /*
             * Set the preferred audio and video codec for media.
             */
            preferAudioCodecs(listOf(audioCodec))
            preferVideoCodecs(listOf(videoCodec))

            /*
             * Set the sender side encoding parameters.
             */
            encodingParameters(encodingParameters)

            /*
             * Toggles automatic track subscription. If set to false, the LocalParticipant will receive
             * notifications of track publish events, but will not automatically subscribe to them. If
             * set to true, the LocalParticipant will automatically subscribe to tracks as they are
             * published. If unset, the default is true. Note: This feature is only available for Group
             * Rooms. Toggling the flag in a P2P room does not modify subscription behavior.
             */
            enableAutomaticSubscription(PREF_ENABLE_AUTOMATIC_SUBCRIPTION_DEFAULT)
        }

        setDisconnectAction()
    }

    private fun addRemoteParticipant(remoteParticipant: RemoteParticipant) {
        participantIdentity = getUserName(remoteParticipant.identity)
        var audioMode = false

        remoteParticipant.remoteAudioTracks.forEach {
            if (it.isTrackSubscribed) {
                it.remoteAudioTrack?.let { track ->
                    audioMode = it.isTrackEnabled
                }
            }
        }
        remoteParticipant.remoteVideoTracks.forEach {
            if (it.isTrackSubscribed) {
                it.remoteVideoTrack?.let { track ->
                    addRemoteParticipantVideo(
                        track,
                        track.isEnabled,
                        audioMode,
                        getUserName(participantIdentity!!),
                        remoteParticipant
                    )
                }
            }
        }
        remoteParticipant.setListener(participantListener)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addRemoteParticipantVideo(
        videoTrack: VideoTrack,
        videoMode: Boolean,
        audioMode: Boolean,
        identity: String,
        mParticipant: RemoteParticipant
    ) {

        adapter.addParticipant(
            VideoTrackModel(
                videoTrack = videoTrack,
                videoMode = videoMode,
                audioMode = audioMode,
                identity = getUserName(identity),
                isLocalParticipant = false,
                mParticipant = mParticipant,
                sid = mParticipant.sid,
                hasInternet = mParticipant.networkQualityLevel
            )
        )
        adapter.notifyDataSetChanged()

    }

    private fun removeRemoteParticipant(remoteParticipant: RemoteParticipant) {
        participantIdentity = remoteParticipant.identity
        var audioMode = false

        remoteParticipant.remoteAudioTracks.forEach {
            if (it.isTrackSubscribed) {
                it.remoteAudioTrack?.let { track ->
                    audioMode = it.isTrackEnabled
                }
            }
        }
        remoteParticipant.remoteVideoTracks.forEach {
            if (it.isTrackSubscribed) {
                it.remoteVideoTrack?.let {track->
                    removeParticipantVideo(
                        track,
                        track.isEnabled,
                        audioMode,
                        getUserName(participantIdentity!!),
                        remoteParticipant
                    )
                }
            }
        }

    }

    fun getUserName(identity: String): String {
        return identity.substringBefore("(").trim()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun removeParticipantVideo(
        videoTrack: VideoTrack,
        videoMode: Boolean,
        audioMode: Boolean,
        identity: String,
        mParticipant: RemoteParticipant
    ) {
        if (tempVideoTrack?.sid.equals(mParticipant.sid)) {
            tempVideoTrack!!.videoTrack.removeSink(binding.primaryVideoView)
            adapter.removeParticipant(
                VideoTrackModel(
                    videoTrack = localVideoTrack!!,
                    videoMode = localVideoTrack!!.isEnabled,
                    audioMode = localAudioTrack!!.isEnabled,
                    identity = getUserName(localParticipant!!.identity),
                    isLocalParticipant = true,
                    mParticipant = localParticipant!!,
                    sid = localParticipant!!.sid,
                    hasInternet = localParticipant!!.networkQualityLevel
                )
            )

            if (!localVideoTrack!!.isEnabled) {
                binding.tvAgentName.visibility = View.VISIBLE
                binding.tvAgentYou.visibility = View.VISIBLE
                binding.ivAudioMode.visibility = View.VISIBLE
                binding.primaryVideoView.setBackgroundColor(
                    ContextCompat.getColor(requireContext(),
                        R.color.bottomSheetBg))
                binding.tvTopInfo.visibility = View.GONE

            } else {
                binding.tvAgentName.visibility = View.GONE
                binding.tvAgentYou.visibility = View.GONE
                binding.tvTopInfo.visibility = View.VISIBLE
                binding.ivAudioMode.visibility = View.GONE
                binding.primaryVideoView.background = null
            }

            localVideoTrack?.addSink(binding.primaryVideoView)
            binding.tvAgentName.text = getUserName(localParticipant!!.identity)
            binding.tvTopInfo.text = getUserName(localParticipant!!.identity)
            hasLocalParticipant = true
            tempVideoTrack = VideoTrackModel(
                videoTrack = localVideoTrack!!,
                videoMode = localVideoTrack!!.isEnabled,
                audioMode = localAudioTrack!!.isEnabled,
                identity = getUserName(localParticipant!!.identity),
                isLocalParticipant = true,
                mParticipant = localParticipant!!,
                sid = localParticipant!!.sid,
                hasInternet = localParticipant!!.networkQualityLevel
            )
        } else {
            adapter.removeParticipant(
                VideoTrackModel(
                    videoTrack = videoTrack,
                    videoMode = videoMode,
                    audioMode = audioMode,
                    identity = getUserName(identity),
                    isLocalParticipant = false,
                    mParticipant = mParticipant,
                    sid = mParticipant.sid,
                    hasInternet = mParticipant.networkQualityLevel
                )
            )
        }
        adapter.notifyDataSetChanged()

    }

    private fun setDisconnectAction() {
        binding.ivCallEnd.setOnClickListener(disconnectClickListener())
    }

    private fun disconnectClickListener(): View.OnClickListener {
        return View.OnClickListener {
            requireActivity().onBackPressed()
        }
    }

//    private fun callTwilioKeyObserver() {
//        liveVideoViewModel.liveVideoToken.observe(viewLifecycleOwner, {
//            if (it != null) {
//                when (it.status) {
//                    Status.SUCCESS -> {
//                        if (it.data != null) {
//                            tokenExpireRetryCount = 0
//                            accessToken = it.data.token!!
//                            Log.d("callTwilioKeyObserver: ",accessToken)
//                            connectToRoom("htcindia")
//                            success()
//                        }
//                    }
//                    Status.ERROR -> {
//
//                    }
//                    Status.LOADING -> {
//                        loading()
//                    }
//                }
//            }
//        })
//    }

    fun loading() {
        binding.clProgressBar.isVisible = true

    }

    fun success() {
        binding.clProgressBar.isVisible = false

    }

    fun error(message: String) {
        binding.clProgressBar.isVisible = false

    }

    private val audioCodec: AudioCodec
        get() {
            val audioCodecName = PREF_AUDIO_CODEC_DEFAULT

            return when (audioCodecName) {
                IsacCodec.NAME -> IsacCodec()
                OpusCodec.NAME -> OpusCodec()
                PcmaCodec.NAME -> PcmaCodec()
                PcmuCodec.NAME -> PcmuCodec()
                G722Codec.NAME -> G722Codec()
                else -> OpusCodec()
            }
        }

    private val videoCodec: VideoCodec
        get() {

            return when (PREF_VIDEO_CODEC_DEFAULT) {
                Vp8Codec.NAME -> {
                    val simulcast = PREF_VP8_SIMULCAST_DEFAULT
                    Vp8Codec(simulcast)
                }
                H264Codec.NAME -> H264Codec()
                Vp9Codec.NAME -> Vp9Codec()
                else -> Vp8Codec()
            }
        }

    private val encodingParameters: EncodingParameters
        get() {
            val maxAudioBitrate = Integer.parseInt(
                PREF_SENDER_MAX_AUDIO_BITRATE_DEFAULT
            )
            val maxVideoBitrate = Integer.parseInt(
                PREF_SENDER_MAX_VIDEO_BITRATE_DEFAULT
            )

            return EncodingParameters(maxAudioBitrate, maxVideoBitrate)
        }

    private fun checkPermissionForCameraAndMicrophone(): Boolean {
        return checkPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    private fun requestPermissionForCameraMicrophoneAndBluetooth() {
        val permissionsList: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
        requestPermissions(permissionsList, 101)
    }

    private fun checkPermissions(permissions: Array<String>): Boolean {
        var shouldCheck = true
        for (permission in permissions) {
            shouldCheck = shouldCheck and (PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(requireContext(), permission))
        }
        return shouldCheck
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            /*
             * The first two permissions are Camera & Microphone, bluetooth isn't required but
             * enabling it enables bluetooth audio routing functionality.
             */
            val cameraAndMicPermissionGranted =
                ((PackageManager.PERMISSION_GRANTED == grantResults[CAMERA_PERMISSION_INDEX])
                        and (PackageManager.PERMISSION_GRANTED == grantResults[MIC_PERMISSION_INDEX]))

            /*
             * Due to bluetooth permissions being requested at the same time as camera and mic
             * permissions, AudioSwitch should be started after providing the user the option
             * to grant the necessary permissions for bluetooth.
             */


            if (cameraAndMicPermissionGranted) {
                createAudioAndVideoTracks()
            } else {
                Toast.makeText(
                    requireContext(),
                    "camera needed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createAudioAndVideoTracks() {
        // Share your microphone
        localAudioTrack = createLocalAudioTrack(requireContext(), false)

        // Share your camera
        localVideoTrack = createLocalVideoTrack(
            requireContext(),
            false,
            cameraCapturerCompat
        )
    }

    override fun onDestroy() {
//        audioSwitch.stop()
        requireActivity().volumeControlStream = savedVolumeControlStream
        room?.disconnect()
        disconnectedFromOnDestroy = true
        localAudioTrack?.release()
        localVideoTrack?.release()
        localVideoTrack = null

        super.onDestroy()
//        twilioViewModel.stopLiveJob()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dropDownBuilder?.dismiss()
        dropDownDialogBinding?.rclrViewIncidentCategory!!.adapter = null
        dropDownBuilder = null
        dropDownDialogBinding = null
        binding.rvThumbnail.adapter = null
        _binding = null
    }

    override fun onPause() {

        cameraFlag = localVideoTrack!!.isEnabled
        localVideoTrack.let {
            it?.enabled = false
        }
        super.onPause()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()

        localParticipant?.setEncodingParameters(encodingParameters)
        if (cameraFlag != null) {
            localVideoTrack?.let {
                it.enabled = cameraFlag!!
            }
        }
        if (tempVideoTrack != null) {
            if (!tempVideoTrack!!.sid.equals(localParticipant?.sid)) {
                adapter.changeVideoStatus(localParticipant!!.sid, cameraFlag!!)
                adapter.notifyDataSetChanged()
            } else {
                localVideoTrack?.addSink(binding.primaryVideoView)
            }
        } else {
            localVideoTrack?.addSink(binding.primaryVideoView)
        }
    }

//    private fun liveVideoTokenObserver() {
//        liveVideoViewModel.liveVideoToken.observe(viewLifecycleOwner) {
//            if(it != null) {
//                when(it.status) {
//                    Status.LOADING -> {
//                    }
//                    Status.SUCCESS -> {
//                        it.data?.token?.let { it1 -> Log.d( "liveTokenObserver: ", it1) }
//                    }
//                    Status.ERROR -> {}
//                }
//            }
//        }
//    }

}