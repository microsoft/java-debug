package com.microsoft.java.debug.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BreakpointTest {
    @Test
    public void testToNoneGeneric() {
        assertEquals("Ljava.util.List;", Breakpoint.toNoneGeneric("Ljava.util.List<java.lang.String;>;"));
        assertEquals("(Ljava/util/Map;)Ljava/util/Map;", Breakpoint.toNoneGeneric(
                "(Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/Integer;>;>;)Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/Integer;>;>;"));
        assertEquals("(Ljava/util/Map;)Ljava/util/Map;",
                Breakpoint.toNoneGeneric(
                        "(Ljava/util/Map<Ljava/util/List<Ljava/lang/Integer;>;Ljava/util/List<Ljava/lang/Integer;>;>;)Ljava/util/Map<Ljava/util/List<Ljava/lang/Integer;>;Ljava/util/List<Ljava/lang/Integer;>;>;"));
    }
}
