package com.csereoka.reactions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateFileAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.IncorrectOperationException;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

public class CreateReactComponentDirectoryAction extends AnAction implements UpdateInBackground, WriteActionAware {
    protected static final Logger LOG = Logger.getInstance(CreateReactComponentDirectoryAction.class);

    private static final String INDEX_FT = "index";
    private static final String COMPONENT_FT = "Component";
    private static final String CSS_FT = "CSS";
    private static final String STORYBOOK_FT = "Storybook";

    private static final String BASIC_NAME = "Basic";
    private static final String WITH_CSS_MODULE_NAME = "With CSS Module";
    private static final String WITH_CSS_MODULE_AND_STORYBOOK_NAME = "With CSS Module and Storybook";

    private static final String NEW_REACT_COMPONENT_FOLDER = "New React Component Folder";
    private static final String CSS_MODULE_SUFFIX = ".module";
    private static final String STORYBOOK_SUFFIX = ".stories";

    @Override
    public void update(@NotNull final AnActionEvent e) {
        final DataContext dataContext = e.getDataContext();
        final Presentation presentation = e.getPresentation();
        final boolean enabled = isAvailable(dataContext);
        presentation.setEnabledAndVisible(enabled);
    }

    private boolean isAvailable(DataContext dataContext) {
        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
        return project != null && view != null && view.getDirectories().length != 0;
    }

    @Override
    public final void actionPerformed(@NotNull final AnActionEvent e) {
        final DataContext dataContext = e.getDataContext();

        final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
        if (view == null) {
            return;
        }

        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        final PsiDirectory dir = view.getOrChooseDirectory();
        if (dir == null || project == null) return;

        final CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog
                .createDialog(project)
                .setTitle(NEW_REACT_COMPONENT_FOLDER)
                .addKind(BASIC_NAME, null, COMPONENT_FT)
                .addKind(WITH_CSS_MODULE_NAME, null, CSS_FT)
                .addKind(WITH_CSS_MODULE_AND_STORYBOOK_NAME, null, STORYBOOK_FT);

        builder.show(CommonBundle.getErrorTitle(), getDefaultTemplateName(),
                new CreateFileFromTemplateDialog.FileCreator<PsiDirectory>() {
                    @Override
                    public PsiDirectory createFile(@NotNull String name, @NotNull String templateName) {
                        return CreateReactComponentDirectoryAction.this.createComponentDirectory(name, templateName, dir);
                    }

                    @Override
                    public boolean startInWriteAction() {
                        return CreateReactComponentDirectoryAction.this.startInWriteAction();
                    }

                    @Override
                    @NotNull
                    public String getActionName(@NotNull String name, @NotNull String templateName) {
                        return CreateReactComponentDirectoryAction.this.getActionName();
                    }
                }, EmptyConsumer.getInstance());
    }

    private String getDefaultTemplateName() {
        return CSS_FT;
    }

    private String getActionName() {
        return "React Component Directory";
    }

    private PsiDirectory createComponentDirectory(String name, String templateName, PsiDirectory dir) {
        PsiDirectory newDirectory = dir.createSubdirectory(name);
        FileTemplateManager templateManager = getTemplateManagerInstance(newDirectory);

        // Create the index file and the component itself, in any of the situations
        createFileFromTemplate("index", templateManager.getInternalTemplate(INDEX_FT), newDirectory, name);
        createFileFromTemplate(name, templateManager.getInternalTemplate(COMPONENT_FT), newDirectory, null);

        switch (templateName) {
            case CSS_FT:
                createFileFromTemplate(name + CSS_MODULE_SUFFIX, templateManager.getInternalTemplate(CSS_FT), newDirectory, null);
                break;
            case STORYBOOK_FT:
                createFileFromTemplate(name + CSS_MODULE_SUFFIX, templateManager.getInternalTemplate(CSS_FT), newDirectory, null);
                createFileFromTemplate(name + STORYBOOK_SUFFIX, templateManager.getInternalTemplate(STORYBOOK_FT), newDirectory, name);
                break;
            default:
                break;
        }

        return newDirectory;
    }

    public static void createFileFromTemplate(@Nullable String fileName, @NotNull FileTemplate template, @NotNull PsiDirectory dir, @Nullable String customName) {
        if (fileName != null) {
            CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(fileName, dir);
            fileName = mkdirs.newName;
            dir = mkdirs.directory;
        }

        try {
            Properties props = getTemplateManagerInstance(dir).getDefaultProperties();
            if (customName != null) {
                // if $NAME should be custom, and different from fileName
                props.setProperty(FileTemplate.ATTRIBUTE_NAME, customName);
            }
            FileTemplateUtil.createFromTemplate(template, fileName, props, dir);
        } catch (ParseException e) {
            throw new IncorrectOperationException("Error parsing Velocity template: " + e.getMessage(), (Throwable) e);
        } catch (IncorrectOperationException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private static FileTemplateManager getTemplateManagerInstance(PsiDirectory dir) {
        return FileTemplateManager.getInstance(dir.getProject());
    }
}

