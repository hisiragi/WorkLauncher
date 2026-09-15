package jp.hisiragi.worklauncher.data.repo

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import jp.hisiragi.worklauncher.data.db.QuickContactDao
import jp.hisiragi.worklauncher.data.db.QuickContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class QuickContactRepository(
    private val context: Context,
    private val dao: QuickContactDao,
) {
    val contacts: Flow<List<QuickContactEntity>> = dao.observeAll()

    suspend fun add(contact: QuickContactEntity): Long = dao.insert(contact)

    suspend fun update(contact: QuickContactEntity) = dao.update(contact)

    suspend fun delete(contact: QuickContactEntity) = dao.delete(contact)

    /**
     * Resolves a contact URI returned by the system picker into the fields the
     * quick-dial grid needs. Returns null when the contact can't be read.
     */
    suspend fun fromPickedUri(uri: Uri): QuickContactEntity? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI,
        )
        val contact = runCatching {
            context.contentResolver.query(uri, projection, null, null, null)
        }.getOrNull()?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Triple(cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2))
        } ?: return@withContext null

        val (contactId, name, photoUri) = contact
        QuickContactEntity(
            name = name,
            phone = firstPhone(contactId),
            email = firstEmail(contactId),
            photoUri = photoUri,
        )
    }

    private fun firstPhone(contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )
    }.getOrNull()?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun firstEmail(contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )
    }.getOrNull()?.use { if (it.moveToFirst()) it.getString(0) else null }
}
