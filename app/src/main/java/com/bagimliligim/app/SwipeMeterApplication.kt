package com.bagimliligim.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.bagimliligim.app.data.RoomSwipeRepository
import com.bagimliligim.app.data.SettingsStore
import com.bagimliligim.app.data.SmokingRepository
import com.bagimliligim.app.data.remote.CatalogSync
import com.bagimliligim.app.data.remote.SocialRepository
import com.bagimliligim.app.data.remote.MerchantCatalog
import com.bagimliligim.app.data.local.SwipeDatabase
import com.bagimliligim.app.domain.PreferencesRepository
import com.bagimliligim.app.domain.SwipeRepository
import com.bagimliligim.app.domain.UserPreferences
import com.bagimliligim.app.service.QuotaNotifier
import com.bagimliligim.app.service.TrackingCoordinator
import com.bagimliligim.app.widget.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SwipeMeterApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val storageError = MutableStateFlow<String?>(null)
    val serviceConnected = MutableStateFlow(false)
    private val database = Room.databaseBuilder(context, SwipeDatabase::class.java, "swipemeter.db")
        .addMigrations(SwipeDatabase.MIGRATION_1_2)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
    val repository: SwipeRepository = RoomSwipeRepository(database.swipeDao())
    val smoking = SmokingRepository(database)
    private val merchant = MerchantCatalog(context, database.smokingDao())
    val catalog = CatalogSync(database, merchant)
    val social by lazy { SocialRepository(context.applicationContext, database) }
    init { scope.launch {
        try { smoking.initialize(); merchant.seedVerifiedPrices() }
        catch (_: Exception) { storageError.value = "Marka listesi yüklenemedi. Uygulamayı yeniden açarak tekrar deneyin." }
    } }
    val settings: PreferencesRepository = SettingsStore(context)
    val preferences = settings.preferences.catch {
        storageError.value = "Ayarlar okunamadı. Uygulamayı yeniden açarak tekrar deneyin."
    }.stateIn(scope, SharingStarted.Eagerly, null as UserPreferences?)
    val tracker = TrackingCoordinator(repository, settings, preferences, scope, QuotaNotifier(context)::show, storageError) {
        WidgetRenderer.updateAll(context, this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SwipeMeterApplication).container
