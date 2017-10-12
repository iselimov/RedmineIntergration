package com.defrag.redmineplugin.view;

import com.defrag.redmineplugin.model.ConnectionInfo;
import com.defrag.redmineplugin.model.Report;
import com.defrag.redmineplugin.model.ReportInfo;
import com.defrag.redmineplugin.model.Task;
import com.defrag.redmineplugin.service.ReportManager;
import com.defrag.redmineplugin.service.TaskManager;
import com.defrag.redmineplugin.service.util.ViewLogger;
import com.defrag.redmineplugin.view.form.ReportForm;
import com.defrag.redmineplugin.view.form.SettingsForm;
import com.defrag.redmineplugin.view.form.TaskForm;
import com.defrag.redmineplugin.view.form.model.TaskTableModel;
import com.defrag.redmineplugin.view.form.wrapper.ReportFormWrapper;
import com.defrag.redmineplugin.view.form.wrapper.SettingsFormWrapper;
import com.defrag.redmineplugin.view.form.wrapper.TaskFormWrapper;
import com.defrag.redmineplugin.view.tree.MainRootNode;
import com.defrag.redmineplugin.view.tree.StatusTreeModel;
import com.defrag.redmineplugin.view.tree.StatusTreeStructure;
import com.defrag.redmineplugin.view.tree.TaskManagerConsumer;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by defrag on 06.07.17.
 */
public class MainPanel extends SimpleToolWindowPanel {

    private final ViewLogger viewLogger;

    private TaskTableModel taskModel;

    private JBTable taskTable;

    private TaskManagerConsumer rootNode;

    private ConnectionInfo connectionInfo;

    private ReportInfo reportInfo;

    private TaskManager taskManager;

    private ReportManager reportManager;


    public MainPanel(Project project) {
        super(true);

        connectionInfo = ServiceManager.getService(project, ConnectionInfo.class);
        reportInfo = ServiceManager.getService(project, ReportInfo.class);
        viewLogger = new ViewLogger(project);

        final DefaultTreeModel model = new StatusTreeModel();
        final SimpleTree reviewTree = new SimpleTree(model);

        rootNode = new MainRootNode(viewLogger);
        final SimpleTreeStructure reviewTreeStructure = new StatusTreeStructure(rootNode);
        new AbstractTreeBuilder(reviewTree, model, reviewTreeStructure, null);
        reviewTree.invalidate();

        final JBSplitter mainSplitter = new JBSplitter(false, 0.2f);
        final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(reviewTree);
        mainSplitter.setFirstComponent(scrollPane);
        mainSplitter.setResizeEnabled(false);

        JBSplitter settingsSplitter = new JBSplitter(true, 0.1f);
        JComponent settingsToolbar = createSettingsToolbar(project);
        JComponent spTable = createTaskTable(project);
        settingsSplitter.setFirstComponent(settingsToolbar);
        settingsSplitter.setSecondComponent(spTable);
        settingsSplitter.setResizeEnabled(false);

        mainSplitter.setSecondComponent(settingsSplitter);

        setContent(mainSplitter);

        if (connectionInfo.hasKeyParams()) {
            createManagers();
        }
    }

    private JComponent createTaskTable(Project project) {
        taskModel = new TaskTableModel(project, viewLogger);
        taskTable = new JBTable(taskModel);
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setStriped(true);
        taskTable.setExpandableItemsEnabled(false);

        setUpColumnWidths(taskTable);

        return ScrollPaneFactory.createScrollPane(taskTable);
    }

    @NotNull
    private JComponent createSettingsToolbar(Project project) {
        JToolBar settingsToolBar = new JToolBar();
        settingsToolBar.setBorderPainted(true);
        settingsToolBar.setFocusable(true);
        settingsToolBar.setFloatable(true);
        settingsToolBar.setOpaque(true);
        settingsToolBar.setRequestFocusEnabled(true);
        settingsToolBar.add(addSettingsButton(project));
        settingsToolBar.add(addEditTaskButton(project));
        settingsToolBar.add(addLinkToRedmineButton());
        settingsToolBar.add(addSubTaskButton());
        settingsToolBar.add(addMailButton(project));

        return settingsToolBar;
    }

    private JButton addSettingsButton(Project project) {
        JButton settingsBut = new JButton(getIcon("settings.png"));
        settingsBut.setFocusable(true);
        settingsBut.setBorderPainted(true);
        settingsBut.setHorizontalAlignment(SwingConstants.LEFT);
        settingsBut.setToolTipText("Plugin Settings");
        settingsBut.addActionListener(e -> {
            SettingsFormWrapper wrapper = new SettingsFormWrapper(project, new SettingsForm(connectionInfo));
            wrapper.show();
            if (wrapper.isOK()) {
                connectionInfo = wrapper.getData();

                try {
                    new URI(connectionInfo.getRedmineUri());
                } catch (URISyntaxException ex) {
                    viewLogger.error("Был введен некорректный Redmine URI");
                    return;
                }

                createManagers();
            }
        });
        return settingsBut;
    }

    private JButton addEditTaskButton(Project project) {
        JButton editTaskBut = new JButton(getIcon("edit.png"));
        editTaskBut.setFocusable(true);
        editTaskBut.setBorderPainted(true);
        editTaskBut.setHorizontalAlignment(SwingConstants.LEFT);
        editTaskBut.setToolTipText("Edit task");
        editTaskBut.addActionListener(e -> {
            int selectedRow = taskTable.getSelectedRow();

            taskModel.getTask(selectedRow)
                    .ifPresent(task -> {
                        TaskFormWrapper wrapper = new TaskFormWrapper(project, new TaskForm(project, task,
                                connectionInfo.hasExtendedProps()), task.getId());
                        wrapper.show();
                        if (wrapper.isOK()) {
                            Task toUpdate = wrapper.getData();
                            taskManager.updateTask(toUpdate);
                            taskModel.updateTask(selectedRow, toUpdate);
                        }
                    });
        });
        return editTaskBut;
    }

    private JButton addLinkToRedmineButton() {
        JButton linkToRedmineBut = new JButton(getIcon("find.png"));
        linkToRedmineBut.setFocusable(true);
        linkToRedmineBut.setBorderPainted(true);
        linkToRedmineBut.setHorizontalAlignment(SwingConstants.LEFT);
        linkToRedmineBut.setToolTipText("Show in Redmine");
        linkToRedmineBut.addActionListener(e -> {
            int selectedRow = taskTable.getSelectedRow();

            taskModel.getTask(selectedRow)
                    .ifPresent(task -> {
                        String[] curlCommand = new String[] {"/bin/bash", "-c", "xdg-open " +
                                connectionInfo.getURIForTask(task.getId())};
                        try {
                            Process post = new ProcessBuilder(curlCommand).start();
                            post.waitFor();
                        } catch (IOException | InterruptedException ex) {
                            viewLogger.error("Не удалось открыть задачу '%d'", task.getId());
                        }
                    });
        });
        return linkToRedmineBut;
    }

    private JButton addSubTaskButton() {
        JButton addSubTaskBut = new JButton(getIcon("copy.png"));
        addSubTaskBut.setFocusable(true);
        addSubTaskBut.setBorderPainted(true);
        addSubTaskBut.setHorizontalAlignment(SwingConstants.LEFT);
        addSubTaskBut.setToolTipText("Create subtask (only for high level task)");
        addSubTaskBut.addActionListener(e -> {
            int selectedRow = taskTable.getSelectedRow();

            taskModel.getTask(selectedRow)
                    .ifPresent(task -> taskManager.createSubTask(task));
        });
        return addSubTaskBut;
    }

    private JButton addMailButton(Project project) {
        JButton mailBut = new JButton(getIcon("mail.png"));
        mailBut.setFocusable(true);
        mailBut.setBorderPainted(true);
        mailBut.setHorizontalAlignment(SwingConstants.LEFT);
        mailBut.setToolTipText("Send report to mail");
        mailBut.addActionListener(e -> {
            ReportFormWrapper wrapper = new ReportFormWrapper(project, new ReportForm(project, reportInfo, viewLogger));
            wrapper.show();
            if (wrapper.isOK()) {
                Report toSend = wrapper.getData();
                reportInfo = toSend.getReportInfo();
                reportManager.sendReport(toSend);
            }
        });
        return mailBut;
    }

    private void createManagers() {
        taskManager = new TaskManager(connectionInfo, viewLogger);
        rootNode.setTaskManager(taskManager);
        rootNode.setTaskModel(taskModel);

        reportManager = new ReportManager(connectionInfo, viewLogger);
    }

    private Icon getIcon(String iconName) {
        return new ImageIcon(ResourceUtil.getResource(this.getClass().getClassLoader(), "", iconName));
    }

    private static void setUpColumnWidths(@NotNull final JBTable table) {
        table.setRowHeight(30);

        table.getColumnModel().getColumn(0).setResizable(false);
        table.getColumnModel().getColumn(1).setResizable(false);
        table.getColumnModel().getColumn(2).setResizable(false);
        table.getColumnModel().getColumn(3).setResizable(false);
        table.getColumnModel().getColumn(4).setResizable(false);
        table.getColumnModel().getColumn(5).setResizable(false);
        table.getColumnModel().getColumn(6).setResizable(false);

        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(2).setMaxWidth(150);
        table.getColumnModel().getColumn(3).setMaxWidth(150);
        table.getColumnModel().getColumn(4).setMaxWidth(800);
        table.getColumnModel().getColumn(5).setMaxWidth(80);
        table.getColumnModel().getColumn(6).setMaxWidth(80);
    }
}