package com.htcindia.live_video.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.htcindia.live_video.R
import com.htcindia.live_video.data.model.VideoTrackModel
import com.htcindia.live_video.databinding.LayoutThumbnailListItemBinding
import com.twilio.video.NetworkQualityLevel
import com.twilio.video.VideoView


class ThumbnailAdapter(
    private val context: Context,
    private val clickListener: (VideoTrackModel) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.ThumbnailViewHolder>() {

    private var listRemoteTracks = ArrayList<VideoTrackModel>()
    private var holderlist: HashMap<String, VideoView> = HashMap()

    inner class ThumbnailViewHolder(val binding: LayoutThumbnailListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("NewApi")
        fun bind(
            context: Context,
            data: VideoTrackModel,
            clickListener: (VideoTrackModel) -> Unit
        ) {

            if (data.isLocalParticipant) {
                binding.tvName.text = data.identity + " (You)"
            } else {
                binding.tvName.text = data.identity
            }

            if (!data.hasInternet.equals(NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO)) {
                binding.ivVideo.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_videooffwhite)
                )
                if (data.videoTrack.isEnabled) {
                    binding.thumbnailVideoView.background = null
                    data.videoTrack.addSink(binding.thumbnailVideoView)
                    binding.ivVideo.visibility = View.GONE
                } else {
                    binding.thumbnailVideoView.setBackgroundColor(context.resources.getColor(R.color.bottomSheetBg))
                    binding.ivVideo.visibility = View.VISIBLE
                }
            } else {
                binding.tvName.text = "${data.identity} lost connection..."
                binding.thumbnailVideoView.setBackgroundColor(context.resources.getColor(R.color.bottomSheetBg))
                binding.ivVideo.visibility = View.VISIBLE
                binding.ivVideo.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_offline)
                )
            }

            data.mParticipant.audioTracks.forEach {
                it.audioTrack?.let { track ->
                    if (track.isEnabled) {
                        binding.tvName.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_audioonwhite,
                            0,
                            0,
                            0
                        )
                    } else {
                        binding.tvName.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_audiooffwhite,
                            0,
                            0,
                            0
                        )

                    }
                }

            }


            binding.cvThumbnail.setOnClickListener {
                clickListener(data)
            }

        }

    }

    fun addParticipant(videoTrack: VideoTrackModel) {
        val tempList = ArrayList<VideoTrackModel>()
        for (i in listRemoteTracks) {
            tempList.add(i)
        }
        listRemoteTracks.clear()
        if (videoTrack.isLocalParticipant) {
            listRemoteTracks.add(videoTrack)
            tempList.forEach {
                listRemoteTracks.add(it)
            }
        } else {
            tempList.forEach {
                listRemoteTracks.add(it)
            }
            listRemoteTracks.add(videoTrack)
        }
    }

    @SuppressLint("NewApi")
    fun removeParticipant(videoTrack: VideoTrackModel) {

        val tempList = ArrayList<VideoTrackModel>()
        for (i in listRemoteTracks) {
            tempList.add(i)
        }
        listRemoteTracks.clear()
        tempList.forEach {
            if (!it.sid.equals(videoTrack.sid)) {
                listRemoteTracks.add(it)
            }
        }
//        listRemoteTracks.remove(videoTrack)
//        val index = listRemoteTracks.indexOfFirst{
//            it.identity == videoTrack.identity
//        }
//        listRemoteTracks.removeAt(index)

        for (i in listRemoteTracks) {
            holderlist[i.sid]?.let {
                if (i.videoTrack.sinks.isNotEmpty()) {
                    i.videoTrack.removeSink(it)
                }
            }
        }
        if (videoTrack.videoTrack.sinks.isNotEmpty()) {
            holderlist[videoTrack.sid]?.let { videoTrack?.videoTrack?.removeSink(it) }
        }
        holderlist.clear()

    }

    fun changeAudioStatus(sid: String, isAudioEnabled: Boolean) {
        listRemoteTracks.find {
            it.sid == sid
        }?.audioMode = isAudioEnabled
    }

    fun changeInternetStatus(sid: String, isEnabled: NetworkQualityLevel) {
        listRemoteTracks.find {
            it.sid == sid
        }?.hasInternet = isEnabled
    }

    fun changeVideoStatus(sid: String, isVideoEnabled: Boolean) {
        listRemoteTracks.find {
            it.sid == sid
        }?.videoMode = isVideoEnabled
    }


    override fun getItemCount(): Int {
        return listRemoteTracks.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = LayoutThumbnailListItemBinding.inflate(
            LayoutInflater.from(parent?.context),
            parent,
            false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(context, listRemoteTracks[position], clickListener)
        holderlist[listRemoteTracks[position].sid] = holder.binding.thumbnailVideoView
    }
}