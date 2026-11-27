package fr.refusbot.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.refusbot.R
import fr.refusbot.core.Policy
import fr.refusbot.core.lab.LabReport
import fr.refusbot.core.lab.LabExecutionResult
import fr.refusbot.core.lab.LabRunner
import fr.refusbot.core.lab.LabScenario
import fr.refusbot.core.lab.LabScenarios
import fr.refusbot.refusBot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nombre d'actions simulées affichées par scénario. */
private const val MAX_STEPS_SHOWN = 12

/**
 * Laboratoire : le vrai moteur de décision est exécuté sur de fausses fenêtres de consentement,
 * entièrement simulées dans CYRAVA. Aucune autre appli n'est lue ni touchée.
 */
@Composable
fun LabScreen(padding: PaddingValues, onBack: () -> Unit) {
    val app = LocalContext.current.refusBot
    val scope = rememberCoroutineScope()
    val activeRules by app.rulesRepository.active.collectAsState()
    var policy by rememberSaveable { mutableStateOf(Policy.STRICT) }
    var reports by remember { mutableStateOf<Map<String, LabReport>>(emptyMap()) }
    var failures by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var running by remember { mutableStateOf(false) }

    // Un changement de profil ou de règles invalide les résultats affichés.
    LaunchedEffect(policy, activeRules) {
        reports = emptyMap()
        failures = emptyMap()
    }

    fun run(scenarios: List<LabScenario>) {
        if (running) return
        running = true
        val rules = activeRules.rules
        val chosen = policy
        scope.launch {
            try {
                val (results, errors) = withContext(Dispatchers.Default) {
                    val successful = linkedMapOf<String, LabReport>()
                    val failed = linkedMapOf<String, String>()
                    scenarios.forEach { scenario ->
                        when (val result = LabRunner.runSafely(scenario, rules, chosen)) {
                            is LabExecutionResult.Success -> successful[scenario.id] = result.report
                            is LabExecutionResult.Failure -> failed[scenario.id] = result.errorType
                        }
                    }
                    successful to failed
                }
                reports = reports + results
                failures = failures + errors
            } finally {
                running = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                stringResource(R.string.set_lab_t),
                stringResource(R.string.lab_subtitle, stringResource(activeRules.source.labelRes())),
                onBack = onBack,
            )
        }
        item {
            SectionCard(title = stringResource(R.string.lab_profile), icon = R.drawable.ic_lab) {
                Policy.entries.forEach { p ->
                    RadioRow(label = p.text(), selected = policy == p, enabled = !running, onClick = { policy = p })
                }
                Button(onClick = { run(LabScenarios.all) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) stringResource(R.string.lab_running) else stringResource(R.string.lab_run_all, LabScenarios.all.size))
                }
            }
        }
        if (failures.isNotEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.lab_errors_t), tone = CardTone.WARNING, icon = R.drawable.ic_info) {
                    BodyText(stringResource(R.string.lab_errors, failures.size))
                }
            }
        }
        items(LabScenarios.all, key = { it.id }) { scenario ->
            ScenarioCard(scenario, reports[scenario.id], failures[scenario.id], policy, running, onRun = { run(listOf(scenario)) })
        }
    }
}

@Composable
private fun ScenarioCard(scenario: LabScenario, report: LabReport?, failure: String?, policy: Policy, running: Boolean, onRun: () -> Unit) {
    val tone = when {
        failure != null -> CardTone.WARNING
        report == null -> CardTone.NORMAL
        report.accepted -> CardTone.ERROR
        policy == Policy.STRICT && !report.asExpected -> CardTone.WARNING
        else -> CardTone.OK
    }
    val texts = labScenarioRes(scenario.id)
    SectionCard(title = texts?.let { stringResource(it.first) } ?: scenario.title, tone = tone) {
        BodyText(texts?.let { stringResource(it.second) } ?: scenario.description)
        if (failure != null) {
            BodyText(stringResource(R.string.lab_tech_fail, failure))
        }
        if (report != null) {
            val status = report.outcome?.status
            val resultLabel = when (report.termination) {
                fr.refusbot.core.lab.LabTermination.COMPLETED -> status?.text() ?: stringResource(R.string.lab_no_terminal)
                fr.refusbot.core.lab.LabTermination.TIME_LIMIT -> stringResource(R.string.lab_time_limit)
                fr.refusbot.core.lab.LabTermination.IDLE -> stringResource(R.string.lab_idle)
                fr.refusbot.core.lab.LabTermination.ITERATION_LIMIT -> stringResource(R.string.lab_iter_limit)
            }
            Text(
                stringResource(R.string.lab_result, resultLabel),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (report.termination == fr.refusbot.core.lab.LabTermination.ITERATION_LIMIT ||
                report.termination == fr.refusbot.core.lab.LabTermination.TIME_LIMIT
            ) {
                BodyText(stringResource(R.string.lab_incomplete))
            }
            if (policy == Policy.STRICT) {
                val expected = scenario.expected.map { it.text() }.joinToString(stringResource(R.string.lab_or))
                BodyText(
                    if (report.asExpected) {
                        stringResource(R.string.lab_expected_ok)
                    } else {
                        stringResource(R.string.lab_expected_ko, expected)
                    },
                )
            }
            BodyText(if (report.accepted) stringResource(R.string.lab_accepted) else stringResource(R.string.lab_not_accepted))
            reasonText(LocalResources.current, report.outcome?.reason)?.let { BodyText(stringResource(R.string.lab_reason, it)) }
            if (report.steps.isEmpty()) {
                BodyText(stringResource(R.string.lab_no_action))
            } else {
                BodyText(stringResource(R.string.lab_steps))
                report.steps.take(MAX_STEPS_SHOWN).forEachIndexed { i, step -> NumberedStep(i + 1, step) }
                if (report.steps.size > MAX_STEPS_SHOWN) BodyText(stringResource(R.string.lab_more_steps, report.steps.size - MAX_STEPS_SHOWN))
            }
            BodyText(stringResource(R.string.lab_final, report.finalState))
        }
        OutlinedButton(onClick = onRun, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            Text(if (report == null && failure == null) stringResource(R.string.lab_run_one) else stringResource(R.string.lab_rerun))
        }
    }
}
