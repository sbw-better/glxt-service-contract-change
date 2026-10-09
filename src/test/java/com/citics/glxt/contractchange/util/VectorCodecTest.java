package com.citics.glxt.contractchange.util;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class VectorCodecTest {
    @Test
    public void shouldPreserveEveryFloatBitIncludingExtremesAndNegativeZero() {
        float[] values = {0F, -0F, 1F, -2.1234567F, Float.MIN_VALUE,
                Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE, 1E-30F, 1E30F};
        assertBits(values, VectorCodec.decode(VectorCodec.encode(values), values.length));
    }

    @Test
    public void shouldRoundTrip1024RandomFiniteFloatsWithoutRounding() {
        Random random = new Random(20261009L);
        float[] values = new float[1024];
        for (int i = 0; i < values.length; i++) {
            do { values[i] = Float.intBitsToFloat(random.nextInt()); }
            while (!Float.isFinite(values[i]));
        }
        String json = VectorCodec.encode(values);
        assertTrue(json.length() > 4000);
        assertBits(values, VectorCodec.decode(json, 1024));
    }

    @Test
    public void shouldAcceptNumbersAndScientificNotationWithWhitespace() {
        assertBits(new float[]{-1F, 0F, -0F, 0.001F, 200F},
                VectorCodec.decode(" \n [-1,0,-0,1e-3,2E+2] \t", 5));
    }

    @Test
    public void shouldRejectInvalidEncodings() {
        reject(() -> VectorCodec.encode(null));
        reject(() -> VectorCodec.encode(new float[0]));
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            reject(() -> VectorCodec.encode(new float[]{value}));
        }
    }

    @Test
    public void shouldRejectInvalidJsonTypesDimensionsOverflowAndTrailingContent() {
        for (String json : new String[]{null, "", " ", "null", "{}", "1", "[]", "[",
                "[1,]", "[\"1\"]", "[null]", "[true]", "[[1]]", "[1,2]",
                "[1e39]", "[-1e1000]", "[NaN]", "[Infinity]", "[-Infinity]",
                "[1] [2]", "[1] true", "[1]x"}) {
            reject(() -> VectorCodec.decode(json, 1));
        }
        reject(() -> VectorCodec.decode("[1]", 2));
        reject(() -> VectorCodec.decode("[1]", 0));
        reject(() -> VectorCodec.decode("[1]", -1));
    }

    @Test
    public void shouldLeaveZeroVectorRejectionToNormalization() {
        float[] zeros = VectorCodec.decode(VectorCodec.encode(new float[]{0F, -0F}), 2);
        reject(() -> VectorUtils.normalize(zeros));
    }

    private static void assertBits(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("component " + i, Float.floatToRawIntBits(expected[i]),
                    Float.floatToRawIntBits(actual[i]));
        }
    }

    private static void reject(Runnable action) {
        try { action.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* strict rejection */ }
    }
}
