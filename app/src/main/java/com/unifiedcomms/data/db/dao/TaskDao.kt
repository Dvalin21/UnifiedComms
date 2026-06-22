package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>): List<Long>

    @Update
    suspend fun update(task: Task): Int

    @Update
    suspend fun updateAll(tasks: List<Task>): Int

    @Delete
    suspend fun delete(task: Task): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM tasks WHERE accountId = :accountId AND listId = :listId")
    suspend fun deleteByAccountAndList(accountId: String, listId: String): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): Task?

    @Query("SELECT * FROM tasks WHERE uid = :uid AND accountId = :accountId")
    suspend fun getByUid(uid: String, accountId: String): Task?

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND listId = :listId ORDER BY position ASC, dueAt ASC")
    fun getByList(accountId: String, listId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND status != :completedStatus ORDER BY dueAt ASC, position ASC")
    fun getActiveByAccount(accountId: String, completedStatus: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId IN (:accountIds) AND status != :completedStatus ORDER BY dueAt ASC, position ASC")
    fun getActiveUnified(accountIds: List<String>, completedStatus: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND status = :status ORDER BY dueAt ASC")
    fun getByStatus(accountId: String, status: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND dueAt != null AND date(dueAt) = :date ORDER BY dueAt ASC")
    fun getDueOnDate(accountId: String, date: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId IN (:accountIds) AND dueAt != null AND date(dueAt) = :date ORDER BY dueAt ASC")
    fun getDueOnDateUnified(accountIds: List<String>, date: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND dueAt != null AND dueAt < :now AND status != :completedStatus ORDER BY dueAt ASC")
    fun getOverdue(accountId: String, now: Long, completedStatus: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId IN (:accountIds) AND dueAt != null AND dueAt < :now AND status != :completedStatus ORDER BY dueAt ASC")
    fun getOverdueUnified(accountIds: List<String>, now: Long, completedStatus: TaskStatus): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId = :accountId AND dueAt != null AND dueAt >= :now AND dueAt <= :end AND status != :completedStatus ORDER BY dueAt ASC LIMIT :limit")
    fun getUpcoming(accountId: String, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE accountId IN (:accountIds) AND dueAt != null AND dueAt >= :now AND dueAt <= :end AND status != :completedStatus ORDER BY dueAt ASC LIMIT :limit")
    fun getUpcomingUnified(accountIds: List<String>, now: Long, end: Long, completedStatus: TaskStatus, limit: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY position ASC")
    fun getSubtasks(parentId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE (title LIKE :query OR description LIKE :query) AND accountId IN (:accountIds) ORDER BY dueAt ASC LIMIT :limit")
    fun searchTasks(query: String, accountIds: List<String>, limit: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE needsSync = 1 AND accountId = :accountId")
    suspend fun getNeedingSync(accountId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE isLocalOnly = 1 AND accountId = :accountId")
    suspend fun getLocalOnly(accountId: String): List<Task>

    @Transaction
    suspend fun markCompleted(id: String, completed: Boolean = true) {
        getById(id)?.let { task ->
            val newStatus = if (completed) TaskStatus.COMPLETED else TaskStatus.NEEDS_ACTION
            val completedAt = if (completed) com.unifiedcomms.data.model.TaskDateTime.fromInstant(Clock.System.now()) else null
            update(task.copy(
                status = newStatus,
                completedAt = completedAt,
                percentComplete = if (completed) 100 else 0,
                updatedAt = Clock.System.now(),
                needsSync = true
            ))
            // Update parent task progress if exists
            task.parentTaskId?.let { parentId ->
                updateParentProgress(parentId)
            }
        }
    }

    @Transaction
    suspend fun updateParentProgress(parentId: String) {
        getById(parentId)?.let { parent ->
            val subtasks = getSubtasks(parentId).first()
            val completed = subtasks.count { it.status == TaskStatus.COMPLETED }
            val total = subtasks.size
            val percent = if (total > 0) (completed * 100 / total) else 0
            val newStatus = when {
                completed == total && total > 0 -> TaskStatus.COMPLETED
                completed > 0 -> TaskStatus.IN_PROCESS
                else -> parent.status
            }
            update(parent.copy(
                completedSubtaskCount = completed,
                subtaskCount = total,
                percentComplete = percent,
                status = newStatus,
                updatedAt = Clock.System.now(),
                needsSync = true
            ))
        }
    }

    @Query("UPDATE tasks SET needsSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String): Int

    @Query("UPDATE tasks SET needsSync = 1 WHERE accountId = :accountId")
    suspend fun markAllNeedingSync(accountId: String): Int

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int): Int
}

@Dao
interface TaskListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: TaskList): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<TaskList>): List<Long>

    @Update
    suspend fun update(list: TaskList): Int

    @Delete
    suspend fun delete(list: TaskList): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId")
    fun getTasksCount(listId: String): Flow<Int>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getById(id: String): TaskList?

    @Query("SELECT * FROM task_lists WHERE accountId = :accountId ORDER BY isDefault DESC, sortOrder ASC, title ASC")
    fun getByAccount(accountId: String): Flow<List<TaskList>>

    @Query("SELECT * FROM task_lists WHERE accountId = :accountId AND serverId = :serverId")
    suspend fun getByServerId(accountId: String, serverId: String): TaskList?

    @Query("UPDATE task_lists SET taskCount = :count WHERE id = :id")
    suspend fun updateTaskCount(id: String, count: Int): Int

    @Query("UPDATE task_lists SET completedCount = :count WHERE id = :id")
    suspend fun updateCompletedCount(id: String, count: Int): Int
}