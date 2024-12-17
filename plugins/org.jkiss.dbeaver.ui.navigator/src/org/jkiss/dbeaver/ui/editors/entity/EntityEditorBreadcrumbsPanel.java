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

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseFolder;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNUtils;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.ui.*;
import org.jkiss.dbeaver.ui.editors.DatabaseLazyEditorInput;
import org.jkiss.dbeaver.ui.internal.UINavigatorActivator;
import org.jkiss.dbeaver.ui.internal.UINavigatorMessages;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerObjectOpen;
import org.jkiss.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public class EntityEditorBreadcrumbsPanel extends Composite {

    private static final String PREF_BREADCRUMBS_VISIBLE = "entity.editor.breadcrumbs";
    private static Object VISIBILITY_ITEM = new Object();

    private static final int MAX_BREADCRUMBS_MENU_ITEM = 300;

    private static final Log log = Log.getLog(EntityEditorBreadcrumbsPanel.class);

    private final EntityEditor editor;
    private final ToolBar bcToolbar;

    private Menu breadcrumbsMenu;
    private ISelectionProvider savedPartSelectionProvider = null;
    private DBNDatabaseNode selectedNode;

    public EntityEditorBreadcrumbsPanel(Composite parent, EntityEditor editor) {
        super(parent, SWT.NONE);
        this.editor = editor;

        this.setLayout(new GridLayout(1, false));

        // Path
        bcToolbar = new ToolBar(this, SWT.HORIZONTAL | SWT.RIGHT);
        bcToolbar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END));
        bcToolbar.setForeground(
            UIUtils.isDark(bcToolbar.getBackground().getRGB()) ? UIUtils.COLOR_WHITE : UIStyles.getDefaultTextForeground());
        bcToolbar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                ToolItem onItem = bcToolbar.getItem(new Point(e.x, e.y));
                selectedNode = onItem == null ? null : onItem.getData() instanceof DBNDatabaseNode node ? node : null;
                if (onItem != null && onItem.getData() == VISIBILITY_ITEM) {
                    toggleToolbars();
                }
            }
        });

        if (UINavigatorActivator.getDefault().getPreferences().getBoolean(PREF_BREADCRUMBS_VISIBLE)) {
            fillBreadcrumbs();
        } else {
            fillDefaultItems();
        }

        if (editor.getEditorInput() instanceof DatabaseLazyEditorInput) {
            bcToolbar.setEnabled(false);
        }

        addDisposeListener(e -> {
            if (breadcrumbsMenu != null) {
                breadcrumbsMenu.dispose();
                breadcrumbsMenu = null;
            }
        });
    }

    private void toggleToolbars() {
        for (ToolItem item : bcToolbar.getItems()) item.dispose();

        DBPPreferenceStore preferences = UINavigatorActivator.getDefault().getPreferences();
        boolean bcVisible = !preferences.getBoolean(PREF_BREADCRUMBS_VISIBLE);
        preferences.setValue(PREF_BREADCRUMBS_VISIBLE, bcVisible);

        if (bcVisible) {
            fillBreadcrumbs();
        } else {
            fillDefaultItems();
        }
        this.getParent().getParent().layout(true, true);
        this.getParent().redraw();
    }

    private void fillDefaultItems() {
        boolean bcVisible = UINavigatorActivator.getDefault().getPreferences().getBoolean(PREF_BREADCRUMBS_VISIBLE);
        ToolItem item = new ToolItem(bcToolbar, SWT.PUSH);
        item.setData(VISIBILITY_ITEM);
        item.setImage(DBeaverIcons.getImage(bcVisible ? UIIcon.NOTIFICATION_CLOSE : UIIcon.ASTERISK));
        item.setToolTipText(bcVisible ? "Hide breadcrumbs" : "Show breadcrumbs");
    }

    private void fillBreadcrumbs() {
        // Make base node path
        DBNDatabaseNode node = editor.getEditorInput().getNavigatorNode();

        List<DBNDatabaseNode> nodeList = new ArrayList<>();
        for (DBNNode n = node; n != null; n = n.getParentNode()) {
            if (n instanceof DBNDatabaseNode) {
                nodeList.add(0, (DBNDatabaseNode) n);
            }
        }
        for (final DBNDatabaseNode databaseNode : nodeList) {
            createBreadcrumbs(bcToolbar, databaseNode);
        }

        {
            // Add context menu
            CustomSelectionProvider selProvider = new CustomSelectionProvider();

            MenuManager menuMgr = new MenuManager();
            Menu menu = menuMgr.createContextMenu(bcToolbar);
            menuMgr.addMenuListener(manager -> {
                savedPartSelectionProvider = editor.getSite().getSelectionProvider();
                editor.getSite().setSelectionProvider(selProvider);
                selProvider.setSelection(selProvider.getSelection());

                if (selectedNode == null) {
                    selProvider.setSelection(new StructuredSelection());
                } else {
                    selProvider.setSelection(new StructuredSelection(selectedNode));
                }
                NavigatorUtils.addStandardMenuItem(editor.getSite(), manager, selProvider);
            });
            menuMgr.setRemoveAllWhenShown(true);
            bcToolbar.setMenu(menu);

            editor.getSite().registerContextMenu("entityBreadcrumbsMenu", menuMgr, selProvider);

            menu.addMenuListener(new MenuAdapter() {
                @Override
                public void menuHidden(MenuEvent e) {
                    UIUtils.asyncExec(() -> {
                        if (savedPartSelectionProvider != null) {
                            editor.getSite().setSelectionProvider(savedPartSelectionProvider);
                            savedPartSelectionProvider = null;
                        }
                    });
                }
            });
        }
        fillDefaultItems();
    }

    private void createBreadcrumbs(ToolBar infoGroup, final DBNDatabaseNode databaseNode) {
        final DBNDatabaseNode curNode = editor.getEditorInput().getNavigatorNode();

        // FIXME: Drop-downs are too high - lead to minor UI glitches during editor opening. Also they don't make much sense.
        final ToolItem item = new ToolItem(infoGroup, databaseNode instanceof DBNDatabaseFolder ? SWT.DROP_DOWN : SWT.PUSH);
        item.setText(databaseNode.getNodeDisplayName());
        item.setImage(DBeaverIcons.getImage(databaseNode.getNodeIconDefault()));
        item.setData(databaseNode);

        if (databaseNode == curNode) {
            item.setToolTipText(databaseNode.getNodeTypeLabel());
            //item.setEnabled(false);
        } else {
            item.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (e.detail == SWT.ARROW) {
                        int itemCount = 0;
                        if (breadcrumbsMenu != null) {
                            breadcrumbsMenu.dispose();
                        }
                        breadcrumbsMenu = new Menu(item.getParent().getShell());
                        try {
                            final DBNNode[] childNodes = DBNUtils.getNodeChildrenFiltered(new VoidProgressMonitor(), databaseNode, false);
                            if (!ArrayUtils.isEmpty(childNodes)) {
                                for (final DBNNode folderItem : childNodes) {
                                    MenuItem childItem = new MenuItem(breadcrumbsMenu, SWT.NONE);
                                    childItem.setText(folderItem.getName());
                                    //                                childItem.setImage(DBeaverIcons.getImage(folderItem.getNodeIconDefault()));
                                    if (folderItem == curNode) {
                                        childItem.setEnabled(false);
                                    }
                                    childItem.addSelectionListener(new SelectionAdapter() {
                                        @Override
                                        public void widgetSelected(SelectionEvent e) {
                                            NavigatorHandlerObjectOpen.openEntityEditor(folderItem, null, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
                                        }
                                    });
                                    itemCount++;
                                    if (itemCount >= MAX_BREADCRUMBS_MENU_ITEM) {
                                        break;
                                    }
                                }
                            }
                        } catch (Throwable e1) {
                            log.error(e1);
                        }

                        Rectangle rect = item.getBounds();
                        Point pt = item.getParent().toDisplay(new Point(rect.x, rect.y));
                        breadcrumbsMenu.setLocation(pt.x, pt.y + rect.height);
                        breadcrumbsMenu.setVisible(true);
                    } else {
                        NavigatorHandlerObjectOpen.openEntityEditor(databaseNode, null, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
                    }
                }
            });
            item.setToolTipText(NLS.bind(UINavigatorMessages.actions_navigator_open, databaseNode.getNodeTypeLabel()));
        }
    }


}
