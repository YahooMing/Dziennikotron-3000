package com.example.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

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