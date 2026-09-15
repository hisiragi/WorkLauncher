package jp.hisiragi.worklauncher.data.repo

import jp.hisiragi.worklauncher.data.db.ExpenseDao
import jp.hisiragi.worklauncher.data.db.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    val expenses: Flow<List<ExpenseEntity>> = dao.observeAll()

    suspend fun add(expense: ExpenseEntity): Long = dao.insert(expense)

    suspend fun update(expense: ExpenseEntity) = dao.update(expense)

    suspend fun delete(expense: ExpenseEntity) = dao.delete(expense)

    suspend fun setReimbursed(expense: ExpenseEntity, reimbursed: Boolean) =
        dao.update(expense.copy(reimbursed = reimbursed))

    suspend fun listRange(fromEpochDay: Long, toEpochDay: Long): List<ExpenseEntity> =
        dao.listRange(fromEpochDay, toEpochDay)
}
