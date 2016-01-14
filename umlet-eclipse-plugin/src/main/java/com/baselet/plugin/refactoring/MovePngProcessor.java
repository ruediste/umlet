package com.baselet.plugin.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.resource.MoveResourceChange;

public abstract class MovePngProcessor {

	private IFile uxfDiagram;

	public boolean initialize(IFile uxfDiagram) {
		this.uxfDiagram = uxfDiagram;
		return true;
	}

	public Change[] createChange() {
		List<Change> result = new ArrayList<Change>();
		// move img files with the diagram
		IContainer parent = uxfDiagram.getParent();
		if (parent != null) {
			IFile pngFile = uxfDiagram.getProject().getFile(uxfDiagram.getProjectRelativePath().removeFileExtension().addFileExtension("png"));
			if (pngFile.exists()) {
				result.add(new MoveResourceChange(pngFile, getDestinationFolder(pngFile, uxfDiagram)));
			}
		}

		return result.toArray(new Change[] {});
	}

	protected abstract IContainer getDestinationFolder(IFile pngFile, IFile affectedDiagram);
}
