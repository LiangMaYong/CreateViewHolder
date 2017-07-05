package utils;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import entity.Element;
import org.apache.http.util.TextUtils;

import java.util.ArrayList;
import java.util.List;


public class CreateViewHolder extends WriteCommandAction.Simple {

    protected PsiFile mFile;
    protected Project mProject;
    protected PsiClass mClass;
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mLayoutFileName;
    protected String mFieldNamePrefix;
    protected String mViewHolderName;
    protected boolean isAutoImplements;

    public CreateViewHolder(PsiFile file, PsiClass clazz, String command, ArrayList<Element> elements, String layoutFileName, String viewHolderName, String fieldNamePrefix, boolean autoImplements) {
        super(clazz.getProject(), command);
        mFile = file;
        mProject = clazz.getProject();
        mClass = clazz;
        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        mLayoutFileName = layoutFileName;
        mViewHolderName = viewHolderName;
        mFieldNamePrefix = fieldNamePrefix;
        isAutoImplements = autoImplements;
    }

    @Override
    public void run() throws Throwable {

        generateViewHolder();
        generateViewModel();

        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();

        Utils.showInfoNotification(mProject, mViewHolderName + " create success");
    }

    /**
     * Create ViewHolder for adapters with injections
     */
    protected void generateViewHolder() {
        // view holder class
        String holderClassName = mViewHolderName + Utils.getViewHolderClassName();
        String modelClassName = mViewHolderName + Utils.getViewModelClassName();
        PsiClass innerClass = mClass.findInnerClassByName(holderClassName, false);
        if (innerClass != null) {
//            Utils.showErrorNotification(mProject, holderClassName + " is exist!");
//            return;
        }
        StringBuilder holderBuilder = new StringBuilder();

        // generator of view holder class
        StringBuilder generator = new StringBuilder();
        generator.append("public " + holderClassName + "(android.view.View view) {\n");

        // rootView

        holderBuilder.append("\n\t\t// " + holderClassName + " create by " + mLayoutFileName + "\n\n");

        String rootViewName = "view";
        holderBuilder.append("public " + "android.view.View " + rootViewName + ";\n");
        generator.append("this." + rootViewName + " = " + rootViewName + ";\n");

        String viewModel = "model";
        holderBuilder.append("public " + modelClassName + " " + viewModel + ";\n");
        generator.append("this." + viewModel + " = new " + modelClassName + "(this);\n");

        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }

            // field
            holderBuilder.append("public " + element.name + " " + element.getFieldName() + ";\n");

            // findViewById in generator
            generator.append("this." + element.getFieldName() + " = (" + element.name + ") "
                    + rootViewName + ".findViewById(" + element.getFullID() + ");\n");

            if (isAutoImplements) {

                if (element.isClickable) {
                    generator.append("this." + element.getFieldName() + ".setOnClickListener(this);\n");
                }

                if (element.isLongClickable) {
                    generator.append("this." + element.getFieldName() + ".setOnLongClickListener(this);\n");
                }
            }
        }
        generator.append("}\n");

        StringBuilder generatorForActivity = new StringBuilder();
        generatorForActivity.append("public " + holderClassName + "(android.app.Activity activity) {\n");
        generatorForActivity.append("this(activity.getWindow().getDecorView());\n");
        generatorForActivity.append("}\n");
        StringBuilder checkChanged = new StringBuilder();
        checkChanged.append("public void checkChanged(){");
        checkChanged.append("this.model.bind();\n");
        checkChanged.append("}\n");

        holderBuilder.append(generatorForActivity.toString());
        holderBuilder.append(generator.toString());
        holderBuilder.append(checkChanged.toString());

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewHolder.setName(holderClassName);

        if (isAutoImplements) {
            generatorOnClick(viewHolder);
            generatorLongOnClick(viewHolder);
            generatorEditSubmit(viewHolder);
        }

        mClass.add(viewHolder);
        mClass.addBefore(mFactory.createKeyword("private", mClass), mClass.findInnerClassByName(holderClassName, true));

        if (innerClass != null) {
            innerClass.delete();
        }
    }


    protected void generateViewModel() {
        // view model class
        String holderClassName = mViewHolderName + Utils.getViewHolderClassName();
        String modelClassName = mViewHolderName + Utils.getViewModelClassName();
        PsiClass innerClass = mClass.findInnerClassByName(modelClassName, false);
        if (innerClass != null) {
//            Utils.showErrorNotification(mProject, holderClassName + " is exist!");
//            return;
        }

        StringBuilder holderBuilder = new StringBuilder();

        // generator of view holder class
        StringBuilder bind = new StringBuilder();
        StringBuilder generator = new StringBuilder();
        generator.append("public " + modelClassName + "(" + holderClassName + " holder) {\n");

        bind.append("void bind(){");


        // rootView

        holderBuilder.append("\n\t\t// " + modelClassName + " create by " + mLayoutFileName + "\n\n");

        String viewHolder = "holder";
        holderBuilder.append("public " + holderClassName + " " + viewHolder + ";\n");
        generator.append("this." + viewHolder + " = " + viewHolder + ";\n");

        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }

            String viewModelName = "Bind" + element.name + "Model";

            // field
            holderBuilder.append("public " + viewModelName + " " + element.getFieldName() + ";\n");

            generator.append("this." + element.getFieldName() + " = new " + viewModelName + "(" + viewHolder + "." + element.getFieldName() + ");\n");

            bind.append("this." + element.getFieldName() + ".bindData(" + viewHolder + "." + element.getFieldName() + ");\n");
        }
        generator.append("}\n");
        bind.append("}\n");

        holderBuilder.append(generator.toString());

        holderBuilder.append(bind.toString());


        PsiClass viewModel = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewModel.setName(modelClassName);


        mClass.add(viewModel);
        mClass.addBefore(mFactory.createKeyword("public", mClass), mClass.findInnerClassByName(modelClassName, true));

        if (innerClass != null) {
            innerClass.delete();
        }
    }

    /**
     * generatorBindOnClick
     */
    protected void generatorOnClick(PsiClass psiClass) {
        List<Element> clickableElements = new ArrayList<Element>();
        for (Element element : mElements) {
            if (element.isClickable) {
                clickableElements.add(element);
            }
        }
        PsiMethod[] initViewMethods = psiClass.findMethodsByName("onClick", false);
        if (initViewMethods.length > 0 && initViewMethods[0].getBody() != null) {
        } else {
            if (clickableElements.size() > 0) {

                GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
                PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("OnClickListener", searchScope);
                if (psiClasses.length > 0) {
                    for (int i = 0; i < psiClasses.length; i++) {
                        if (psiClasses[i].getQualifiedName().equals("android.view.View.OnClickListener")) {
                            psiClass.getImplementsList().add(mFactory.createClassReferenceElement(psiClasses[i]));
                        }
                    }
                }

                StringBuilder caseBuider = new StringBuilder();
                for (int i = 0; i < clickableElements.size(); i++) {
                    caseBuider.append("case " + clickableElements.get(i).getFullID() + " :\n\nbreak;\n");
                }
                PsiMethod method = mFactory.createMethodFromText("public void onClick(View v){\nswitch (v.getId()) {\n" +
                        caseBuider.toString() +
                        "\t\t}\n" +
                        "}\n", psiClass);
                method.getModifierList().addAnnotation("Override");
                psiClass.add(method);
            }
        }
    }

    /**
     * generatorBindLongOnClick
     */
    protected void generatorLongOnClick(PsiClass psiClass) {
        List<Element> longClickableElements = new ArrayList<Element>();
        for (Element element : mElements) {
            if (element.isLongClickable) {
                longClickableElements.add(element);
            }
        }
        PsiMethod[] initViewMethods = psiClass.findMethodsByName("onLongClick", false);
        if (initViewMethods.length > 0 && initViewMethods[0].getBody() != null) {
        } else {
            if (longClickableElements.size() > 0) {

                GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
                PsiClass[] psiOnLongClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("OnLongClickListener", searchScope);
                if (psiOnLongClasses.length > 0) {
                    for (int i = 0; i < psiOnLongClasses.length; i++) {
                        if (psiOnLongClasses[i].getQualifiedName().equals("android.view.View.OnLongClickListener")) {
                            psiClass.getImplementsList().add(mFactory.createClassReferenceElement(psiOnLongClasses[i]));
                        }
                    }
                }

                StringBuilder caseBuider = new StringBuilder();
                for (int i = 0; i < longClickableElements.size(); i++) {
                    caseBuider.append("case " + longClickableElements.get(i).getFullID() + " :\n\nbreak;\n");
                }
                PsiMethod method = mFactory.createMethodFromText("public boolean onLongClick(View v){\nswitch (v.getId()) {\n" +
                        caseBuider.toString() +
                        "\t\t}\n" +
                        "\t\treturn false;\n" +
                        "}\n", psiClass);
                method.getModifierList().addAnnotation("Override");
                psiClass.add(method);
            }
        }
    }


    /**
     * generatorEditSubmit
     */
    protected void generatorEditSubmit(PsiClass psiClass) {
        List<Element> editTextElements = new ArrayList<>();
        for (Element element : mElements) {
            // set flag
            if (element.isEditText) {
                String hint = element.xml.getAttributeValue("android:hint");
                if (hint != null) {
                    editTextElements.add(element);
                }
            }
        }
        PsiMethod[] initViewMethods = psiClass.findMethodsByName("submit", false);
        if (initViewMethods.length > 0 && initViewMethods[0].getBody() != null) {
        } else {
            // generator EditText validate code if need
            StringBuilder sbEditText = new StringBuilder();
            if (editTextElements.size() > 0) {

                sbEditText.append("public void submit() {\n");
                sbEditText.append("\t\t// validate\n");

                for (Element element : editTextElements) {
                    // generator EditText string name
                    String idName = element.id;
                    int index = idName.lastIndexOf("_");
                    String name = index == -1 ? idName : idName.substring(index + 1);
                    if (name.equals(idName)) {
                        name += "String";
                    }
                    sbEditText.append("String " + name + " = " + idName + ".getText().toString().trim();\n");
                    sbEditText.append("if(" + name + " != null && !" + name + ".equals(\"\")) {\n");
                    String emptyTint = "\"" + name + "不能为空" + "\"";
                    String hint = element.xml.getAttributeValue("android:hint");
                    if (hint.startsWith("@string")) {
                        emptyTint = "R.string." + hint.replace("@string/", "");
                    } else if (!TextUtils.isEmpty(hint)) {
                        emptyTint = "\"" + hint + "\"";
                    }
                    sbEditText.append("Toast.makeText(view.getContext()," + emptyTint + ",Toast.LENGTH_SHORT).show();\n");
                    sbEditText.append("return;\n");
                    sbEditText.append("}\n");
                    sbEditText.append("\n");
                }
                sbEditText.append("\t\t// TODO validate success, do something\n");
                sbEditText.append("\t\t\n}\n");

                psiClass.add(mFactory.createMethodFromText(sbEditText.toString(), psiClass));
            }
        }
    }

}