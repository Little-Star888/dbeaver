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

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jkiss.dbeaver.DBUncheckedException;
import org.jkiss.dbeaver.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A thing that starts and runs DBeaver language server.
 */
public final class DBLRunner implements Runnable {
    private static final Log log = Log.getLog(DBLRunner.class);

    private final DBLRunnerParams params;

    /**
     * Constructs a runner with the specified parameters.
     *
     * @param params runner params
     */
    public DBLRunner(DBLRunnerParams params) {
        this.params = params;
    }

    @Override
    public void run() {
        log.trace("running the runner"); //NON-NLS
        do {
            listenAndServe();
        } while (params.develMode());
    }

    private void listenAndServe() {
        try (ServerSocket serverSocket = new ServerSocket(params.port())) {
            log.debug("listening for an LSP client on port " + params.port()); //NON-NLS
            try (Socket socket = serverSocket.accept()) {
                log.debug("a client has connected"); //NON-NLS
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                DBLServer server = DBLServer.of();
                Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
                LanguageClient client = launcher.getRemoteProxy();
                server.connect(client);
                Future<Void> launcherFuture = launcher.startListening();
                launcherFuture.get();
                log.debug("the LSP client has closed the stream. " + client); //NON-NLS
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error("unexpected exception has been caught; I'm killing myself"); //NON-NLS
            // It's fine to just terminate the thread with the server. Let's do it with a runtime exception
            throw new DBUncheckedException(e);
        }
    }
}
