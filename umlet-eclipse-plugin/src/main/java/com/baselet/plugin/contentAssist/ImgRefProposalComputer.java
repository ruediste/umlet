package com.baselet.plugin.contentAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.baselet.plugin.refactoring.JavaDocParser;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagAttr;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagStartNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocCommentNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocNodeBase;

public class ImgRefProposalComputer implements IJavaCompletionProposalComputer {

	private String errorMessage;

	/**
	 * Proposal to insert a link to an image
	 *
	 */
	private static class ReplacementProposal implements ICompletionProposal {

		private final int replacementOffset;
		private final int replacementLength;
		private final String displayString;
		private final String replacementString;

		public ReplacementProposal(String displayString, String replacementString, int replacementOffset, int replacementLength) {
			this.displayString = displayString;
			this.replacementString = replacementString;
			this.replacementOffset = replacementOffset;
			this.replacementLength = replacementLength;
		}

		@Override
		public void apply(IDocument document) {
			try {
				document.replace(replacementOffset, replacementLength, replacementString);
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return null;
		}

		@Override
		public String getDisplayString() {
			return displayString;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context, IProgressMonitor monitor) {

		ArrayList<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();

		if (context instanceof JavaContentAssistInvocationContext) {
			JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext) context;
			try {
				IDocument document = context.getDocument();
				if (document != null) {
					int offset = context.getInvocationOffset();
					String content = document.get();

					collectTransformProposals(javaContext, proposals, content);

					int lastSpaceIndex = content.lastIndexOf(' ', offset) + 1;
					String prefix = content.substring(lastSpaceIndex, offset);
					collectImageLinkProposals(javaContext, proposals, prefix, lastSpaceIndex, offset - lastSpaceIndex);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return proposals;
	}

	private boolean collectTransformProposals(JavaContentAssistInvocationContext javaContext, ArrayList<ICompletionProposal> proposals, String content) throws JavaModelException {
		int offset = javaContext.getInvocationOffset();

		// try to get the javadoc of the element at the offset
		IJavaElement elementAt = javaContext.getCompilationUnit().getElementAt(offset);
		if (elementAt instanceof IMember) {
			ISourceRange range = ((IMember) elementAt).getJavadocRange();
			if (range != null) {
				JavaDocCommentNode comment = new JavaDocParser(content, range.getOffset(), range.getOffset() + range.getLength()).comment();

				// search the html tags
				for (JavaDocNodeBase child : comment.children) {
					if (child instanceof HtmlTagStartNode) {
						HtmlTagStartNode tag = (HtmlTagStartNode) child;
						if (tag.start <= offset && offset < tag.end) {
							if ("img".equals(tag.tagName.getValue())) {
								HtmlTagAttr srcAttr = tag.getAttr("src");
								if (srcAttr != null) {
									// we've found an img tag with a src attribute
									final IPath javaResourceParentPath = getJavaResourceParentPath(javaContext);
									if (javaResourceParentPath == null) {
										break;
									}

									String src = srcAttr.value.getValue();
									if (src.startsWith("{@docRoot}")) {
										// propose to transform to relative
										Path path = new Path(src.substring("{@docRoot}".length()));
										String replacement = path.makeRelativeTo(javaResourceParentPath).toString();
										proposals.add(new ReplacementProposal("Transform src to " + replacement, replacement, srcAttr.value.start, srcAttr.value.length()));
									}
									else {
										// propose to transform to absolute
										String replacement = "{@docRoot}/" + javaResourceParentPath.append(src).toString();
										proposals.add(new ReplacementProposal("Transform src to " + replacement, replacement, srcAttr.value.start, srcAttr.value.length()));
									}
									break;
								}
							}
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private IPath getJavaResourceParentPath(JavaContentAssistInvocationContext context) throws JavaModelException {
		IResource javaResource = context.getCompilationUnit().getResource();
		if (javaResource == null) {
			return null;
		}
		final IContainer parent = javaResource.getParent();
		if (parent == null) {
			return null;
		}

		return getPackageFragmentRootRelativePath(context.getProject(), parent.getProjectRelativePath());
	}

	private void collectImageLinkProposals(final JavaContentAssistInvocationContext context, final ArrayList<ICompletionProposal> proposals, final String prefix, final int inputOffset, final int inputLength) throws CoreException {
		final IPath javaResourceParentPath = getJavaResourceParentPath(context);
		if (javaResourceParentPath == null) {
			return;
		}

		// search all source folders of the current project for images
		for (IPackageFragmentRoot root : context.getProject().getPackageFragmentRoots()) {
			final IResource rootResource = root.getResource();
			if (rootResource == null) {
				continue;
			}

			rootResource.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource imageResource) throws CoreException {
					if (!imageResource.isAccessible()) {
						return false;
					}
					if (imageResource instanceof IFile) {
						if (imageResource.getName().endsWith(".png") && imageResource.getName().contains(prefix)) {
							IPath imagePath = imageResource.getProjectRelativePath().makeRelativeTo(rootResource.getProjectRelativePath());
							IPath relativePath = imagePath.makeRelativeTo(javaResourceParentPath);
							int parentCount = 0;
							while (parentCount < relativePath.segmentCount() && "..".equals(relativePath.segment(parentCount))) {
								parentCount++;
							}
							if (parentCount > 1) {
								String path = "{@docRoot}/" + imagePath;
								proposals.add(new ReplacementProposal("Link to " + path, "<img src=\"" + path + "\" alt=\"\">", inputOffset, inputLength));
							}
							else {
								String path = relativePath.toString();
								proposals.add(new ReplacementProposal("Link to " + path, "<img src=\"" + path + "\" alt=\"\">", inputOffset, inputLength));
							}
						}
					}
					return true;
				}
			});
		}
	}

	private IPath getPackageFragmentRootRelativePath(IJavaProject project, IPath path) throws JavaModelException {
		for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
			IResource rootResource = root.getResource();
			if (rootResource != null) {
				if (rootResource.getProjectRelativePath().isPrefixOf(path)) {
					return path.makeRelativeTo(rootResource.getProjectRelativePath());
				}
			}
		}
		return path;
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context, IProgressMonitor monitor) {
		return Collections.emptyList();
	}

	@Override
	public String getErrorMessage() {
		return errorMessage;
	}

	@Override
	public void sessionStarted() {
		errorMessage = null;
	}

	@Override
	public void sessionEnded() {
		errorMessage = null;
	}

}
