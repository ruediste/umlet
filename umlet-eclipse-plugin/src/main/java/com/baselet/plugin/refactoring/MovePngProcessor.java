package com.baselet.plugin.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.resource.MoveResourceChange;

public abstract class MovePngProcessor {

	public boolean initialize() {
		return true;
	}

	public Change[] createChange(List<IFile> affectedDiagrams) {
		List<Change> result = new ArrayList<Change>();
		for (IFile affectedDiagram : affectedDiagrams) {
			// move img files with the diagram
			IContainer parent = affectedDiagram.getParent();
			if (parent != null) {
				IFile pngFile = affectedDiagram.getProject().getFile(affectedDiagram.getProjectRelativePath().removeFileExtension().addFileExtension("png"));
				if (pngFile.exists()) {
					result.add(new MoveResourceChange(pngFile, getDestinationFolder(pngFile, affectedDiagram)));
				}
			}
		}

		return result.toArray(new Change[] {});
	}

	protected abstract IContainer getDestinationFolder(IFile pngFile, IFile affectedDiagram);
}
