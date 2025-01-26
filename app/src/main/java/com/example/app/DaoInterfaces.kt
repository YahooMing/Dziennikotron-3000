package com.example.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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