package com.eddieswallet.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A versioned seam for a future HTTPS API. The demo deliberately ships without one. */
interface WalletApiClient {
    fun submit(command: RecordCommand): ApiResult
}

sealed interface ApiResult {
    data object Accepted : ApiResult
    data class Rejected(val code: String, val message: String) : ApiResult
}

/** Local fixture transport: every valid pending command is accepted when Sync now is tapped. */
class NoBackendApiClient : WalletApiClient {
    override fun submit(command: RecordCommand): ApiResult = ApiResult.Accepted
}

enum class ParentScreen { Dashboard, Record, Confirm, Activity, Pairing }
enum class ChildTab { Home, Story, Learn }
enum class SyncStatus { Confirmed, PendingSync }
enum class EventKind { Allowance, Spending, Save, Loan, Repayment, Reward, Reversal }

data class WalletSnapshot(
    val spending: Int,
    val save: Int,
    val owed: Int,
) {
    val balance: Int get() = spending + save

    fun plus(posting: Posting): WalletSnapshot = copy(
        spending = spending + posting.spending,
        save = save + posting.save,
        owed = owed + posting.owed,
    )
}

data class Posting(val spending: Int = 0, val save: Int = 0, val owed: Int = 0)

data class WalletEvent(
    val id: String,
    val kind: EventKind,
    val amount: Int,
    val reason: String,
    val whenLabel: String,
    val status: SyncStatus,
    val reversalOf: String? = null,
    val inversePosting: Posting? = null,
)

data class RecordDraft(
    val kind: EventKind = EventKind.Allowance,
    val amount: Int = 8,
    val reason: String = "Weekly allowance",
)

data class Rejection(val code: String, val message: String)

data class RecordCommand(val event: WalletEvent)

data class DemoState(
    val confirmed: WalletSnapshot = WalletSnapshot(spending = 24, save = 15, owed = 6),
    val confirmedEvents: List<WalletEvent> = fixtureEvents(),
    val pending: List<WalletEvent> = emptyList(),
    val childConfirmed: WalletSnapshot = WalletSnapshot(spending = 24, save = 15, owed = 6),
    val childEvents: List<WalletEvent> = fixtureEvents(),
    val parentScreen: ParentScreen = ParentScreen.Dashboard,
    val childTab: ChildTab = ChildTab.Home,
    val draft: RecordDraft = RecordDraft(),
    val parentOnline: Boolean = true,
    val childOnline: Boolean = true,
    val devicePaired: Boolean = true,
    val askSent: Boolean = false,
    val rejection: Rejection? = null,
    val nextId: Int = 6,
    val lastConfirmedLabel: String = "9:24 this morning",
) {
    val parentSnapshot: WalletSnapshot
        get() = pending.fold(confirmed) { snapshot, event -> snapshot.plus(event.posting()) }
}

class LocalWalletRepository(
    private val apiClient: WalletApiClient = NoBackendApiClient(),
) : WalletRepository {
    private val mutableState = MutableStateFlow(DemoState())
    override val state: StateFlow<DemoState> = mutableState.asStateFlow()

    override fun navigate(screen: ParentScreen) {
        mutableState.update { it.copy(parentScreen = screen, rejection = null) }
    }

    override fun openAllowance() {
        mutableState.update {
            it.copy(
                parentScreen = ParentScreen.Record,
                draft = RecordDraft(EventKind.Allowance, 8, "Weekly allowance"),
                rejection = null,
            )
        }
    }

    override fun selectKind(kind: EventKind) {
        require(kind != EventKind.Reversal) { "Reversals are created from activity" }
        mutableState.update { it.copy(draft = it.draft.copy(kind = kind), rejection = null) }
    }

    override fun setAmount(amount: Int) {
        mutableState.update { it.copy(draft = it.draft.copy(amount = amount.coerceAtLeast(1)), rejection = null) }
    }

    override fun setReason(reason: String) {
        mutableState.update { it.copy(draft = it.draft.copy(reason = reason), rejection = null) }
    }

    override fun previewDraft(): RecordPreview {
        val current = mutableState.value
        val posting = current.draft.posting()
        return RecordPreview(current.parentSnapshot, current.draft, posting, current.parentSnapshot.plus(posting), validate(current))
    }

    override fun confirmPreview() {
        val current = mutableState.value
        val rejection = validate(current)
        if (rejection != null) {
            mutableState.update { it.copy(rejection = rejection) }
            return
        }
        val draft = current.draft
        val event = WalletEvent(
            id = "demo-${current.nextId}",
            kind = draft.kind,
            amount = draft.amount,
            reason = draft.reason,
            whenLabel = "Just now",
            status = SyncStatus.PendingSync,
        )
        mutableState.update {
            it.copy(
                pending = it.pending + event,
                parentScreen = ParentScreen.Activity,
                rejection = null,
                nextId = it.nextId + 1,
            )
        }
    }

    override fun syncPending() {
        val current = mutableState.value
        if (!current.parentOnline || current.pending.isEmpty()) return

        var confirmed = current.confirmed
        var confirmedEvents = current.confirmedEvents
        val stillPending = mutableListOf<WalletEvent>()
        var rejection: Rejection? = null
        current.pending.forEach { event ->
            when (val result = apiClient.submit(RecordCommand(event))) {
                ApiResult.Accepted -> {
                    confirmed = confirmed.plus(event.posting())
                    confirmedEvents = listOf(event.copy(status = SyncStatus.Confirmed)) + confirmedEvents
                }
                is ApiResult.Rejected -> {
                    rejection = Rejection(result.code, result.message)
                }
            }
        }
        val childIsCurrent = current.childOnline && current.devicePaired
        mutableState.update {
            it.copy(
                confirmed = confirmed,
                confirmedEvents = confirmedEvents,
                pending = stillPending,
                childConfirmed = if (childIsCurrent) confirmed else it.childConfirmed,
                childEvents = if (childIsCurrent) confirmedEvents else it.childEvents,
                rejection = rejection,
                lastConfirmedLabel = if (confirmedEvents != current.confirmedEvents) "just now" else it.lastConfirmedLabel,
            )
        }
    }

    override fun toggleParentOnline() {
        mutableState.update { it.copy(parentOnline = !it.parentOnline) }
    }

    override fun toggleChildOnline() {
        mutableState.update { current ->
            val nowOnline = !current.childOnline
            if (nowOnline && current.devicePaired) {
                current.copy(
                    childOnline = true,
                    childConfirmed = current.confirmed,
                    childEvents = current.confirmedEvents,
                    lastConfirmedLabel = "just now",
                )
            } else current.copy(childOnline = nowOnline)
        }
    }

    override fun revokeDevice() {
        mutableState.update { it.copy(devicePaired = false) }
    }

    override fun pairDevice() {
        mutableState.update {
            it.copy(
                devicePaired = true,
                childOnline = true,
                childConfirmed = it.confirmed,
                childEvents = it.confirmedEvents,
                childTab = ChildTab.Home,
            )
        }
    }

    override fun setChildTab(tab: ChildTab) {
        mutableState.update { it.copy(childTab = tab) }
    }

    override fun askParent() {
        mutableState.update { it.copy(askSent = true) }
    }

    override fun clearAsk() {
        mutableState.update { it.copy(askSent = false) }
    }

    override fun reverse(event: WalletEvent) {
        if (event.status != SyncStatus.Confirmed || event.kind == EventKind.Reversal) return
        val current = mutableState.value
        val reversal = WalletEvent(
            id = "reversal-${current.nextId}",
            kind = EventKind.Reversal,
            amount = event.amount,
            reason = "Undoes: ${event.reason}",
            whenLabel = "Just now",
            status = SyncStatus.PendingSync,
            reversalOf = event.id,
            inversePosting = event.posting().inverse(),
        )
        mutableState.update {
            it.copy(pending = it.pending + reversal, parentScreen = ParentScreen.Activity, nextId = it.nextId + 1)
        }
    }

    private fun validate(current: DemoState): Rejection? {
        val draft = current.draft
        val available = current.parentSnapshot
        if (draft.amount <= 0) return Rejection("AMOUNT_REQUIRED", "Enter an amount above zero.")
        if ((draft.kind == EventKind.Spending || draft.kind == EventKind.Save) && draft.amount > available.spending) {
            return Rejection(
                "INSUFFICIENT_SPENDING",
                "The Spending Jar only holds ${available.spending} credits. The demo server would refuse this record.",
            )
        }
        if (draft.kind == EventKind.Repayment && draft.amount > available.owed) {
            return Rejection(
                "REPAYMENT_EXCEEDS_OWED",
                "Eddie only owes ${available.owed} credits. Paying back more than that is not a valid record.",
            )
        }
        if (draft.kind == EventKind.Repayment && draft.amount > available.spending) {
            return Rejection(
                "INSUFFICIENT_SPENDING",
                "Repayment comes out of the Spending Jar, which holds ${available.spending} credits.",
            )
        }
        return null
    }
}

interface WalletRepository {
    val state: StateFlow<DemoState>
    fun navigate(screen: ParentScreen)
    fun openAllowance()
    fun selectKind(kind: EventKind)
    fun setAmount(amount: Int)
    fun setReason(reason: String)
    fun previewDraft(): RecordPreview
    fun confirmPreview()
    fun syncPending()
    fun toggleParentOnline()
    fun toggleChildOnline()
    fun revokeDevice()
    fun pairDevice()
    fun setChildTab(tab: ChildTab)
    fun askParent()
    fun clearAsk()
    fun reverse(event: WalletEvent)
}

data class RecordPreview(
    val before: WalletSnapshot,
    val draft: RecordDraft,
    val posting: Posting,
    val after: WalletSnapshot,
    val rejection: Rejection?,
)

fun RecordDraft.posting(): Posting = when (kind) {
    EventKind.Allowance -> Posting(spending = amount)
    EventKind.Spending -> Posting(spending = -amount)
    EventKind.Save -> Posting(spending = -amount, save = amount)
    EventKind.Loan -> Posting(spending = amount, owed = amount)
    EventKind.Repayment -> Posting(spending = -amount, owed = -amount)
    EventKind.Reward -> Posting(save = amount)
    EventKind.Reversal -> Posting()
}

fun WalletEvent.posting(): Posting = if (kind == EventKind.Reversal) {
    inversePosting ?: Posting()
} else RecordDraft(kind, amount, reason).posting()

fun Posting.inverse(): Posting = Posting(-spending, -save, -owed)

fun WalletEvent.parentTitle(): String = when (kind) {
    EventKind.Allowance -> "Allowance"
    EventKind.Spending -> "Spending"
    EventKind.Save -> "Move to Save Jar"
    EventKind.Loan -> "Loan"
    EventKind.Repayment -> "Loan repayment"
    EventKind.Reward -> "Saving reward"
    EventKind.Reversal -> "Reversal"
}

fun WalletEvent.childTitle(): String = when (kind) {
    EventKind.Allowance -> "Allowance day"
    EventKind.Spending -> "You spent some"
    EventKind.Save -> "Moved into your Save Jar"
    EventKind.Loan -> "You borrowed"
    EventKind.Repayment -> "You paid some back"
    EventKind.Reward -> "Save Jar reward"
    EventKind.Reversal -> "A mistake was undone"
}

fun WalletEvent.childExplanation(): String = when (kind) {
    EventKind.Allowance -> "Your allowance was added to the Spending Jar, so your total went up by $amount credits."
    EventKind.Spending -> "$amount credits left the Spending Jar for ${reason.lowercase()}, so your total went down."
    EventKind.Save -> "The same $amount credits changed jars — out of Spending and into Save. Your total is exactly the same."
    EventKind.Loan -> "A parent lent you $amount credits. You can spend them now, and the you-owe number went up by $amount too."
    EventKind.Repayment -> "You paid back $amount credits. Your Spending Jar went down and what you owe went down by the same amount."
    EventKind.Reward -> "A little extra for keeping credits in the Save Jar. It went straight into Save, not Spending."
    EventKind.Reversal -> "A parent undid an earlier record because it was a mistake. Both records stay here so you can see exactly what changed."
}

private fun fixtureEvents() = listOf(
    WalletEvent("e5", EventKind.Reward, 1, "Well done for saving", "Yesterday, 6:40pm", SyncStatus.Confirmed),
    WalletEvent("e4", EventKind.Save, 5, "Skateboard fund", "Yesterday, 6:38pm", SyncStatus.Confirmed),
    WalletEvent("e3", EventKind.Spending, 3, "Comic book", "Monday, 4:15pm", SyncStatus.Confirmed),
    WalletEvent("e2", EventKind.Allowance, 8, "Weekly allowance", "Sunday, 9:00am", SyncStatus.Confirmed),
    WalletEvent("e1", EventKind.Loan, 6, "Lego set", "Last Saturday", SyncStatus.Confirmed),
)
