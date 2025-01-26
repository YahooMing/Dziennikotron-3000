package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(userViewModelFactory: UserViewModelFactory, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val database = AppDatabase.getDatabase(navController.context)

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                userViewModelFactory = userViewModelFactory,
                onLoginSuccess = { user -> navController.navigate("welcome/${user.id}") },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }
        composable("register") {
            RegisterScreen(
                userViewModelFactory = userViewModelFactory,
                onRegisterSuccess = { navController.popBackStack() }
            )
        }
        composable("welcome/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            val userDao = database.userDao()
            val user = remember { mutableStateOf<User?>(null) }

            LaunchedEffect(userId) {
                userDao.getUserById(userId).collect { fetchedUser ->
                    user.value = fetchedUser
                }
            }

            user.value?.let {
                WelcomeScreen(
                    user = it,
                    onNavigateToGrades = { navController.navigate("grades/$userId") },
                    onNavigateToSubjects = { navController.navigate("subjects/$userId") },
                    onNavigateToCalendar = { navController.navigate("calendar/$userId") },
                    onNavigateToSubjectRegistration = { navController.navigate("subject_registration/$userId") },
                    onNavigateToWeeklyCalendar = { navController.navigate("weekly_calendar/$userId") },
                    onLogout = onLogout
                )
            }
        }
        composable("grades/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            GradesScreen(userId = userId, gradeDao = database.gradeDao(), subjectDao = database.subjectDao(), navController = navController)
        }
        composable("subjects/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            SubjectListScreen(subjectDao = database.subjectDao(), gradeDao = database.gradeDao(), userId = userId, navController = navController)
        }
        composable("calendar/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            val userViewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
            CalendarScreen(userId = userId, userViewModel = userViewModel, navController = navController)
        }
        composable("subject_registration/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            val userViewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
            SubjectRegistrationScreen(userId = userId, userViewModel = userViewModel, subjectDao = database.subjectDao(), navController = navController)
        }
        composable("weekly_calendar/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            val userViewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
            WeeklyCalendarScreen(userId = userId, userViewModel = userViewModel, navController = navController)
        }
    }
}