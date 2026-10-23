package com.bagimliligim.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bagimliligim.app.AppContainer
import com.bagimliligim.app.data.remote.SocialSnapshot
import com.bagimliligim.app.data.remote.BackendException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SocialViewModel(private val container: AppContainer) : ViewModel() {
    val repository = container.social
    val snapshot = MutableStateFlow<SocialSnapshot?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val brand = MutableStateFlow<String?>(null)
    val metric = MutableStateFlow("reduction")

    private fun operation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message.value = "İşlem tamamlanamadı. Bağlantını ve bilgilerini kontrol ederek yeniden dene." }
            finally { busy.value = false }
        }
    }
    private suspend fun load() { snapshot.value = repository.snapshot(brand.value, metric.value) }
    fun refresh() = operation { load() }
    suspend fun signInWithGoogle(requestIdentity: suspend () -> GoogleIdentity?) {
        if (busy.value) return
        busy.value = true
        message.value = null
        try {
            val identity = requestIdentity() ?: return
            repository.authenticateGoogle(identity.idToken, identity.nonce)
            load()
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: GoogleSignInFailure) { message.value = failure.message }
        catch (failure: BackendException) { message.value = failure.message }
        catch (_: Exception) { message.value = "Giriş tamamlanamadı. Bağlantını kontrol ederek yeniden dene." }
        finally { busy.value = false }
    }
    fun filter(brandId: String?, order: String) = operation { brand.value = brandId; metric.value = order; load() }
    fun profile(name: String, reels: Boolean, smoking: Boolean) = operation {
        repository.profile(name, reels, smoking)
        load()
        message.value = "Paylaşım tercihlerin kaydedildi."
    }
    fun sync() = operation {
        container.tracker.flushAndAwait()
        val current = snapshot.value?.profile ?: return@operation
        repository.upload(current)
        load()
        message.value = "Seçtiğin özetler güncellendi."
    }
    fun friend(action: String, id: String) = operation {
        repository.friend(action, id); load()
        if (action == "request") message.value = "Kod geçerliyse arkadaşlık isteğin iletildi."
    }
    fun signOut() = operation { repository.signOut(); snapshot.value = null }
    fun deleteAccount() = operation {
        repository.deleteAccount(); snapshot.value = null
        message.value = "Hesabın ve sunucudaki paylaşımların silindi. Cihazındaki kişisel kayıtların duruyor."
    }
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SocialViewModel::class.java))
            return SocialViewModel(container) as T
        }
    }
}
