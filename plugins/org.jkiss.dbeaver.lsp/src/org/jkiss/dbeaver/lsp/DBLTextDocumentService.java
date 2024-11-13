/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.lsp;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBUncheckedException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.impl.sql.BasicSQLDialect;
import org.jkiss.dbeaver.model.sql.SQLScriptElement;
import org.jkiss.dbeaver.model.sql.SQLSyntaxManager;
import org.jkiss.dbeaver.model.sql.format.SQLFormatter;
import org.jkiss.dbeaver.model.sql.format.SQLFormatterConfiguration;
import org.jkiss.dbeaver.model.sql.format.tokenized.SQLFormatterTokenized;
import org.jkiss.dbeaver.model.sql.parser.SQLParserContext;
import org.jkiss.dbeaver.model.sql.parser.SQLRuleManager;
import org.jkiss.dbeaver.model.sql.parser.SQLScriptParser;
import org.jkiss.dbeaver.runtime.DBWorkbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

class DBLTextDocumentService implements TextDocumentService, LanguageClientAware {
    private static final Log log = Log.getLog(DBLTextDocumentService.class);

    private static final String ERROR_MESSAGE_UNABLE_TO_CALCULATE_FOLDING_RANGES = "fatal error while calculating folding ranges"; //NON-NLS

    private final Map<String, String> textCache = new ConcurrentHashMap<>();

    @Nullable
    private LanguageClient languageClient;

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        log.trace(params);
        TextDocumentItem textDocument = params.getTextDocument();
        textCache.put(textDocument.getUri(), textDocument.getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        log.trace(params);
        VersionedTextDocumentIdentifier textDocument = params.getTextDocument();
        List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
        if (contentChanges.size() != 1) {
            // There should be exactly one change since we use TextDocumentSyncKind.Full
            throw new DBUncheckedException("unexpected number of document changes: " + contentChanges.size());
        }
        textCache.put(textDocument.getUri(), contentChanges.get(0).getText());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        log.trace(params);
        textCache.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        log.trace(params);
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        log.trace(params);
        return CompletableFutures.computeAsync(cancelChecker -> foldingRange(params, cancelChecker));
    }

    // Only works with ineFoldingOnly == false for now
    private List<FoldingRange> foldingRange(FoldingRangeRequestParams params, CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        TextDocumentIdentifier textDocument = params.getTextDocument();
        String text = textCache.get(textDocument.getUri());
        if (text == null) {
            log.warn("a folding was requested for an unknown document"); //NON-NLS
            return List.of();
        }
        SQLSyntaxManager syntaxManager = new SQLSyntaxManager();
        syntaxManager.init(BasicSQLDialect.INSTANCE, DBWorkbench.getPlatform().getPreferenceStore());
        SQLRuleManager ruleManager = new SQLRuleManager(syntaxManager);
        IDocument document = new Document(text);
        SQLParserContext parserContext = new SQLParserContext((DBPDataSource) null, syntaxManager, ruleManager, document);

        cancelChecker.checkCanceled();
        List<SQLScriptElement> scriptElements = SQLScriptParser.extractScriptQueries(
            parserContext,
            0,
            text.length(),
            true,
            true,
            true
        );

        cancelChecker.checkCanceled();
        List<FoldingRange> foldingRanges = new ArrayList<>(scriptElements.size());
        for (SQLScriptElement scriptElement : scriptElements) {
            cancelChecker.checkCanceled();
            try {
                foldingRanges.add(mapScriptElementToFoldingRange(scriptElement, document));
            } catch (BadLocationException e) {
                log.error(ERROR_MESSAGE_UNABLE_TO_CALCULATE_FOLDING_RANGES, e);
                if (languageClient != null) {
                    languageClient.logMessage(new MessageParams(MessageType.Error, ERROR_MESSAGE_UNABLE_TO_CALCULATE_FOLDING_RANGES));
                }
                return List.of();
            }
        }
        return foldingRanges;
    }

    private static FoldingRange mapScriptElementToFoldingRange(
        SQLScriptElement scriptElement,
        IDocument document
    ) throws BadLocationException {
        int scriptElementOffset = scriptElement.getOffset();
        int startLine = document.getLineOfOffset(scriptElementOffset);
        int startCharacter = scriptElementOffset - document.getLineOffset(startLine);
        int scriptElementEndOffset = scriptElementOffset + scriptElement.getLength();
        int endLine = document.getLineOfOffset(scriptElementEndOffset);
        int endCharacter = scriptElementEndOffset - document.getLineOffset(endLine);
        FoldingRange foldingRange = new FoldingRange(startLine, endLine);
        foldingRange.setStartCharacter(startCharacter);
        foldingRange.setEndCharacter(endCharacter);
        foldingRange.setKind(FoldingRangeKind.Region);
        return foldingRange;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        log.trace(params);
        return CompletableFutures.computeAsync(cancelChecker -> formatting(params, cancelChecker));
    }

    // TODO #0: take FormattingOptions into account
    // TODO #1: create an incremental formatter instead of the one that just replaces the whole document
    private List<? extends TextEdit> formatting(DocumentFormattingParams params, CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        String text = textCache.get(params.getTextDocument().getUri());
        if (text == null) {
            log.warn("formatting requested for an unknown document"); //NON-NLS
            return List.of();
        }
        SQLFormatter sqlFormatter = new SQLFormatterTokenized();
        SQLSyntaxManager syntaxManager = new SQLSyntaxManager();
        syntaxManager.init(BasicSQLDialect.INSTANCE, DBWorkbench.getPlatform().getPreferenceStore());
        SQLFormatterConfiguration sqlFormatterConfiguration = new SQLFormatterConfiguration(null, syntaxManager);
        String formattedText = sqlFormatter.format(text, sqlFormatterConfiguration);
        Position startPosition = new Position(0, 0);
        Range range = new Range(startPosition, lastTextPosition(text));
        return List.of(new TextEdit(range, formattedText));
    }

    private static Position lastTextPosition(String text) {
        int numberOfLines = 0;
        int indexOfLastLineSeparator = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                numberOfLines++;
                indexOfLastLineSeparator = i;
            }
        }
        int startOfTheLastLine = indexOfLastLineSeparator + 1;
        if (startOfTheLastLine == text.length()) {
            return new Position(numberOfLines, 0);
        }
        return new Position(numberOfLines, text.substring(startOfTheLastLine).length());
    }

    @Override
    public void connect(LanguageClient client) {
        languageClient = client;
    }
}
