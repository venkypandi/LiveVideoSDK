package com.htcindia.live_video.data.model

import com.twilio.video.NetworkQualityLevel
import com.twilio.video.Participant
import com.twilio.video.VideoTrack

data class VideoTrackModel(
    var videoTrack: VideoTrack,
    var videoMode:Boolean,
    var audioMode:Boolean,
    var identity:String,
    var isLocalParticipant: Boolean,
    var mParticipant: Participant,
    var hasInternet: NetworkQualityLevel,
    var sid:String
)