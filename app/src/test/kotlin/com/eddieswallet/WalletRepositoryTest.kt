package com.eddieswallet

import com.eddieswallet.data.EventKind
import com.eddieswallet.data.ApiResult
import com.eddieswallet.data.LocalWalletRepository
import com.eddieswallet.data.RecordCommand
import com.eddieswallet.data.SyncStatus
import com.eddieswallet.data.WalletApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRepositoryTest {
    @Test
    fun confirmedParentRecordIsVisibleToChildOnlyAfterSync() {
        val repository = LocalWalletRepository()
        repository.selectKind(EventKind.Allowance)
        repository.setAmount(2)
        repository.confirmPreview()

        assertEquals(1, repository.state.value.pending.size)
        assertEquals(24, repository.state.value.childConfirmed.balance)
        assertEquals(26, repository.state.value.parentSnapshot.balance)

        repository.syncPending()

        assertTrue(repository.state.value.pending.isEmpty())
        assertEquals(26, repository.state.value.confirmed.balance)
        assertEquals(26, repository.state.value.childConfirmed.balance)
        assertEquals(SyncStatus.Confirmed, repository.state.value.childEvents.first().status)
    }

    @Test
    fun offlineParentRecordRemainsPendingAndDoesNotChangeChildSnapshot() {
        val repository = LocalWalletRepository()
        repository.toggleParentOnline()
        repository.selectKind(EventKind.Spending)
        repository.setAmount(2)
        repository.confirmPreview()
        repository.syncPending()

        assertFalse(repository.state.value.parentOnline)
        assertEquals(1, repository.state.value.pending.size)
        assertEquals(24, repository.state.value.childConfirmed.balance)
        assertEquals(37, repository.state.value.parentSnapshot.balance)
    }

    @Test
    fun invalidRecordIsRejectedWithoutCreatingAnEvent() {
        val repository = LocalWalletRepository()
        repository.selectKind(EventKind.Spending)
        repository.setAmount(999)

        val preview = repository.previewDraft()
        assertNotNull(preview.rejection)
        assertEquals("INSUFFICIENT_SPENDING", preview.rejection?.code)

        repository.confirmPreview()
        assertTrue(repository.state.value.pending.isEmpty())
        assertEquals("INSUFFICIENT_SPENDING", repository.state.value.rejection?.code)
    }

    @Test
    fun apiRejectionDoesNotBecomeConfirmedBalance() {
        val repository = LocalWalletRepository(object : WalletApiClient {
            override fun submit(command: RecordCommand): ApiResult = ApiResult.Rejected(
                "SERVER_REJECTED",
                "The demo server rejected this command.",
            )
        })
        val before = repository.state.value.confirmed
        repository.confirmPreview()
        repository.syncPending()

        assertEquals(before, repository.state.value.confirmed)
        assertTrue(repository.state.value.pending.isEmpty())
        assertEquals("SERVER_REJECTED", repository.state.value.rejection?.code)
    }

    @Test
    fun reversalIsAnAdditionalImmutableEventWithInversePostings() {
        val repository = LocalWalletRepository()
        val original = repository.state.value.confirmedEvents.first()
        val before = repository.state.value.confirmed.save

        repository.reverse(original)
        assertEquals(1, repository.state.value.pending.size)
        assertEquals(before, repository.state.value.confirmed.save)
        repository.syncPending()

        assertEquals(before - 1, repository.state.value.confirmed.save)
        assertEquals(EventKind.Reversal, repository.state.value.confirmedEvents.first().kind)
        assertEquals(original.id, repository.state.value.confirmedEvents.first().reversalOf)
    }

    @Test
    fun revokedDeviceHasNoChildSnapshotRefreshPathUntilPaired() {
        val repository = LocalWalletRepository()
        repository.revokeDevice()
        assertFalse(repository.state.value.devicePaired)
        repository.toggleChildOnline()
        assertFalse(repository.state.value.devicePaired)
        repository.pairDevice()
        assertTrue(repository.state.value.devicePaired)
    }
}
