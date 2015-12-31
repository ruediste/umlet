package com.baselet.plugin.builder;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class ProjectPropertyPage extends FieldEditorPreferencePage implements IWorkbenchPropertyPage {

	private IAdaptable element;

	@Override
	public IAdaptable getElement() {
		return element;
	}

	@Override
	public void setElement(IAdaptable element) {
		this.element = element;

	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor("test", "test", getFieldEditorParent()));
	}

	@Override
	protected IPreferenceStore doGetPreferenceStore() {
		return new ScopedPreferenceStore(new ProjectScope(getProject()), "com.umlet.projectPreferences");
	}

	private IProject getProject() {
		return getElement().getAdapter(IProject.class);
	}

}
