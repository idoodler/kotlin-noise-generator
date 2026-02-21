import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ReadmePage {
    private val defaultPath = Paths.get("README.md")

    /**
     * Renders a README Markdown file into a self-contained HTML page.
     */
    fun render(path: Path = defaultPath): String {
        val markdown = Files.readString(path)
        val content = MarkdownRenderer().render(markdown)
        val title = extractTitle(markdown) ?: "README"
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>${escapeHtml(title)}</title>
              <style>
                :root {
                  color-scheme: light dark;
                  --bg: #f4f1ea;
                  --text: #1e1e1e;
                  --card: #ffffff;
                  --shadow: rgba(0,0,0,0.08);
                  --code-bg: #f0f0f0;
                  --pre-bg: #111111;
                  --pre-text: #f0f0f0;
                  --border: #e3e0d7;
                }
                :root[data-theme="dark"] {
                  --bg: #121214;
                  --text: #e9e6df;
                  --card: #1b1b1f;
                  --shadow: rgba(0,0,0,0.5);
                  --code-bg: #2a2a30;
                  --pre-bg: #0c0c0e;
                  --pre-text: #e9e6df;
                  --border: #2a2a30;
                }
                body { font-family: "Georgia", "Times New Roman", serif; margin: 0; background: var(--bg); color: var(--text); }
                main { max-width: 900px; margin: 40px auto; padding: 32px; background: var(--card); box-shadow: 0 10px 30px var(--shadow); border: 1px solid var(--border); }
                h1, h2, h3 { font-family: "Trebuchet MS", "Gill Sans", sans-serif; margin-top: 24px; }
                p { line-height: 1.6; }
                code { background: var(--code-bg); padding: 2px 4px; border-radius: 4px; }
                pre { background: var(--pre-bg); color: var(--pre-text); padding: 16px; overflow-x: auto; }
                pre code { background: none; padding: 0; }
                ul { padding-left: 20px; }
                table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                th, td { border: 1px solid var(--border); padding: 8px 10px; text-align: left; }
                th { background: var(--code-bg); }
                .toolbar { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 16px; }
                .theme-switch { display: inline-flex; align-items: center; gap: 10px; cursor: pointer; }
                .theme-toggle {
                  appearance: none;
                  position: absolute;
                  opacity: 0;
                  width: 0;
                  height: 0;
                }
                .theme-track {
                  width: 46px;
                  height: 26px;
                  border-radius: 999px;
                  background: var(--border);
                  border: 1px solid var(--border);
                  position: relative;
                  transition: background 0.2s ease;
                  display: inline-block;
                }
                .theme-thumb {
                  position: absolute;
                  top: 3px;
                  left: 3px;
                  width: 20px;
                  height: 20px;
                  border-radius: 50%;
                  background: var(--card);
                  box-shadow: 0 2px 6px rgba(0,0,0,0.2);
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  font-size: 12px;
                  transition: transform 0.2s ease;
                }
                .theme-toggle:checked + .theme-track { background: #6b6b6b; }
                .theme-toggle:checked + .theme-track .theme-thumb { transform: translateX(20px); }
              </style>
            </head>
            <body>
              <main>
                <div class="toolbar">
                  <label class="theme-switch" for="theme-toggle">
                    <input class="theme-toggle" id="theme-toggle" type="checkbox" data-theme-toggle />
                    <span class="theme-track">
                      <span class="theme-thumb" data-theme-emoji>🌞</span>
                    </span>
                  </label>
                </div>
              $content
              </main>
              <script>
                (function() {
                  var root = document.documentElement;
                  var key = "noise-theme";
                  var toggle = document.querySelector("[data-theme-toggle]");
                  var emoji = document.querySelector("[data-theme-emoji]");
                  var stored = localStorage.getItem(key);
                  if (stored === "dark" || stored === "light") {
                    root.setAttribute("data-theme", stored);
                  } else {
                    root.setAttribute("data-theme", window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
                  }
                  if (toggle) {
                    toggle.checked = root.getAttribute("data-theme") === "dark";
                    toggle.addEventListener("change", function() {
                      var next = toggle.checked ? "dark" : "light";
                      root.setAttribute("data-theme", next);
                      localStorage.setItem(key, next);
                    });
                  }
                  if (emoji) {
                    emoji.textContent = root.getAttribute("data-theme") === "dark" ? "🌙" : "🌞";
                  }
                  if (toggle && emoji) {
                    toggle.addEventListener("change", function() {
                      emoji.textContent = toggle.checked ? "🌙" : "🌞";
                    });
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun extractTitle(markdown: String): String? {
        return markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

private class MarkdownRenderer {
    /**
     * Minimal markdown renderer for headings, lists, code blocks, and inline code.
     */
    fun render(markdown: String): String {
        val out = StringBuilder()
        val lines = markdown.lines()
        var inCode = false
        var inList = false
        var inTable = false
        val tableBuffer = mutableListOf<String>()
        var paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(paragraph.toString().trim()).append("</p>\n")
                paragraph = StringBuilder()
            }
        }

        fun closeList() {
            if (inList) {
                out.append("</ul>\n")
                inList = false
            }
        }

        fun flushTable() {
            if (tableBuffer.isEmpty()) return
            out.append(renderTable(tableBuffer))
            tableBuffer.clear()
            inTable = false
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.startsWith("```")) {
                flushParagraph()
                flushTable()
                if (inList) closeList()
                inCode = !inCode
                if (inCode) {
                    out.append("<pre><code>")
                } else {
                    out.append("</code></pre>\n")
                }
                continue
            }
            if (inCode) {
                out.append(escapeHtml(line)).append("\n")
                continue
            }

            when {
                line.startsWith("# ") -> {
                    flushParagraph()
                    flushTable()
                    closeList()
                    out.append("<h1>").append(escapeHtml(line.removePrefix("# ").trim())).append("</h1>\n")
                }
                line.startsWith("## ") -> {
                    flushParagraph()
                    flushTable()
                    closeList()
                    out.append("<h2>").append(escapeHtml(line.removePrefix("## ").trim())).append("</h2>\n")
                }
                line.startsWith("### ") -> {
                    flushParagraph()
                    flushTable()
                    closeList()
                    out.append("<h3>").append(escapeHtml(line.removePrefix("### ").trim())).append("</h3>\n")
                }
                isTableLine(line) -> {
                    flushParagraph()
                    closeList()
                    inTable = true
                    tableBuffer.add(line)
                }
                line.startsWith("- ") -> {
                    flushParagraph()
                    flushTable()
                    if (!inList) {
                        out.append("<ul>\n")
                        inList = true
                    }
                    val item = inlineCode(escapeHtml(line.removePrefix("- ").trim()))
                    out.append("<li>").append(item).append("</li>\n")
                }
                line.isBlank() -> {
                    flushParagraph()
                    flushTable()
                    closeList()
                }
                else -> {
                    flushTable()
                    val text = inlineCode(escapeHtml(line))
                    if (paragraph.isNotEmpty()) paragraph.append(" ")
                    paragraph.append(text)
                }
            }
        }

        flushParagraph()
        flushTable()
        closeList()
        if (inCode) out.append("</code></pre>\n")
        return out.toString()
    }

    private fun renderTable(lines: List<String>): String {
        val rows = lines
            .filterNot { isSeparatorLine(it) }
            .map { splitTableRow(it) }
        if (rows.isEmpty()) return ""
        val header = rows.first()
        val bodyRows = rows.drop(1)
        val sb = StringBuilder()
        sb.append("<table>\n<thead><tr>")
        header.forEach { cell ->
            sb.append("<th>").append(inlineCode(escapeHtml(cell.trim()))).append("</th>")
        }
        sb.append("</tr></thead>\n")
        sb.append("<tbody>\n")
        bodyRows.forEach { row ->
            sb.append("<tr>")
            row.forEach { cell ->
                sb.append("<td>").append(inlineCode(escapeHtml(cell.trim()))).append("</td>")
            }
            sb.append("</tr>\n")
        }
        sb.append("</tbody>\n</table>\n")
        return sb.toString()
    }

    private fun isTableLine(line: String): Boolean {
        return line.trim().startsWith("|") && line.contains("|")
    }

    private fun isSeparatorLine(line: String): Boolean {
        val trimmed = line.trim().trim('|').trim()
        if (trimmed.isEmpty()) return false
        return trimmed.all { it == '-' || it == ':' || it == '|' || it == ' ' }
    }

    private fun splitTableRow(line: String): List<String> {
        return line.trim()
            .trim('|')
            .split("|")
            .map { it.trim() }
    }

    private fun inlineCode(text: String): String {
        val sb = StringBuilder()
        var i = 0
        var inCode = false
        while (i < text.length) {
            val ch = text[i]
            if (ch == '`') {
                sb.append(if (inCode) "</code>" else "<code>")
                inCode = !inCode
            } else {
                sb.append(ch)
            }
            i++
        }
        if (inCode) sb.append("</code>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
