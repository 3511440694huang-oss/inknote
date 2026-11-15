package com.inknote.app.ui.math

/**
 * LaTeX 命令 -> Unicode 的符号表。
 *
 * 排版和行内线性化共用同一张表，保证块级公式与 `$...$` 行内公式显示一致。
 * 只收录常用符号（希腊字母、关系/集合/箭头/大型运算符等 150+ 个），
 * 表里没有的命令由解析器退化成“命令名当普通文本”，不会崩。
 */

/** 希腊字母。 */
internal val GREEK_SYMBOLS: Map<String, String> = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ϵ", "zeta" to "ζ", "eta" to "η",
    "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
    "varkappa" to "ϰ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
    "xi" to "ξ", "omicron" to "ο", "pi" to "π", "varpi" to "ϖ",
    "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς",
    "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ",
    "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
    "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
    "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    "Alpha" to "Α", "Beta" to "Β", "Epsilon" to "Ε", "Zeta" to "Ζ",
    "Eta" to "Η", "Iota" to "Ι", "Kappa" to "Κ", "Mu" to "Μ",
    "Nu" to "Ν", "Omicron" to "Ο", "Rho" to "Ρ", "Tau" to "Τ", "Chi" to "Χ",
    "varUpsilon" to "ϒ",
)

/** 运算符、关系符、箭头、集合/逻辑符号、杂项。 */
internal val SYMBOLS: Map<String, String> = mapOf(
    // 四则与二元运算
    "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷",
    "cdot" to "·", "cdotp" to "·", "ast" to "∗", "star" to "⋆",
    "circ" to "∘", "bullet" to "∙", "oplus" to "⊕", "ominus" to "⊖",
    "otimes" to "⊗", "oslash" to "⊘", "odot" to "⊙", "uplus" to "⊎",
    "sqcup" to "⊔", "sqcap" to "⊓", "wr" to "≀", "diamond" to "◇",
    // 关系
    "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
    "ne" to "≠", "neq" to "≠", "equiv" to "≡", "sim" to "∼",
    "simeq" to "≃", "approx" to "≈", "cong" to "≅", "propto" to "∝",
    "ll" to "≪", "gg" to "≫", "doteq" to "≐", "asymp" to "≍",
    "prec" to "≺", "succ" to "≻", "preceq" to "⪯", "succeq" to "⪰",
    "models" to "⊨", "vdash" to "⊢", "dashv" to "⊣",
    // 集合与逻辑
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "subsetneq" to "⊊", "supsetneq" to "⊋", "in" to "∈", "ni" to "∋",
    "notin" to "∉", "cup" to "∪", "cap" to "∩", "setminus" to "∖",
    "emptyset" to "∅", "varnothing" to "∅", "forall" to "∀", "exists" to "∃",
    "nexists" to "∄", "infty" to "∞", "partial" to "∂", "nabla" to "∇",
    "wedge" to "∧", "land" to "∧", "vee" to "∨", "lor" to "∨",
    "neg" to "¬", "lnot" to "¬", "top" to "⊤", "bot" to "⊥",
    "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "mid" to "∣",
    "nmid" to "∤", "triangle" to "△", "bigtriangleup" to "△",
    "square" to "□", "Box" to "□", "blacksquare" to "■",
    // 箭头
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "gets" to "←",
    "leftrightarrow" to "↔", "Leftarrow" to "⇐", "Rightarrow" to "⇒",
    "Leftrightarrow" to "⇔", "implies" to "⟹", "impliedby" to "⟸",
    "iff" to "⟺", "uparrow" to "↑", "downarrow" to "↓", "updownarrow" to "↕",
    "mapsto" to "↦", "longmapsto" to "⟼", "longrightarrow" to "⟶",
    "longleftarrow" to "⟵", "Longrightarrow" to "⟹", "Longleftarrow" to "⟸",
    "Longleftrightarrow" to "⟺", "hookrightarrow" to "↪", "hookleftarrow" to "↩",
    "rightharpoonup" to "⇀", "leftharpoonup" to "↼", "nearrow" to "↗",
    "searrow" to "↘", "swarrow" to "↙", "nwarrow" to "↖",
    // 其他常用
    "prime" to "′", "dprime" to "″", "degree" to "°", "hbar" to "ℏ",
    "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "wp" to "℘",
    "imath" to "ı", "jmath" to "ȷ", "dots" to "…", "ldots" to "…",
    "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱", "therefore" to "∴",
    "because" to "∵", "colon" to ":", "checkmark" to "✓", "dag" to "†",
    "ddag" to "‡", "S" to "§", "P" to "¶", "copyright" to "©",
    "pounds" to "£", "euro" to "€", "textbackslash" to "\\",
    "backslash" to "\\", "backprime" to "‵", "varnothing" to "∅",
    "iint" to "∬", "iiint" to "∭", "iiiint" to "⨌", "oint" to "∮",
    // 定界符（\left \right 里也复用）
    "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋",
    "lceil" to "⌈", "rceil" to "⌉", "vert" to "|", "Vert" to "‖",
    "lvert" to "|", "rvert" to "|", "lVert" to "‖", "rVert" to "‖",
    "lbrace" to "{", "rbrace" to "}", "lbrack" to "[", "rbrack" to "]",
    "uparrow" to "↑", "downarrow" to "↓",
    // 大型运算符
    "sum" to "∑", "prod" to "∏", "coprod" to "∐", "bigcup" to "⋃",
    "bigcap" to "⋂", "bigvee" to "⋁", "bigwedge" to "⋀", "bigoplus" to "⨁",
    "bigotimes" to "⨂", "bigodot" to "⨀", "bigsqcup" to "⨆", "biguplus" to "⨄",
)

/** 行内/展示都按正体渲染的函数名。 */
internal val FUNCTION_NAMES: Set<String> = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
    "sinh", "cosh", "tanh", "coth", "log", "ln", "lg", "exp", "erf",
    "det", "dim", "hom", "ker", "deg", "mod", "bmod", "Pr",
)

/** 有上下限、按上下堆叠排版的运算符名（函数名类用正体文字）。 */
internal val LIMIT_OPERATOR_NAMES: Set<String> = setOf(
    "lim", "max", "min", "sup", "inf", "limsup", "liminf", "argmin", "argmax", "det", "gcd", "Pr",
)

/** 需要放大字号的单字符大型运算符（∑∏∫ 等）。 */
internal val BIG_OPERATOR_CHARS: Set<String> = setOf(
    "∑", "∏", "∐", "∫", "∬", "∭", "⨌", "∮", "⋃", "⋂", "⋁", "⋀", "⨁", "⨂", "⨀", "⨆", "⨄",
)

/** 重音命令 -> 重音种类。 */
internal val ACCENT_COMMANDS: Map<String, AccentKind> = mapOf(
    "overline" to AccentKind.Overline,
    "bar" to AccentKind.Bar,
    "hat" to AccentKind.Hat,
    "widehat" to AccentKind.WideHat,
    "vec" to AccentKind.Vec,
    "overrightarrow" to AccentKind.Vec,
    "dot" to AccentKind.Dot,
    "ddot" to AccentKind.Ddot,
    "tilde" to AccentKind.Tilde,
    "widetilde" to AccentKind.WideTilde,
    "check" to AccentKind.Check,
    "acute" to AccentKind.Acute,
    "grave" to AccentKind.Grave,
    "breve" to AccentKind.Breve,
)

/** 空白命令 -> em 宽度。 */
internal val SPACE_COMMANDS: Map<String, Float> = mapOf(
    "," to 0.167f,
    ":" to 0.222f,
    ";" to 0.278f,
    "!" to -0.167f,
    " " to 0.333f,
    "thinspace" to 0.167f,
    "medspace" to 0.222f,
    "thickspace" to 0.278f,
    "negthinspace" to -0.167f,
    "negmedspace" to -0.222f,
    "negthickspace" to -0.278f,
    "enspace" to 0.5f,
    "quad" to 1f,
    "qquad" to 2f,
    "space" to 0.333f,
)

/** 不产生内容、可直接忽略的命令（\displaystyle 之类）。 */
internal val IGNORED_COMMANDS: Set<String> = setOf(
    "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
    "limits", "nolimits", "nonumber", "notag", "mathstrut", "strut",
    "rm", "it", "bf", "cal", "tt", "sf", "tiny", "small", "large",
    "Large", "LARGE", "huge", "Huge", "normalsize", "centering",
)

/** 单字符转义命令 -> 字面字符。 */
internal val ESCAPED_CHARS: Map<Char, String> = mapOf(
    '{' to "{", '}' to "}", '%' to "%", '$' to "$", '&' to "&",
    '#' to "#", '_' to "_", '^' to "^", '~' to "~", '|' to "‖",
)

/** 上标 Unicode 映射。 */
internal val SUPERSCRIPTS: Map<Char, Char> = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'a' to 'ᵃ', 'b' to 'ᵇ',
    'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ',
    'h' to 'ʰ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ',
    'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ',
    'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'y' to 'ʸ', 'z' to 'ᶻ',
    'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'G' to 'ᴳ',
    'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ', 'K' to 'ᴷ', 'L' to 'ᴸ',
    'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
    'T' to 'ᵀ', 'U' to 'ᵁ', 'V' to 'ⱽ', 'W' to 'ᵂ',
)

/** 下标 Unicode 映射。 */
internal val SUBSCRIPTS: Map<Char, Char> = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ',
)

/** 重音对应的组合附加字符（行内线性化用）。 */
internal fun accentCombining(kind: AccentKind): Char? = when (kind) {
    AccentKind.Overline, AccentKind.Bar -> '\u0304'
    AccentKind.Hat, AccentKind.WideHat -> '\u0302'
    AccentKind.Vec -> '\u20D7'
    AccentKind.Dot -> '\u0307'
    AccentKind.Ddot -> '\u0308'
    AccentKind.Tilde, AccentKind.WideTilde -> '\u0303'
    AccentKind.Check -> '\u030C'
    AccentKind.Acute -> '\u0301'
    AccentKind.Grave -> '\u0300'
    AccentKind.Breve -> '\u0306'
}

/** 重音在排版时绘制的字形（顶部小符号）。 */
internal fun accentGlyph(kind: AccentKind): String = when (kind) {
    AccentKind.Hat -> "\u02C6"
    AccentKind.WideHat -> "\u02C6"
    AccentKind.Vec -> "→"
    AccentKind.Dot -> "\u02D9"
    AccentKind.Ddot -> "\u00A8"
    AccentKind.Tilde -> "\u02DC"
    AccentKind.WideTilde -> "\u02DC"
    AccentKind.Check -> "\u02C7"
    AccentKind.Acute -> "\u00B4"
    AccentKind.Grave -> "`"
    AccentKind.Breve -> "\u02D8"
    AccentKind.Overline, AccentKind.Bar -> "¯"
}
