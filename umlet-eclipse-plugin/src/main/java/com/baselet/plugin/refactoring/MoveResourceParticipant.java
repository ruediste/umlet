package com.baselet.plugin.refactoring;

import java.util.Collections;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;

import com.baselet.plugin.UmletPluginUtils;

/**
 * Refactoring participant updating JavaDoc references to a diagram beeing moved
 */
public class MoveResourceParticipant extends MoveParticipant {

	private UpdateImgReferencesProcessor refprocessor;
	private MovePngProcessor pngProcessor;
	private IResource resource;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IResource)) {
			return false;
		}
		resource = (IResource) element;
		if (!resource.exists() || !"uxf".equals(resource.getFileExtension())) {
			return false;
		}
		final IFolder destinationFolder;
		{
			Object destination = getArguments().getDestination();
			if (!(destination instanceof IFolder)) {
				return false;
			}
			destinationFolder = (IFolder) destination;
		}

		refprocessor = new UpdateImgReferencesProcessor() {

			@Override
			protected IFile calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit) {
				if (!resource.equals(uxf)) {
					return null;
				}
				return destinationFolder.getFile(uxf.getName());
			}

		};

		pngProcessor = new MovePngProcessor() {

			@Override
			protected IContainer getDestinationFolder(IFile pngFile, IFile affectedDiagram) {
				return destinationFolder;
			}
		};

		return refprocessor.initialize(UmletPluginUtils.getJavaProject(resource.getProject())) && pngProcessor.initialize();
	}

	@Override
	public String getName() {
		return "Umlet Move Resource Participant";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		CompositeChange change = new CompositeChange("Umlet");
		change.add(refprocessor.createChange(pm));
		change.addAll(pngProcessor.createChange(Collections.singletonList((IFile) resource)));
		return change;
	}

}
