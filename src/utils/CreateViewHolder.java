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
        reformat();

        Utils.showInfoNotification(mProject, mViewHolderName + " create success");
    }

    protected void reformat() {
        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
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
        StringBuilder generatorForView = new StringBuilder();
        generatorForView.append("public " + holderClassName + "(android.view.View view) {\n");

        // rootView

        holderBuilder.append("\n\t\t// " + holderClassName + " create by " + mLayoutFileName + "\n\n");

        String rootViewName = "view";
        holderBuilder.append("public " + "android.view.View " + rootViewName + ";\n");
        generatorForView.append("this." + rootViewName + " = " + rootViewName + ";\n");

        String viewModel = "viewModel";
        holderBuilder.append("public " + modelClassName + " " + viewModel + ";\n");

        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }

            // field
            holderBuilder.append("public " + element.name + " " + element.getFieldName() + ";\n");

            // findViewById in generator
            generatorForView.append("this." + element.getFieldName() + " = (" + element.name + ") "
                    + rootViewName + ".findViewById(" + element.getFullID() + ");\n");

            if (isAutoImplements) {

                if (element.isClickable) {
                    generatorForView.append("this." + element.getFieldName() + ".setOnClickListener(this);\n");
                }

                if (element.isLongClickable) {
                    generatorForView.append("this." + element.getFieldName() + ".setOnLongClickListener(this);\n");
                }
            }
        }
        generatorForView.append("this." + viewModel + " = new " + modelClassName + "(this);\n");
        generatorForView.append("}\n");

        StringBuilder generatorForLayoutId = new StringBuilder();
        generatorForLayoutId.append("public " + holderClassName + "(android.content.Context context,int layoutId) {\n");
        generatorForLayoutId.append("this(LayoutInflater.from(context).inflate(layoutId, null));\n");
        generatorForLayoutId.append("}\n");

        StringBuilder onResume = new StringBuilder();
        onResume.append("@Override\npublic void onResume(){");
        onResume.append("this.viewModel.resume();\n");
        onResume.append("}\n");

        StringBuilder onPause = new StringBuilder();
        onPause.append("@Override\npublic void onPause(){");
        onPause.append("this.viewModel.pause();\n");
        onPause.append("}\n");

        StringBuilder getView = new StringBuilder();
        getView.append("@Override\npublic View getView(){");
        getView.append("return view;\n");
        getView.append("}\n");

        holderBuilder.append(generatorForLayoutId.toString());
        holderBuilder.append(generatorForView.toString());

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);
        viewHolder.setName(holderClassName);

        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName(CreateViewHolderConfig.VIEWHOLDER_INTERFACE_NAME, searchScope);
        if (psiClasses.length > 0) {
            for (int i = 0; i < psiClasses.length; i++) {
                if (psiClasses[i].getQualifiedName().equals(CreateViewHolderConfig.VIEWHOLDER_INTERFACE_FULL_NAME)) {
                    viewHolder.getImplementsList().add(mFactory.createClassReferenceElement(psiClasses[i]));
                }
            }
        }

        if (isAutoImplements) {
            generatorOnClick(viewHolder);
            generatorLongOnClick(viewHolder);
            generatorEditSubmit(viewHolder);
        }

        PsiMethod onResumeMethod = mFactory.createMethodFromText(onResume.toString(), viewHolder);
        PsiMethod onPauseMethod = mFactory.createMethodFromText(onPause.toString(), viewHolder);
        PsiMethod getViewMethod = mFactory.createMethodFromText(getView.toString(), viewHolder);

        viewHolder.add(onResumeMethod);
        viewHolder.add(onPauseMethod);
        viewHolder.add(getViewMethod);
        generateViewModel(viewHolder);

        mClass.add(viewHolder);
        mClass.addBefore(mFactory.createKeyword("public", mClass), mClass.findInnerClassByName(holderClassName, true));

        if (innerClass != null) {
            innerClass.delete();
        }
    }


    protected void generateViewModel(PsiClass psiClass) {
        // view model class
        String holderClassName = mViewHolderName + Utils.getViewHolderClassName();
        String modelClassName = mViewHolderName + Utils.getViewModelClassName();
        PsiClass innerClass = psiClass.findInnerClassByName(modelClassName, false);
        if (innerClass != null) {
//            Utils.showErrorNotification(mProject, holderClassName + " is exist!");
//            return;
        }

        StringBuilder holderBuilder = new StringBuilder();

        // generator of view holder class
        StringBuilder resume = new StringBuilder();
        StringBuilder pause = new StringBuilder();
        StringBuilder generator = new StringBuilder();
        generator.append("public " + modelClassName + "(" + holderClassName + " viewHolder) {\n");

        resume.append("void resume(){");
        pause.append("void pause(){");


        // rootView

        holderBuilder.append("\n\t\t// " + modelClassName + " create by " + mLayoutFileName + "\n\n");

        String viewHolder = "viewHolder";
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

            resume.append("this." + element.getFieldName() + ".onResume();\n");
            pause.append("this." + element.getFieldName() + ".onPause();\n");
        }
        generator.append("}\n");
        resume.append("}\n");
        pause.append("}\n");

        holderBuilder.append(generator.toString());

        holderBuilder.append(resume.toString());
        holderBuilder.append(pause.toString());


        PsiClass viewModel = mFactory.createClassFromText(holderBuilder.toString(), psiClass);
        viewModel.setName(modelClassName);


        psiClass.add(viewModel);
        psiClass.addBefore(mFactory.createKeyword("public", psiClass), psiClass.findInnerClassByName(modelClassName, true));

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
        PsiMethod[] initViewMethods = psiClass.findMethodsByName(CreateViewHolderConfig.ONCLICK_NAME, false);
        if (initViewMethods.length > 0 && initViewMethods[0].getBody() != null) {
        } else {
            if (clickableElements.size() > 0) {

                GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
                PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName(CreateViewHolderConfig.ONCLICK_INTERFACE_NAME, searchScope);
                if (psiClasses.length > 0) {
                    for (int i = 0; i < psiClasses.length; i++) {
                        if (psiClasses[i].getQualifiedName().equals(CreateViewHolderConfig.ONCLICK_INTERFACE_FULL_NAME)) {
                            psiClass.getImplementsList().add(mFactory.createClassReferenceElement(psiClasses[i]));
                        }
                    }
                }

                StringBuilder caseBuider = new StringBuilder();
                for (int i = 0; i < clickableElements.size(); i++) {
                    caseBuider.append("case " + clickableElements.get(i).getFullID() + " :\n\nbreak;\n");
                }
                PsiMethod method = mFactory.createMethodFromText("public void " + CreateViewHolderConfig.ONCLICK_NAME + "(View v){\nswitch (v.getId()) {\n" +
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
        PsiMethod[] initViewMethods = psiClass.findMethodsByName(CreateViewHolderConfig.ONLONGCLICK_NAME, false);
        if (initViewMethods.length > 0 && initViewMethods[0].getBody() != null) {
        } else {
            if (longClickableElements.size() > 0) {

                GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
                PsiClass[] psiOnLongClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName(CreateViewHolderConfig.ONLONGCLICK_INTERFACE_NAME, searchScope);
                if (psiOnLongClasses.length > 0) {
                    for (int i = 0; i < psiOnLongClasses.length; i++) {
                        if (psiOnLongClasses[i].getQualifiedName().equals(CreateViewHolderConfig.ONLONGCLICK_INTERFACE_FULL_NAME)) {
                            psiClass.getImplementsList().add(mFactory.createClassReferenceElement(psiOnLongClasses[i]));
                        }
                    }
                }

                StringBuilder caseBuider = new StringBuilder();
                for (int i = 0; i < longClickableElements.size(); i++) {
                    caseBuider.append("case " + longClickableElements.get(i).getFullID() + " :\n\nbreak;\n");
                }
                PsiMethod method = mFactory.createMethodFromText("public boolean " + CreateViewHolderConfig.ONLONGCLICK_NAME + "(View v){\nswitch (v.getId()) {\n" +
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