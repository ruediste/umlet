package com.baselet.plugin.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

import com.baselet.diagram.DiagramHandler;
import com.baselet.plugin.UmletPluginUtils;
import com.baselet.plugin.refactoring.ImageReference;
import com.baselet.plugin.refactoring.JavaDocParser.SourceString;

public class UmletBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "com.umlet.plugin.builder";
	public static final String PROBLEM_MARKER_TYPE = "com.umlet.plugin.builderProblem";
	public static final String IMG_MISSING_MARKER_TYPE = "com.umlet.plugin.imgMissing";

	private static class ResourceSet {
		List<IFile> uxfFiles = new ArrayList<IFile>();
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();

		public void handle(IResource res) {
			IFile file = res.getAdapter(IFile.class);
			if (file == null) {
				return;
			}

			if (file.getName().endsWith(".uxf")) {
				if (!file.isDerived(IResource.CHECK_ANCESTORS) && file.exists()) {
					uxfFiles.add(file);
				}
				return;
			}

			IJavaElement element = JavaCore.create(file);
			if (element instanceof ICompilationUnit) {
				units.add((ICompilationUnit) element);
			}
		}

		public int size() {
			return uxfFiles.size() + units.size();
		}
	}

	@Override
	protected IProject[] build(final int kind, final Map<String, String> args, final IProgressMonitor monitor)
			throws CoreException {
		// collect resources
		ResourceSet resources = new ResourceSet();
		try {
			if (kind == FULL_BUILD) {
				collectFullBuildResources(resources);
			}
			else {
				IResourceDelta delta = getDelta(getProject());
				if (delta == null) {
					collectFullBuildResources(resources);
				}
				else {
					collectIncrementalBuildResources(delta, resources);
				}
			}
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}

		// process resources
		processResources(resources, monitor);
		return null;
	}

	private void collectIncrementalBuildResources(IResourceDelta delta, final ResourceSet resources) throws CoreException {
		delta.accept(new IResourceDeltaVisitor() {
			@Override
			public boolean visit(IResourceDelta delta) {
				resources.handle(delta.getResource());
				// visit children too
				return true;
			}

		});

	}

	private void collectFullBuildResources(final ResourceSet resources) throws CoreException {
		getProject().accept(new IResourceVisitor() {

			@Override
			public boolean visit(IResource res) throws CoreException {
				resources.handle(res);
				// visit children too
				return true;
			}
		});
	}

	private void processResources(ResourceSet resources, final IProgressMonitor monitor) {
		if (monitor != null) {
			monitor.beginTask("Update Umlet Diagrams", resources.size());
		}

		// create thread pool to process the diagrams in parallel
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<ExportTask> tasks = new ArrayList<ExportTask>();
			final Object monitorLock = new Object();
			final LinkedHashSet<String> diagramsInProgress = new LinkedHashSet<String>();

			// create a task for each resource. This will submit them to the executor
			for (final IResource resource : resources.uxfFiles) {
				tasks.add(new ExportTask(resource, executor, monitor, monitorLock, diagramsInProgress));
			}

			// await finishing all tasks. This also causes the resource refresh and placing problem markers
			for (ExportTask task : tasks) {
				task.awaitFinish();
			}
		} finally {
			// shutdown the pool
			executor.shutdownNow();
		}

		// process compilation units
		for (ICompilationUnit cu : resources.units) {
			if (monitor != null) {
				monitor.subTask("processing " + cu.getElementName());
			}

			try {
				cu.getCorrespondingResource().deleteMarkers(IMG_MISSING_MARKER_TYPE, false, IResource.DEPTH_INFINITE);
				for (ImageReference reference : UmletPluginUtils.collectImgRefs(cu)) {
					SourceString srcAttrValue = reference.srcAttr.value;
					IPath path = UmletPluginUtils.getRootRelativePath(cu, srcAttrValue.getValue());
					List<IFile> files = UmletPluginUtils.findExistingFiles(cu.getJavaProject(), path);
					if (files.isEmpty()) {
						IMarker marker = cu.getCorrespondingResource().createMarker(IMG_MISSING_MARKER_TYPE);
						marker.setAttribute(IMarker.MESSAGE, "Unable to find referenced image " + path);
						marker.setAttribute(IMarker.LOCATION, "JavaDoc");
						marker.setAttribute(IMarker.CHAR_START, srcAttrValue.start);
						marker.setAttribute(IMarker.CHAR_END, srcAttrValue.end + 1);
						marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
					}
				}
			} catch (CoreException e) {
				throw new RuntimeException(e);
			}
			if (monitor != null) {
				monitor.worked(1);
			}
		}
		if (monitor != null) {
			monitor.done();
		}
	}

	private class ExportTask {
		private IFile outFile;
		private IFile inFile;
		private final File inputFile;
		private final File outputFile;
		private final Future<?> future;

		public ExportTask(IResource res, ExecutorService exec, final IProgressMonitor monitor, final Object monitorLock, final LinkedHashSet<String> diagramsInProgress) {
			inFile = res.getAdapter(IFile.class);
			String baseName = inFile.getName().substring(0, inFile.getName().length() - 4);
			IContainer parent = inFile.getParent();
			{
				IProject p = parent.getAdapter(IProject.class);
				if (p != null) {
					outFile = p.getFile(baseName + ".png");
				}
			}
			if (outFile == null) {
				IFolder f = parent.getAdapter(IFolder.class);
				if (f != null) {
					outFile = f.getFile(baseName + ".png");
				}
			}

			if (outFile == null) {
				throw new RuntimeException("unable to determine target location for " + inFile);
			}
			inputFile = new File(inFile.getLocationURI());
			outputFile = new File(outFile.getLocationURI());
			future = exec.submit(new Runnable() {

				@Override
				public void run() {

					if (monitor != null) {
						synchronized (monitorLock) {
							if (monitor.isCanceled()) {
								throw new OperationCanceledException();
							}
							diagramsInProgress.add(inFile.getName());
							updateSubTask();
						}
					}
					try {
						new DiagramHandler(inputFile).getFileHandler().doExportAs("png", outputFile);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}

					if (monitor != null) {
						synchronized (monitorLock) {
							monitor.worked(1);
							diagramsInProgress.remove(inFile.getName());
							updateSubTask();
						}
					}
				}

				private void updateSubTask() {
					if (monitor == null) {
						return;
					}
					StringBuilder sb = new StringBuilder();
					for (String s : diagramsInProgress) {
						if (sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(s);
					}
					monitor.subTask("Processing Diagrams " + sb.toString());
				}
			});
		}

		void awaitFinish() throws OperationCanceledException {
			try {
				// remove markers
				try {
					if (inFile.exists()) {
						inFile.deleteMarkers(PROBLEM_MARKER_TYPE, false, IResource.DEPTH_INFINITE);
					}
				} catch (CoreException e1) {
					throw new RuntimeException(e1);
				}

				// wait for task to complete
				future.get();

			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				if (e.getCause() instanceof OperationCanceledException) {
					throw (OperationCanceledException) e.getCause();
				}
				try {
					if (inFile.exists()) {
						IMarker marker = inFile.createMarker(PROBLEM_MARKER_TYPE);
						marker.setAttribute(IMarker.MESSAGE, "Error while exporting Umlet diagram: " + e.getCause().getMessage());
						marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
					}
				} catch (CoreException e1) {
					throw new RuntimeException(e1);
				}
			} finally {
				try {
					outFile.refreshLocal(IResource.DEPTH_ZERO, null);
					outFile.setDerived(true, null);
				} catch (CoreException e) {
					// swallow
				}
			}
		}
	}

}