# Terminal typography

Commands, paths, diffs, and Markdown code use the unmodified JetBrains Mono Regular font.
Chinese prose keeps Android's sans-serif font/fallback. The font is bundled locally, never fetched at runtime.

- Upstream: https://github.com/JetBrains/JetBrainsMono
- Pinned commit: `19371302b95d218af43299bce79ddbddd0bc364d`
- Upstream path: `fonts/ttf/JetBrainsMono-Regular.ttf`
- Git blob SHA-1: `711830ede02a366f8b99f88e52f3148405e67eaf`
- SHA-256: `e6fd0d7e91550b3ed2b735d4312474362c4716edc4fc0577a0f61ed782d5aed1`
- Original size: 270,224 bytes
- License: SIL Open Font License 1.1, bundled in `app/src/main/assets/licenses/JetBrainsMono-OFL.txt` and accessible in the App under 我的 → 字体与开源许可.

References inspected on 2026-09-28 (stars fluctuate):

- [Tabby](https://github.com/Eugeny/tabby): 74,719 stars. Its [terminal defaults](https://github.com/Eugeny/tabby/blob/master/tabby-terminal/src/config.ts) use `linePadding: 0` and `ligatures: false`; we borrow the compact spacing and literal code symbols, not its desktop pixel sizes or platform fonts.
- [Termux](https://github.com/termux/termux-app): 61,593 stars. Its [renderer](https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java) lays out a font-metric-based character grid; we borrow consistent monospace code rows, not its source code or full terminal UI.
- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono): 13,049 stars. The font is a separate selection, not claimed as either terminal's default font. No font outlines were modified.

Chat changes: message gap 16 → 4 dp; assistant vertical padding 8 → 2 dp; user vertical padding 14 → 9 dp; body 15 → 14 sp with no extra line leading; blank Markdown paragraph separator lines use 6 sp height without changing text or code-block blank lines. Tool disclosure rows retain 48 dp touch targets. Command type grows 11 → 12 sp; compactness comes from spacing, not unreadably tiny code. The glass composer, collapsed details, tail shimmer, sync protocol and notifications remain in place.
