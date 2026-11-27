package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticThrottleTest {
    @Test fun same_window_is_blocked_after_first_diagnostic() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire("com.example.app", 11, 1_000L))
        assertFalse(throttle.tryAcquire("com.example.app", 11, 1_001L))
    }

    @Test fun different_signatures_in_same_app_have_independent_windows() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire("com.example.app", 11, 1_000L))
        assertTrue(throttle.tryAcquire("com.example.app", 22, 1_001L))
        assertFalse(throttle.tryAcquire("com.example.app", 11, 1_002L))
    }

    @Test fun different_apps_have_independent_windows() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire("com.example.a", 1, 1_000L))
        assertTrue(throttle.tryAcquire("com.example.b", 1, 1_001L))
        assertFalse(throttle.tryAcquire("com.example.a", 1, 2_000L))
    }

    @Test fun diagnostic_is_allowed_again_after_ten_minutes() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire("com.example.app", 1, 1_000L))
        assertTrue(throttle.tryAcquire("com.example.app", 1, 601_000L))
    }

    @Test fun clock_rollback_does_not_allow_a_burst() {
        val throttle = DiagnosticThrottle()
        assertTrue(throttle.tryAcquire("com.example.app", 1, 10_000L))
        assertFalse(throttle.tryAcquire("com.example.app", 1, 9_000L))
    }

    @Test fun blank_package_is_rejected() {
        assertFalse(DiagnosticThrottle().tryAcquire(" ", 1, 1L))
    }

    @Test fun tracked_window_count_is_bounded() {
        val throttle = DiagnosticThrottle(intervalMs = 10L, maxEntries = 2)
        assertTrue(throttle.tryAcquire("a", 1, 1L))
        assertTrue(throttle.tryAcquire("b", 1, 1L))
        assertTrue(throttle.tryAcquire("c", 1, 1L))
        assertTrue(throttle.tryAcquire("a", 1, 2L))
    }

    @Test fun concurrent_events_acquire_one_slot_for_same_window() {
        val throttle = DiagnosticThrottle(intervalMs = 600_000)
        val workers = 24
        val ready = java.util.concurrent.CountDownLatch(workers)
        val start = java.util.concurrent.CountDownLatch(1)
        val successes = java.util.concurrent.atomic.AtomicInteger(0)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) {
                pool.submit {
                    ready.countDown()
                    check(start.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    if (throttle.tryAcquire("com.example.browser", 42, 1_000_000L)) successes.incrementAndGet()
                }
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
            org.junit.Assert.assertEquals(1, successes.get())
        } finally {
            pool.shutdownNow()
        }
    }
}
