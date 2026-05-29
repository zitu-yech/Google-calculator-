package com.example.calculator

import kotlin.math.*

object MathEvaluator {
    fun evaluate(str: String, isDegree: Boolean = true): Double {
        // Clean up expression space/formatting to parse easily
        val cleanStr = str.replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .trim()

        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < cleanStr.length) cleanStr[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < cleanStr.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm() // addition
                    else if (eat('-'.code)) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor() // multiplication
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor // division
                    } else if (eat('%'.code)) {
                        x %= parseFactor() // modulo
                    } else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor() // unary plus
                if (eat('-'.code)) return -parseFactor() // unary minus

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) { // parentheses
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = cleanStr.substring(startPos, this.pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code || ch == 'π'.code || ch == 'e'.code) {
                    while (ch >= 'a'.code && ch <= 'z'.code || ch == 'π'.code || ch == 'e'.code) nextChar()
                    val func = cleanStr.substring(startPos, this.pos)
                    if (func == "pi" || func == "π") {
                        x = PI
                    } else if (func == "e") {
                        x = E
                    } else {
                        // Function of factor
                        val nextArg = parseFactor()
                        x = when (func) {
                            "sqrt" -> {
                                if (nextArg < 0.0) throw ArithmeticException("Square root of negative number")
                                sqrt(nextArg)
                            }
                            "sin" -> sin(if (isDegree) Math.toRadians(nextArg) else nextArg)
                            "cos" -> cos(if (isDegree) Math.toRadians(nextArg) else nextArg)
                            "tan" -> {
                                val rad = if (isDegree) Math.toRadians(nextArg) else nextArg
                                // Check for infinity edge cases in tan e.g. 90 deg
                                val cosVal = cos(rad)
                                if (abs(cosVal) < 1e-15) throw ArithmeticException("Tangent undefined")
                                tan(rad)
                            }
                            "log" -> {
                                if (nextArg <= 0.0) throw ArithmeticException("Log of negative/zero")
                                log10(nextArg)
                            }
                            "ln" -> {
                                if (nextArg <= 0.0) throw ArithmeticException("Ln of negative/zero")
                                ln(nextArg)
                            }
                            else -> throw RuntimeException("Unknown function: $func")
                        }
                    }
                } else {
                    throw RuntimeException("Unexpected character: " + ch.toChar())
                }

                if (eat('^'.code)) x = x.pow(parseFactor()) // exponentiation

                return x
            }
        }.parse()
    }
}
