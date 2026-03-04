package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.function.Function;

/**
 * Math/calculation skill for the AI agent.
 * Evaluates mathematical expressions safely.
 */
@Configuration
public class MathSkill {

    private static final Logger log = LoggerFactory.getLogger(MathSkill.class);

    public record MathRequest(String expression) {}
    public record MathResponse(String expression, String result, boolean success, String error) {}

    @Bean
    @Description("Evaluate a mathematical expression. Supports basic arithmetic (+, -, *, /), parentheses, and common math operations. Example: '(2 + 3) * 4 / 2'")
    public Function<MathRequest, MathResponse> evaluateMath() {
        return request -> {
            log.info("Skill invoked: evaluateMath({})", request.expression());
            try {
                String expr = request.expression().trim();
                // Sanitize: only allow numbers, operators, parentheses, dots, spaces
                if (!expr.matches("[0-9+\\-*/().\\s%^]+")) {
                    return new MathResponse(expr, null, false,
                            "Invalid expression. Only numbers and basic operators (+, -, *, /, %, ^, parentheses) are allowed.");
                }

                // Replace ^ with Math.pow pattern for simple cases
                // Use a simple recursive descent parser for safety
                double result = evaluateExpression(expr);
                String formatted = (result == Math.floor(result) && !Double.isInfinite(result))
                        ? String.valueOf((long) result)
                        : String.valueOf(result);

                return new MathResponse(expr, formatted, true, null);
            } catch (Exception e) {
                log.error("Math evaluation failed: {}", e.getMessage());
                return new MathResponse(request.expression(), null, false, e.getMessage());
            }
        };
    }

    /**
     * Simple expression evaluator supporting +, -, *, /, parentheses.
     */
    private double evaluateExpression(String expr) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("Unexpected character: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else if (eat('%')) x %= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new RuntimeException("Missing closing parenthesis");
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected character: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }
}
