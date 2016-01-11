package com.baselet.plugin.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.baselet.plugin.UmletPluginUtils;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagAttr;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagStartNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocCommentNode;

public class MoveResourceParticipant extends MoveParticipant {

	IResource resouce;

	@Override
	protected boolean initialize(Object element) {
		if (!(element instanceof IResource)) {
			return false;
		}
		resouce = (IResource) element;
		return true;
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
		IFolder destinationFolder;
		{
			Object destination = getArguments().getDestination();
			if (!(destination instanceof IFolder)) {
				return null;
			}
			destinationFolder = (IFolder) destination;
		}

		// calculate target location
		IFile destinationFile = destinationFolder.getFile(resouce.getName());

		// obtain java project
		IJavaProject project;
		{
			if (!resouce.getProject().hasNature(JavaCore.NATURE_ID)) {
				return null;
			}
			project = JavaCore.create(resouce.getProject());
		}

		CompositeChange result = new CompositeChange("Update <img> references");
		// iterate all Compilation units
		for (IClasspathEntry entry : project.getResolvedClasspath(true)) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				continue;
			}
			for (IPackageFragmentRoot root : project.findPackageFragmentRoots(entry)) {
				List<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();
				collectCompilationUnits(root, compilationUnits);
				for (ICompilationUnit cu : compilationUnits) {
					String source = cu.getBuffer().getContents();
					CompilationUnitChange change = null;

					for (ISourceRange range : UmletPluginUtils.collectJavadocRanges(cu)) {
						JavaDocCommentNode comment = new JavaDocParser(source, range.getOffset(), range.getOffset() + range.getLength()).comment();
						for (HtmlTagStartNode tag : comment.ofType(HtmlTagStartNode.class)) {
							if ("img".equals(tag.tagName.getValue())) {
								HtmlTagAttr srcAttr = tag.getAttr("src");
								if (srcAttr == null) {
									continue;
								}

								IFile uxf = UmletPluginUtils.findUmletDiagram(cu, srcAttr.value.getValue());
								if (resouce.equals(uxf)) {
									// the src attribute references the diagram beeing moved
									if (change == null) {
										change = new CompilationUnitChange(cu.getElementName(), cu);
										change.setKeepPreviewEdits(true);
										change.setEdit(new MultiTextEdit());
										result.add(change);
									}

									change.addEdit(new ReplaceEdit(srcAttr.value.start, srcAttr.value.length(), UmletPluginUtils.calculateImageRef(cu, UmletPluginUtils.getPackageFragmentRootRelativePath(project, destinationFile))));
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	private void collectCompilationUnits(IJavaElement element, List<ICompilationUnit> compilationUnits) throws JavaModelException {
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

}
