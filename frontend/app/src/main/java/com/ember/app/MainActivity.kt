package com.ember.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.ember.app.data.ActivityRepository
import com.ember.app.data.AuthRepository
import com.ember.app.data.FriendRepository
import com.ember.app.data.PhotoRepository
import com.ember.app.data.local.ThemePreferenceStore
import com.ember.app.data.remote.NetworkModule
import com.ember.app.ui.activity.ActivityScreen
import com.ember.app.ui.activity.ActivityViewModel
import com.ember.app.ui.auth.LoginScreen
import com.ember.app.ui.auth.LoginViewModel
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.friends.FindPeopleScreen
import com.ember.app.ui.friends.FindPeopleViewModel
import com.ember.app.ui.friends.FriendsScreen
import com.ember.app.ui.friends.FriendsViewModel
import com.ember.app.ui.home.HomeScreen
import com.ember.app.ui.home.HomeViewModel
import com.ember.app.ui.settings.SettingsScreen
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.ThemeScreen
import com.ember.app.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

/** Screens reached from within a tab (Settings -> Theme, Friends -> Find People) rather than
 * from the bottom nav directly. Kept separate from the active tab so back navigation can pop
 * just the nested screen without losing which tab you were on. */
private enum class NestedScreen { THEME, FIND_PEOPLE }

class MainActivity : ComponentActivity() {

    private val networkModule by lazy { NetworkModule(applicationContext) }
    private val authRepository by lazy { AuthRepository(networkModule.api, networkModule.tokenStore) }
    private val photoRepository by lazy { PhotoRepository(networkModule.api) }
    private val friendRepository by lazy { FriendRepository(networkModule.api) }
    private val activityRepository by lazy { ActivityRepository(networkModule.api) }
    private val themePreferenceStore by lazy { ThemePreferenceStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ThemeViewModel(themePreferenceStore) }
                },
            )

            // Hoisted above EmberAppTheme so picking a theme on ThemeScreen re-themes the
            // whole app immediately, not just that screen.
            EmberAppTheme(themeKey = themeViewModel.selectedTheme) {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { LoginViewModel(authRepository) }
                    },
                )
                var authenticated by remember { mutableStateOf(false) }
                var activeTab by remember { mutableStateOf(NavDestination.HOME) }
                var nestedScreen by remember { mutableStateOf<NestedScreen?>(null) }
                val coroutineScope = rememberCoroutineScope()

                if (!authenticated) {
                    LoginScreen(viewModel = loginViewModel, onAuthenticated = { authenticated = true })
                } else {
                    // Swiping/pressing back should always retrace the last navigation step
                    // instead of falling through to the system default (which closes the app):
                    // first close any nested screen, then return to Home before actually exiting.
                    BackHandler(enabled = nestedScreen != null || activeTab != NavDestination.HOME) {
                        if (nestedScreen != null) {
                            nestedScreen = null
                        } else {
                            activeTab = NavDestination.HOME
                        }
                    }

                    val onNavigate: (NavDestination) -> Unit = { destination ->
                        activeTab = destination
                        nestedScreen = null
                    }
                    val onCameraClick = { Toast.makeText(this, "Camera — coming soon", Toast.LENGTH_SHORT).show() }
                    val onComingSoon: (String) -> Unit = { label ->
                        Toast.makeText(this, "$label — coming soon", Toast.LENGTH_SHORT).show()
                    }
                    val onSignOut = {
                        coroutineScope.launch { networkModule.tokenStore.clear() }
                        authenticated = false
                        activeTab = NavDestination.HOME
                        nestedScreen = null
                    }

                    when {
                        nestedScreen == NestedScreen.THEME -> ThemeScreen(
                            viewModel = themeViewModel,
                            onBack = { nestedScreen = null },
                        )

                        nestedScreen == NestedScreen.FIND_PEOPLE -> {
                            val findPeopleViewModel: FindPeopleViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FindPeopleViewModel(friendRepository) }
                                },
                            )
                            FindPeopleScreen(
                                viewModel = findPeopleViewModel,
                                onBack = { nestedScreen = null },
                            )
                        }

                        activeTab == NavDestination.SETTINGS -> SettingsScreen(
                            currentTheme = themeViewModel.selectedTheme,
                            onNavigate = onNavigate,
                            onCameraClick = onCameraClick,
                            onThemeClick = { nestedScreen = NestedScreen.THEME },
                            onComingSoon = onComingSoon,
                            onSignOut = onSignOut,
                        )

                        activeTab == NavDestination.ACTIVITY -> {
                            val activityViewModel: ActivityViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { ActivityViewModel(activityRepository) }
                                },
                            )
                            ActivityScreen(
                                viewModel = activityViewModel,
                                onNavigate = onNavigate,
                                onCameraClick = onCameraClick,
                            )
                        }

                        activeTab == NavDestination.FRIENDS -> {
                            val friendsViewModel: FriendsViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { FriendsViewModel(friendRepository) }
                                },
                            )
                            FriendsScreen(
                                viewModel = friendsViewModel,
                                onNavigate = onNavigate,
                                onCameraClick = onCameraClick,
                                onFindPeopleClick = { nestedScreen = NestedScreen.FIND_PEOPLE },
                            )
                        }

                        else -> {
                            val homeViewModel: HomeViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer { HomeViewModel(photoRepository) }
                                },
                            )
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigate = onNavigate,
                                onCameraClick = onCameraClick,
                            )
                        }
                    }
                }
            }
        }
    }
}
