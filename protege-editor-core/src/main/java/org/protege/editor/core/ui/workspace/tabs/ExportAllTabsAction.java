package org.protege.editor.core.ui.workspace.tabs;

import org.protege.editor.core.ui.action.ProtegeAction;
import org.protege.editor.core.ui.util.UIUtil;
import org.protege.editor.core.ui.workspace.TabbedWorkspace;
import org.protege.editor.core.ui.workspace.WorkspaceTab;
import org.protege.editor.core.ui.workspace.WorkspaceViewsTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by vblagodarov on 11-08-17.
 */
public class ExportAllTabsAction extends ProtegeAction {

    private static final long serialVersionUID = 1241733437687512399L;
    private final Logger logger = LoggerFactory.getLogger(ExportTabAction.class);

    public ExportAllTabsAction() {
    }

    public void initialise() throws Exception {
    }

    public void dispose() throws Exception {
    }

    public void actionPerformed(ActionEvent event) {
        TabbedWorkspace workspace = (TabbedWorkspace) getWorkspace();
        File dir=UIUtil.chooseFolder(SwingUtilities.getAncestorOfClass(Window.class, workspace), "Select directory");
        if(!dir.isDirectory()){
            logger.error("The selected object is not a directory");
            JOptionPane.showMessageDialog(workspace,
                    "The selected object is not a directory",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        for(WorkspaceTab tab : workspace.getWorkspaceTabs()){
            String fileName = tab.getLabel().replace(' ', '_') + ".layout.xml";
            File f = new File(dir, fileName);
            try(FileWriter writer = new FileWriter(f))
            {
                ((WorkspaceViewsTab) tab).getViewsPane().saveViews(writer);
                writer.close();
            } catch (IOException e) {
                logger.error("An error occurred when saving a tab layout to {}.", f, e);
                JOptionPane.showMessageDialog(workspace,
                        "There was a problem saving the layout",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

}
