package com.baselet.plugin;

import java.util.Set;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import com.baselet.plugin.builder.AddBuilder;

public class UmletPropertyTester extends PropertyTester {

	private static final String HAS_PROJECT_UMLET_BUILDER = "hasProjectUmletBuilder";

	@Override
	public boolean test(final Object receiver, final String property, final Object[] args, final Object expectedValue) {

		if (HAS_PROJECT_UMLET_BUILDER.equals(property)) {
			final IProject project = Platform.getAdapterManager().getAdapter(receiver, IProject.class);

			if (project != null) {
				return AddBuilder.hasBuilder(project);
			}
		}

		if ("isImageRefSelected".equals(property)) {
			if (!(receiver instanceof Set<?>)) {
				return false;
			}
			Set<?> receiverSet = (Set<?>) receiver;
			if (receiverSet.size() != 1) {
				return false;
			}
			ITextSelection selection = Platform.getAdapterManager().getAdapter(receiverSet.iterator().next(), TextSelection.class);
			if (selection == null) {
				return false;
			}

			selection.getOffset();
			IEditorPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			if (part instanceof ITextEditor) {
				final ITextEditor editor = (ITextEditor) part;
				IDocumentProvider prov = editor.getDocumentProvider();
				IDocument doc = prov.getDocument(editor.getEditorInput());
			}
			return true;
		}

		return false;
	}

	private void showClasses(Class<?> clazz) {
		if (clazz == null) {
			return;
		}
		System.out.println(clazz);
		showClasses(clazz.getSuperclass());
		for (Class<?> i : clazz.getInterfaces()) {
			showClasses(i);
		}
	}
}