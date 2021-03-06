package app.coinverse.utils.viewmodel

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * See 'Dagger Tips' > 'Injecting ViewModel + SavedStateHandle'
 * [https://proandroiddev.com/dagger-tips-leveraging-assistedinjection-to-inject-viewmodels-with-savedstatehandle-and-93fe009ad874#7919]
 * Author: Gabor Varadi
 * [https://twitter.com/Zhuinden]
 */

/**
 * Create a single instance of the ViewModel.
 *
 * @receiver SavedStateRegistryOwner
 * @param arguments Bundle?
 * @param creator Function1<SavedStateHandle, T>
 * @return ViewModelProvider.Factory
 */
inline fun <reified T : ViewModel> SavedStateRegistryOwner.viewModelFactory(
        arguments: Bundle?,
        crossinline creator: (SavedStateHandle) -> T
): ViewModelProvider.Factory {
    return object : AbstractSavedStateViewModelFactory(this, arguments) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
        ): T = creator(handle) as T
    }
}

/**
 * Create the ViewModel instance to share across a given navGraphId.
 *
 * @receiver Fragment launches ViewModel
 * @param navGraphId Int defines ViewModel lifecycle
 * @param creator Function1<SavedStateHandle, T>
 * @return Lazy<T>
 */
inline fun <reified T : ViewModel> Fragment.navGraphSavedStateViewModels(
        @IdRes navGraphId: Int,
        crossinline creator: (SavedStateHandle) -> T
): Lazy<T> {
    // Wrapped in lazy to not search the NavController each time we want the backStackEntry
    val backStackEntry by lazy { findNavController().getBackStackEntry(navGraphId) }

    return createViewModelLazy(
            viewModelClass = T::class,
            storeProducer = { backStackEntry.viewModelStore },
            factoryProducer = {
                backStackEntry.viewModelFactory(
                        arguments = backStackEntry.arguments ?: Bundle(),
                        creator = creator
                )
            })
}

inline fun <reified T : ViewModel> Fragment.fragmentSavedStateViewModels(
        crossinline creator: (SavedStateHandle) -> T
): Lazy<T> {
    return createViewModelLazy(
            viewModelClass = T::class,
            storeProducer = { viewModelStore },
            factoryProducer = {
                viewModelFactory(
                        arguments = arguments ?: Bundle(),
                        creator = creator
                )
            })
}

/**
 * Configure CoroutineScope injection for production and testing.
 *
 * @receiver ViewModel provides viewModelScope for production
 * @param coroutineScope null for production, injects TestCoroutineScope for unit tests
 * @return CoroutineScope to launch coroutines on
 */
fun ViewModel.getViewModelScope(coroutineScope: CoroutineScope?) =
        if (coroutineScope == null) this.viewModelScope
        else coroutineScope

/**
 * Configure Dispatchers injection for production and testing.
 */
interface DispatcherProvider {
    fun main(): CoroutineDispatcher = Dispatchers.Main
    fun default(): CoroutineDispatcher = Dispatchers.Default
    fun io(): CoroutineDispatcher = Dispatchers.IO
    fun unconfined(): CoroutineDispatcher = Dispatchers.Unconfined
}

class DefaultDispatcherProvider : DispatcherProvider