package com.github.aleabramov.annotationfoldingplugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for locating foldable regions inside Java annotation text.
 */
final class AnnotationFoldingUtil {

    private AnnotationFoldingUtil() {
    }

    static boolean containsFoldTrigger(@NotNull String lineText) {
        return lineText.indexOf('@') >= 0
            || lineText.indexOf('{') >= 0
            || lineText.indexOf('}') >= 0;
    }

    /**
     * Returns the text range of a full annotation invocation: from {@code @} through the matching closing {@code )}.
     */
    @Nullable
    static TextRange findAnnotationInvocationRange(@NotNull Document document, int atOffset, int maxOffset) {
        CharSequence text = document.getCharsSequence();
        int length = Math.min(maxOffset, text.length());

        int annotationStart = findAnnotationStart(text, atOffset, length);
        if (annotationStart < 0) {
            return null;
        }

        int openParen = findOpenParenAfterAnnotationName(text, annotationStart, length);
        if (openParen < 0) {
            return null;
        }

        int closeParen = findMatchingCloseParen(text, openParen, length);
        if (closeParen < 0 || closeParen <= annotationStart) {
            return null;
        }

        return new TextRange(annotationStart, closeParen + 1);
    }

    /**
     * Returns the text range of an annotation parameter list: from {@code (} after the name
     * to the matching closing {@code )}, inclusive of both parentheses.
     */
    @Nullable
    static TextRange findAnnotationParameterListRange(@NotNull Document document, int atOffset, int maxOffset) {
        CharSequence text = document.getCharsSequence();
        int length = Math.min(maxOffset, text.length());

        int openParen = findOpenParenAfterAnnotationName(text, atOffset, length);
        if (openParen < 0) {
            return null;
        }

        int closeParen = findMatchingCloseParen(text, openParen, length);
        if (closeParen < 0 || closeParen <= openParen) {
            return null;
        }

        return new TextRange(openParen, closeParen + 1);
    }

    /**
     * Returns the text range of a braced block: from {@code {} to the matching {@code }}.
     */
    @Nullable
    static TextRange findBracedBlockRange(@NotNull Document document, int openBraceOffset, int maxOffset) {
        CharSequence text = document.getCharsSequence();
        int length = Math.min(maxOffset, text.length());

        if (openBraceOffset >= length || text.charAt(openBraceOffset) != '{') {
            return null;
        }

        int closeBrace = findMatchingCloseBrace(text, openBraceOffset, length);
        if (closeBrace < 0 || closeBrace <= openBraceOffset) {
            return null;
        }

        return new TextRange(openBraceOffset, closeBrace + 1);
    }

    @NotNull
    static String buildAnnotationPlaceholder(@NotNull Document document, @NotNull TextRange range) {
        String folded = document.getText(range).trim();
        if (!folded.startsWith("@")) {
            return "(...)";
        }

        int openParen = folded.indexOf('(');
        if (openParen < 0) {
            return folded;
        }

        String annotationPrefix = folded.substring(0, openParen).trim();
        if (openParen + 1 >= folded.length()) {
            return annotationPrefix + "(...)";
        }

        String inner = folded.substring(openParen + 1, folded.length() - 1).trim();
        if (inner.isEmpty() || inner.contains("\n")) {
            return annotationPrefix + "(...)";
        }
        if (inner.length() > 60) {
            inner = inner.substring(0, 57) + "...";
        }
        return annotationPrefix + "(" + inner + ")";
    }

    private static int findAnnotationStart(@NotNull CharSequence text, int atOffset, int length) {
        int i = atOffset;
        while (i < length && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i < length && text.charAt(i) == '@') {
            return i;
        }
        return -1;
    }

    @NotNull
    static String buildBracePlaceholder() {
        return "{...}";
    }

    private static int findOpenParenAfterAnnotationName(@NotNull CharSequence text, int atOffset, int length) {
        int i = atOffset;
        while (i < length && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= length || text.charAt(i) != '@') {
            return -1;
        }
        i++;

        while (i < length && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '.')) {
            i++;
        }

        while (i < length && Character.isWhitespace(text.charAt(i))) {
            i++;
        }

        if (i < length && text.charAt(i) == '(') {
            return i;
        }
        return -1;
    }

    private static int findMatchingCloseParen(@NotNull CharSequence text, int openParen, int length) {
        int depth = 0;
        for (int i = openParen; i < length; i++) {
            char c = text.charAt(i);
            if (isInLineComment(text, i) || isInBlockComment(text, i)) {
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipString(text, i, length, c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingCloseBrace(@NotNull CharSequence text, int openBrace, int length) {
        int depth = 0;
        for (int i = openBrace; i < length; i++) {
            char c = text.charAt(i);
            if (isInLineComment(text, i) || isInBlockComment(text, i)) {
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipString(text, i, length, c);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipString(@NotNull CharSequence text, int start, int length, char quote) {
        int i = start + 1;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i;
            }
            i++;
        }
        return length - 1;
    }

    private static boolean isInLineComment(@NotNull CharSequence text, int offset) {
        if (offset <= 0 || offset >= text.length()) {
            return false;
        }
        return text.charAt(offset - 1) == '/' && text.charAt(offset) == '/';
    }

    private static boolean isInBlockComment(@NotNull CharSequence text, int offset) {
        if (offset <= 0 || offset + 1 >= text.length()) {
            return false;
        }
        for (int i = offset; i >= Math.max(0, offset - 100); i--) {
            if (i > 0 && text.charAt(i - 1) == '/' && text.charAt(i) == '*') {
                return true;
            }
            if (i > 0 && text.charAt(i - 1) == '*' && text.charAt(i) == '/') {
                return false;
            }
        }
        return false;
    }
}
