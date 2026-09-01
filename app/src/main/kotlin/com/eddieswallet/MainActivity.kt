package com.eddieswallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.eddieswallet.data.ChildTab
import com.eddieswallet.data.DemoState
import com.eddieswallet.data.EventKind
import com.eddieswallet.data.LocalWalletRepository
import com.eddieswallet.data.ParentScreen
import com.eddieswallet.data.RecordPreview
import com.eddieswallet.data.SyncStatus
import com.eddieswallet.data.WalletEvent
import com.eddieswallet.data.WalletRepository
import com.eddieswallet.data.WalletSnapshot
import com.eddieswallet.data.childExplanation
import com.eddieswallet.data.childTitle
import com.eddieswallet.data.parentTitle
import com.eddieswallet.data.posting
import com.eddieswallet.ui.theme.ChildCream
import com.eddieswallet.ui.theme.ChildDot
import com.eddieswallet.ui.theme.ChildSurface
import com.eddieswallet.ui.theme.ChildText
import com.eddieswallet.ui.theme.ConfirmedTint
import com.eddieswallet.ui.theme.EddieCoral
import com.eddieswallet.ui.theme.EddieCoralShadow
import com.eddieswallet.ui.theme.EddieElevation
import com.eddieswallet.ui.theme.EddieShapes
import com.eddieswallet.ui.theme.EddieSpacing
import com.eddieswallet.ui.theme.Ink
import com.eddieswallet.ui.theme.Line
import com.eddieswallet.ui.theme.OfflineTint
import com.eddieswallet.ui.theme.OwedPurple
import com.eddieswallet.ui.theme.OwedTint
import com.eddieswallet.ui.theme.ParentCoral
import com.eddieswallet.ui.theme.RejectedTint
import com.eddieswallet.ui.theme.SaveTeal
import com.eddieswallet.ui.theme.SaveTealDark
import com.eddieswallet.ui.theme.SaveTint
import com.eddieswallet.ui.theme.SecondaryInk
import com.eddieswallet.ui.theme.SpendTint
import com.eddieswallet.ui.theme.SpendYellow
import com.eddieswallet.ui.theme.SurfaceCream
import com.eddieswallet.ui.theme.WarmCream
import com.eddieswallet.ui.theme.EddiesWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EddiesWalletApp() }
    }
}

enum class DemoRole { Parent, Eddie }

@Composable
fun EddiesWalletApp(repository: WalletRepository = remember { LocalWalletRepository() }) {
    val state by repository.state.collectAsState()
    var role by remember { mutableStateOf(DemoRole.Parent) }

    EddiesWalletTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = if (role == DemoRole.Eddie) ChildCream else WarmCream) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                DemoRoleSwitcher(role = role, onRoleChange = { role = it })
                if (role == DemoRole.Parent) {
                    ParentApp(state = state, repository = repository)
                } else {
                    ChildApp(state = state, repository = repository)
                }
            }
        }
    }
}

@Composable
private fun DemoRoleSwitcher(role: DemoRole, onRoleChange: (DemoRole) -> Unit) {
    Surface(color = if (role == DemoRole.Eddie) ChildCream else WarmCream) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EddieSpacing.Lg, vertical = EddieSpacing.Sm)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
        ) {
            EddieMark(size = 34.dp)
            Text(
                text = "Eddie's Wallet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            RoleButton("Parent", role == DemoRole.Parent, ParentCoral) { onRoleChange(DemoRole.Parent) }
            RoleButton("Eddie view", role == DemoRole.Eddie, EddieCoral) { onRoleChange(DemoRole.Eddie) }
        }
    }
}

@Composable
private fun RoleButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val background = if (selected) color else Color.Transparent
    val foreground = if (selected) SurfaceCream else SecondaryInk
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button; contentDescription = "$label demo view" },
        colors = ButtonDefaults.textButtonColors(contentColor = foreground, containerColor = background),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun ParentApp(state: DemoState, repository: WalletRepository) {
    Column(modifier = Modifier.fillMaxSize()) {
        ParentHeader(state = state, repository = repository)
        when (state.parentScreen) {
            ParentScreen.Dashboard -> ParentDashboard(state, repository)
            ParentScreen.Record -> ParentRecord(state, repository)
            ParentScreen.Confirm -> ParentConfirm(state, repository)
            ParentScreen.Activity -> ParentActivity(state, repository)
            ParentScreen.Pairing -> ParentPairing(state, repository)
        }
    }
}

@Composable
private fun ParentHeader(state: DemoState, repository: WalletRepository) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = EddieSpacing.Md)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ParentCoral, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("M", color = SurfaceCream, fontWeight = FontWeight.Bold) }
        Column(modifier = Modifier.weight(1f)) {
            Text("Family space", style = MaterialTheme.typography.titleMedium)
            Text("Parent · you record, Eddie reads", style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
        }
        NetworkPill(
            online = state.parentOnline,
            label = if (state.parentOnline) "Wi-Fi on" else "Wi-Fi off",
            onClick = repository::toggleParentOnline,
            description = "Toggle parent demo connection",
        )
    }
}

@Composable
private fun ParentDashboard(state: DemoState, repository: WalletRepository) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
        ) {
            item { VirtualOnlyNotice() }
            if (wide) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Xl)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
                            ParentBalanceCard(state)
                            ParentPendingCard(state, repository)
                            RecordButton { repository.navigate(ParentScreen.Record) }
                        }
                        ParentQuickLinks(state, repository, Modifier.widthIn(max = 330.dp).weight(0.68f))
                    }
                }
            } else {
                item { ParentBalanceCard(state) }
                item { ParentPendingCard(state, repository) }
                item { RecordButton { repository.navigate(ParentScreen.Record) } }
                item { ParentQuickLinks(state, repository, Modifier.fillMaxWidth()) }
            }
            state.rejection?.let { rejection ->
                item {
                    AlertCard(
                        title = "Rejected · ${rejection.code}",
                        body = rejection.message,
                        color = RejectedTint,
                        icon = Icons.Default.ErrorOutline,
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualOnlyNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = EddieShapes.Small,
    ) {
        Text(
            text = "Virtual credits only — a practice record of allowance. No bank account, no card, and nothing moves out of a real wallet.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryInk,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun ParentBalanceCard(state: DemoState) {
    val snapshot = state.parentSnapshot
    val status = when {
        state.pending.isNotEmpty() && !state.parentOnline -> "Offline"
        state.pending.isNotEmpty() -> "Pending sync"
        else -> "Confirmed"
    }
    val tint = when (status) {
        "Confirmed" -> ConfirmedTint
        "Offline" -> OfflineTint
        else -> SurfaceCream
    }
    Card(
        shape = EddieShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = SurfaceCream),
        elevation = CardDefaults.cardElevation(defaultElevation = EddieElevation.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(EddieSpacing.Xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BALANCE", style = MaterialTheme.typography.labelMedium, color = SecondaryInk, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                StatusPill(label = status, background = tint, foreground = if (status == "Offline") ParentCoral else SaveTealDark)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm), modifier = Modifier.padding(top = EddieSpacing.Sm)) {
                Text("${snapshot.balance}", style = MaterialTheme.typography.displayLarge)
                Text("credits", style = MaterialTheme.typography.titleMedium, color = SecondaryInk, modifier = Modifier.padding(bottom = 5.dp))
            }
            Row(modifier = Modifier.padding(top = EddieSpacing.Lg), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
                ParentMetric("Spending Jar", snapshot.spending, SpendTint, SpendYellow, Modifier.weight(1f))
                ParentMetric("Save Jar", snapshot.save, SaveTint, SaveTealDark, Modifier.weight(1f))
                ParentMetric("Owed", snapshot.owed, SurfaceCream, SecondaryInk, Modifier.weight(1f), outlined = true)
            }
        }
    }
}

@Composable
private fun ParentMetric(label: String, value: Int, background: Color, foreground: Color, modifier: Modifier, outlined: Boolean = false) {
    Card(
        modifier = modifier,
        shape = EddieShapes.Small,
        colors = CardDefaults.cardColors(containerColor = background),
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.dp, Line) else null,
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
            Text("$value", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ParentPendingCard(state: DemoState, repository: WalletRepository) {
    if (state.pending.isEmpty()) return
    Card(
        shape = EddieShapes.Card,
        colors = CardDefaults.cardColors(containerColor = SurfaceCream),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(EddieSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.pending.size == 1) "1 record not confirmed yet" else "${state.pending.size} records not confirmed yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (state.parentOnline) "Saved on this phone. Eddie won't see it until the demo server confirms it." else "No connection. The parent copy is provisional; Eddie still sees the last confirmed picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Button(
                onClick = if (state.parentOnline) repository::syncPending else repository::toggleParentOnline,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = SurfaceCream),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (state.parentOnline) "Sync now" else "Go online") }
        }
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = EddieShapes.Card,
        colors = ButtonDefaults.buttonColors(containerColor = ParentCoral, contentColor = SurfaceCream),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = EddieElevation.Hero),
    ) {
        Text("Record something", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(EddieSpacing.Sm))
        Icon(Icons.Default.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun ParentQuickLinks(state: DemoState, repository: WalletRepository, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        QuickLink(
            title = "Allowance rule",
            supporting = "8 credits every Sunday — you confirm each one",
            action = "Preview",
            onClick = repository::openAllowance,
            modifier = Modifier.fillMaxWidth(),
        )
        QuickLink(
            title = "Activity",
            supporting = "${state.confirmedEvents.size + state.pending.size} records · none can be edited or deleted",
            action = "Open",
            onClick = { repository.navigate(ParentScreen.Activity) },
            modifier = Modifier.fillMaxWidth(),
        )
        QuickLink(
            title = "Eddie's tablet",
            supporting = if (state.devicePaired) "Paired · ${if (state.childOnline) "reading now" else "last read ${state.lastConfirmedLabel}"}" else "Not linked · needs a new code",
            action = "Manage",
            onClick = { repository.navigate(ParentScreen.Pairing) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuickLink(title: String, supporting: String, action: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCream),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EddieSpacing.Lg, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = SecondaryInk, modifier = Modifier.padding(top = 2.dp))
            }
            Text(action, color = ParentCoral, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ParentRecord(state: DemoState, repository: WalletRepository) {
    val preview = repository.previewDraft()
    ParentPageScaffold(title = "Record a virtual event", onBack = { repository.navigate(ParentScreen.Dashboard) }) {
        ProgressSteps(active = 1)
        SectionLabel("What happened?")
        EventKindPicker(selected = state.draft.kind, onSelect = repository::selectKind)
        Card(
            shape = EddieShapes.Card,
            colors = CardDefaults.cardColors(containerColor = SurfaceCream),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        ) {
            Column(modifier = Modifier.padding(EddieSpacing.Xl)) {
                Text("How many credits?", style = MaterialTheme.typography.labelLarge, color = SecondaryInk)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundIconButton("Decrease amount", Icons.Default.Remove, enabled = state.draft.amount > 1) {
                        repository.setAmount(state.draft.amount - 1)
                    }
                    Text("${state.draft.amount}", style = MaterialTheme.typography.displayMedium)
                    RoundIconButton("Increase amount", Icons.Default.Add) { repository.setAmount(state.draft.amount + 1) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Md), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
                    listOf(1, 2, 5, 10).forEach { amount ->
                        ChoiceChip(label = "$amount", selected = amount == state.draft.amount, modifier = Modifier.weight(1f)) {
                            repository.setAmount(amount)
                        }
                    }
                }
            }
        }
        SectionLabel("Reason Eddie will read")
        ReasonPicker(selected = state.draft.reason, onSelect = repository::setReason)
        PreviewCard(preview = preview)
        preview.rejection?.let { rejection ->
            AlertCard(
                title = rejection.code,
                body = rejection.message,
                color = RejectedTint,
                icon = Icons.Default.ErrorOutline,
            )
        }
        Button(
            onClick = { repository.navigate(ParentScreen.Confirm) },
            enabled = preview.rejection == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = EddieShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = ParentCoral, contentColor = SurfaceCream, disabledContainerColor = Line),
        ) { Text("Preview and confirm", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ParentPageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md), content = content)
        }
    }
}

@Composable
private fun ProgressSteps(active: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
        listOf("1 Amount", "2 Preview", "3 Confirm").forEachIndexed { index, label ->
            val selected = index + 1 == active
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                shape = EddieShapes.Pill,
                color = if (selected) Ink else SurfaceCream,
                border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) SurfaceCream else SecondaryInk)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = SecondaryInk, modifier = Modifier.padding(top = EddieSpacing.Sm))
}

@Composable
private fun EventKindPicker(selected: EventKind, onSelect: (EventKind) -> Unit) {
    val kinds = listOf(EventKind.Allowance, EventKind.Spending, EventKind.Save, EventKind.Loan, EventKind.Repayment, EventKind.Reward)
    Column(verticalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
        kinds.chunked(3).forEach { rowKinds ->
            Row(horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
                rowKinds.forEach { kind ->
                    ChoiceChip(
                        label = kind.shortLabel(),
                        selected = selected == kind,
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    ) { onSelect(kind) }
                }
                repeat(3 - rowKinds.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm), verticalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
        listOf("Weekly allowance", "Comic book", "Skateboard fund", "Helped with the shopping", "Lego set").forEach { reason ->
            ChoiceChip(label = reason, selected = selected == reason, pill = true) { onSelect(reason) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, pill: Boolean = false, onClick: () -> Unit) {
    val shape = if (pill) EddieShapes.Pill else EddieShapes.Small
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp),
        shape = shape,
        color = if (selected) Ink else SurfaceCream,
        contentColor = if (selected) SurfaceCream else Ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Ink else Line),
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RoundIconButton(description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .background(SurfaceCream, CircleShape)
            .border(1.dp, Line, CircleShape)
            .semantics { contentDescription = description },
    ) { Icon(icon, contentDescription = null) }
}

@Composable
private fun PreviewCard(preview: RecordPreview) {
    Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = SaveTint)) {
        Column(modifier = Modifier.padding(EddieSpacing.Lg)) {
            Text("AFTER THIS RECORD", style = MaterialTheme.typography.labelMedium, color = SaveTealDark, letterSpacing = 1.sp)
            PreviewRow("Spending Jar", preview.before.spending, preview.after.spending)
            PreviewRow("Save Jar", preview.before.save, preview.after.save)
            PreviewRow("Eddie owes", preview.before.owed, preview.after.owed)
            Divider(modifier = Modifier.padding(vertical = EddieSpacing.Sm), color = SaveTeal.copy(alpha = 0.22f))
            PreviewRow("Total balance", preview.before.balance, preview.after.balance, suffix = " credits")
        }
    }
}

@Composable
private fun PreviewRow(label: String, before: Int, after: Int, suffix: String = "") {
    Row(modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SaveTealDark)
        Text("$before → $after$suffix", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ParentConfirm(state: DemoState, repository: WalletRepository) {
    val preview = repository.previewDraft()
    ParentPageScaffold(title = "Confirm this record", onBack = { repository.navigate(ParentScreen.Record) }) {
        ProgressSteps(active = 3)
        Card(shape = EddieShapes.LargeCard, colors = CardDefaults.cardColors(containerColor = SurfaceCream), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(modifier = Modifier.padding(EddieSpacing.Xl)) {
                Text(state.draft.kind.parentLabel(), style = MaterialTheme.typography.bodyMedium, color = SecondaryInk)
                Text("${state.draft.amount} credits", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = EddieSpacing.Sm))
                Text(state.draft.reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = EddieSpacing.Sm))
                Divider(modifier = Modifier.padding(vertical = EddieSpacing.Lg), color = Line)
                PreviewRow("Spending Jar", preview.before.spending, preview.after.spending)
                PreviewRow("Save Jar", preview.before.save, preview.after.save)
                PreviewRow("Eddie owes", preview.before.owed, preview.after.owed)
            }
        }
        Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = SurfaceCream), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text("EDDIE WILL READ", style = MaterialTheme.typography.labelMedium, color = SecondaryInk, letterSpacing = 1.sp)
                Text(
                    text = preview.draft.asEvent().childExplanation(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = EddieSpacing.Sm),
                )
            }
        }
        Text(
            text = "This writes a virtual record that can't be edited or deleted. A mistake is fixed later with a reversal, and Eddie sees both.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryInk,
        )
        Button(
            onClick = repository::confirmPreview,
            enabled = preview.rejection == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = EddieShapes.Card,
            colors = ButtonDefaults.buttonColors(containerColor = ParentCoral, contentColor = SurfaceCream, disabledContainerColor = Line),
        ) { Text("Confirm record", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = { repository.navigate(ParentScreen.Dashboard) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
    }
}

@Composable
private fun ParentActivity(state: DemoState, repository: WalletRepository) {
    ParentPageScaffold(title = "Activity", onBack = { repository.navigate(ParentScreen.Dashboard) }) {
        Text(
            text = "Every record is kept. Fix a mistake with a reversal — the original stays visible to both of you.",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryInk,
        )
        state.rejection?.let { rejection ->
            AlertCard("Rejected · ${rejection.code}", rejection.message, RejectedTint, Icons.Default.ErrorOutline)
        }
        if (state.pending.isNotEmpty()) {
            Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = OfflineTint), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(modifier = Modifier.padding(EddieSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Waiting to be confirmed", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.parentOnline) "Tap Sync now to share this with Eddie." else "Offline — this is not a confirmed shared balance.", style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
                    }
                    TextButton(onClick = if (state.parentOnline) repository::syncPending else repository::toggleParentOnline) { Text(if (state.parentOnline) "Sync now" else "Go online") }
                }
            }
        }
        val allEvents = state.pending + state.confirmedEvents
        allEvents.forEachIndexed { index, event ->
            ActivityEventCard(
                event = event,
                childName = "Eddie",
                canReverse = event.status == SyncStatus.Confirmed && event.id == state.confirmedEvents.firstOrNull()?.id,
                onReverse = { repository.reverse(event) },
            )
        }
    }
}

@Composable
private fun ActivityEventCard(event: WalletEvent, childName: String, canReverse: Boolean, onReverse: () -> Unit) {
    val posting = event.posting()
    val net = posting.spending + posting.save
    val amount = if (net == 0) "${event.amount} credits" else "${if (net > 0) "+" else "−"}${kotlin.math.abs(net)} credits"
    Card(
        shape = EddieShapes.Card,
        colors = CardDefaults.cardColors(containerColor = SurfaceCream),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (event.status == SyncStatus.PendingSync) Line else Line),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.parentTitle(), style = MaterialTheme.typography.titleMedium)
                    Text(event.reason, style = MaterialTheme.typography.bodySmall, color = SecondaryInk, modifier = Modifier.padding(top = 2.dp))
                }
                Text(amount, style = MaterialTheme.typography.titleMedium, color = if (net > 0) SaveTealDark else Ink)
            }
            Row(modifier = Modifier.padding(top = EddieSpacing.Md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
                StatusPill(
                    label = if (event.status == SyncStatus.PendingSync) "Pending sync" else "Confirmed · visible to $childName",
                    background = if (event.status == SyncStatus.PendingSync) SurfaceCream else ConfirmedTint,
                    foreground = if (event.status == SyncStatus.PendingSync) SecondaryInk else SaveTealDark,
                )
                Text(event.whenLabel, style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
                if (canReverse) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReverse, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Undo, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reverse")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentPairing(state: DemoState, repository: WalletRepository) {
    ParentPageScaffold(title = "Eddie's tablet", onBack = { repository.navigate(ParentScreen.Dashboard) }) {
        Card(shape = EddieShapes.LargeCard, colors = CardDefaults.cardColors(containerColor = SurfaceCream), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(modifier = Modifier.padding(EddieSpacing.Xl)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
                    Box(modifier = Modifier.size(42.dp).background(SaveTint, EddieShapes.Small), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.TabletAndroid, contentDescription = null, tint = SaveTealDark)
                    }
                    Column {
                        Text("Tablet in the kitchen", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.devicePaired) "Paired · read-only" else "Revoked · needs a new code", style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
                    }
                }
                Divider(modifier = Modifier.padding(vertical = EddieSpacing.Lg), color = Line)
                Text(
                    "This tablet can read Eddie's balance and activity. It can't record, change, or remove anything — the server refuses writes from it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                )
                if (state.devicePaired) {
                    OutlinedButton(
                        onClick = repository::revokeDevice,
                        modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Lg).heightIn(min = 52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ParentCoral),
                    ) { Text("Unlink this tablet") }
                } else {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Lg), shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = SaveTint)) {
                        Column(modifier = Modifier.padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ONE-TIME PAIRING CODE", style = MaterialTheme.typography.labelMedium, color = SaveTealDark)
                            Text("K7P-4RM", style = MaterialTheme.typography.headlineLarge, letterSpacing = 4.sp, modifier = Modifier.padding(top = 6.dp))
                            Text("Expires in 10 minutes · usable once", style = MaterialTheme.typography.bodySmall, color = SaveTealDark)
                        }
                    }
                    Button(
                        onClick = repository::pairDevice,
                        modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Md).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = SurfaceCream),
                    ) { Text("Simulate Eddie entering the code") }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(title: String, body: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = color), border = androidx.compose.foundation.BorderStroke(1.dp, ParentCoral.copy(alpha = .35f))) {
        Row(modifier = Modifier.padding(EddieSpacing.Lg), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = ParentCoral)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ParentCoral)
                Text(body, style = MaterialTheme.typography.bodySmall, color = SecondaryInk, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun ChildApp(state: DemoState, repository: WalletRepository) {
    Box(modifier = Modifier.fillMaxSize()) {
        DottedCreamBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            ChildHeader(state, repository)
            if (!state.devicePaired) {
                ChildRevoked()
            } else {
                ChildTabBar(state.childTab, repository::setChildTab)
                when (state.childTab) {
                    ChildTab.Home -> ChildHome(state, repository)
                    ChildTab.Story -> ChildStory(state)
                    ChildTab.Learn -> ChildLearn(state)
                }
            }
        }
    }
}

@Composable
private fun DottedCreamBackground() {
    val density = LocalDensity.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gap = with(density) { 24.dp.toPx() }
        val radius = with(density) { 1.6.dp.toPx() }
        var x = gap / 2
        while (x < size.width) {
            var y = gap / 2
            while (y < size.height) {
                drawCircle(ChildDot, radius, Offset(x, y))
                y += gap
            }
            x += gap
        }
    }
}

@Composable
private fun ChildHeader(state: DemoState, repository: WalletRepository) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = EddieSpacing.Lg, vertical = EddieSpacing.Md).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        EddieMark(size = 52.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text("Hi, Eddie!", style = MaterialTheme.typography.headlineMedium, color = ChildText)
            Text("Your jars, your story", style = MaterialTheme.typography.bodySmall, color = SecondaryInk, fontWeight = FontWeight.Bold)
        }
        NetworkPill(
            online = state.childOnline,
            label = if (state.childOnline) "Online" else "Offline",
            onClick = repository::toggleChildOnline,
            description = "Toggle Eddie demo connection",
            child = true,
        )
    }
}

@Composable
private fun ChildTabBar(tab: ChildTab, onTabChange: (ChildTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = EddieSpacing.Lg, vertical = EddieSpacing.Sm), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
        listOf(ChildTab.Home, ChildTab.Story, ChildTab.Learn).forEach { item ->
            val selected = item == tab
            val icon = when (item) {
                ChildTab.Home -> Icons.Default.Home
                ChildTab.Story -> Icons.Default.MenuBook
                ChildTab.Learn -> Icons.Default.School
            }
            TextButton(
                onClick = { onTabChange(item) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) EddieCoral else ChildSurface,
                    contentColor = if (selected) SurfaceCream else SecondaryInk,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(item.label(), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ChildHome(state: DemoState, repository: WalletRepository) {
    val snapshot = state.childConfirmed
    val latest = state.childEvents.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        if (!state.childOnline) {
            item {
                Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = OfflineTint), border = androidx.compose.foundation.BorderStroke(2.dp, SpendYellow)) {
                    Text("No internet right now. This is the last picture we saved, from ${state.lastConfirmedLabel}.", style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(15.dp))
                }
            }
        }
        item { ChildBalanceHero(snapshot) }
        item { ChildOwedCard(snapshot) }
        latest?.let { event ->
            item { ChildLatestCard(event) }
        }
        item {
            Card(shape = EddieShapes.Friendly, colors = CardDefaults.cardColors(containerColor = SaveTeal), elevation = CardDefaults.cardElevation(defaultElevation = EddieElevation.Hero), modifier = Modifier.clickable { repository.setChildTab(ChildTab.Learn) }) {
                Row(modifier = Modifier.padding(EddieSpacing.Lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
                    Box(modifier = Modifier.size(44.dp).background(ChildSurface, EddieShapes.Small), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = SaveTealDark)
                    }
                    Column {
                        Text("New thing unlocked", style = MaterialTheme.typography.bodySmall, color = SurfaceCream, fontWeight = FontWeight.Bold)
                        Text("What a balance is", style = MaterialTheme.typography.titleLarge, color = SurfaceCream, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item {
            if (state.askSent) {
                Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = OfflineTint)) {
                    Text("Sent! A grown-up will have a look.", style = MaterialTheme.typography.titleMedium, color = SecondaryInk, modifier = Modifier.padding(EddieSpacing.Lg))
                }
            } else {
                OutlinedButton(onClick = repository::askParent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = EddieShapes.Card) {
                    Text("Hmm, that looks wrong — tell a grown-up", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Text("These credits are for practising. They're not real money and they don't come out of a bank.", style = MaterialTheme.typography.bodySmall, color = SecondaryInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun ChildBalanceHero(snapshot: WalletSnapshot) {
    Card(shape = EddieShapes.Hero, colors = CardDefaults.cardColors(containerColor = EddieCoral), elevation = CardDefaults.cardElevation(defaultElevation = EddieElevation.Hero)) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = EddieSpacing.Xl)) {
            Text("Everything you have", style = MaterialTheme.typography.bodyMedium, color = SurfaceCream, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Sm)) {
                Text("${snapshot.balance}", style = MaterialTheme.typography.displayLarge, color = SurfaceCream, fontSize = 62.sp)
                Text("credits", style = MaterialTheme.typography.titleMedium, color = SurfaceCream, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = EddieSpacing.Lg), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
                ChildJarCard("Save Jar", snapshot.save, SaveTeal, SaveTealDark, (snapshot.save / 40f).coerceIn(0f, 1f), Modifier.weight(1f))
                ChildJarCard("Spend Jar", snapshot.spending, SpendYellow, SecondaryInk, (snapshot.spending / 30f).coerceIn(0f, 1f), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChildJarCard(label: String, value: Int, color: Color, textColor: Color, progress: Float, modifier: Modifier) {
    Card(modifier = modifier, shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = ChildSurface)) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = textColor)
            Text("$value", style = MaterialTheme.typography.displayMedium, color = ChildText, modifier = Modifier.padding(top = 3.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 2.dp).background(color.copy(alpha = .18f), EddieShapes.Pill)) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, EddieShapes.Pill))
            }
            Text(if (label.startsWith("Save")) "${40 - value} more for a skateboard" else "Ready to use whenever", style = MaterialTheme.typography.bodySmall, color = textColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ChildOwedCard(snapshot: WalletSnapshot) {
    Card(shape = EddieShapes.Card, colors = CardDefaults.cardColors(containerColor = ChildSurface), border = androidx.compose.foundation.BorderStroke(2.dp, OwedPurple.copy(alpha = .6f))) {
        Column(modifier = Modifier.padding(EddieSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Still to pay back", style = MaterialTheme.typography.titleMedium, color = OwedPurple)
                    Text(if (snapshot.owed > 0) "You borrowed for a Lego set" else "All paid back — nice one!", style = MaterialTheme.typography.bodySmall, color = SecondaryInk, modifier = Modifier.padding(top = 2.dp))
                }
                Text("${snapshot.owed}", style = MaterialTheme.typography.displayMedium, color = OwedPurple)
            }
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = EddieSpacing.Md).background(OwedTint, EddieShapes.Pill)) {
                Box(modifier = Modifier.fillMaxWidth((snapshot.owed / 6f).coerceIn(0f, 1f)).fillMaxHeight().background(OwedPurple, EddieShapes.Pill))
            }
        }
    }
}

@Composable
private fun ChildLatestCard(event: WalletEvent) {
    Card(shape = EddieShapes.Friendly, colors = CardDefaults.cardColors(containerColor = ChildSurface), elevation = CardDefaults.cardElevation(defaultElevation = EddieElevation.Friendly)) {
        Column(modifier = Modifier.padding(19.dp)) {
            Surface(shape = EddieShapes.Pill, color = OfflineTint) {
                Text("●  Newest news", style = MaterialTheme.typography.labelMedium, color = SecondaryInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Text("${event.childTitle()} — ${event.reason}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = EddieSpacing.Md), color = ChildText)
            Text(event.childExplanation(), style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, modifier = Modifier.padding(top = EddieSpacing.Sm))
        }
    }
}

@Composable
private fun ChildStory(state: DemoState) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        item { Text("Your story", style = MaterialTheme.typography.headlineLarge, color = ChildText) }
        items(state.childEvents, key = { it.id }) { event ->
            ChildStoryCard(event)
        }
    }
}

@Composable
private fun ChildStoryCard(event: WalletEvent) {
    val posting = event.posting()
    val net = posting.spending + posting.save
    val amount = if (net == 0) "${event.amount}" else "${if (net > 0) "+" else "−"}${kotlin.math.abs(net)}"
    val accent = when (event.kind) {
        EventKind.Save, EventKind.Reward -> SaveTeal
        EventKind.Loan, EventKind.Repayment -> OwedPurple
        EventKind.Spending -> SpendYellow
        else -> EddieCoral
    }
    Card(shape = EddieShapes.Friendly, colors = CardDefaults.cardColors(containerColor = ChildSurface), elevation = CardDefaults.cardElevation(defaultElevation = EddieElevation.Friendly)) {
        Row(modifier = Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md)) {
            Box(modifier = Modifier.size(46.dp).background(accent.copy(alpha = .16f), EddieShapes.Small), contentAlignment = Alignment.Center) {
                Text(event.glyph(), style = MaterialTheme.typography.titleLarge, color = accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(event.childTitle(), style = MaterialTheme.typography.titleMedium, color = ChildText, modifier = Modifier.weight(1f))
                    Text(amount, style = MaterialTheme.typography.titleLarge, color = if (net > 0) SaveTealDark else SecondaryInk)
                }
                Text(event.childExplanation(), style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, modifier = Modifier.padding(top = EddieSpacing.Sm))
                Text(event.whenLabel, style = MaterialTheme.typography.bodySmall, color = SecondaryInk, modifier = Modifier.padding(top = EddieSpacing.Md))
            }
        }
    }
}

@Composable
private fun ChildLearn(state: DemoState) {
    val unlocked = state.childEvents.map { it.kind }.toSet()
    val lessons = listOf(
        Lesson("1", "What a balance is", "Your balance is everything you have right now, in both jars added together.", true, SaveTint, SaveTealDark),
        Lesson("2", "Saving is not spending", "Credits in the Save Jar are still yours. You just told them to wait for something bigger.", EventKind.Save in unlocked, SaveTint, SaveTealDark),
        Lesson("3", "A reward for waiting", "Sometimes a parent adds a little extra because you kept credits in the Save Jar. It is a thank-you, not a promise.", EventKind.Reward in unlocked, SaveTint, SaveTealDark),
        Lesson("4", "Borrowing means owing", "A loan makes your Spending Jar bigger and adds the same amount to what you owe. Nothing is free — it is waiting to be paid back.", EventKind.Loan in unlocked, OwedTint, OwedPurple),
        Lesson("5", "Paying it back", "Every repayment takes credits out of Spending and takes the same amount off what you owe.", EventKind.Repayment in unlocked, OwedTint, OwedPurple),
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = EddieSpacing.Lg, end = EddieSpacing.Lg, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(EddieSpacing.Md),
    ) {
        item {
            Text("Money ideas", style = MaterialTheme.typography.headlineLarge, color = ChildText)
            Text("Each one pops open after it really happens to you.", style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = EddieSpacing.Sm))
        }
        items(lessons, key = { it.number }) { lesson -> LessonCard(lesson) }
    }
}

data class Lesson(val number: String, val title: String, val body: String, val unlocked: Boolean, val background: Color, val foreground: Color)

@Composable
private fun LessonCard(lesson: Lesson) {
    Card(
        shape = EddieShapes.Friendly,
        colors = CardDefaults.cardColors(containerColor = if (lesson.unlocked) lesson.background else Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (lesson.unlocked) lesson.background else Line),
    ) {
        Row(modifier = Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(EddieSpacing.Md), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(42.dp).background(if (lesson.unlocked) lesson.foreground.copy(alpha = .14f) else Line, EddieShapes.Small), contentAlignment = Alignment.Center) {
                Text(if (lesson.unlocked) lesson.number else "?", style = MaterialTheme.typography.titleLarge, color = lesson.foreground)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (lesson.unlocked) lesson.title else "Locked for now", style = MaterialTheme.typography.titleLarge, color = ChildText, modifier = Modifier.weight(1f))
                    StatusPill(if (lesson.unlocked) "Open" else "Later", if (lesson.unlocked) lesson.foreground.copy(alpha = .14f) else Line, lesson.foreground)
                }
                Text(if (lesson.unlocked) lesson.body else "This one pops open when it happens to you.", style = MaterialTheme.typography.bodyMedium, color = SecondaryInk, modifier = Modifier.padding(top = EddieSpacing.Sm))
            }
        }
    }
}

@Composable
private fun ChildRevoked() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(92.dp).background(OfflineTint, EddieShapes.Friendly), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = SpendYellow, modifier = Modifier.size(38.dp))
        }
        Text("This tablet is unplugged", style = MaterialTheme.typography.headlineLarge, color = ChildText, textAlign = TextAlign.Center, modifier = Modifier.padding(top = EddieSpacing.Lg))
        Text("Your credits are safe! This tablet just can't see them right now. Ask a grown-up for a new code and everything comes back.", style = MaterialTheme.typography.bodyLarge, color = SecondaryInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = EddieSpacing.Md))
        Text("The numbers saved here have been tidied away.", style = MaterialTheme.typography.bodySmall, color = SecondaryInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = EddieSpacing.Md))
    }
}

@Composable
private fun EddieMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .rotate(-5f)
            .shadow(EddieElevation.Friendly, RoundedCornerShape(30)),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(EddieCoralShadow, RoundedCornerShape(30)).padding(top = 4.dp))
        Box(modifier = Modifier.fillMaxSize().background(EddieCoral, RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
            Text("E", color = ChildCream, fontSize = (size.value * .53f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun NetworkPill(online: Boolean, label: String, onClick: () -> Unit, description: String, child: Boolean = false) {
    val background = if (online) (if (child) ChildSurface else SurfaceCream) else OfflineTint
    val foreground = if (online) SecondaryInk else ParentCoral
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        shape = EddieShapes.Pill,
        color = background,
        border = androidx.compose.foundation.BorderStroke(if (child) 2.dp else 1.dp, Line),
    ) {
        Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(if (online) Icons.Default.Wifi else Icons.Default.WifiOff, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
        }
    }
}

@Composable
private fun StatusPill(label: String, background: Color, foreground: Color) {
    Surface(shape = EddieShapes.Pill, color = background) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

private fun EventKind.shortLabel(): String = when (this) {
    EventKind.Allowance -> "Allowance"
    EventKind.Spending -> "Spending"
    EventKind.Save -> "Save"
    EventKind.Loan -> "Loan"
    EventKind.Repayment -> "Repay"
    EventKind.Reward -> "Reward"
    EventKind.Reversal -> "Reversal"
}

private fun EventKind.parentLabel(): String = when (this) {
    EventKind.Allowance -> "Allowance"
    EventKind.Spending -> "Spending"
    EventKind.Save -> "Move to Save Jar"
    EventKind.Loan -> "Loan"
    EventKind.Repayment -> "Loan repayment"
    EventKind.Reward -> "Saving reward"
    EventKind.Reversal -> "Reversal"
}

private fun ChildTab.label(): String = when (this) {
    ChildTab.Home -> "Home"
    ChildTab.Story -> "Story"
    ChildTab.Learn -> "Learn"
}

private fun WalletEvent.glyph(): String = when (kind) {
    EventKind.Allowance -> "+"
    EventKind.Spending -> "−"
    EventKind.Save -> "↓"
    EventKind.Loan -> "→"
    EventKind.Repayment -> "←"
    EventKind.Reward -> "★"
    EventKind.Reversal -> "↺"
}

private fun com.eddieswallet.data.RecordDraft.asEvent(): WalletEvent = WalletEvent(
    id = "preview",
    kind = kind,
    amount = amount,
    reason = reason,
    whenLabel = "preview",
    status = SyncStatus.PendingSync,
)
