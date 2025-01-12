package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.*
import androidx.room.*
import com.example.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

// Database Models
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val password: String = ""
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectName: String
)

@Entity(
    tableName = "student_subjects",
    primaryKeys = ["userId", "subjectId"],
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"]),
        ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"])
    ]
)
data class StudentSubject(
    val userId: Int,
    val subjectId: Int
)

@Entity(
    tableName = "grades",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"]),
        ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"])
    ]
)
data class Grade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val grade: Float,
    val userId: Int,
    val subjectId: Int
)

@Entity(
    tableName = "academic_calendar",
    foreignKeys = [
        ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"])
    ]
)
data class AcademicCalendar(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String,
    val time: String,
    val subjectId: Int
)

// DAO Interfaces
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    fun login(email: String, password: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun register(user: User)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject)
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE userId = :userId")
    fun getGradesForStudent(userId: Int): Flow<List<Grade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrade(grade: Grade)
}

@Dao
interface AcademicCalendarDao {
    @Query("SELECT * FROM academic_calendar")
    fun getAllCalendarEntries(): Flow<List<AcademicCalendar>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEntry(calendar: AcademicCalendar)
}

// Database
@Database(
    entities = [User::class, Subject::class, StudentSubject::class, Grade::class, AcademicCalendar::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun subjectDao(): SubjectDao
    abstract fun gradeDao(): GradeDao
    abstract fun academicCalendarDao(): AcademicCalendarDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build().also { instance = it }
            }
        }
    }
}

// Repository
class UserRepository(private val userDao: UserDao) {
    val currentUser = MutableStateFlow<User?>(null)

    suspend fun login(email: String, password: String) {
        userDao.login(email, password).collect { user ->
            currentUser.value = user
        }
    }

    suspend fun register(user: User) {
        userDao.register(user)
    }
}

// ViewModel
class UserViewModel(private val userRepository: UserRepository) : ViewModel() {
    val currentUser: StateFlow<User?> = userRepository.currentUser

    fun login(email: String, password: String) {
        viewModelScope.launch {
            userRepository.login(email, password)
        }
    }

    fun register(name: String, surname: String, email: String, password: String) {
        viewModelScope.launch {
            val user = User(name = name, surname = surname, email = email, password = password)
            userRepository.register(user)
        }
    }
}

// ViewModel Factory
class UserViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// UI Components
@Composable
fun LoginScreen(
    userViewModelFactory: UserViewModelFactory,
    onLoginSuccess: (User) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val viewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
            viewModel.login(email, password)
        }) {
            Text("Log In")
        }
        Button(onClick = onNavigateToRegister) {
            Text("Register")
        }
    }

    LaunchedEffect(viewModel.currentUser.collectAsState().value) {
        viewModel.currentUser.value?.let { user ->
            onLoginSuccess(user)
        }
    }
}

@Composable
fun RegisterScreen(
    userViewModelFactory: UserViewModelFactory,
    onRegisterSuccess: () -> Unit
) {
    val viewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        TextField(value = surname, onValueChange = { surname = it }, label = { Text("Surname") })
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
            viewModel.register(name, surname, email, password)
            onRegisterSuccess()
        }) {
            Text("Register")
        }
    }
}

@Composable
fun WelcomeScreen(user: User) {
    Text(text = "Witaj ${user.name}!", modifier = Modifier.fillMaxSize(), style = MaterialTheme.typography.headlineMedium)
}

@Composable
fun AppNavigation(userViewModelFactory: UserViewModelFactory) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                userViewModelFactory = userViewModelFactory,
                onLoginSuccess = { user -> navController.navigate("welcome") },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }
        composable("register") {
            RegisterScreen(
                userViewModelFactory = userViewModelFactory,
                onRegisterSuccess = { navController.popBackStack() }
            )
        }
        composable("welcome") {
            val user = remember { mutableStateOf(User()) } // Placeholder
            WelcomeScreen(user = user.value)
        }
    }
}

// MainActivity
class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val userRepository by lazy { UserRepository(database.userDao()) }
    private val userViewModelFactory by lazy { UserViewModelFactory(userRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation(userViewModelFactory)
            }
        }
    }
}
