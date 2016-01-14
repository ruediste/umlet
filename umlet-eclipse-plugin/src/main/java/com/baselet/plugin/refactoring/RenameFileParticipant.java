package com.baselet.plugin.refactoring;

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
	private IFile origFile;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IFile)) {
			return false;
		}

		origFile = (IFile) element;

		if (!origFile.exists() || !"uxf".equals(origFile.getFileExtension())) {
			return false;
		}

		refProcessor = new UpdateImgReferencesProcessor() {

			@Override
			protected IFile calculateImgDestination(IFile img, ICompilationUnit referencingCompilationUnit) {
				IFile uxfFile = UmletPluginUtils.getUxfDiagramForImgFile(img);
				if (origFile.equals(uxfFile)) {
					return origFile.getParent().getFile(new Path(getArguments().getNewName()).removeFileExtension().addFileExtension(img.getFileExtension()));
				}
				return null;
			}
		};
		pngProcessor = new RenamePngProcessor() {

			@Override
			protected String getTargetname(IFile pngFile, IFile affectedDiagram) {
				return new Path(getArguments().getNewName()).removeFileExtension().addFileExtension(pngFile.getFileExtension()).lastSegment();
			}
		};

		return refProcessor.initialize(UmletPluginUtils.getJavaProject(origFile.getProject())) && pngProcessor.initialize(origFile);
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
		change.addAll(pngProcessor.createChange());
		return change;
	}

}
