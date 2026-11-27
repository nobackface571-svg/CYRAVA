package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lecture plus rapide (22/11 au soir) : pendant une longue liste, seule la fenêtre de cookies est
 * relue (chaîne de ses ancêtres + le panneau entier). Mode prudent : panneau différent ou décision
 * de fermer la fenêtre → lecture complète d'abord.
 */
class LecturePartielleTest {
    private val rules = TestRules.rules

    /** Même travail que le service Android : chaîne des ancêtres du panneau (sans leurs autres enfants), puis le panneau entier. */
    private fun partialOf(root: UiNode, panel: UiNode): Pair<SnapshotNode, Int> {
        fun copy(n: UiNode) = SnapshotNode(
            className = n.className, text = n.text, contentDescription = n.contentDescription, viewId = n.viewId,
            isClickable = n.isClickable, isSelected = n.isSelected, isCheckable = n.isCheckable, isChecked = n.isChecked,
            isEnabled = n.isEnabled, isScrollable = n.isScrollable, isPassword = n.isPassword,
            isVisibleToUser = n.isVisibleToUser, bounds = n.bounds, isEditable = n.isEditable, isExpandable = n.isExpandable,
            stateDescription = n.stateDescription, supplementalDescription = n.supplementalDescription,
        )
        fun deep(n: UiNode): SnapshotNode = copy(n).also { c -> n.children.forEach { c.addChild(deep(it)) } }
        val chain = generateSequence(panel.parent) { it.parent }.toList().reversed()
        var top: SnapshotNode? = null
        var cursor: SnapshotNode? = null
        for (anc in chain) {
            val c = copy(anc)
            if (cursor == null) top = c else cursor.addChild(c)
            cursor = c
        }
        val p = deep(panel)
        if (cursor == null) top = p else cursor.addChild(p)
        top!!.assignKeys()
        return top to chain.size
    }

    /** Longue liste dans une WebView, validation tout en bas (Chrome expose toute la liste). */
    private class LongList(val n: Int = 30) : FakeApp("com.android.chrome") {
        val on = BooleanArray(n) { true }
        var saved = false
        var accepted = false
        override fun build(): SnapshotNode = if (saved) {
            Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
        } else {
            Tree.window(
                node("android.widget.FrameLayout", viewId = "com.android.chrome:id/toolbar", children = listOf(text("exemple.fr"))),
                node("android.webkit.WebView", children = listOf(
                    text("Article du jour, très long, sans rapport avec les cookies"),
                    node(viewId = "didomi-popup", children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                        Tree.group(*Array(n) { i ->
                            Tree.group(text("Partenaire $i"), Tree.switch("Partenaire $i", checked = on[i]).onClick { on[i] = false })
                        }),
                        button("Tout accepter").onClick { accepted = true },
                        button("Enregistrer vos préférences et fermer").onClick { saved = true },
                    )),
                )),
            )
        }
    }

    private data class Run(val outcome: Outcome?, val partialReads: Int, val closingOnPartial: Boolean)

    /** Comme le service : lecture partielle après une étape de décochage ou de défilement, une complète toutes les 4. */
    private fun runWithPartialReads(app: FakeApp, engine: RefusalEngine, clock: FakeClock): Run {
        var listWork = false
        var streak = 0
        var partialReads = 0
        var closingOnPartial = false
        var panelKey = -1
        repeat(1000) {
            val full = app.render()
            val usePartial = engine.isActive && listWork && panelKey >= 0 && streak < 4 && !engine.consumeFullCaptureRequest()
            val screen = if (usePartial) {
                // Le panneau repéré à la dernière lecture complète (le service garde son nœud Android).
                val fresh = full.walk().first { it.key == panelKey }
                val (root, top) = partialOf(full, fresh)
                streak++
                partialReads++
                Screen(app.packageName, root, partialTopKey = top)
            } else {
                streak = 0
                Screen(app.packageName, full)
            }
            val step = engine.next(screen)
            if (!screen.isPartial) panelKey = engine.lastAnalysis?.panelRoot?.key ?: -1
            when (step) {
                Step.Idle -> return Run(null, partialReads, closingOnPartial)
                is Step.Wait -> clock.advance(step.delayMs)
                is Step.Act -> {
                    if (screen.isPartial && step.ops.any { it is Op.Click && !it.isToggle }) closingOnPartial = true
                    step.ops.forEach(app::perform)
                    listWork = step.ops.all { (it is Op.Click && it.isToggle) || it is Op.Scroll || it is Op.Swipe || it is Op.ShowOnScreen }
                    clock.advance(250)
                }
                is Step.Done -> return Run(step.outcome, partialReads, closingOnPartial)
            }
        }
        return Run(null, partialReads, closingOnPartial)
    }

    @Test
    fun `meme panneau en lecture partielle et complete - memes empreintes`() {
        val app = LongList()
        val full = app.render()
        val a = ScreenAnalyzer(rules).analyze(full, webContentOnly = true)
        val (root, top) = partialOf(full, a.panelRoot!!)
        val b = ScreenAnalyzer(rules).analyze(root, webContentOnly = true)
        assertEquals(top, b.panelRoot!!.key)
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.contentSignature, b.contentSignature)
        assertEquals(a.lightFingerprint, b.lightFingerprint)
        assertEquals(a.toggleStates.keys, b.toggleStates.keys)
        assertTrue("la lecture partielle doit être plus petite", root.walk().count() < full.walk().count())
    }

    @Test
    fun `longue liste - meme resultat, lectures partielles utilisees, fermeture jamais decidee sur une lecture partielle`() {
        val reference = LongList().let { app ->
            val clock = FakeClock()
            runEngine(app, RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }, clock).outcome!!
        }
        val app = LongList()
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        val r = runWithPartialReads(app, engine, clock)
        val o = r.outcome!!
        assertTrue("trop peu de lectures partielles : ${r.partialReads}", r.partialReads > 0)
        assertFalse("validation décidée sur une lecture partielle", r.closingOnPartial)
        assertFalse(app.accepted)
        assertTrue(app.on.none { it })
        assertTrue(app.saved)
        assertEquals(reference.status, o.status)
        assertEquals(reference.togglesOff, o.togglesOff)
    }

    @Test
    fun `lecture partielle qui ne redonne pas le meme panneau - rien n'est decide, lecture complete demandee`() {
        val app = LongList()
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        val first = engine.next(Screen(app.packageName, app.render()))
        assertTrue(first is Step.Act)
        (first as Step.Act).ops.forEach(app::perform)
        clock.advance(250)
        val full = app.render()
        // Mauvais panneau : un seul groupe de la liste.
        val wrong = full.walk().first { it.text == "Partenaire 3" }.parent!!
        val (root, top) = partialOf(full, wrong)
        val step = engine.next(Screen(app.packageName, root, partialTopKey = top))
        assertEquals(Step.Wait(0), step)
        assertTrue(engine.consumeFullCaptureRequest())
        assertFalse("la demande n'est rendue qu'une fois", engine.consumeFullCaptureRequest())
    }

    @Test
    fun `lecture partielle hors session - jamais utilisee pour commencer`() {
        val app = LongList()
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }
        val full = app.render()
        val panel = ScreenAnalyzer(rules).analyze(full, webContentOnly = true).panelRoot!!
        val (root, top) = partialOf(full, panel)
        assertEquals(Step.Wait(0), engine.next(Screen(app.packageName, root, partialTopKey = top)))
        assertFalse(engine.isActive)
        assertTrue(engine.consumeFullCaptureRequest())
    }
}
