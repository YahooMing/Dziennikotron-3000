package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.*
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

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
    val subjectName: String,
    val maxStudents: Int = 30,
    val dayOfWeek: String,
    val time: String
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Perform the necessary schema changes
        database.execSQL("ALTER TABLE subjects ADD COLUMN maxStudents INTEGER NOT NULL DEFAULT 30")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE subjects ADD COLUMN dayOfWeek TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE subjects ADD COLUMN time TEXT NOT NULL DEFAULT ''")
    }
}

// DAO Interfaces
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    fun login(email: String, password: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun register(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserById(userId: Int): Flow<User?>
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
interface StudentSubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentSubject(studentSubject: StudentSubject)

    @Query("""
        SELECT subjects.* FROM subjects
        INNER JOIN student_subjects ON subjects.id = student_subjects.subjectId
        WHERE student_subjects.userId = :userId
    """)
    fun getSubjectsForUser(userId: Int): Flow<List<Subject>>

    @Query("SELECT COUNT(*) FROM student_subjects WHERE subjectId = :subjectId")
    fun getStudentCountForSubject(subjectId: Int): Flow<Int> // Dodaj tę metodę
}
@Dao
interface AcademicCalendarDao {
    @Query("""
        SELECT * FROM academic_calendar
        WHERE subjectId IN (
            SELECT subjectId FROM student_subjects
            WHERE userId = :userId
        )
    """)
    fun getCalendarForUser(userId: Int): Flow<List<AcademicCalendar>>

    @Query("SELECT * FROM academic_calendar")
    fun getAllCalendarEntries(): Flow<List<AcademicCalendar>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEntry(calendar: AcademicCalendar)
}

@Database(
    entities = [User::class, Subject::class, StudentSubject::class, Grade::class, AcademicCalendar::class],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun subjectDao(): SubjectDao
    abstract fun gradeDao(): GradeDao
    abstract fun academicCalendarDao(): AcademicCalendarDao
    abstract fun studentSubjectDao(): StudentSubjectDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Ensure all migrations are added
                    .build().also { instance = it }
            }
        }
    }
}

class UserRepository(
    private val userDao: UserDao,
    private val academicCalendarDao: AcademicCalendarDao,
    private val studentSubjectDao: StudentSubjectDao,
    private val subjectDao: SubjectDao
) {
    val currentUser = MutableStateFlow<User?>(null)

    suspend fun login(email: String, password: String) {
        userDao.login(email, password).collect { user ->
            currentUser.value = user
        }
    }

    suspend fun register(user: User) {
        userDao.register(user)
    }

    fun getUserSubjects(userId: Int): Flow<List<Subject>> {
        return studentSubjectDao.getSubjectsForUser(userId)
    }

    fun getStudentCountForSubject(subjectId: Int): Flow<Int> {
        return studentSubjectDao.getStudentCountForSubject(subjectId)
    }

    suspend fun registerForSubject(userId: Int, subjectId: Int, maxStudents: Int) {
        val studentCount = studentSubjectDao.getStudentCountForSubject(subjectId).first()
        if (studentCount < maxStudents) {
            studentSubjectDao.insertStudentSubject(StudentSubject(userId = userId, subjectId = subjectId))
        } else {
            throw IllegalStateException("Max number of students reached for this subject")
        }
    }
}

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

    fun getUserSubjects(userId: Int): Flow<List<Subject>> {
        return userRepository.getUserSubjects(userId)
    }

    fun getStudentCountForSubject(subjectId: Int): Flow<Int> {
        return userRepository.getStudentCountForSubject(subjectId)
    }

    fun registerForSubject(userId: Int, subjectId: Int, maxStudents: Int) {
        viewModelScope.launch {
            userRepository.registerForSubject(userId, subjectId, maxStudents)
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

@Composable
fun WeeklyCalendarScreen(userId: Int, userViewModel: UserViewModel, navController: NavController) {
    val subjects by userViewModel.getUserSubjects(userId).collectAsState(initial = emptyList())

    val groupedSubjects = subjects.groupBy { it.dayOfWeek }.mapValues { entry ->
        entry.value.sortedBy { it.time }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        groupedSubjects.forEach { (day, subjects) ->
            item {
                Text(
                    text = day,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            subjects.forEach { subject ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Godzina: ${subject.time}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Przedmiot: ${subject.subjectName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Divider(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("welcome/$userId") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("WRÓĆ")
            }
        }
    }
}


@Composable
fun CalendarScreen(userId: Int, userViewModel: UserViewModel, navController: NavController) {
    val subjects by userViewModel.getUserSubjects(userId).collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(subjects) { subject ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = subject.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Godzina: ${subject.time}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("welcome/$userId") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("WRÓĆ")
            }
        }
    }
}


@Composable
fun SubjectRegistrationScreen(userId: Int, userViewModel: UserViewModel, subjectDao: SubjectDao, navController: NavController) {
    val subjects by subjectDao.getAllSubjects().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        items(subjects) { subject ->
            val studentCount by userViewModel.getStudentCountForSubject(subject.id).collectAsState(initial = 0)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = subject.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zarejestrowanych: $studentCount/${subject.maxStudents}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    userViewModel.registerForSubject(userId, subject.id, subject.maxStudents)
                                    errorMessage = null // Czyszczenie wiadomości o błędzie
                                } catch (e: IllegalStateException) {
                                    errorMessage = "Nie udało się zarejestrować: ${e.message}"
                                }
                            }
                        },
                        enabled = studentCount < subject.maxStudents,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (studentCount < subject.maxStudents) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Zarejestruj się")
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("welcome/$userId") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("WRÓĆ")
            }
        }
        item {
            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    userViewModelFactory: UserViewModelFactory,
    onLoginSuccess: (User) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val viewModel: UserViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = userViewModelFactory)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Zaloguj się",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (loginError) {
            Text(
                text = "Nieprawidłowy login lub hasło",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                viewModel.login(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Zaloguj się")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Zarejestruj się")
        }
    }

    LaunchedEffect(viewModel.currentUser.collectAsState().value) {
        viewModel.currentUser.value?.let { user ->
            loginError = false
            onLoginSuccess(user)
        } ?: run {
            loginError = true
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
    var confirmPassword by remember { mutableStateOf("") }
    var registerError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Rejestracja",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Name Input
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Imię") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Surname Input
        TextField(
            value = surname,
            onValueChange = { surname = it },
            label = { Text("Nazwisko") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Email Input
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Password Input
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Confirm Password Input
        TextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Potwierdź Hasło") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Error Message
        registerError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Register Button
        Button(
            onClick = {
                if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    registerError = "Wszystkie pola muszą być wypełnione."
                } else if (password != confirmPassword) {
                    registerError = "Hasła muszą się zgadzać."
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    registerError = "Niepoprawny adres email."
                } else {
                    viewModel.register(name, surname, email, password)
                    onRegisterSuccess()
                    registerError = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Zarejestruj się")
        }

        // Spacer before alternate action
        Spacer(modifier = Modifier.height(8.dp))

        // Go to Login Button
        OutlinedButton(
            onClick = { onRegisterSuccess() }, // You can navigate to login screen here if needed
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text("Masz już konto? Zaloguj się")
        }
    }
}


@Composable
fun WelcomeScreen(
    user: User,
    onNavigateToGrades: () -> Unit,
    onNavigateToSubjects: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSubjectRegistration: () -> Unit,
    onNavigateToWeeklyCalendar: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Witaj, ${user.name}!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        WelcomeButton("Zobacz Oceny", onNavigateToGrades)
        WelcomeButton("Zobacz Przedmioty", onNavigateToSubjects)
        WelcomeButton("Zobacz Kalendarz", onNavigateToCalendar)
        WelcomeButton("Zarejestruj się na Przedmioty", onNavigateToSubjectRegistration)
        WelcomeButton("Zobacz Kalendarz Tygodniowy", onNavigateToWeeklyCalendar)

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("WYLOGUJ")
        }
    }
}

@Composable
fun WelcomeButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text)
    }
}


@Composable
fun SubjectListScreen(subjectDao: SubjectDao, gradeDao: GradeDao, userId: Int, navController: NavController) {
    val subjects by subjectDao.getAllSubjects().collectAsState(initial = emptyList())
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var grade by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        items(subjects) { subject ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = subject.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            selectedSubject = subject
                            showDialog = true
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dodaj Ocenę")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("welcome/$userId") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("WRÓĆ")
            }
        }
    }

    if (showDialog) {
        AddGradeDialog(
            selectedSubject = selectedSubject,
            grade = grade,
            onGradeChange = { grade = it },
            onDismiss = { showDialog = false },
            onSave = {
                val gradeValue = grade.toFloatOrNull()
                if (gradeValue != null && gradeValue in 2.0..5.0) { // Zakres ocen
                    coroutineScope.launch {
                        gradeDao.insertGrade(Grade(grade = gradeValue, userId = userId, subjectId = selectedSubject!!.id))
                        showDialog = false
                        grade = ""
                    }
                }
            }
        )
    }
}

@Composable
fun AddGradeDialog(
    selectedSubject: Subject?,
    grade: String,
    onGradeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Dodaj Ocenę", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text(
                    text = "Przedmiot: ${selectedSubject?.subjectName ?: "Nieznany"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = grade,
                    onValueChange = onGradeChange,
                    label = { Text("Ocena") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}


@Composable
fun GradesScreen(userId: Int, gradeDao: GradeDao, subjectDao: SubjectDao, navController: NavController) {
    val grades by gradeDao.getGradesForStudent(userId).collectAsState(initial = emptyList())
    val subjects by subjectDao.getAllSubjects().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        items(grades) { grade ->
            val subjectName = subjects.find { it.id == grade.subjectId }?.subjectName ?: "Unknown"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface ,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ocena: ${grade.grade}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("welcome/$userId") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("WRÓĆ")
            }
        }
    }
}


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
