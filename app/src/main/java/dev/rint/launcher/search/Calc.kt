package dev.rint.launcher.search

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Small recursive-descent calculator: + - * / ^ %, parentheses, functions, pi/e, implicit ×. */
object Calc {
    private class P(val s: String) {
        var i = 0
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun eat(c: Char): Boolean {
            while (peek() == ' ') i++
            if (peek() == c) { i++; return true }
            return false
        }

        fun expr(): Double {
            var v = term()
            while (true) v = when {
                eat('+') -> v + term()
                eat('-') -> v - term()
                else -> return v
            }
        }

        fun term(): Double {
            var v = factor()
            while (true) {
                v = when {
                    eat('*') || eat('×') -> v * factor()
                    eat('/') || eat('÷') -> v / factor()
                    eat('%') -> v / 100
                    peek() == '(' || peek().isLetter() -> v * factor()
                    else -> return v
                }
            }
        }

        fun factor(): Double {
            if (eat('+')) return factor()
            if (eat('-')) return -factor()
            val b = atom()
            return if (eat('^')) b.pow(factor()) else b
        }

        fun atom(): Double {
            while (peek() == ' ') i++
            if (eat('(')) {
                val v = expr()
                eat(')')
                return v
            }
            val start = i
            if (peek().isDigit() || peek() == '.') {
                while (peek().isDigit() || peek() == '.' || peek() == ',') i++
                return s.substring(start, i).replace(",", "").toDouble()
            }
            if (peek().isLetter() || peek() == '√' || peek() == 'π') {
                if (eat('√')) return sqrt(factor())
                if (eat('π')) return PI
                while (peek().isLetter()) i++
                val name = s.substring(start, i).lowercase()
                return when (name) {
                    "pi" -> PI
                    "e" -> E
                    "sqrt" -> sqrt(factor())
                    "sin" -> sin(Math.toRadians(factor()))
                    "cos" -> cos(Math.toRadians(factor()))
                    "tan" -> tan(Math.toRadians(factor()))
                    "ln" -> ln(factor())
                    "log" -> log10(factor())
                    "abs" -> abs(factor())
                    else -> throw IllegalArgumentException(name)
                }
            }
            throw IllegalArgumentException("at $i")
        }
    }

    fun eval(input: String): Double? = runCatching {
        val p = P(input.trim())
        val v = p.expr()
        while (p.peek() == ' ') p.i++
        if (p.i != p.s.length || v.isNaN()) null else v
    }.getOrNull()

    /** Only treat input as math when it actually looks like math, so "tiktok" never evaluates. */
    fun looksLikeMath(q: String): Boolean {
        val t = q.trim()
        if (t.length < 2) return false
        val hasOp = t.any { it in "+-*/^%×÷√(" } || t.startsWith("sqrt") || t.contains("pi")
        return hasOp && t.any { it.isDigit() || it == 'π' }
    }

    fun format(v: Double): String {
        if (v.isInfinite()) return "∞"
        if (abs(v) >= 1e15 || (abs(v) < 1e-6 && v != 0.0)) return "%.6e".format(v)
        if (v == floor(v)) return "%,d".format(v.toLong())
        return "%,.10f".format(v).trimEnd('0').trimEnd('.')
    }
}

object Units {
    private data class U(val names: List<String>, val kind: String, val factor: Double, val offset: Double = 0.0)

    private val units = listOf(
        U(listOf("km", "kilometer", "kilometers"), "len", 1000.0),
        U(listOf("m", "meter", "meters"), "len", 1.0),
        U(listOf("cm", "centimeter", "centimeters"), "len", 0.01),
        U(listOf("mm"), "len", 0.001),
        U(listOf("mi", "mile", "miles"), "len", 1609.344),
        U(listOf("ft", "foot", "feet"), "len", 0.3048),
        U(listOf("in", "inch", "inches"), "len", 0.0254),
        U(listOf("kg", "kilo", "kilos"), "mass", 1.0),
        U(listOf("g", "gram", "grams"), "mass", 0.001),
        U(listOf("lb", "lbs", "pound", "pounds"), "mass", 0.45359237),
        U(listOf("oz", "ounce", "ounces"), "mass", 0.028349523125),
        U(listOf("c", "celsius", "°c"), "temp", 1.0, 0.0),
        U(listOf("f", "fahrenheit", "°f"), "temp", 5.0 / 9.0, -32.0),
        U(listOf("k", "kelvin"), "temp", 1.0, -273.15),
        U(listOf("l", "liter", "liters"), "vol", 1.0),
        U(listOf("ml"), "vol", 0.001),
        U(listOf("gal", "gallon", "gallons"), "vol", 3.785411784),
        U(listOf("kmh", "km/h"), "speed", 1.0),
        U(listOf("mph"), "speed", 1.609344),
    )

    private fun find(n: String) = units.firstOrNull { n.lowercase() in it.names }

    fun convert(q: String): String? {
        val m = Regex("""^\s*(-?[\d.,]+)\s*([a-zA-Z°/]+)\s+(?:to|in)\s+([a-zA-Z°/]+)\s*$""").find(q) ?: return null
        val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val a = find(m.groupValues[2]) ?: return null
        val b = find(m.groupValues[3]) ?: return null
        if (a.kind != b.kind) return null
        val out = if (a.kind == "temp") {
            val c = (v + a.offset) * a.factor
            c / b.factor - b.offset
        } else v * a.factor / b.factor
        return "${Calc.format(v)} ${m.groupValues[2]} = ${Calc.format((out * 1000).let { kotlin.math.round(it) } / 1000)} ${m.groupValues[3]}"
    }
}
