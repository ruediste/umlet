package com.baselet.plugin.contentAssist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
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
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.baselet.plugin.UmletPluginUtils;
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
	private static class ReplacementProposal implements ICompletionProposal, ICompletionProposalExtension4 {

		private final int replacementOffset;
		private final int replacementLength;
		private final String displayString;
		private final String replacementString;
		private boolean autoInsertable = true;

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

		@Override
		public boolean isAutoInsertable() {
			return autoInsertable;
		}

		public ReplacementProposal setAutoInsertable(boolean autoInsertable) {
			this.autoInsertable = autoInsertable;
			return this;
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
					computeCompleteionProposals(javaContext, document, proposals);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return proposals;
	}

	private void computeCompleteionProposals(JavaContentAssistInvocationContext javaContext, IDocument document, ArrayList<ICompletionProposal> proposals) throws CoreException {
		String content = document.get();
		int offset = javaContext.getInvocationOffset();

		// try to get the javadoc of the element at the offset
		IJavaElement elementAt = javaContext.getCompilationUnit().getElementAt(offset);
		if (elementAt instanceof IMember) {
			ISourceRange range = ((IMember) elementAt).getJavadocRange();
			if (range != null) {
				// parse javadoc
				JavaDocCommentNode comment = new JavaDocParser(content, range.getOffset(), range.getOffset() + range.getLength()).comment();

				boolean addPrefixProposals = true;
				// search the html tags
				for (JavaDocNodeBase child : comment.children) {
					if (child instanceof HtmlTagStartNode) {
						HtmlTagStartNode tag = (HtmlTagStartNode) child;
						if (tag.start <= offset && offset < tag.end) {
							// no prefix proposals within start tags
							addPrefixProposals = false;
							if ("img".equals(tag.tagName.getValue())) {
								HtmlTagAttr srcAttr = tag.getAttr("src");
								if (srcAttr != null) {
									addTransformProposals(javaContext, proposals, srcAttr);
									if (srcAttr.value.start <= offset && offset <= srcAttr.value.end) {
										collectSrcAttrLinkToResources(javaContext, proposals, srcAttr);
									}
								}
							}
							break;
						}
					}
				}

				if (addPrefixProposals) {
					int lastSpaceIndex = content.lastIndexOf(' ', offset - 1) + 1;
					String prefix = content.substring(lastSpaceIndex, offset);
					collectImageLinkProposals(javaContext, proposals, prefix, lastSpaceIndex, offset - lastSpaceIndex);
				}
			}
		}
	}

	private void collectSrcAttrLinkToResources(JavaContentAssistInvocationContext javaContext, ArrayList<ICompletionProposal> proposals, HtmlTagAttr srcAttr) throws CoreException {
		String src = srcAttr.value.getValue();
		// add proposals for resource links
		{
			int prefixStart = src.lastIndexOf('/') + 1;
			int prefixEnd = javaContext.getInvocationOffset() - srcAttr.value.start;
			if (prefixEnd >= 0 && prefixStart < prefixEnd && prefixEnd <= src.length()) {
				String prefix = src.substring(prefixStart, prefixEnd);
				for (String path : collectResourcePaths(javaContext, prefix)) {
					proposals.add(new ReplacementProposal("Change link to " + path, path, srcAttr.value.start, srcAttr.value.length()));
				}
			}
		}
	}

	private void addTransformProposals(JavaContentAssistInvocationContext javaContext, ArrayList<ICompletionProposal> proposals, HtmlTagAttr srcAttr) throws JavaModelException {
		final IPath javaResourceParentPath = UmletPluginUtils.getJavaResourceParentPath(javaContext.getCompilationUnit());
		if (javaResourceParentPath == null) {
			return;
		}

		String src = srcAttr.value.getValue();
		if (UmletPluginUtils.isAbsoluteImageRef(src)) {
			// propose to transform to relative
			Path path = new Path(src.substring("{@docRoot}".length()));
			String replacement = path.makeRelativeTo(javaResourceParentPath).toString();
			proposals.add(new ReplacementProposal("Transform src to " + replacement, replacement, srcAttr.value.start, srcAttr.value.length()).setAutoInsertable(false));
		}
		else {
			// propose to transform to absolute
			String replacement = "{@docRoot}/" + javaResourceParentPath.append(src).toString();
			proposals.add(new ReplacementProposal("Transform src to " + replacement, replacement, srcAttr.value.start, srcAttr.value.length()).setAutoInsertable(false));
		}
	}

	private List<String> collectResourcePaths(final JavaContentAssistInvocationContext context, final String prefix) throws CoreException {
		final ArrayList<String> result = new ArrayList<String>();
		final IPath javaResourceParentPath = UmletPluginUtils.getJavaResourceParentPath(context.getCompilationUnit());
		if (javaResourceParentPath == null) {
			return result;
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
							String path;
							if (parentCount > 1) {
								path = "{@docRoot}/" + imagePath;
							}
							else {
								path = relativePath.toString();
							}
							result.add(path);
						}
					}
					return true;
				}
			});
		}
		return result;
	}

	private void collectImageLinkProposals(final JavaContentAssistInvocationContext context, final ArrayList<ICompletionProposal> proposals, final String prefix, final int inputOffset, final int inputLength) throws CoreException {
		for (String path : collectResourcePaths(context, prefix)) {
			proposals.add(new ReplacementProposal("Link to " + path, "<img src=\"" + path + "\" alt=\"\">", inputOffset, inputLength));
		}
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
