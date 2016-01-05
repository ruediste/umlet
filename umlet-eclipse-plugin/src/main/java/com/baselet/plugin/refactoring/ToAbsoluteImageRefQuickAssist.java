package com.baselet.plugin.refactoring;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagAttr;
import com.baselet.plugin.refactoring.JavaDocParser.HtmlTagStartNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocCommentNode;
import com.baselet.plugin.refactoring.JavaDocParser.JavaDocNodeBase;

public class ToAbsoluteImageRefQuickAssist implements IQuickAssistProcessor {

	private static final class Proposal implements IJavaCompletionProposal {
		private final HtmlTagStartNode nodeToRefactor;
		private final String source;
		private final HtmlTagAttr srcAttr;

		public Proposal(HtmlTagStartNode nodeToRefactor, String source) {
			this.nodeToRefactor = nodeToRefactor;
			this.source = source;
			srcAttr = nodeToRefactor.getAttr("src");
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public String getDisplayString() {
			return "To absolute ref " + nodeToRefactor.getAttr("src").value.getValue();
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return source.substring(nodeToRefactor.start, srcAttr.value.start) + "foo" + source.substring(srcAttr.value.end, nodeToRefactor.end);
		}

		@Override
		public void apply(IDocument document) {
			try {
				document.replace(srcAttr.value.start, srcAttr.value.length(), "foo");
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public int getRelevance() {
			return 0;
		}
	}

	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException {
		return getNodeToRefactor(context) != null;
	}

	private HtmlTagStartNode getNodeToRefactor(IInvocationContext context) throws JavaModelException {
		HtmlTagStartNode nodeToRefactor = null;

		ASTNode javadocNode = context.getCoveringNode();
		while (javadocNode != null && javadocNode.getNodeType() != ASTNode.JAVADOC) {
			javadocNode = javadocNode.getParent();
		}

		if (javadocNode != null) {

			String source = context.getCompilationUnit().getSource();
			JavaDocCommentNode comment = new JavaDocParser(source, javadocNode.getStartPosition(), javadocNode.getStartPosition() + javadocNode.getLength()).comment();
			int idx = context.getSelectionOffset() - javadocNode.getStartPosition();
			for (JavaDocNodeBase child : comment.children) {
				if (child instanceof HtmlTagStartNode) {
					HtmlTagStartNode node = (HtmlTagStartNode) child;
					HtmlTagAttr srcAttr = node.getAttr("src");
					if ("img".equals(node.tagName.getValue()) && srcAttr != null && !srcAttr.value.getValue().startsWith("{@docroot}")) {
						if (node.start <= idx && idx < node.end) {
							nodeToRefactor = node;
							break;
						}
					}
				}
			}
		}
		return nodeToRefactor;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations) throws CoreException {
		HtmlTagStartNode nodeToRefactor = getNodeToRefactor(context);
		ArrayList<IJavaCompletionProposal> result = new ArrayList<IJavaCompletionProposal>();
		if (nodeToRefactor != null) {
			String source = context.getCompilationUnit().getSource();
			result.add(new Proposal(nodeToRefactor, source));
		}
		return result.toArray(new IJavaCompletionProposal[] {});
	}

}
