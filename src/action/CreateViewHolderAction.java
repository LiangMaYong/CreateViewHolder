package action;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import entity.Element;
import form.EntryList;
import listener.ICancelListener;
import listener.IConfirmListener;
import utils.CreateViewHolder;
import utils.CreateViewHolderConfig;
import utils.StringUtils;
import utils.Utils;

import javax.swing.*;
import java.util.ArrayList;

public class CreateViewHolderAction extends BaseGenerateAction implements IConfirmListener, ICancelListener {

    protected JFrame mDialog;

    @SuppressWarnings("unused")
    public CreateViewHolderAction() {
        super(null);
    }

    @SuppressWarnings("unused")
    public CreateViewHolderAction(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(PlatformDataKeys.PROJECT);
        Editor editor = event.getData(PlatformDataKeys.EDITOR);

        actionPerformedImpl(project, editor);
    }

    @Override
    public void actionPerformedImpl(Project project, Editor editor) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        if (layout == null) {
            Utils.showErrorNotification(project, "No layout found");
            return; // no layout found
        }
        ArrayList<Element> elements = Utils.getIDsFromLayout(layout);
        if (!elements.isEmpty()) {
            showDialog(project, editor, layout.getName(), elements);
        } else {
            Utils.showErrorNotification(project, "No IDs found in layout");
        }
    }

    public void onConfirm(Project project, Editor editor, String viewHolderName, ArrayList<Element> elements, String fieldNamePrefix, boolean autoImplements) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiFile layout = Utils.getLayoutFileFromCaret(editor, file);

        closeDialog();

        // count selected elements
        int cnt = 0;
        for (Element element : elements) {
            if (element.used) {
                cnt++;
            }
        }

        if (cnt > 0) { // generate injections
            if (layout == null) {
                return;
            }
            new CreateViewHolder(file, getTargetClass(editor, file), "Generate Injections", elements, layout.getName(), viewHolderName, fieldNamePrefix, autoImplements).execute();
        } else { // just notify user about no element selected
            Utils.showInfoNotification(project, "No injection was selected");
        }
    }

    public void onCancel() {
        closeDialog();
    }

    protected void showDialog(Project project, Editor editor, String viewHolderName, ArrayList<Element> elements) {
        PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
        if (file == null) {
            return;
        }
        PsiClass clazz = getTargetClass(editor, file);

        if (clazz == null) {
            return;
        }

        String[] names = viewHolderName.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            sb.append(StringUtils.firstToUpperCase(names[i]));
        }
        viewHolderName = sb.toString().replaceAll(".xml", "") + Utils.getViewHolderClassName();
        EntryList panel = new EntryList(project, editor, viewHolderName, elements, this, this);

        mDialog = new JFrame();
        mDialog.setTitle(CreateViewHolderConfig.DIALIG_TITLE);
        mDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mDialog.getRootPane().setDefaultButton(panel.getConfirmButton());
        mDialog.getContentPane().add(panel);
        mDialog.pack();
        mDialog.setLocationRelativeTo(null);
        mDialog.setVisible(true);
    }

    protected void closeDialog() {
        if (mDialog == null) {
            return;
        }

        mDialog.setVisible(false);
        mDialog.dispose();
    }
}
