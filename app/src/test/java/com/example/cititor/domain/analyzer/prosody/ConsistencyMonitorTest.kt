package com.example.cititor.domain.analyzer.prosody

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsistencyMonitorTest {

    @Test
    fun `test parameter smoothing within tolerance`() {
        val monitor = ConsistencyMonitor(windowSize = 3, maxDeviation = 0.10f) // 10% tolerance
        
        // Initial value (no history)
        val (s1, p1) = monitor.validateAndAdjust(1.0f, 1.0f)
        assertEquals(1.0f, s1, 0.01f)
        
        // Second value within tolerance (1.05 is within 10% of 1.0)
        val (s2, p2) = monitor.validateAndAdjust(1.05f, 1.05f)
        assertEquals(1.05f, s2, 0.01f)
        
        // Third value outside tolerance (1.30 is > 10% of avg(1.0, 1.05) = 1.025)
        // Max allowed = 1.025 * 1.10 = 1.1275
        val (s3, p3) = monitor.validateAndAdjust(1.30f, 1.30f)
        assertEquals(1.1275f, s3, 0.01f)
    }

    @Test
    fun `test reset clears history`() {
        val monitor = ConsistencyMonitor(windowSize = 3, maxDeviation = 0.10f)
        
        monitor.validateAndAdjust(2.0f, 2.0f) // History: [2.0]
        monitor.reset()
        
        // After reset, 1.0 should be accepted as is (no history to compare)
        val (s, p) = monitor.validateAndAdjust(1.0f, 1.0f)
        assertEquals(1.0f, s, 0.01f)
    }
}
