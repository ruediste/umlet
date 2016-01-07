package com.baselet.plugin;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

public class UmletPluginUtils {

	public static IPath getPackageFragmentRootRelativePath(IJavaProject project, IPath projectRelativePath) throws JavaModelException {
		for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
			IResource rootResource = root.getResource();
			if (rootResource != null) {
				if (rootResource.getProjectRelativePath().isPrefixOf(projectRelativePath)) {
					return projectRelativePath.makeRelativeTo(rootResource.getProjectRelativePath());
				}
			}
		}
		return projectRelativePath;
	}

	/**
	 * Return the path of the parent of the given compilation unit, relative to the PackageFragmentRoot
	 */
	public static IPath getJavaResourceParentPath(ICompilationUnit compilationUnit) throws JavaModelException {
		IResource javaResource = compilationUnit.getResource();
		if (javaResource == null) {
			return null;
		}
		final IContainer parent = javaResource.getParent();
		if (parent == null) {
			return null;
		}

		return getPackageFragmentRootRelativePath(compilationUnit.getJavaProject(), parent.getProjectRelativePath());
	}

	/**
	 * Tests if a reference in a javadoc comment is absolute
	 */
	public static boolean isAbsoluteImageRef(String src) {
		return src.startsWith("{@docRoot}");
	}

	/**
	 * Returns the package fragment root relative path for the given src
	 */
	public static IPath resolveImgRef(ICompilationUnit unit, String src) throws JavaModelException {
		if (isAbsoluteImageRef(src)) {
			return new Path(src.substring("{@docRoot}".length()));
		}
		else {
			return getJavaResourceParentPath(unit).append(src);
		}
	}

	public static IFile findUmletDiagram(ICompilationUnit unit, String src) throws JavaModelException {
		IPath uxfRef = resolveImgRef(unit, src).removeFileExtension().addFileExtension("uxf");
		for (IPackageFragmentRoot root : unit.getJavaProject().getAllPackageFragmentRoots()) {
			IResource rootResource = root.getResource();
			if (rootResource instanceof IContainer) {
				IFile uxfFile = ((IContainer) rootResource).getFile(uxfRef);
				if (uxfFile.exists()) {
					return uxfFile;
				}
			}
		}
		return null;
	}
}
