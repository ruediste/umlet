package com.baselet.plugin.refactoring;

import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import com.baselet.plugin.UmletPluginUtils;

/**
 * Participant updating img tags in JavaDocs when diagram resources are renamed
 */
public class RenameFileParticipant extends RenameParticipant {

	UpdateImgReferencesProcessor refProcessor;
	RenamePngProcessor pngProcessor;
	private IFile renamedFile;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IFile)) {
			return false;
		}

		renamedFile = (IFile) element;

		refProcessor = new UpdateImgReferencesProcessor() {

			@Override
			protected IFile calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit) {
				if (!renamedFile.equals(uxf)) {
					return null;
				}
				return renamedFile.getParent().getFile(new Path(getArguments().getNewName()));
			}
		};
		pngProcessor = new RenamePngProcessor() {

			@Override
			protected String getTargetname(IFile pngFile, IFile affectedDiagram) {
				return new Path(getArguments().getNewName()).removeFileExtension().addFileExtension(pngFile.getFileExtension()).lastSegment();
			}
		};

		return refProcessor.initialize(UmletPluginUtils.getJavaProject(renamedFile.getProject()));
	}

	@Override
	public String getName() {
		return "Umlet rename resouce participant";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		CompositeChange change = new CompositeChange("Umlet");
		change.add(refProcessor.createChange(pm));
		change.addAll(pngProcessor.createChange(Collections.singletonList(renamedFile)));
		return change;
	}

}
