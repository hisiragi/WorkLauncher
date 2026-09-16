package jp.hisiragi.worklauncher.core

import android.content.Context
import jp.hisiragi.worklauncher.data.db.WorkDatabase
import jp.hisiragi.worklauncher.data.repo.AppRepository
import jp.hisiragi.worklauncher.data.repo.CalendarRepository
import jp.hisiragi.worklauncher.data.repo.ExpenseRepository
import jp.hisiragi.worklauncher.data.repo.NoteRepository
import jp.hisiragi.worklauncher.data.repo.QuickContactRepository
import jp.hisiragi.worklauncher.data.repo.ReceiptStore
import jp.hisiragi.worklauncher.data.repo.TaskRepository
import jp.hisiragi.worklauncher.data.repo.TimeCardRepository
import jp.hisiragi.worklauncher.data.repo.UsageRepository
import jp.hisiragi.worklauncher.data.settings.SettingsRepository
import jp.hisiragi.worklauncher.service.FocusController
import jp.hisiragi.worklauncher.service.llm.LlmManager
import jp.hisiragi.worklauncher.service.llm.ModelDownloader
import jp.hisiragi.worklauncher.service.llm.NoteSummarizer
import jp.hisiragi.worklauncher.service.llm.NotificationDigest
import jp.hisiragi.worklauncher.service.llm.SearchSkill
import jp.hisiragi.worklauncher.service.llm.WebSearch
import jp.hisiragi.worklauncher.service.llm.ReceiptReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency graph. The launcher has a single process and a single
 * activity, so a container held by the [android.app.Application] is enough —
 * no DI framework needed.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: WorkDatabase by lazy { WorkDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }

    val timeCardRepository: TimeCardRepository by lazy { TimeCardRepository(database.timeCardDao()) }

    val expenseRepository: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao()) }

    val receiptStore: ReceiptStore by lazy { ReceiptStore(appContext) }

    val widgetHostController: WidgetHostController by lazy {
        WidgetHostController(appContext, database.homeWidgetDao())
    }

    val modelDownloader: ModelDownloader by lazy {
        ModelDownloader(appContext, applicationScope)
    }

    val llmManager: LlmManager by lazy {
        LlmManager(appContext, settingsRepository, applicationScope)
    }

    val receiptReader: ReceiptReader by lazy { ReceiptReader(llmManager) }

    val notificationDigest: NotificationDigest by lazy { NotificationDigest(llmManager) }

    val noteSummarizer: NoteSummarizer by lazy { NoteSummarizer(llmManager) }

    val searchSkill: SearchSkill by lazy { SearchSkill(llmManager, WebSearch()) }

    val quickContactRepository: QuickContactRepository by lazy {
        QuickContactRepository(appContext, database.quickContactDao())
    }

    val appRepository: AppRepository by lazy {
        AppRepository(appContext, database.appMetaDao(), applicationScope)
    }

    val calendarRepository: CalendarRepository by lazy { CalendarRepository(appContext) }

    val usageRepository: UsageRepository by lazy { UsageRepository(appContext, appRepository) }

    val focusController: FocusController by lazy {
        FocusController(
            context = appContext,
            scope = applicationScope,
            focusSessionDao = database.focusSessionDao(),
            taskRepository = taskRepository,
            settingsRepository = settingsRepository,
        )
    }

    val focusSessionDao by lazy { database.focusSessionDao() }

    val appLauncher: AppLauncher by lazy {
        AppLauncher(appRepository, focusController, applicationScope)
    }

    fun start() {
        appRepository.start()
    }
}
