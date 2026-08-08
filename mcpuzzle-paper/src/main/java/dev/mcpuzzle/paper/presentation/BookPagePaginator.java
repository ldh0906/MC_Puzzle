package dev.mcpuzzle.paper.presentation;

import java.util.ArrayList;
import java.util.List;

/** Wraps book text before the client can clip overflowing lines at the bottom of a page. */
final class BookPagePaginator {
    static final int MAX_LINE_WIDTH = 96;
    static final int MAX_LINES_PER_PAGE = 12;

    List<String> paginate(String logicalPage) {
        return paginate(logicalPage, false);
    }

    List<String> paginate(String logicalPage, boolean uniform) {
        List<String> lines = new ArrayList<>();
        for (String sourceLine : logicalPage.replace("\r\n", "\n").split("\n", -1)) {
            lines.addAll(wrapLine(sourceLine, uniform));
        }

        List<String> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += MAX_LINES_PER_PAGE) {
            int end = Math.min(start + MAX_LINES_PER_PAGE, lines.size());
            pages.add(ensureBlack(String.join("\n", lines.subList(start, end))));
        }
        if (pages.isEmpty()) pages.add("§0");
        return pages;
    }

    private List<String> wrapLine(String source, boolean uniform) {
        if (source.isEmpty()) return List.of("");

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        StringBuilder spaces = new StringBuilder();
        int lineWidth = 0;
        int spacesWidth = 0;

        int index = 0;
        while (index < source.length()) {
            int codePoint = source.codePointAt(index);
            int length = Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                spaces.appendCodePoint(codePoint);
                spacesWidth += glyphWidth(codePoint, uniform);
                index += length;
                continue;
            }

            int wordStart = index;
            while (index < source.length()) {
                int next = source.codePointAt(index);
                if (Character.isWhitespace(next)) break;
                index += Character.charCount(next);
                if (next == '§' && index < source.length()) {
                    index += Character.charCount(source.codePointAt(index));
                }
            }
            String word = source.substring(wordStart, index);
            int wordWidth = visibleWidth(word, uniform);

            if (lineWidth > 0 && lineWidth + spacesWidth + wordWidth > MAX_LINE_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
                spaces.setLength(0);
                spacesWidth = 0;
            }

            if (wordWidth > MAX_LINE_WIDTH) {
                if (lineWidth > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                List<String> pieces = hardWrap(word, uniform);
                lines.addAll(pieces.subList(0, pieces.size() - 1));
                String tail = pieces.get(pieces.size() - 1);
                line.append(tail);
                lineWidth = visibleWidth(tail, uniform);
            } else {
                if (lineWidth > 0 || (lines.isEmpty() && spacesWidth + wordWidth <= MAX_LINE_WIDTH)) {
                    line.append(spaces);
                    lineWidth += spacesWidth;
                }
                line.append(word);
                lineWidth += wordWidth;
            }
            spaces.setLength(0);
            spacesWidth = 0;
        }

        if (line.length() > 0) lines.add(line.toString());
        else if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private List<String> hardWrap(String word, boolean uniform) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int width = 0;
        int index = 0;
        while (index < word.length()) {
            int codePoint = word.codePointAt(index);
            int length = Character.charCount(codePoint);
            if (codePoint == '§' && index + length < word.length()) {
                piece.appendCodePoint(codePoint);
                index += length;
                piece.appendCodePoint(word.codePointAt(index));
                index += Character.charCount(word.codePointAt(index));
                continue;
            }
            int glyphWidth = glyphWidth(codePoint, uniform);
            if (width > 0 && width + glyphWidth > MAX_LINE_WIDTH) {
                pieces.add(piece.toString());
                piece.setLength(0);
                width = 0;
            }
            piece.appendCodePoint(codePoint);
            width += glyphWidth;
            index += length;
        }
        pieces.add(piece.toString());
        return pieces;
    }

    static int visibleWidth(String value) {
        return visibleWidth(value, false);
    }

    static int visibleWidth(String value, boolean uniform) {
        int width = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '§' && index < value.length()) {
                index += Character.charCount(value.codePointAt(index));
                continue;
            }
            width += glyphWidth(codePoint, uniform);
        }
        return width;
    }

    private static int glyphWidth(int codePoint, boolean uniform) {
        if (uniform) return codePoint == ' ' ? 4 : 8;
        if (codePoint == ' ') return 4;
        if (codePoint < 128) {
            if (".,:;!'|`ilI".indexOf(codePoint) >= 0) return 3;
            if ("()[]{}t".indexOf(codePoint) >= 0) return 4;
            if ("@MW%#&".indexOf(codePoint) >= 0) return 7;
            return 6;
        }
        return 8;
    }

    private String ensureBlack(String page) {
        return page.startsWith("§0") ? page : "§0" + page;
    }
}
