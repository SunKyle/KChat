package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计算器工具
 *
 * 解析并计算数学表达式，支持：
 * <ul>
 *   <li>基本运算：+、-、*、/、%</li>
 *   <li>括号优先级</li>
 *   <li>幂运算：^</li>
 *   <li>函数：sqrt, abs, sin, cos, tan, log, ln, exp, floor, ceil, round</li>
 *   <li>常量：pi, e</li>
 * </ul>
 *
 * <p>LLM 做数学计算容易出错，此工具保证精度和正确性。
 */
@Slf4j
@Component
public class CalculatorTool implements ToolComponent {

    @Tool("""
            计算数学表达式并返回结果。
            支持四则运算 (+, -, *, /, %)、括号、幂运算 (^)、
            数学函数 (sqrt, abs, sin, cos, tan, log, ln, exp, floor, ceil, round)
            和常量 (pi, e)。
            例如: "2 + 3 * 4", "sqrt(16) + log(100)", "2^10"
            """)
    public String calculate(String expression) {
        if (expression == null || expression.isBlank()) {
            return "错误：表达式不能为空";
        }

        expression = expression.trim();

        try {
            double result = new ExpressionParser(expression).parse();
            if (Double.isNaN(result)) {
                return "错误：计算结果为 NaN，请检查表达式";
            }
            if (Double.isInfinite(result)) {
                return "错误：计算结果为无穷大，请检查表达式";
            }

            // 格式化输出：整数显示为整数，否则保留最多 10 位小数
            if (result == Math.floor(result) && Math.abs(result) < 1e15) {
                return "计算结果：" + (long) result;
            }
            return "计算结果：" + String.format("%.10g", result);

        } catch (Exception e) {
            return "错误：表达式解析失败 - " + e.getMessage();
        }
    }

    /**
     * 递归下降解析器，支持运算符优先级：
     * 1. 加减 (expr)
     * 2. 乘除模 (term)
     * 3. 一元正负 (unary)
     * 4. 幂运算 (power)
     * 5. 函数/常量/数字/括号 (factor)
     */
    private static class ExpressionParser {
        private final String expr;
        private int pos;

        ExpressionParser(String expr) {
            this.expr = expr.replaceAll("\\s+", "");
            this.pos = 0;
        }

        double parse() {
            double result = parseExpr();
            if (pos < expr.length()) {
                throw new IllegalArgumentException(
                        "第 " + (pos + 1) + " 个字符处有无法解析的内容: '" + expr.charAt(pos) + "'");
            }
            return result;
        }

        private double parseExpr() {
            double result = parseTerm();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '+') {
                    pos++;
                    result += parseTerm();
                } else if (op == '-') {
                    pos++;
                    result -= parseTerm();
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseTerm() {
            double result = parseUnary();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '*') {
                    pos++;
                    result *= parseUnary();
                } else if (op == '/') {
                    pos++;
                    double divisor = parseUnary();
                    if (divisor == 0) throw new ArithmeticException("除数不能为零");
                    result /= divisor;
                } else if (op == '%') {
                    pos++;
                    double divisor = parseUnary();
                    if (divisor == 0) throw new ArithmeticException("除数不能为零");
                    result %= divisor;
                } else {
                    break;
                }
            }
            return result;
        }

        private double parseUnary() {
            if (pos < expr.length() && expr.charAt(pos) == '+') {
                pos++;
                return parseUnary();
            }
            if (pos < expr.length() && expr.charAt(pos) == '-') {
                pos++;
                return -parseUnary();
            }
            return parsePower();
        }

        private double parsePower() {
            double base = parseFactor();
            if (pos < expr.length() && expr.charAt(pos) == '^') {
                pos++;
                double exponent = parseUnary();
                return Math.pow(base, exponent);
            }
            return base;
        }

        private double parseFactor() {
            if (pos >= expr.length()) {
                throw new IllegalArgumentException("表达式不完整");
            }

            char ch = expr.charAt(pos);

            // 括号
            if (ch == '(') {
                pos++;
                double result = parseExpr();
                if (pos >= expr.length() || expr.charAt(pos) != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                pos++;
                return result;
            }

            // 函数或常量
            if (Character.isLetter(ch)) {
                String name = parseName();

                // 常量
                switch (name) {
                    case "pi": return Math.PI;
                    case "e": return Math.E;
                }

                // 函数
                if (pos < expr.length() && expr.charAt(pos) == '(') {
                    pos++;
                    double arg = parseExpr();
                    if (pos >= expr.length() || expr.charAt(pos) != ')') {
                        throw new IllegalArgumentException("函数 " + name + " 缺少右括号");
                    }
                    pos++;
                    return applyFunction(name, arg);
                }

                throw new IllegalArgumentException("未知标识符: " + name);
            }

            // 数字
            if (Character.isDigit(ch) || ch == '.') {
                return parseNumber();
            }

            throw new IllegalArgumentException("无法解析的字符: '" + ch + "'");
        }

        private String parseName() {
            int start = pos;
            while (pos < expr.length() && (Character.isLetterOrDigit(expr.charAt(pos)) || expr.charAt(pos) == '_')) {
                pos++;
            }
            return expr.substring(start, pos).toLowerCase();
        }

        private double parseNumber() {
            int start = pos;
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                pos++;
            }
            // 科学计数法
            if (pos < expr.length() && (expr.charAt(pos) == 'e' || expr.charAt(pos) == 'E')) {
                pos++;
                if (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = expr.substring(start, pos);
            try {
                return Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无效的数字: " + numStr);
            }
        }

        private double applyFunction(String name, double arg) {
            return switch (name) {
                case "sqrt" -> Math.sqrt(arg);
                case "abs" -> Math.abs(arg);
                case "sin" -> Math.sin(arg);
                case "cos" -> Math.cos(arg);
                case "tan" -> Math.tan(arg);
                case "log" -> Math.log10(arg);
                case "ln" -> Math.log(arg);
                case "exp" -> Math.exp(arg);
                case "floor" -> Math.floor(arg);
                case "ceil" -> Math.ceil(arg);
                case "round" -> (double) Math.round(arg);
                case "asin" -> Math.asin(arg);
                case "acos" -> Math.acos(arg);
                case "atan" -> Math.atan(arg);
                default -> throw new IllegalArgumentException("未知函数: " + name);
            };
        }
    }
}