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
import androidx.lifecycle.lifecycleScope
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
fun WelcomeScreen(user: User, onNavigateToGrades: () -> Unit, onNavigateToSubjects: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text = "Witaj ${user.name}!", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onNavigateToGrades) {
            Text("Zobacz Oceny")
        }
        Button(onClick = onNavigateToSubjects) {
            Text("Zobacz Przedmioty")
        }
    }
}

@Composable
fun SubjectListScreen(subjectDao: SubjectDao, gradeDao: GradeDao, userId: Int) {
    val subjects by subjectDao.getAllSubjects().collectAsState(initial = emptyList())
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var grade by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        subjects.forEach { subject ->
            Text(text = subject.subjectName)
            Button(onClick = { selectedSubject = subject }) {
                Text("Dodaj Ocenę")
            }
        }

        selectedSubject?.let {
            TextField(value = grade, onValueChange = { grade = it }, label = { Text("Ocena") })
            Button(onClick = {
                val gradeValue = grade.toFloatOrNull()
                if (gradeValue != null) {
                    coroutineScope.launch {
                        gradeDao.insertGrade(Grade(grade = gradeValue, userId = userId, subjectId = it.id))
                    }
                }
            }) {
                Text("Zapisz Ocenę")
            }
        }
    }
}
@Composable
fun GradesScreen(userId: Int, gradeDao: GradeDao, subjectDao: SubjectDao) {
    val grades by gradeDao.getGradesForStudent(userId).collectAsState(initial = emptyList())
    val subjects by subjectDao.getAllSubjects().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        grades.forEach { grade ->
            val subjectName = subjects.find { it.id == grade.subjectId }?.subjectName ?: "Unknown"
            Text(text = "PRZEDMIOT: $subjectName, OCENA: ${grade.grade}")
        }
    }
}

@Composable
fun AppNavigation(userViewModelFactory: UserViewModelFactory) {
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
            val user = remember { mutableStateOf(User()) }

            LaunchedEffect(userId) {
                userDao.login("", "").collect { fetchedUser ->
                    if (fetchedUser != null && fetchedUser.id == userId) {
                        user.value = fetchedUser
                    }
                }
            }

            WelcomeScreen(
                user = user.value,
                onNavigateToGrades = { navController.navigate("grades/$userId") },
                onNavigateToSubjects = { navController.navigate("subjects/$userId") }
            )
        }
        composable("grades/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            GradesScreen(userId = userId, gradeDao = database.gradeDao(), subjectDao = database.subjectDao())
        }
        composable("subjects/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
            SubjectListScreen(subjectDao = database.subjectDao(), gradeDao = database.gradeDao(), userId = userId)
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

            // Add subjects to the database
            LaunchedEffect(Unit) {
                database.subjectDao().insertSubject(Subject(subjectName = "Mathematics"))
                database.subjectDao().insertSubject(Subject(subjectName = "Physics"))
                database.subjectDao().insertSubject(Subject(subjectName = "Chemistry"))
            }
        }
    }
}
