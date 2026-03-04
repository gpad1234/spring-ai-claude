package com.example.springai.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathSkillTest {

    private final MathSkill mathSkill = new MathSkill();

    @Test
    void evaluateSimpleAddition() {
        var fn = mathSkill.evaluateMath();
        var result = fn.apply(new MathSkill.MathRequest("2 + 3"));
        assertTrue(result.success());
        assertEquals("5", result.result());
    }

    @Test
    void evaluateComplexExpression() {
        var fn = mathSkill.evaluateMath();
        var result = fn.apply(new MathSkill.MathRequest("(10 + 5) * 2 / 3"));
        assertTrue(result.success());
        assertEquals("10", result.result());
    }

    @Test
    void evaluateExponentiation() {
        var fn = mathSkill.evaluateMath();
        var result = fn.apply(new MathSkill.MathRequest("2^10"));
        assertTrue(result.success());
        assertEquals("1024", result.result());
    }

    @Test
    void rejectInvalidExpression() {
        var fn = mathSkill.evaluateMath();
        var result = fn.apply(new MathSkill.MathRequest("System.exit(0)"));
        assertFalse(result.success());
        assertNotNull(result.error());
    }
}
