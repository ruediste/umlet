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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
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

public class ImgRefProposalComputer implements IJavaCompletionProposalComputer {

	private String errorMessage;

	/**
	 * Proposal to insert a link to an image
	 *
	 */
	private static class InsertImageLinkCompletionProposal implements ICompletionProposal {

		private final int inputOffset;
		private final int inputLength;
		private final String path;

		public InsertImageLinkCompletionProposal(String path, int inputOffset, int inputLength) {
			this.path = path;
			this.inputOffset = inputOffset;
			this.inputLength = inputLength;
		}

		@Override
		public void apply(IDocument document) {
			try {
				document.replace(inputOffset, inputLength, "<img src=\"" + path + "\" alt=\"\">");
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
			return "Link to " + path;
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

		// proposals.add(new CompletionProposal("codeandme.blogspot.com", context.getInvocationOffset(), 0, "codeandme.blogspot.com".length()));
		// proposals.add(new CompletionProposal("<your proposal here>", context.getInvocationOffset(), 0, "<your proposal here>".length()));

		if (context instanceof JavaContentAssistInvocationContext) {
			JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext) context;
			try {
				IDocument document = context.getDocument();
				if (document != null) {
					int offset = context.getInvocationOffset();
					if (offset >= 0) {
						String content = document.get();
						int lastSpaceIndex = content.lastIndexOf(' ', offset) + 1;
						String prefix = content.substring(lastSpaceIndex, offset);
						collectImageLinkProposals(javaContext, proposals, prefix, lastSpaceIndex, offset - lastSpaceIndex);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return proposals;
	}

	private void collectImageLinkProposals(final JavaContentAssistInvocationContext context, final ArrayList<ICompletionProposal> proposals, final String prefix, final int inputOffset, final int inputLength) throws CoreException {

		IResource javaResource = context.getCompilationUnit().getResource();
		if (javaResource == null) {
			return;
		}
		final IContainer parent = javaResource.getParent();
		if (parent == null) {
			return;
		}

		final IPath javaResourceParentPath = getPackageFragmentRootRelativePath(context.getProject(), parent.getProjectRelativePath());

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
								proposals.add(new InsertImageLinkCompletionProposal("{@docRoot}/" + imagePath, inputOffset, inputLength));
							}
							else {
								proposals.add(new InsertImageLinkCompletionProposal(relativePath.toString(), inputOffset, inputLength));
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
