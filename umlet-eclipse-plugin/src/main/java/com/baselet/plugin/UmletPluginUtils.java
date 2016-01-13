package com.baselet.plugin;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class UmletPluginUtils {

	public static IPath getPackageFragmentRootRelativePath(IJavaProject project, IResource resource) throws JavaModelException {
		return getPackageFragmentRootRelativePath(project, resource.getProjectRelativePath());
	}

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

		return getPackageFragmentRootRelativePath(compilationUnit.getJavaProject(), parent);
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

	/**
	 * Return the umlet diagram referenced by the given src attribute value in the given compilation unit.
	 *
	 * @return the corresponding umlet diagram corresponding or null, if no diagram could be found
	 */
	public static IFile findUmletDiagram(ICompilationUnit unit, String src) throws JavaModelException {
		IPath imgRef = resolveImgRef(unit, src);
		return findUmletDiagram(unit.getJavaProject(), imgRef);
	}

	/**
	 * Return the umlet diagram referenced by the path in the given project.
	 *
	 * @param imgRef package root relative path of the img file
	 * @return the corresponding umlet diagram corresponding or null, if no diagram could be found
	 */
	public static IFile findUmletDiagram(IJavaProject project, IPath imgRef) throws JavaModelException {
		IPath uxfRef = imgRef.removeFileExtension().addFileExtension("uxf");
		for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
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

	/**
	 * @param imagePath path of the image, relative to the package fragment root
	 */
	public static String calculateImageRef(ICompilationUnit cu, IPath imagePath) throws JavaModelException {
		return calculateImageRef(getJavaResourceParentPath(cu), imagePath);
	}

	/**
	 * Calculate the image reference for the given java resource path and the image path.
	 * Both paths need to be relative to the package fragment root.
	 */
	public static String calculateImageRef(final IPath javaResourceParentPath, IPath imagePath) {
		IPath relativePath = imagePath.makeRelativeTo(javaResourceParentPath);
		int parentCount = 0;
		while (parentCount < relativePath.segmentCount() && "..".equals(relativePath.segment(parentCount))) {
			parentCount++;
		}
		String path;
		if (parentCount > 1) {
			path = "{@docRoot}/" + imagePath;
		}
		else {
			path = relativePath.toString();
		}
		return path;
	}

	public static List<ISourceRange> collectJavadocRanges(ICompilationUnit unit) throws JavaModelException {
		List<ISourceRange> result = new ArrayList<ISourceRange>();
		collectJavadocRanges(unit, result);
		return result;
	}

	private static void collectJavadocRanges(IJavaElement element, List<ISourceRange> javadocRanges) throws JavaModelException {
		if (element instanceof IParent) {
			for (IJavaElement child : ((IParent) element).getChildren()) {
				collectJavadocRanges(child, javadocRanges);
			}
		}
		if (element instanceof IMember) {
			ISourceRange range = ((IMember) element).getJavadocRange();
			if (range != null) {
				javadocRanges.add(range);
			}
		}
	}

	public static List<ICompilationUnit> collectCompilationUnits(IJavaProject project) throws JavaModelException {
		List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();
		for (IClasspathEntry entry : project.getResolvedClasspath(true)) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				continue;
			}
			for (IPackageFragmentRoot root : project.findPackageFragmentRoots(entry)) {
				collectCompilationUnits(root, compilationUnits);
			}
		}
		return compilationUnits;
	}

	private static void collectCompilationUnits(IJavaElement element, List<ICompilationUnit> compilationUnits) throws JavaModelException {
		if (element instanceof ICompilationUnit) {
			compilationUnits.add((ICompilationUnit) element);
			// don't process children of compilation units
			return;
		}

		if (element instanceof IParent) {
			for (IJavaElement child : ((IParent) element).getChildren()) {
				collectCompilationUnits(child, compilationUnits);
			}
		}
	}

	public static IJavaProject getJavaProject(IProject project) {
		try {
			if (!project.hasNature(JavaCore.NATURE_ID)) {
				return null;
			}
		} catch (CoreException e) {
			return null;
		}
		return JavaCore.create(project);
	}

	public static IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		if (element == null) {
			return null;
		}
		if (element instanceof IPackageFragmentRoot) {
			return (IPackageFragmentRoot) element;
		}
		return getPackageFragmentRoot(element.getParent());
	}
}
