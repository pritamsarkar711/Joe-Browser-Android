package com.joebrowser.app

import android.content.Context
import android.content.Intent
import com.joebrowser.app.browser.BrowserActivity
import com.joebrowser.app.data.model.Profile

/**
 * Opens a profile in the browser.
 */
object BrowserLauncher {

    fun launch(context: Context, profile: Profile) {
        val intent = Intent(context, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id)
            // Whether this is the first time the profile has been opened is decided here,
            // before the open is recorded. The browser asks the question "is this the
            // first time?" and the answer has to be the same answer both times.
            putExtra(BrowserActivity.EXTRA_FIRST_RUN, !profile.launchedOnce)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
