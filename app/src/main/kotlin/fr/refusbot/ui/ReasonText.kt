package fr.refusbot.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import fr.refusbot.R
import fr.refusbot.core.Confidence
import fr.refusbot.core.PLATFORM_GENERIC_TCF
import fr.refusbot.core.PLATFORM_UNKNOWN
import fr.refusbot.core.ReasonCatalog
import fr.refusbot.core.ReasonKey

/**
 * Motif du moteur affiché dans la langue de l'appli (27/11/2026 : en anglais, l'historique montrait
 * encore « seul un bouton de validation est proposé… »). Le motif enregistré, lui, reste en français
 * (historique, exports JSON/CSV, diagnostics). Un motif inconnu est affiché tel quel.
 */
fun reasonText(res: Resources, reason: String?): String? {
    if (reason.isNullOrBlank()) return reason
    val key = ReasonCatalog.parse(reason) ?: return reason
    return keyText(res, key) ?: reason
}

private fun keyText(res: Resources, key: ReasonKey): String? {
    val id = reasonRes(key.id) ?: return null
    val nested = key.nested
    val args: List<String> = when {
        nested != null -> listOf(keyText(res, nested) ?: return null)
        key.id == "prudent" || key.id == "prudent_truncated" -> key.args.map { confidenceText(res, it) }
        else -> key.args
    }
    return res.getString(id, *args.toTypedArray())
}

/** « certaine » / « probable » / « insuffisante » écrit par le moteur, dans la langue de l'appli. */
private fun confidenceText(res: Resources, label: String): String =
    Confidence.entries.firstOrNull { it.label == label }?.let { res.getString(it.labelRes()) } ?: label

/** Nom de plateforme affichable : seuls « TCF générique » et « inconnue » sont des mots à traduire. */
fun platformText(res: Resources, platform: String): String = when (platform) {
    PLATFORM_GENERIC_TCF -> res.getString(R.string.platform_generic_tcf)
    PLATFORM_UNKNOWN -> res.getString(R.string.platform_unknown)
    else -> platform
}

@StringRes
private fun reasonRes(id: String): Int? = when (id) {
    "window_left" -> R.string.reason_window_left
    "left_truncated" -> R.string.reason_left_truncated
    "truncated_no_action" -> R.string.reason_truncated_no_action
    "observation_truncated" -> R.string.reason_observation_truncated
    "prudent_truncated" -> R.string.reason_prudent_truncated
    "observation" -> R.string.reason_observation
    "prudent" -> R.string.reason_prudent
    "long_list_hold" -> R.string.reason_long_list_hold
    "budget_time" -> R.string.reason_budget_time
    "timeout" -> R.string.reason_timeout
    "stall" -> R.string.reason_stall
    "scroll_impossible" -> R.string.reason_scroll_impossible
    "scroll_global" -> R.string.reason_scroll_global
    "closed_no_confirm" -> R.string.reason_closed_no_confirm
    "budget_actions" -> R.string.reason_budget_actions
    "max_actions" -> R.string.reason_max_actions
    "only_confirm" -> R.string.reason_only_confirm
    "untouched_on" -> R.string.reason_untouched_on
    "radio_unknown_checked" -> R.string.reason_radio_unknown_checked
    "radio_unsafe" -> R.string.reason_radio_unsafe
    "doubtful_hold" -> R.string.reason_doubtful_hold
    "unknown_state_block" -> R.string.reason_unknown_state_block
    "no_pref_controls" -> R.string.reason_no_pref_controls
    "confirm_greyed" -> R.string.reason_confirm_greyed
    "list_not_read" -> R.string.reason_list_not_read
    "vendors_not_opened" -> R.string.reason_vendors_not_opened
    "categories_not_opened" -> R.string.reason_categories_not_opened
    "vendors_skipped" -> R.string.reason_vendors_skipped
    "unexplored_hold" -> R.string.reason_unexplored_hold
    "unexplored_block" -> R.string.reason_unexplored_block
    "radio_not_verified" -> R.string.reason_radio_not_verified
    "toggles_blocked" -> R.string.reason_toggles_blocked
    "confirm_no_close" -> R.string.reason_confirm_no_close
    "confirm_not_found_scrolled" -> R.string.reason_confirm_not_found_scrolled
    "confirm_not_found" -> R.string.reason_confirm_not_found
    "paywall" -> R.string.reason_paywall
    "accept_only" -> R.string.reason_accept_only
    "no_refuse" -> R.string.reason_no_refuse
    "guard_sensitive" -> R.string.reason_guard_sensitive
    "guard_protected" -> R.string.reason_guard_protected
    "guard_foreign" -> R.string.reason_guard_foreign
    "guard_token" -> R.string.reason_guard_token
    "still_on" -> R.string.reason_still_on
    "radio_partial" -> R.string.reason_radio_partial
    "blocked_n" -> R.string.reason_blocked_n
    "unconfirmed" -> R.string.reason_unconfirmed
    "unknown_state" -> R.string.reason_unknown_state
    "too_long_unverified" -> R.string.reason_too_long_unverified
    "unreadable_choices" -> R.string.reason_unreadable_choices
    "seen_on_untried" -> R.string.reason_seen_on_untried
    "master_block" -> R.string.reason_master_block
    "unverified_closed" -> R.string.reason_unverified_closed
    "new_interface_prudent" -> R.string.reason_new_interface_prudent
    "rules_changed" -> R.string.reason_rules_changed
    "emergency_stop" -> R.string.reason_emergency_stop
    "user_pause" -> R.string.reason_user_pause
    "service_interrupted" -> R.string.reason_service_interrupted
    "service_stopped" -> R.string.reason_service_stopped
    "legacy_entry" -> R.string.reason_legacy_entry
    else -> null
}

/**
 * Conseil affiché sous un résultat qui n'est pas un refus réussi (27/11) : que faire maintenant.
 * Rien pour un refus vérifié ou « non vérifiable » (le site a reçu le refus), ni pour un motif inconnu.
 */
@StringRes
fun adviceRes(e: fr.refusbot.data.LogEntry): Int? {
    if (e.status == fr.refusbot.core.OutcomeStatus.OPTIONS_SUCCESS ||
        e.status == fr.refusbot.core.OutcomeStatus.UNVERIFIED ||
        e.status == fr.refusbot.core.OutcomeStatus.DIRECT_SUCCESS
    ) return null
    if (e.status == fr.refusbot.core.OutcomeStatus.HANDED_OVER) return R.string.advice_finish_by_hand
    val key = ReasonCatalog.parse(e.reason) ?: return when (e.status) {
        fr.refusbot.core.OutcomeStatus.PARTIAL -> R.string.advice_partial
        else -> null
    }
    return when (key.id) {
        "accept_only", "only_confirm" -> R.string.advice_no_refusal
        "paywall" -> R.string.advice_paywall
        "observation", "observation_truncated" -> R.string.advice_observation
        "prudent", "prudent_truncated", "new_interface_prudent" -> R.string.advice_prudent
        "closed_no_confirm", "window_left", "left_truncated" -> R.string.advice_closed
        "user_pause", "emergency_stop", "rules_changed", "service_interrupted", "service_stopped" -> null
        "long_list_hold", "doubtful_hold", "unexplored_hold", "confirm_greyed" -> R.string.advice_finish_by_hand
        else -> when (e.status) {
            fr.refusbot.core.OutcomeStatus.PARTIAL -> R.string.advice_partial
            fr.refusbot.core.OutcomeStatus.IMPOSSIBLE,
            fr.refusbot.core.OutcomeStatus.INCOMPLETE_CAPTURE,
            -> R.string.advice_not_supported
            else -> null
        }
    }
}
