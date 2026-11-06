package io.github.icenux.ccc

import android.app.Application
import android.content.Context
import io.github.icenux.ccc.data.Language
import io.github.icenux.ccc.notify.WatchService

/** Only to give the application context the app's own language (see [Language]). */
class CccApp : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Language.wrap(base))

    // Channels exist from the first start, so the app shows up in the system notification settings.
    override fun onCreate() {
        super.onCreate()
        WatchService.channels(this)
    }
}
