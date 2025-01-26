package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*


class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val userRepository by lazy { UserRepository(database.userDao(), database.academicCalendarDao(), database.studentSubjectDao(), database.subjectDao()) }
    private val userViewModelFactory by lazy { UserViewModelFactory(userRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation(userViewModelFactory, onLogout = { restartApp() })
            }
            // Add subjects to the database
            LaunchedEffect(Unit) {
                //database.subjectDao().insertSubject(Subject(subjectName = "PUM", dayOfWeek = "Monday", time = "10:00 AM"))
                //database.subjectDao().insertSubject(Subject(subjectName = "Pythonowe królestwo", dayOfWeek = "Wednesday", time = "2:00 PM"))
                //database.subjectDao().insertSubject(Subject(subjectName = "Kanalizacja C++", dayOfWeek = "Friday", time = "11:00 AM"))
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
