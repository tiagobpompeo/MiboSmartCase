package br.com.pompeo.casa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import br.com.pompeo.casa.domain.TokenFormat;
import org.junit.Test;

// Mesmas regras do TokenFormatTest (Kotlin), chamadas do Java: TokenFormat.mask(...) só compila
// como estático porque as funções são @JvmStatic; sem isso seria TokenFormat.INSTANCE.mask(...).
public class TokenFormatJavaTest {
    private static final String HIDDEN = "••••";
    private static final String FAKE_TOKEN = "Ot_abcdefghwxyz";

    @Test
    public void maskShowsOnlyFiveFirstAndFourLast() {
        // Arrange: espaços nas pontas para provar que a máscara normaliza antes.
        String token = "  " + FAKE_TOKEN + "  ";
        // Act
        String masked = TokenFormat.mask(token);
        // Assert
        assertEquals("Ot_ab…wxyz", masked);
    }

    @Test
    public void maskHidesShortAndNullTokens() {
        // Arrange: abaixo de 12 caracteres a máscara não mostra nada, para não vazar quase o token inteiro.
        String shortToken = "Ot_abc_0001";
        // Act
        String maskedShort = TokenFormat.mask(shortToken);
        String maskedNull = TokenFormat.mask(null);
        // Assert
        assertEquals(HIDDEN, maskedShort);
        assertEquals(HIDDEN, maskedNull);
    }

    @Test
    public void plausibleNeedsEightCharsWithoutInnerSpaces() {
        // Arrange: o limite é 8 caracteres depois do trim.
        String atLimit = "  Ot_abc12  ";
        String tooShort = "Ot_abc1";
        String innerSpace = "Ot_abc 12345";
        // Act
        boolean atLimitPlausible = TokenFormat.isPlausible(atLimit);
        boolean tooShortPlausible = TokenFormat.isPlausible(tooShort);
        boolean innerSpacePlausible = TokenFormat.isPlausible(innerSpace);
        boolean nullPlausible = TokenFormat.isPlausible(null);
        // Assert
        assertTrue(atLimitPlausible);
        assertFalse(tooShortPlausible);
        assertFalse(innerSpacePlausible);
        assertFalse(nullPlausible);
    }

    @Test
    public void normalizeTrimsAndTurnsNullIntoEmpty() {
        // Arrange
        String raw = "  Ot_abc_0001\n";
        // Act
        String normalized = TokenFormat.normalize(raw);
        String fromNull = TokenFormat.normalize(null);
        // Assert
        assertEquals("Ot_abc_0001", normalized);
        assertEquals("", fromNull);
    }
}
