package com.htcindia.live_video.ui.adapter


import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.htcindia.live_video.R
import com.htcindia.live_video.databinding.LayoutParticipantListBinding
import com.twilio.video.LocalParticipant
import com.twilio.video.Participant
import com.twilio.video.RemoteParticipant

class ParticipantsAdapter(var context: Context) :
    RecyclerView.Adapter<ParticipantsAdapter.ParticipantViewHolder>() {

    private val participantList = ArrayList<Participant>()
    var count = 0

    inner class ParticipantViewHolder(val binding: LayoutParticipantListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            participant: Participant
        ) {
            count++
            if (count == 1) {
                binding.tvItem.text = getUserName(participant.identity) + " (You)"
            } else {
                binding.tvItem.text = getUserName(participant.identity)
            }
            participant.videoTracks.forEach {
                if (it.isTrackEnabled) {
                    binding.ivVideoList.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_videowhite
                        )
                    )
                } else {
                    binding.ivVideoList.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_videooffwhite
                        )
                    )
                }
                binding.ivVideoList.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        if (it.isTrackEnabled) R.color.green else R.color.sos_btn
                    )
                )
            }
            participant.audioTracks.forEach {
                if (it.isTrackEnabled) {
                    binding.ivAudioList.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_audioonwhite)
                    )
                } else {
                    binding.ivAudioList.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_audiooffwhite)
                    )
                }
                binding.ivAudioList.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        if (it.isTrackEnabled) R.color.green else R.color.sos_btn
                    )
                )
            }
        }

        private fun getUserName(identity: String): String {
            return identity.substringBefore("(").trim()
        }

    }


    fun setParticipantList(
        participants: List<RemoteParticipant>?,
        localParticipant: LocalParticipant
    ) {
        participantList.clear()
        participantList.add(localParticipant)
        if (participants != null) {
            val sortedList = participants.sortedBy {
                it.identity
            }
            participantList.addAll(sortedList)

        }
    }

    fun resetCount() {
        count = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = LayoutParticipantListBinding.inflate(layoutInflater, parent, false)
        return ParticipantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        holder.bind(participantList[position])
    }

    override fun getItemCount(): Int {
        return participantList.size
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

}