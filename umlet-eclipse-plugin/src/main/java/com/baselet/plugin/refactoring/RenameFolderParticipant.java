package com.baselet.plugin.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import com.baselet.plugin.UmletPluginUtils;

/**
 * Participant updating img tags in JavaDocs when diagram resources are renamed
 */
public class RenameFolderParticipant extends RenameParticipant {

	UpdateImgReferencesProcessor refProcessor;
	private IFolder renamedFolder;

	@Override
	protected boolean initialize(Object element) {
		// don't participate in package renames
		if ("org.eclipse.jdt.ui.renamePackageProcessor".equals(getProcessor().getIdentifier())) {
			return false;
		}
		if (!(element instanceof IFolder)) {
			return false;
		}
		renamedFolder = (IFolder) element;
		final IPath renamedFolderPath = renamedFolder.getFullPath();
		final IFolder newFolder = renamedFolder.getParent().getFolder(new Path(getArguments().getNewName()));

		refProcessor = new UpdateImgReferencesProcessor() {
			@Override
			protected void calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit, Destination dest) throws CoreException {
				IResource cuResource = referencingCompilationUnit.getCorrespondingResource();
				if (cuResource == null) {
					return;
				}
				if (renamedFolderPath.isPrefixOf(uxf.getFullPath())) {
					IPath relativePath = uxf.getFullPath().makeRelativeTo(renamedFolderPath);
					dest.uxfFileDestination = newFolder.getFile(relativePath);
				}
			}

			@Override
			protected IFile calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit) throws JavaModelException {
				throw new UnsupportedOperationException();
			}

		};

		return refProcessor.initialize(UmletPluginUtils.getJavaProject(renamedFolder.getProject()));
	}

	@Override
	public String getName() {
		return "Umlet rename folder participant";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return refProcessor.createChange(pm);
	}

}
