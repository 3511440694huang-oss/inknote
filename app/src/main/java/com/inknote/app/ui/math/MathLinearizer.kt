package com.inknote.app.ui.math

/**
 * 行内公式线性化：把 LaTeX 变成可读的 Unicode 行内文本，用于正文里 `$...$`。
 *
 * 例：
 * - `\alpha^2`      -> `α²`
 * - `\frac{a}{b}`   -> `a/b`
 * - `\frac{a+1}{b}` -> `(a+1)/b`
 * - `\sqrt[3]{x}`   -> `∛x`
 * - `\sum_{i=1}^{n}` -> `∑ᵢ₌₁ⁿ`
 * - `\text{中文}`   -> `中文`
 * - 未知命令 `\foo` -> `foo`（只去掉反斜杠，内容绝不丢）
 *
 * 永不抛异常；空输入返回空串；超长输入（> [MAX_MATH_INPUT_LENGTH]）截断解析。
 */
fun linearizeMath(latex: String): String {
    if (latex.isBlank()) return ""
    return try {
        val node = parseMath(latex)
        linearizeNode(node).trim()
    } catch (t: Throwable) {
        // 兜底：宁可退化成去掉反斜杠的纯文本，也不崩。
        latex.replace("\\", "").replace("{", "").replace("}", "").trim()
    }
}

private fun linearizeNode(node: MathNode): String = when (node) {
    is MathNode.Row -> {
        val sb = StringBuilder()
        for (item in node.items) sb.append(linearizeNode(item))
        sb.toString()
    }
    is MathNode.Symbol -> node.text
    is MathNode.TextRun -> node.text
    is MathNode.Styled -> linearizeNode(node.child)
    is MathNode.Space -> spaceText(node.widthEm)
    is MathNode.Script -> linearizeScript(node)
    is MathNode.Frac -> linearizeFrac(node)
    is MathNode.Root -> linearizeRoot(node)
    is MathNode.Delimited -> linearizeDelimited(node)
    is MathNode.Accent -> linearizeAccent(node)
    is MathNode.Matrix -> linearizeMatrix(node)
    is MathNode.BigOperator -> node.op
}

private fun linearizeScript(node: MathNode.Script): String {
    val base = linearizeNode(node.base)
    val subText = node.sub?.let(::linearizeNode) ?: ""
    val supText = node.sup?.let(::linearizeNode) ?: ""
    val sub = if (node.sub == null) "" else {
        subscriptOrNull(subText) ?: "_($subText)"
    }
    val sup = if (node.sup == null) "" else {
        superscriptOrNull(supText) ?: "^($supText)"
    }
    return base + sub + sup
}

private fun linearizeFrac(node: MathNode.Frac): String {
    val numerator = linearizeNode(node.numerator)
    val denominator = linearizeNode(node.denominator)
    return wrapFractionPart(numerator) + "/" + wrapFractionPart(denominator)
}

/** 分子/分母不是单个字符时加括号，保证 `(a+1)/(b-2)` 这种线性写法不歧义。 */
private fun wrapFractionPart(text: String): String {
    if (text.isEmpty()) return ""
    return if (text.length <= 1) text else "($text)"
}

private fun linearizeRoot(node: MathNode.Root): String {
    val body = linearizeNode(node.body)
    val wrapped = if (body.length <= 1) body else "($body)"
    val index = node.index?.let(::linearizeNode) ?: ""
    return when {
        index.isEmpty() -> "√$wrapped"
        index == "3" -> "∛$wrapped"
        index == "4" -> "∜$wrapped"
        else -> {
            val supIndex = superscriptOrNull(index)
            if (supIndex != null) "$supIndex√$wrapped" else "√[$index]$wrapped"
        }
    }
}

private fun linearizeDelimited(node: MathNode.Delimited): String {
    val open = if (node.open == ".") "" else node.open
    val close = if (node.close == ".") "" else node.close
    return open + linearizeNode(node.body) + close
}

private fun linearizeAccent(node: MathNode.Accent): String {
    val body = linearizeNode(node.body)
    val combining = accentCombining(node.kind) ?: return body
    if (body.isEmpty()) return body
    // 单个码点才能把重音组合到它上面；多字符时省略重音但保留内容。
    return if (body.codePointCount(0, body.length) == 1) body + combining else body
}

private fun linearizeMatrix(node: MathNode.Matrix): String {
    if (node.rows.isEmpty()) return ""
    val rowTexts = ArrayList<String>(node.rows.size)
    for (row in node.rows) {
        val cells = ArrayList<String>(row.size)
        for (cell in row) {
            val text = linearizeNode(cell)
            if (text.isNotEmpty()) cells.add(text)
        }
        if (cells.isNotEmpty()) rowTexts.add(cells.joinToString("  "))
    }
    return rowTexts.joinToString(" ; ")
}

private fun spaceText(widthEm: Float): String = when {
    widthEm <= 0f -> ""
    widthEm >= 3f -> "    "
    widthEm >= 1.5f -> "  "
    else -> " "
}

/** 整段都能映射成 Unicode 上标时返回替换结果，否则返回 null。 */
private fun superscriptOrNull(text: String): String? = mapScript(text, SUPERSCRIPTS)

/** 整段都能映射成 Unicode 下标时返回替换结果，否则返回 null。 */
private fun subscriptOrNull(text: String): String? = mapScript(text, SUBSCRIPTS)

private fun mapScript(text: String, table: Map<Char, Char>): String? {
    if (text.isEmpty()) return ""
    val sb = StringBuilder(text.length)
    for (c in text) {
        val mapped = table[c] ?: return null
        sb.append(mapped)
    }
    return sb.toString()
}
