package dev.rint.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

/** tty-clock style digits: 5 rows x 3 columns of blocks. */
object BlockFont {
    private val digits = mapOf(
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
        '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("###", "..#", "###", "#..", "###"),
        '3' to listOf("###", "..#", "###", "..#", "###"),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
        '5' to listOf("###", "#..", "###", "..#", "###"),
        '6' to listOf("###", "#..", "###", "#.#", "###"),
        '7' to listOf("###", "..#", "..#", "..#", "..#"),
        '8' to listOf("###", "#.#", "###", "#.#", "###"),
        '9' to listOf("###", "#.#", "###", "..#", "###"),
        ':' to listOf(".", "#", ".", "#", "."),
        ' ' to listOf(".", ".", ".", ".", "."),
    )

    /** 5x5 letters for pixel wordmarks. */
    private val letters = mapOf(
        'A' to listOf(".###.", "#...#", "#####", "#...#", "#...#"),
        'B' to listOf("####.", "#...#", "####.", "#...#", "####."),
        'E' to listOf("#####", "#....", "####.", "#....", "#####"),
        'G' to listOf(".####", "#....", "#..##", "#...#", ".###."),
        'H' to listOf("#...#", "#...#", "#####", "#...#", "#...#"),
        'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
        'L' to listOf("#....", "#....", "#....", "#....", "#####"),
        'M' to listOf("#...#", "##.##", "#.#.#", "#...#", "#...#"),
        'N' to listOf("#...#", "##..#", "#.#.#", "#..##", "#...#"),
        'O' to listOf(".###.", "#...#", "#...#", "#...#", ".###."),
        'P' to listOf("####.", "#...#", "####.", "#....", "#...."),
        'R' to listOf("####.", "#...#", "####.", "#..#.", "#...#"),
        'S' to listOf(".####", "#....", ".###.", "....#", "####."),
        'T' to listOf("#####", "..#..", "..#..", "..#..", "..#.."),
        'U' to listOf("#...#", "#...#", "#...#", "#...#", ".###."),
        'Y' to listOf("#...#", ".#.#.", "..#..", "..#..", "..#.."),
        '!' to listOf("#", "#", "#", ".", "#"),
        '>' to listOf("#..", ".#.", "..#", ".#.", "#.."),
        ' ' to listOf("..", "..", "..", "..", ".."),
    )

    fun glyph(c: Char): List<String> = digits[c] ?: letters[c.uppercaseChar()] ?: letters[' ']!!

    /** Returns the list of filled cells (col,row) for a string, with 1-cell gaps between glyphs. */
    fun cells(text: String): Pair<List<Pair<Int, Int>>, Int> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = 0
        text.forEach { ch ->
            val g = glyph(ch)
            g.forEachIndexed { row, line ->
                line.forEachIndexed { col, c -> if (c == '#') out += (x + col) to row }
            }
            x += g[0].length + 1
        }
        return out to (x - 1).coerceAtLeast(0)
    }
}

fun DrawScope.drawBlocks(
    text: String,
    color: Color,
    cell: Float,
    gap: Float = cell * 0.12f,
    origin: Offset = Offset.Zero,
    colonAlpha: Float = 1f,
    shadow: Color? = null,
    bottomColor: Color? = null,
) {
    var x = origin.x
    text.forEach { ch ->
        val g = BlockFont.glyph(ch)
        val a = if (ch == ':') colonAlpha else 1f
        g.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                if (c == '#') {
                    val tl = Offset(x + col * cell + gap / 2, origin.y + row * cell + gap / 2)
                    val sz = Size(cell - gap, cell - gap)
                    if (shadow != null) drawRoundRect(shadow, tl + Offset(cell * 0.14f, cell * 0.14f), sz, CornerRadius(cell * 0.12f))
                    val c = if (bottomColor != null) androidx.compose.ui.graphics.lerp(color, bottomColor, row / 4f) else color
                    drawRoundRect(c.copy(alpha = c.alpha * a), tl, sz, CornerRadius(cell * 0.12f))
                }
            }
        }
        x += (g[0].length + 1) * cell
    }
}

fun blockWidth(text: String): Int = text.sumOf { BlockFont.glyph(it)[0].length + 1 } - 1

@Composable
fun BlockText(text: String, color: Color, height: Dp, modifier: Modifier = Modifier, colonAlpha: Float = 1f, shadow: Color? = null) {
    Canvas(modifier) {
        val cell = height.toPx() / 5f
        drawBlocks(text, color, cell, colonAlpha = colonAlpha, shadow = shadow)
    }
}
