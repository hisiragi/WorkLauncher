package jp.hisiragi.worklauncher.data.repo

import jp.hisiragi.worklauncher.data.db.NoteDao
import jp.hisiragi.worklauncher.data.db.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    val notes: Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun find(id: Long): NoteEntity? = dao.findById(id)

    suspend fun save(note: NoteEntity): Long =
        if (note.id == 0L) {
            dao.insert(note)
        } else {
            dao.update(note.copy(updatedAt = System.currentTimeMillis()))
            note.id
        }

    suspend fun delete(note: NoteEntity) = dao.delete(note)

    suspend fun setPinned(note: NoteEntity, pinned: Boolean) =
        dao.update(note.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
}
