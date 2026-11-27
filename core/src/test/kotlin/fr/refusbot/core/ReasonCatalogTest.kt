package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 27/11/2026 : appli en anglais, mais l'historique affichait encore les motifs du moteur en français
 * (« seul un bouton de validation est proposé : rien n'a été refusé, aucun clic »). Chaque motif écrit
 * par le moteur ou le service doit être reconnu pour pouvoir être traduit à l'affichage.
 */
class ReasonCatalogTest {
    private fun id(reason: String) = ReasonCatalog.parse(reason)?.id

    @Test fun motif_du_rapport_du_27_11_reconnu() {
        assertEquals("only_confirm", id("seul un bouton de validation est proposé : rien n'a été refusé, aucun clic"))
        assertEquals("closed_no_confirm", id("la fenêtre s'est fermée sans validation"))
        assertEquals(
            "unverified_closed",
            id("bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement"),
        )
        assertEquals(
            "new_interface_prudent",
            id("fenêtre différente de celles déjà refusées dans cette appli : automatisation suspendue (profil Prudent), à vérifier dans l'onglet Compatibilité"),
        )
    }

    @Test fun libelle_du_site_conserve_tel_quel() {
        val k = ReasonCatalog.parse("aucune option de refus (seulement « i accept »)")!!
        assertEquals("accept_only", k.id)
        assertEquals(listOf("i accept"), k.args)
    }

    @Test fun nombres_extraits() {
        assertEquals(listOf("45"), ReasonCatalog.parse("délai de 45 s dépassé")!!.args)
        assertEquals("budget_time", id("délai de 45 s presque écoulé avant la validation"))
        assertEquals("budget_actions", id("limite de 60 actions atteinte avant la validation"))
        assertEquals("max_actions", id("limite de 60 actions atteinte"))
        val r = ReasonCatalog.parse(
            "choix radio non sûrs : 1 « Bloquer » non sélectionné(s), 2 « Autoriser » encore sélectionné(s), 3 état(s) inconnu(s) ; validation bloquée",
        )!!
        assertEquals("radio_unsafe", r.id)
        assertEquals(listOf("1", "2", "3"), r.args)
    }

    @Test fun motif_compose_traduit_en_deux_parties() {
        val k = ReasonCatalog.parse("page des partenaires non ouverte faute de temps : validation laissée à l'utilisateur")!!
        assertEquals("unexplored_hold", k.id)
        assertEquals("vendors_not_opened", k.nested?.id)
        assertEquals("doubtful_hold", id("3 interrupteur(s) impossibles à lire ou à désactiver : validation laissée à l'utilisateur"))
        assertEquals("long_list_hold", id("liste trop longue : un choix encore accepté est visible ; validation laissée à l'utilisateur"))
        // Un début inconnu n'est jamais « deviné » : le texte d'origine reste affiché.
        assertNull(ReasonCatalog.parse("quelque chose d'inconnu : validation laissée à l'utilisateur"))
    }

    @Test fun motifs_des_constantes_du_moteur() {
        assertEquals("paywall", id(ConsentOrPay.REASON))
        assertEquals("guard_token", id(SafetyGuard.Verdict.Blocked("Tout accepter", "accepter").reason))
        assertEquals("guard_foreign", id(SafetyGuard.Verdict.Blocked("x", "foreign-script").reason))
        assertEquals("guard_sensitive", id(SafetyGuard.Verdict.Blocked("", "accessibility-data-sensitive").reason))
        assertEquals("guard_protected", id(SafetyGuard.Verdict.Blocked("", "protected-accessibility-subtree").reason))
        for (c in Confidence.entries) {
            assertEquals("prudent", id("reconnaissance ${c.label} : aucune action en mode prudent"))
        }
    }

    @Test fun tous_les_motifs_connus_sont_reconnus() {
        val all = listOf(
            "fenêtre quittée avant la fin",
            "fenêtre de consentement quittée pendant une capture incomplète",
            "capture incomplète : limite de nœuds ou de profondeur atteinte ; aucune action exécutée",
            "mode observation : aucune action (capture incomplète)",
            "reconnaissance probable : aucune action en mode prudent (capture incomplète)",
            "mode observation : aucune action",
            "plus rien ne bougeait à l'écran depuis 12 s",
            "défilement impossible : certains interrupteurs peuvent être restés hors de la capture",
            "défilement impossible : refus global utilisé, la fin de la liste n'a pas été lue",
            "2 interrupteur(s) visible(s) encore activé(s) et non traité(s) ; validation bloquée",
            "un choix radio coché n'est pas un refus reconnu ; validation bloquée",
            "liste des choix pas lue jusqu'en bas (temps ou défilements épuisés) : validation bloquée par prudence",
            "catégorie(s) de cookies non ouverte(s)",
            "liste des partenaires trop longue pour être lue en entier : non traitée",
            "état de 4 interrupteur(s) non communiqué ; validation bloquée par prudence",
            "aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence",
            "bouton de validation grisé par le site : un choix reste à faire à la main",
            "choix radio « Bloquer / Autoriser » non vérifié",
            "interrupteurs impossibles à décocher sans risque",
            "bouton de validation cliqué, mais la fenêtre ne s'est pas fermée",
            "bouton de validation introuvable, même après défilement jusqu'en bas de la fenêtre",
            "bouton de validation introuvable",
            "aucun bouton de refus reconnu",
            "5 interrupteur(s) restés activés",
            "choix radio non vérifiés : 1 « Bloquer » non sélectionné(s), 0 « Autoriser » sélectionné(s), 2 état(s) inconnu(s)",
            "3 interrupteur(s) non modifiables sans risque",
            "2 interrupteur(s) cliqué(s) dont le nouvel état n'a pas pu être relu",
            "état de 7 interrupteur(s) non communiqué par l'appli, non vérifiés",
            "fenêtre trop longue pour être relue en entier : refus non vérifié",
            "2 choix « Refuser » / « Non » cliqué(s) dont l'appli ne publie pas l'état",
            "1 interrupteur(s) vu(s) activé(s) sans avoir pu être désactivé(s)",
            "choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre",
            "règles modifiées pendant le traitement",
            "arrêt d'urgence demandé",
            "mise en pause par l'utilisateur",
            "service d'accessibilité interrompu par Android",
            "service d'accessibilité arrêté par Android",
            "Ancienne entrée : l'état final des préférences n'était pas enregistré.",
        )
        for (r in all) assertTrue("motif non reconnu : $r", ReasonCatalog.parse(r) != null)
    }

    @Test fun motif_vide_ou_inconnu() {
        assertNull(ReasonCatalog.parse(null))
        assertNull(ReasonCatalog.parse("  "))
        assertNull(ReasonCatalog.parse("motif d'une ancienne version"))
    }
}
