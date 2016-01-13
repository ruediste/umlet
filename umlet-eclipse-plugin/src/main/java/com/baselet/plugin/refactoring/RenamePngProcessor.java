package com.baselet.plugin.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;

public abstract class RenamePngProcessor {

	public boolean initialize() {
		return true;
	}

	public Change[] createChange(List<IFile> affectedDiagrams) {
		List<Change> result = new ArrayList<Change>();
		for (IFile affectedDiagram : affectedDiagrams) {
			// rename img files with the diagram
			IContainer parent = affectedDiagram.getParent();
			if (parent != null) {
				IFile pngFile = affectedDiagram.getProject().getFile(affectedDiagram.getProjectRelativePath().removeFileExtension().addFileExtension("png"));
				if (pngFile.exists()) {
					result.add(new RenameResourceChange(pngFile.getFullPath(), getTargetname(pngFile, affectedDiagram)));
				}
			}
		}

		return result.toArray(new Change[] {});
	}

	protected abstract String getTargetname(IFile pngFile, IFile affectedDiagram);
}
