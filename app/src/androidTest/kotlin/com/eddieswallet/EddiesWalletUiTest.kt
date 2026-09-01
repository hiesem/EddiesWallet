package com.eddieswallet

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class EddiesWalletUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun parentCanRecordPreviewConfirmAndSync() {
        rule.onNodeWithText("Record something").performClick()
        rule.onNodeWithText("Preview and confirm").performClick()
        rule.onNodeWithText("Confirm record").performClick()
        rule.onNodeWithText("Activity").assertIsDisplayed()
        rule.onNodeWithText("Sync now").performClick()
        rule.onNodeWithText("Confirmed · visible to Eddie").assertIsDisplayed()
    }

    @Test
    fun childViewHasReadOnlyBoundaryAndNoWriteControl() {
        rule.onNodeWithText("Eddie view").performClick()
        rule.onNodeWithText("Everything you have").assertIsDisplayed()
        rule.onNodeWithText("Record something").assertDoesNotExist()
        rule.onNodeWithText("Hmm, that looks wrong — tell a grown-up").assertIsDisplayed()
    }

    @Test
    fun revokingFromParentShowsChildRevokedState() {
        rule.onNodeWithText("Eddie's tablet").performClick()
        rule.onNodeWithText("Unlink this tablet").performClick()
        rule.onNodeWithText("Eddie view").performClick()
        rule.onNodeWithText("This tablet is unplugged").assertIsDisplayed()
        rule.onNodeWithText("Record something").assertDoesNotExist()
    }
}
