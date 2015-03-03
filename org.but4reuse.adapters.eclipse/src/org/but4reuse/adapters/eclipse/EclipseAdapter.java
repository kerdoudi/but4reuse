package org.but4reuse.adapters.eclipse;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.eclipse.plugin_infos_extractor.utils.DependenciesBuilder;
import org.but4reuse.adapters.eclipse.plugin_infos_extractor.utils.PluginInfosExtractor;
import org.but4reuse.utils.files.FileUtils;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Eclipse adapter
 * 
 * @author Fjorilda Gjermizi
 * @author Krista Drushku
 * @author Diana MALABARD
 * @author Jason CHUMMUN
 * 
 */
public class EclipseAdapter implements IAdapter {

	private URI rootURI;

	/**
	 * This method check if the artefact is adaptable with the EclipseAdapter
	 */

	@Override
	public boolean isAdaptable(URI uri, IProgressMonitor monitor) {
		File file = FileUtils.getFile(uri);
		if (file.isDirectory()) {
			File pluginsFolder = new File(file.getAbsolutePath() + "/plugins");
			if (pluginsFolder.exists() && pluginsFolder.isDirectory()) {
				return true;
			} else {
				return false;
			}
		}
		return false;
	}

	/**
	 * Provides the atomic elements (plugins) this distribution is made of
	 * 
	 * @param uri
	 *            URI of the distribution
	 * @param monitor
	 */
	@Override
	public List<IElement> adapt(URI uri, IProgressMonitor monitor) {
		List<IElement> elements = new ArrayList<IElement>();
		File file = FileUtils.getFile(uri);
		rootURI = file.toURI();
		// start the containment tree traversal, with null as initial container
		adapt(file, elements, null);

		// plugin dependencies
		for (IElement elem : elements) {
			if (elem instanceof PluginElement) {
				DependenciesBuilder builder = new DependenciesBuilder((PluginElement) elem, elements);
				builder.build();
			}
		}

		// in elements we have the result
		return elements;
	}

	/**
	 * adapt recursively
	 * 
	 * @param file
	 * @param elements
	 * @param container
	 */
	private void adapt(File file, List<IElement> elements, IElement container) {
		FileElement newElement = null;
		if (isAPlugin(file)) {
			try {
				if (file.isDirectory()) {
					newElement = PluginInfosExtractor.getPluginInfosFromManifest(file.getAbsolutePath()
							+ "/META-INF/MANIFEST.MF");
				} else {
					newElement = PluginInfosExtractor.getPluginInfosFromJar(file.getAbsolutePath());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			newElement = new FileElement();
		}

		// Set the relevant information
		newElement.setUri(file.toURI());
		newElement.setRelativeURI(rootURI.relativize(file.toURI()));

		// Add dependency to the parent folder
		if (container != null) {
			newElement.addDependency("container", container);
		}

		// Add to the list
		elements.add(newElement);

		// Go for the files in case of folder
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File subFile : files) {
				adapt(subFile, elements, newElement);
			}
		}
	}

	private boolean isAPlugin(File file) {
		if (file.getParentFile().getName().equals("plugins") || file.getParentFile().getName().equals("dropins")) {
			if (file.isDirectory()) {
				File manif = new File(file.getAbsolutePath() + "/META-INF/MANIFEST.MF");
				if (manif.exists()) {
					return true;
				}
			} else if (FileUtils.getExtension(file).equalsIgnoreCase("jar")) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void construct(URI uri, List<IElement> elements, IProgressMonitor monitor) {
		for (IElement element : elements) {
			// check user cancel for each element
			if (!monitor.isCanceled()) {
				// provide user info
				monitor.subTask(element.getText());
				if (element instanceof FileElement) {
					FileElement fileElement = (FileElement) element;
					try {
						// Create parent folders structure
						URI newDirectoryURI = uri.resolve(fileElement.getRelativeURI());
						File destinationFile = FileUtils.getFile(newDirectoryURI);
						if (destinationFile != null && !destinationFile.getParentFile().exists()) {
							destinationFile.getParentFile().mkdirs();
						}
						if (destinationFile != null && !destinationFile.exists()) {
							// Copy the content. In the case of a folder, its
							// content is not copied
							File file = FileUtils.getFile(fileElement.getUri());
							Files.copy(file.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			monitor.worked(1);
		}
	}

}
