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
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
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
    private static final Object VISIBILITY_ITEM = new Object();

    private static final int MAX_BREADCRUMBS_MENU_ITEM = 300;

    private static final Log log = Log.getLog(EntityEditorBreadcrumbsPanel.class);

    @Nullable
    private EntityEditor editor;
    private final boolean statusLine;
    private final ToolBar bcToolbar;

    private Menu breadcrumbsMenu;
    private ISelectionProvider savedPartSelectionProvider = null;
    private DBNDatabaseNode selectedNode;

    public EntityEditorBreadcrumbsPanel(@NotNull Composite parent, @Nullable EntityEditor editor, boolean statusLine) {
        super(parent, SWT.NONE);
        this.editor = editor;
        this.statusLine = statusLine;

        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        layout.verticalSpacing = 0;
        layout.horizontalSpacing = 0;
        this.setLayout(layout);

        // Path
        bcToolbar = new ToolBar(this, SWT.HORIZONTAL | SWT.RIGHT | SWT.FLAT);
        GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
        gd.grabExcessHorizontalSpace = true;
        gd.grabExcessVerticalSpace = true;
        bcToolbar.setLayoutData(gd);
//        bcToolbar.setForeground(
//            UIUtils.isDark(bcToolbar.getBackground().getRGB()) ? UIUtils.COLOR_WHITE : UIStyles.getDefaultTextForeground());
        bcToolbar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                ToolItem onItem = bcToolbar.getItem(new Point(e.x, e.y));
                selectedNode = onItem == null ? null : onItem.getData() instanceof DBNDatabaseNode node ? node : null;
                if (onItem != null && onItem.getData() == VISIBILITY_ITEM) {
                    UIUtils.asyncExec(() -> toggleToolbars());
                }
            }
        });
        bcToolbar.setForeground(UIStyles.getDefaultTextForeground());
        //bcToolbar.setBackground(UIStyles.getDefaultTextForeground());

        fillToolbar();

        if (editor == null || editor.getEditorInput() instanceof DatabaseLazyEditorInput) {
            bcToolbar.setEnabled(false);
        }

        addDisposeListener(e -> {
            if (breadcrumbsMenu != null) {
                breadcrumbsMenu.dispose();
                breadcrumbsMenu = null;
            }
        });
    }

    private void fillToolbar() {
        if (editor != null && UINavigatorActivator.getDefault().getPreferences().getBoolean(PREF_BREADCRUMBS_VISIBLE)) {
            fillBreadcrumbs();
        } else {
            fillDefaultItems();
        }
    }

    public void setEditor(@Nullable EntityEditor editor) {
        this.editor = editor;
        for (ToolItem item : bcToolbar.getItems()) item.dispose();
        fillToolbar();

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

        getParent().layout(true, true);
    }

    private void fillDefaultItems() {
        boolean bcVisible = UINavigatorActivator.getDefault().getPreferences().getBoolean(PREF_BREADCRUMBS_VISIBLE);
        ToolItem item = new ToolItem(bcToolbar, SWT.PUSH);
        item.setData(VISIBILITY_ITEM);
        item.setImage(DBeaverIcons.getImage(bcVisible ? UIIcon.NOTIFICATION_CLOSE : UIIcon.ASTERISK));
        item.setToolTipText(bcVisible ? "Hide breadcrumbs" : "Show breadcrumbs");
    }

    private void fillBreadcrumbs() {
        if (editor == null) {
            return;
        }
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
                if (!statusLine) {
                    savedPartSelectionProvider = editor.getSite().getSelectionProvider();
                    editor.getSite().setSelectionProvider(selProvider);
                    selProvider.setSelection(selProvider.getSelection());

                    if (selectedNode == null) {
                        selProvider.setSelection(new StructuredSelection());
                    } else {
                        selProvider.setSelection(new StructuredSelection(selectedNode));
                    }
                }
                NavigatorUtils.addStandardMenuItem(editor.getSite(), manager, selProvider);
            });
            menuMgr.setRemoveAllWhenShown(true);
            bcToolbar.setMenu(menu);

            if (!statusLine) {
                editor.getSite().registerContextMenu("entityBreadcrumbsMenu", menuMgr, selProvider);
            }

            if (!statusLine) {
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
        }
        fillDefaultItems();
    }

    private void createBreadcrumbs(ToolBar infoGroup, final DBNDatabaseNode databaseNode) {
        if (editor == null || databaseNode instanceof DBNDatabaseFolder) {
            return;
        }
        final DBNDatabaseNode curNode = editor.getEditorInput().getNavigatorNode();

        // FIXME: Drop-downs are too high - lead to minor UI glitches during editor opening. Also they don't make much sense.
        final ToolItem item = new ToolItem(infoGroup, SWT.PUSH);
        item.setText(databaseNode.getNodeDisplayName());
        if (infoGroup.getItemCount() > 1) {
            item.setImage(DBeaverIcons.getImage(UIIcon.ARROW_RIGHT));
        }
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
