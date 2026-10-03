package com.commerce.cs.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RrfFusionTest {
    @Test
    void itemRankedFirstInMoreListsWins() {
        List<RrfFusion.Scored> fused = RrfFusion.fuse(List.of(
                List.of("URI", "FLU"),
                List.of("URI"),
                List.of("GASTRO")));
        assertEquals("URI", fused.get(0).key());
        assertEquals("GASTRO", fused.get(1).key());
        assertEquals("FLU", fused.get(2).key());
        assertTrue(fused.get(0).score() > fused.get(1).score());
    }

    @Test
    void duplicateInOneListCountsOnce() {
        List<RrfFusion.Scored> fused = RrfFusion.fuse(List.of(List.of("URI", "URI", "FLU")));
        assertEquals(2, fused.size());
        assertEquals("URI", fused.get(0).key());
        assertEquals(1.0 / 61, fused.get(0).score(), 1e-9);
        assertEquals(1.0 / 62, fused.get(1).score(), 1e-9);
    }
}
