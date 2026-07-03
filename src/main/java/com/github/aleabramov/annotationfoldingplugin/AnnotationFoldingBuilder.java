package com.github.aleabramov.annotationfoldingplugin;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides gutter fold/unfold controls for nested blocks inside class-level Java annotations.
 * <p>
 * A fold marker is added on every annotation line that contains {@code @}, {@code {}}, or {@code }}.
 */
public final class AnnotationFoldingBuilder extends FoldingBuilderEx implements DumbAware {

    @Override
    public @NotNull FoldingDescriptor @NotNull [] buildFoldRegions(
        @NotNull PsiElement root,
        @NotNull Document document,
        boolean quick
    ) {
        if (!(root instanceof PsiJavaFile)) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        Set<TextRange> processedRanges = new HashSet<>();

        for (PsiClass psiClass : ((PsiJavaFile) root).getClasses()) {
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList == null) {
                continue;
            }

            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                collectFoldsForAnnotation(annotation, document, descriptors, processedRanges);
            }
        }

        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void collectFoldsForAnnotation(@NotNull PsiAnnotation annotation,
                                                  @NotNull Document document,
                                                  @NotNull List<FoldingDescriptor> descriptors,
                                                  @NotNull Set<TextRange> processedRanges) {
        TextRange annotationRange = annotation.getTextRange();
        int startLine = document.getLineNumber(annotationRange.getStartOffset());
        int endLine = document.getLineNumber(annotationRange.getEndOffset());
        int maxOffset = annotationRange.getEndOffset();

        for (int line = startLine; line <= endLine; line++) {
            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            String lineText = document.getText(new TextRange(lineStart, lineEnd));

            if (!AnnotationFoldingUtil.containsFoldTrigger(lineText)) {
                continue;
            }

            for (int column = 0; column < lineText.length(); column++) {
                char ch = lineText.charAt(column);
                int absoluteOffset = lineStart + column;
                if (absoluteOffset < annotationRange.getStartOffset() || absoluteOffset >= maxOffset) {
                    continue;
                }

                TextRange foldRange = null;
                if (ch == '@') {
                    foldRange = AnnotationFoldingUtil.findAnnotationInvocationRange(document, absoluteOffset, maxOffset);
                } else if (ch == '{') {
                    foldRange = AnnotationFoldingUtil.findBracedBlockRange(document, absoluteOffset, maxOffset);
                } else if (ch == '}') {
                    foldRange = findRangeForClosingBraceLine(document, lineStart, lineEnd, maxOffset);
                }

                if (foldRange == null || foldRange.getLength() <= 2) {
                    continue;
                }
                if (!annotationRange.contains(foldRange)) {
                    continue;
                }
                if (!processedRanges.add(foldRange)) {
                    continue;
                }

                PsiElement anchor = findAnchorElement(annotation, absoluteOffset);
                descriptors.add(new FoldingDescriptor(anchor.getNode(), foldRange));
            }
        }
    }

    /**
     * For lines that only contain a closing brace, attach the fold region of the nearest
     * braced block that ends on this line so the gutter icon is visible there as well.
     */
    @Nullable
    private static TextRange findRangeForClosingBraceLine(
        @NotNull Document document,
        int lineStart,
        int lineEnd,
        int maxOffset
    ) {
        CharSequence text = document.getCharsSequence();
        int closingBrace = -1;
        for (int i = lineStart; i < lineEnd && i < maxOffset; i++) {
            if (text.charAt(i) == '}') {
                closingBrace = i;
            }
        }
        if (closingBrace < 0) {
            return null;
        }

        int depth = 0;
        for (int i = closingBrace; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                depth--;
                if (depth == 0) {
                    return new TextRange(i, closingBrace + 1);
                }
            }
        }
        return null;
    }

    @NotNull
    private static PsiElement findAnchorElement(@NotNull PsiAnnotation rootAnnotation, int offset) {
        PsiElement element = rootAnnotation.getContainingFile().findElementAt(offset);
        if (element == null) {
            return rootAnnotation;
        }

        PsiAnnotation nestedAnnotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class, false);
        if (nestedAnnotation != null && rootAnnotation.getTextRange().contains(nestedAnnotation.getTextRange())) {
            return nestedAnnotation;
        }

        return rootAnnotation;
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        PsiElement psi = node.getPsi();
        if (psi == null) {
            return "...";
        }
        PsiFile file = psi.getContainingFile();
        if (file == null) {
            return "...";
        }
        Document document = file.getViewProvider().getDocument();
        if (document == null) {
            return "...";
        }

        String foldedText = document.getText(range);
        if (foldedText.startsWith("@")) {
            return AnnotationFoldingUtil.buildAnnotationPlaceholder(document, range);
        }
        if (foldedText.startsWith("(")) {
            return AnnotationFoldingUtil.buildAnnotationPlaceholder(document, range);
        }
        if (foldedText.startsWith("{")) {
            return AnnotationFoldingUtil.buildBracePlaceholder();
        }
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
