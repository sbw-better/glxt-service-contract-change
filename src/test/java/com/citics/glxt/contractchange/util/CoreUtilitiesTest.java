package com.citics.glxt.contractchange.util;

import com.citics.glxt.contractchange.common.ContractChangeBusinessException;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CoreUtilitiesTest {
    @Test
    public void shouldNormalizeTextAndHashDeterministically() {
        String first = ContractTextNormalizer.normalize("  甲方\r\n 不得转让  ");
        String second = ContractTextNormalizer.normalize("甲方 不得转让");
        assertEquals("甲方 不得转让", first);
        assertEquals(HashUtils.sha256(first), HashUtils.sha256(second));
    }

    @Test
    public void shouldCanonicalizeAndParseChangeTypeCodes() {
        String codes = ChangeTypeCodes.canonicalize(" TYPE03；TYPE01,TYPE03 ; TYPE02 ");
        assertEquals("TYPE01;TYPE02;TYPE03", codes);
        assertEquals(Arrays.asList("TYPE01", "TYPE02", "TYPE03"), ChangeTypeCodes.parse(codes));
    }

    @Test(expected = ContractChangeBusinessException.class)
    public void shouldRejectCodeContainingWhitespace() {
        ChangeTypeCodes.canonicalize("TYPE 01");
    }

    @Test
    public void shouldRoundTripLittleEndianVectorAndNormalize() {
        float[] vector = new float[]{3F, 4F, 0F};
        VectorUtils.normalize(vector);
        assertEquals(0.6D, vector[0], 0.000001D);
        assertEquals(0.8D, vector[1], 0.000001D);
        byte[] bytes = VectorCodec.encode(vector);
        assertEquals(12, bytes.length);
        assertArrayEquals(vector, VectorCodec.decode(bytes, 3), 0.000001F);
        assertEquals(1D, VectorUtils.dot(vector, vector), 0.000001D);
    }
}
