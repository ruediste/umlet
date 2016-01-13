package com.baselet.plugin.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.baselet.plugin.UmletPluginUtils;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagAttr;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagStartNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocCommentNode;

/**
 * Processor used by multiple refactoring participants to update image references
 * in JavaDoc comments.
 */
public abstract class UpdateImgReferencesProcessor {

	private IJavaProject project;

	protected static class Destination {
		IFile cuDestination;
		IFile uxfFileDestination;
	}

	public boolean initialize(IJavaProject project) {
		if (project == null) {
			return false;
		}
		this.project = project;
		return true;
	}

	public Change createChange(IProgressMonitor pm) throws CoreException {
		// calculate target location

		CompositeChange imgRefChange = new CompositeChange("Update <img> references");

		// iterate all Compilation units
		for (ICompilationUnit cu : UmletPluginUtils.collectCompilationUnits(project)) {
			if (cu.getCorrespondingResource() == null) {
				continue;
			}
			if (cu.getBuffer() == null) {
				continue;
			}
			String source = cu.getBuffer().getContents();
			CompilationUnitChange change = null;

			for (ISourceRange range : UmletPluginUtils.collectJavadocRanges(cu)) {
				JavaDocCommentNode comment = new JavaDocParser(source, range.getOffset(), range.getOffset() + range.getLength()).comment();
				for (HtmlTagStartNode tag : comment.ofType(HtmlTagStartNode.class)) {
					// skip non-image tags
					if (!"img".equals(tag.tagName.getValue())) {
						continue;
					}
					HtmlTagAttr srcAttr = tag.getAttr("src");
					if (srcAttr == null) {
						continue;
					}
					IPath originalImgRef = UmletPluginUtils.resolveImgRef(cu, srcAttr.value.getValue());
					IFile uxf = UmletPluginUtils.findUmletDiagram(cu.getJavaProject(), originalImgRef);
					Destination dest = new Destination();

					dest.cuDestination = (IFile) cu.getCorrespondingResource();
					dest.uxfFileDestination = uxf;
					calculateDestination(uxf, cu, dest);
					IPath oldUxfRelativePath = uxf.getFullPath().makeRelativeTo(cu.getCorrespondingResource().getFullPath());
					IPath newUxfRelativePath = dest.uxfFileDestination.getFullPath().makeRelativeTo(dest.cuDestination.getFullPath());
					// IFile destinationUxf = calculateDestination(uxf, cu);
					if (!oldUxfRelativePath.equals(newUxfRelativePath)) {
						// the src attribute references the diagram beeing moved, update the reference
						if (change == null) {
							change = new CompilationUnitChange(cu.getElementName(), cu);
							change.setKeepPreviewEdits(true);
							change.setEdit(new MultiTextEdit());
							imgRefChange.add(change);
						}
						IPath destinationImgRef = UmletPluginUtils.getPackageFragmentRootRelativePath(project, dest.uxfFileDestination).removeFileExtension().addFileExtension(originalImgRef.getFileExtension());
						IPath javaResourceParentPath = UmletPluginUtils.getPackageFragmentRootRelativePath(cu.getJavaProject(), dest.cuDestination.getParent());
						String imgRef = UmletPluginUtils.calculateImageRef(javaResourceParentPath, destinationImgRef);
						change.addEdit(new ReplaceEdit(srcAttr.value.start, srcAttr.value.length(), imgRef));
					}
				}
			}
		}
		if (imgRefChange.getChildren().length == 0) {
			return null;
		}
		return imgRefChange;
	}

	/**
	 * Calculate the destination of the given umlet diagram. Return null if the diagram reference does not need to be updated
	 */
	protected void calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit, Destination dest) throws CoreException {
		IFile uxfDest = calculateDestination(uxf, referencingCompilationUnit);
		if (uxfDest != null) {
			dest.uxfFileDestination = uxfDest;
		}
	}

	/**
	 * Calculate the destination of the given umlet diagram. Return null if the diagram reference does not need to be updated
	 */
	protected abstract IFile calculateDestination(IFile uxf, ICompilationUnit referencingCompilationUnit) throws CoreException;

}
