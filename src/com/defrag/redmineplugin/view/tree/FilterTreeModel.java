package com.defrag.redmineplugin.view.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Created by defrag on 23.07.17.
 */
public class FilterTreeModel extends DefaultTreeModel {

    public FilterTreeModel() {
        super(new DefaultMutableTreeNode(), false);
    }
}