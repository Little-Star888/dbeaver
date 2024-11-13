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

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeProviderOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;

import java.util.concurrent.CompletableFuture;

final class DBLServer implements LanguageServer, LanguageClientAware {
    private static final Log log = Log.getLog(DBLServer.class);

    private static final String SERVER_NAME = "DBeaver language server"; //NON-NLS
    private static final String FALLBACK_SERVER_VERSION = "unknown version"; //NON-NLS

    @Nullable
    private ClientInfo clientInfo;

    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;

    private DBLServer(TextDocumentService textDocumentService, WorkspaceService workspaceService) {
        this.textDocumentService = textDocumentService;
        this.workspaceService = workspaceService;
    }

    static DBLServer of() {
        return new DBLServer(new DBLTextDocumentService(), new DBLWorkspaceService());
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        clientInfo = params.getClientInfo();
        log.info("an LSP client has sent an initialize request. " + clientInfo); //NON-NLS
        log.trace(params);
        InitializeResult result = new InitializeResult(serverCapabilities(params.getCapabilities()), serverInfo());
        return CompletableFuture.completedFuture(result);
    }

    private static ServerCapabilities serverCapabilities(ClientCapabilities clientCapabilities) {
        ServerCapabilities serverCapabilities = new ServerCapabilities();
        //https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_synchronization
        serverCapabilities.setTextDocumentSync(textDocumentSyncOptions());
        //https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange
        FoldingRangeCapabilities foldingRange = clientCapabilities.getTextDocument().getFoldingRange();
        if (foldingRange != null && !Boolean.TRUE.equals(foldingRange.getLineFoldingOnly())) {
            serverCapabilities.setFoldingRangeProvider(new FoldingRangeProviderOptions());
        }
        //https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting
        serverCapabilities.setDocumentFormattingProvider(new DocumentFormattingOptions());
        return serverCapabilities;
    }

    private static TextDocumentSyncOptions textDocumentSyncOptions() {
        TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
        textDocumentSyncOptions.setOpenClose(true);
        textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
        textDocumentSyncOptions.setWillSave(false);
        textDocumentSyncOptions.setWillSaveWaitUntil(false);
        textDocumentSyncOptions.setSave(false);
        return textDocumentSyncOptions;
    }

    private static ServerInfo serverInfo() {
        String serverVersion = GeneralUtils.getPlainVersion(FALLBACK_SERVER_VERSION); //NON-NLS
        return new ServerInfo(SERVER_NAME, serverVersion);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        log.info("shutdown request received by the language server. " + clientInfo); //NON-NLS
        return CompletableFuture.completedFuture(CommonUtils.DUMMY);
    }

    @Override
    public void exit() {
        /*
         * The spec says: "A notification to ask the server to exit its process.
         * The server should exit with success code 0 if the shutdown request has been received before; otherwise with error code 1."
         * Let's ignore it for now as it's not clear at this point what to do.
         */
        log.info("exit notification received by the language server. " + clientInfo); //NON-NLS
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        log.debug("LSP client connected"); //NON-NLS
        if (textDocumentService instanceof LanguageClientAware clientAwareTextDocumentService) {
            clientAwareTextDocumentService.connect(client);
        }
    }
}
