package com.htcindia.live_video.ui.livevideo

import androidx.appcompat.app.AppCompatActivity

class FragmentManager {
    companion object {
        /**
         * displays the fragment
         */
        fun showFragment(activity: AppCompatActivity,accessToken:String,roomName:String) {
            if (activity.supportFragmentManager.findFragmentById(android.R.id.content) == null) {
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, LiveVideoFragment(accessToken, roomName))
                    .addToBackStack(null)
                    .commit()
            }
        }
    }
}