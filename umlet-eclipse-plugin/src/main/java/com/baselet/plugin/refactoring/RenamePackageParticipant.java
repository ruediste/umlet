package com.baselet.plugin.refactoring;

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
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import com.baselet.plugin.UmletPluginUtils;

public class RenamePackageParticipant extends RenameParticipant {

	IPackageFragment packageFragment;
	UpdateImgReferencesProcessor refProcessor;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IPackageFragment)) {
			return false;
		}
		packageFragment = (IPackageFragment) element;
		IJavaProject javaProject = packageFragment.getJavaProject();
		final IPackageFragmentRoot packageFragmentRoot = UmletPluginUtils.getPackageFragmentRoot(packageFragment);
		if (packageFragmentRoot == null) {
			return false;
		}
		final IFolder packageFragmentFolder;
		final IFolder newPackageFragmentFolder;
		try {
			IResource resource;

			resource = packageFragment.getCorrespondingResource();
			if (!(resource instanceof IFolder)) {
				return false;
			}
			packageFragmentFolder = (IFolder) resource;

			IResource pfrResource = packageFragmentRoot.getCorrespondingResource();
			if (!(pfrResource instanceof IFolder)) {
				return false;
			}
			newPackageFragmentFolder = ((IFolder) pfrResource).getFolder(getArguments().getNewName().replace('.', '/'));
		} catch (JavaModelException e) {
			return false;
		}
		final IPath renamedFolderPath = packageFragmentFolder.getFullPath();

		refProcessor = new UpdateImgReferencesProcessor() {

			@Override
			protected void calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit, Destination dest) throws CoreException {
				IResource cuResource = referencingCompilationUnit.getCorrespondingResource();
				if (cuResource == null) {
					return;
				}
				boolean uxfInFolder = renamedFolderPath.isPrefixOf(uxf.getFullPath());
				boolean cuInFolder = renamedFolderPath.isPrefixOf(cuResource.getFullPath());
				if (uxfInFolder && !cuInFolder) {
					IPath relativePath = uxf.getFullPath().makeRelativeTo(renamedFolderPath);
					dest.uxfFileDestination = newPackageFragmentFolder.getFile(relativePath);
				}
				else if (!uxfInFolder && cuInFolder) {
					IPath relativePath = cuResource.getFullPath().makeRelativeTo(renamedFolderPath);
					dest.cuDestination = newPackageFragmentFolder.getFile(relativePath);
				}
			}

			@Override
			protected IFile calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit) throws JavaModelException {
				throw new UnsupportedOperationException();
			}
		};

		return refProcessor.initialize(javaProject);
	}

	@Override
	public String getName() {
		return "Umlet Rename Package Participant";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		return RefactoringStatus.create(Status.OK_STATUS);
	}

	@Override
	public Change createPreChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return refProcessor.createChange(pm);
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return null;
	}

}
