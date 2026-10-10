package com.mide.trifedetect

import kotlin.math.absoluteValue
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 表达式抽象语法树。既用于求值, 也用于图形化编辑器的渲染与增删。
 */
sealed interface FNode {

    /** 数字常量 */
    data class Num(val value: Double) : FNode

    /** 变量: "cal" (设备校准值) 或 "val" (采集结果) */
    data class Variable(val name: String) : FNode

    /** 一元运算: 负号 "-" 或函数名 (abs/sqrt/sin/...) */
    data class Unary(val op: String, val child: FNode) : FNode

    /** 二元运算: + - * / ^ ; 或二元函数 min/max */
    data class Binary(val op: String, val left: FNode, val right: FNode) : FNode
}

class FormulaException(message: String) : Exception(message)

/**
 * 一个轻量的、无依赖的四则运算 + 基本初等函数表达式引擎。
 * 变量固定为 `cal` 与 `val`; 常量 `pi`、`e`。
 */
object Formula {

    /** 一元函数白名单 */
    val UNARY_FUNCTIONS = listOf(
        "abs", "sqrt", "exp", "ln", "log", "log2",
        "sin", "cos", "tan", "asin", "acos", "atan",
        "floor", "ceil", "round"
    )

    /** 二元函数白名单 */
    val BINARY_FUNCTIONS = listOf("min", "max")

    val VARIABLES = listOf("cal", "val")

    val OPERATORS = listOf("+", "-", "*", "/", "^")

    const val DEFAULT_EXPRESSION = "val - cal"

    // ---------------------------------------------------------------- lexer

    private enum class TokType { NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA, END }

    private data class Tok(val type: TokType, val text: String, val pos: Int)

    private class Lexer(private val src: String) {
        private var i = 0
        fun tokens(): List<Tok> {
            val list = ArrayList<Tok>()
            while (i < src.length) {
                val c = src[i]
                when {
                    c.isWhitespace() -> i++
                    c.isDigit() || c == '.' -> list.add(number())
                    c.isLetter() || c == '_' -> list.add(ident())
                    c == '(' -> { list.add(Tok(TokType.LPAREN, "(", i)); i++ }
                    c == ')' -> { list.add(Tok(TokType.RPAREN, ")", i)); i++ }
                    c == ',' -> { list.add(Tok(TokType.COMMA, ",", i)); i++ }
                    c == '+' || c == '-' || c == '*' || c == '/' || c == '^' ->
                        { list.add(Tok(TokType.OP, c.toString(), i)); i++ }
                    else -> throw FormulaException("非法字符 '$c' (位置 ${i + 1})")
                }
            }
            list.add(Tok(TokType.END, "", src.length))
            return list
        }

        private fun number(): Tok {
            val start = i
            var seenDot = false
            while (i < src.length && (src[i].isDigit() || src[i] == '.')) {
                if (src[i] == '.') {
                    if (seenDot) throw FormulaException("数字格式错误: ${src.substring(start, i + 1)}")
                    seenDot = true
                }
                i++
            }
            return Tok(TokType.NUMBER, src.substring(start, i), start)
        }

        private fun ident(): Tok {
            val start = i
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            return Tok(TokType.IDENT, src.substring(start, i), start)
        }
    }

    // --------------------------------------------------------------- parser

    private class Parser(private val toks: List<Tok>) {
        private var p = 0
        private fun peek() = toks[p]
        private fun next() = toks[p++]
        private fun expect(type: TokType, what: String): Tok {
            if (peek().type != type) throw FormulaException("缺少 $what (位置 ${peek().pos + 1})")
            return next()
        }

        fun parse(): FNode {
            if (peek().type == TokType.END) throw FormulaException("表达式为空")
            val node = expr()
            if (peek().type != TokType.END)
                throw FormulaException("多余的内容 '${peek().text}' (位置 ${peek().pos + 1})")
            return node
        }

        private fun expr(): FNode {
            var left = term()
            while (peek().type == TokType.OP && (peek().text == "+" || peek().text == "-")) {
                val op = next().text
                left = FNode.Binary(op, left, term())
            }
            return left
        }

        private fun term(): FNode {
            var left = power()
            while (peek().type == TokType.OP && (peek().text == "*" || peek().text == "/")) {
                val op = next().text
                left = FNode.Binary(op, left, power())
            }
            return left
        }

        private fun power(): FNode {
            val base = unary()
            if (peek().type == TokType.OP && peek().text == "^") {
                next()
                return FNode.Binary("^", base, power()) // 右结合
            }
            return base
        }

        private fun unary(): FNode {
            if (peek().type == TokType.OP && (peek().text == "-" || peek().text == "+")) {
                val op = next().text
                return if (op == "-") FNode.Unary("-", unary()) else unary()
            }
            return primary()
        }

        private fun primary(): FNode {
            val t = peek()
            when (t.type) {
                TokType.NUMBER -> { next(); return FNode.Num(t.text.toDoubleOrNull() ?: throw FormulaException("数字格式错误: ${t.text}")) }
                TokType.LPAREN -> { next(); val e = expr(); expect(TokType.RPAREN, "')'"); return e }
                TokType.IDENT -> return ident(t)
                else -> throw FormulaException("意外的符号 '${t.text}' (位置 ${t.pos + 1})")
            }
        }

        private fun ident(t: Tok): FNode {
            val name = t.text
            next() // 消费标识符
            // 变量 / 常量
            if (peek().type != TokType.LPAREN) {
                when (name.lowercase()) {
                    "cal", "val" -> return FNode.Variable(name.lowercase())
                    "pi" -> return FNode.Num(Math.PI)
                    "e" -> return FNode.Num(Math.E)
                    else -> throw FormulaException("未知变量 '$name' (位置 ${t.pos + 1})")
                }
            }
            // 函数调用
            expect(TokType.LPAREN, "'('")
            val args = ArrayList<FNode>()
            if (peek().type != TokType.RPAREN) {
                args.add(expr())
                while (peek().type == TokType.COMMA) { next(); args.add(expr()) }
            }
            expect(TokType.RPAREN, "')'")
            val fn = name.lowercase()
            return when {
                fn in UNARY_FUNCTIONS -> {
                    if (args.size != 1) throw FormulaException("函数 $fn 需要 1 个参数")
                    FNode.Unary(fn, args[0])
                }
                fn in BINARY_FUNCTIONS -> {
                    if (args.size != 2) throw FormulaException("函数 $fn 需要 2 个参数")
                    FNode.Binary(fn, args[0], args[1])
                }
                else -> throw FormulaException("未知函数 '$name'")
            }
        }
    }

    fun parse(expression: String): FNode = Parser(Lexer(expression).tokens()).parse()

    // ------------------------------------------------------------- evaluate

    fun evaluate(node: FNode, cal: Double = 0.0, value: Double = 0.0): Double =
        when (node) {
            is FNode.Num -> node.value
            is FNode.Variable -> if (node.name == "cal") cal else value
            is FNode.Unary -> applyUnary(node.op, evaluate(node.child, cal, value))
            is FNode.Binary -> applyBinary(node.op, evaluate(node.left, cal, value), evaluate(node.right, cal, value))
        }

    private fun applyUnary(op: String, x: Double): Double = when (op) {
        "-" -> -x
        "abs" -> x.absoluteValue
        "sqrt" -> sqrt(x)
        "exp" -> exp(x)
        "ln" -> ln(x)
        "log" -> log10(x)
        "log2" -> kotlin.math.log2(x)
        "sin" -> sin(x)
        "cos" -> cos(x)
        "tan" -> tan(x)
        "asin" -> asin(x)
        "acos" -> acos(x)
        "atan" -> atan(x)
        "floor" -> floor(x)
        "ceil" -> ceil(x)
        "round" -> round(x)
        else -> throw FormulaException("未知函数 '$op'")
    }

    private fun applyBinary(op: String, a: Double, b: Double): Double = when (op) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> a / b
        "^" -> a.pow(b)
        "min" -> min(a, b)
        "max" -> max(a, b)
        else -> throw FormulaException("未知运算符 '$op'")
    }

    /** 将表达式树还原为规范化文本 (用于预览/编辑回显) */
    fun toExpression(node: FNode, parentPrecedence: Int = 0): String {
        return when (node) {
            is FNode.Num -> formatNumber(node.value)
            is FNode.Variable -> node.name
            is FNode.Unary -> {
                val inner = toExpression(node.child, 3)
                if (node.op == "-") "-$inner" else "${node.op}($inner)"
            }
            is FNode.Binary -> {
                val precedence = when (node.op) {
                    "+", "-" -> 1
                    "*", "/" -> 2
                    else -> 3 // ^ min max
                }
                val text = if (node.op in BINARY_FUNCTIONS)
                    "${node.op}(${toExpression(node.left, 0)}, ${toExpression(node.right, 0)})"
                else
                    "${toExpression(node.left, precedence)} ${node.op} ${toExpression(node.right, precedence + if (node.op == "^") 0 else 1)}"
                if (precedence < parentPrecedence) "($text)" else text
            }
        }
    }

    private fun formatNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /** 便捷: 解析并求值, 出错抛 FormulaException */
    fun eval(expression: String, cal: Double, value: Double): Double =
        evaluate(parse(expression), cal, value)
}
