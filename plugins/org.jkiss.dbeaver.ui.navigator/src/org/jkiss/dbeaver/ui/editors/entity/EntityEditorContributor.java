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

package org.jkiss.dbeaver.ui.editors.entity;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorPart;
import org.jkiss.dbeaver.ui.editors.EditorSearchActionsContributor;

/**
 * Search actions contributor
 */
public class EntityEditorContributor extends EditorSearchActionsContributor {

    private BreadcrumbsContributionItem entityStatusItem;
    private EntityEditor activeEditor;

    @Override
    public void setActiveEditor(IEditorPart part) {
        super.setActiveEditor(part);
        activeEditor = part instanceof EntityEditor ee ? ee : null;
        if (entityStatusItem != null && entityStatusItem.panel != null) {
            entityStatusItem.panel.setEditor(activeEditor);
        }
    }

    @Override
    public void contributeToStatusLine(IStatusLineManager statusLineManager) {
        entityStatusItem = new BreadcrumbsContributionItem();
        statusLineManager.insertBefore(StatusLineManager.BEGIN_GROUP, entityStatusItem);
    }

    private class BreadcrumbsContributionItem extends ContributionItem {
        EntityEditorBreadcrumbsPanel panel;

        @Override
        public void fill(Composite parent) {
            dispose();
            Label sep = new Label(parent, SWT.SEPARATOR);
//            new Label(parent, SWT.NONE).setText("BC: ");
            panel = new EntityEditorBreadcrumbsPanel(parent, activeEditor, true);
        }

        @Override
        public boolean isDynamic() {
            return true;
        }


        @Override
        public void dispose() {
            if (panel != null) {
                panel.dispose();
            }
            super.dispose();
        }
    }
}
