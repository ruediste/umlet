package com.baselet.plugin.refactoring;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;

import com.baselet.plugin.UmletPluginUtils;

/**
 * Participant updating img tags in JavaDocs when a diagram file is moved.
 */
public class MoveResourceParticipant extends MoveParticipant {

	UmletRefactoringProcessorManager mgr = new UmletRefactoringProcessorManager();

	@Override
	protected boolean initialize(Object element) {
		// don't participate in package renames
		if ("org.eclipse.jdt.ui.renamePackageProcessor".equals(getProcessor().getIdentifier())) {
			return false;
		}

		if (!(element instanceof IResource)) {
			return false;
		}

		IResource origResource = (IResource) element;
		final IFolder destinationFolder;
		{
			Object destination = getArguments().getDestination();
			if (!(destination instanceof IFolder)) {
				return false;
			}
			destinationFolder = (IFolder) destination;
		}

		IJavaProject javaProject = UmletPluginUtils.getJavaProject(origResource.getProject());
		if (javaProject == null) {
			return false;
		}

		if (element instanceof IFile) {
			final IFile origFile = (IFile) element;
			if (!origFile.exists()) {
				return false;
			}

			if ("uxf".equals(origFile.getFileExtension())) {
				// we're moving the diagram
				mgr.add(new UpdateImgReferencesProcessor(javaProject) {

					@Override
					protected IFile calculateImgDestination(IFile img, ICompilationUnit referencingCompilationUnit) {
						IFile uxfFile = UmletPluginUtils.getUxfDiagramForImgFile(img);
						if (origFile.equals(uxfFile)) {
							return destinationFolder.getFile(img.getName());
						}
						return null;
					}
				});
				mgr.add(new MovePngProcessor(origFile) {

					@Override
					protected IContainer getDestinationFolder(IFile pngFile, IFile affectedDiagram) {
						return destinationFolder;
					}
				});
				return true;
			}
			else if ("png".equals(origFile.getFileExtension())) {
				// we are moving a png
				mgr.add(new UpdateImgReferencesProcessor(javaProject) {

					@Override
					protected IFile calculateImgDestination(IFile img, ICompilationUnit referencingCompilationUnit) {
						if (origFile.equals(img)) {
							return destinationFolder.getFile(img.getName());
						}
						return null;
					}
				});
			}
			return true;
		}

		if (origResource instanceof IFolder) {
			final IFolder origFolder = (IFolder) origResource;
			final IFolder newFolder = destinationFolder.getFolder(origFolder.getName());
			final IPath origFolderPath = origFolder.getFullPath();
			mgr.add(new UpdateImgReferencesProcessor(javaProject) {

				@Override
				protected IFile calculateImgDestination(IFile img, ICompilationUnit referencingCompilationUnit) {
					IPath imgPath = img.getFullPath();
					if (origFolderPath.isPrefixOf(imgPath)) {
						IPath relativePath = imgPath.makeRelativeTo(origFolderPath);
						return newFolder.getFile(relativePath);
					}
					return null;
				}
			});
			return true;
		}
		return false;
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
		return mgr.createChange(pm);
	}

}
