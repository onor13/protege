package org.protege.editor.owl.model.library;

import org.protege.editor.core.ui.util.UIUtil;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.ui.library.XMLCatalogManager;
import org.protege.editor.owl.ui.ontology.imports.AddImportsStrategy;
import org.protege.editor.owl.ui.ontology.imports.wizard.GetImportsVisitor;
import org.protege.editor.owl.ui.ontology.imports.wizard.ImportInfo;
import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.XMLCatalog;
import org.protege.xmlcatalog.entry.Entry;
import org.protege.xmlcatalog.entry.NextCatalogEntry;
import org.protege.xmlcatalog.entry.UriEntry;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: 23-Aug-2006<br><br>

 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class OntologyCatalogManager {
	private static final String DEFAULT_CATALOG_NAME = "catalog-v001.xml";
	public static final String CATALOG_BACKUP_PREFIX = "catalog-backup-";
	
	public static final String TIMESTAMP        = "Timestamp";
	    
    private Map<File, XMLCatalog> localCatalogs = new HashMap<>();
    
    private XMLCatalog customCatalog;
    private XMLCatalog defaultCatalog;
    private File defaultCatalogFolder;

	private static XmlCatalogPreferences catalogPreferences = new XmlCatalogPreferences();

    private List<CatalogEntryManager> entryManagers;

	private Logger logger = LoggerFactory.getLogger(OntologyCatalogManager.class);
    
    private static void backup(File folder, File catalogFile) {
	    File backup;
	    int i = 0;
	    while (true) {
			if (!((backup = new File(folder, CATALOG_BACKUP_PREFIX + (i++) + ".xml")).exists())) {
				break;
			}
		}
	    catalogFile.renameTo(backup);
	}

	private final static Set<String> XMLCATALOG_EXTENSIONS;
	static {
		Set<String> extensions = new HashSet<>();
		extensions.add("xml");
		XMLCATALOG_EXTENSIONS = Collections.unmodifiableSet(extensions);
	}
	
	private File getActiveCatalogFile(File folder) {
    	if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.useDefaultCatalog){
			return getDefaultCatalogFile(folder);
		}
		if(customCatalog != null){
    		return getCatalogFile(customCatalog);
		}
		File cf = chooseCatalogFile();
		return cf == null ? getDefaultCatalogFile(folder) : cf ;
	}

	public static File getDefaultCatalogFile(File folder) {
		return new File(folder, DEFAULT_CATALOG_NAME);
	}
	
	/**
	 * this works for catalogs that are generated from a parse or created
	 * by the OntologyCatalogManager.
	 */
	public File getCatalogFile(XMLCatalog catalog) {
		if (catalog == null || catalog.getXmlBaseContext() == null) {
			return  null;
		}
		File f = new File(catalog.getXmlBaseContext().getXmlBase());
		if (f.exists() && f.isDirectory())  {
		    f = getCatalogFile(getActiveCatalog());
		}
		return f.exists() ? f : null;
	}
		
	public OntologyCatalogManager() {
    	entryManagers = new ArrayList<>();
    	CatalogEntryManagerLoader pluginLoader = new CatalogEntryManagerLoader();
    	for (CatalogEntryManagerPlugin plugin : pluginLoader.getPlugins()) {
    		try {
    			entryManagers.add(plugin.newInstance());
    		}
    		catch (Throwable t) {
    			logger.warn("An error occurred whilst instantiating a CatalogEntryManager plugin: {}", t);
    		}
    	}
    }
	
	public OntologyCatalogManager(List<? extends CatalogEntryManager> entryManagers) {
		this.entryManagers = new ArrayList<>(entryManagers);
	}

	public List<CatalogEntryManager>  getCatalogEntryManagers() {
		return Collections.unmodifiableList(entryManagers);
	}
	
    public XMLCatalog ensureCatalogExists(File folder) {
		XMLCatalog catalog = null;
		File catalogFile;
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.alwaysAskForCustomCatalog && customCatalog != null)
		{
			return customCatalog;
		}
		catalogFile = getCatalogFile(getActiveCatalog());
		if(catalogFile == null){
			catalogFile = getDefaultCatalogFile(folder);
		}
		boolean alreadyExists = catalogFile.exists();
		boolean modified = false;
		if (alreadyExists) {
			try {
				catalog = CatalogUtilities.parseDocument(catalogFile.toURI().toURL());
			}
			catch (Throwable e) {
				logger.warn("An error occurred whilst parsing the catalog document at {}.  Error: {}", catalogFile.getAbsolutePath(), e);
				backup(folder, catalogFile);
			}
		}
		if (catalog == null) {
			catalog = new XMLCatalog(folder.toURI());
			modified = true;
		}
		if (alreadyExists) {
			try {
				modified = modified | update(catalog);
			}
			catch (Throwable t) {
				logger.warn("An error occurred whilst updating the catalog document at {}.  Error: {}", catalogFile.getAbsolutePath(), t);
			}
		}
		else {
			for (CatalogEntryManager entryManager : entryManagers) {
				try {
					modified = modified | entryManager.initializeCatalog(folder, catalog);
				}
				catch (Throwable t) {
					logger.warn("An error occurred whilst initializing the catalog at {}.  Error: {}", catalogFile.getAbsolutePath(), t);
				}
			}
		}
		if (modified) {
			try {
				CatalogUtilities.save(catalog, catalogFile);
			}
			catch (IOException e) {
				logger.warn("An error occurred whilst saving the catalog at {}.  Error: {}", catalogFile.getAbsolutePath(), e);
			}
		}
		return catalog;
	}

	public void updateActiveCatalog(ImportInfo importInfo, File folder, URI ontologyToImport){
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.alwaysAskForCustomCatalog){
			return;
		}

		if(defaultCatalog == null){
			addFolder(folder);
		}
		if(defaultCatalog == null){
			return;
		}
		XMLCatalogManager xmlCatalogManager = new XMLCatalogManager(defaultCatalog);
		URI physicalLocationURI = CatalogUtilities.relativize(importInfo.getPhysicalLocation(), defaultCatalog);
		if(xmlCatalogManager == null || xmlCatalogManager.containsUri(physicalLocationURI)){
			return;
		}
		defaultCatalog.addEntry(0, new UriEntry("Imports Wizard Entry", defaultCatalog, ontologyToImport.toString(), physicalLocationURI, null));
		try {
			CatalogUtilities.save(defaultCatalog, OntologyCatalogManager.getDefaultCatalogFile(folder.getParentFile()));
		} catch (IOException e) {
			logger.warn("An error occurred whilst saving the catalog file: {}", e);
		}
	}

	public void updateActiveCatalog(IRI ontologyIRI, File file, boolean fileIsACatalog){
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.alwaysAskForCustomCatalog){
			try{
				if(fileIsACatalog){
					XMLCatalog newCatalog = CatalogUtilities.parseDocument(file.toURI().toURL());
					customCatalog = newCatalog;
					return;
				}
				else{
					if(customCatalog == null){
						return;
					}
					URI relativeFile = CatalogUtilities.relativize(file.toURI(), customCatalog);
					customCatalog.addEntry(0, new UriEntry("User Entered Import Resolution", customCatalog, ontologyIRI.toString(), relativeFile, null));
					saveCatalog(customCatalog);
				}
			}
			catch (IOException e){
				logger.error("An error occurred whilst reading the XML Catalog: ", file.getAbsolutePath(), e);
			}
			return;
		}

		if (defaultCatalog == null) {
			return;
		}
		URI relativeFile = CatalogUtilities.relativize(file.toURI(), defaultCatalog);
		if (fileIsACatalog) {
			defaultCatalog.addEntry(0, new NextCatalogEntry("User Entered Import Resolution", defaultCatalog, relativeFile, null));
		} else {
			defaultCatalog.addEntry(0, new UriEntry("User Entered Import Resolution", defaultCatalog, ontologyIRI.toString(), relativeFile, null));
		}

		saveCatalog(defaultCatalog);
	}

	public URI getRedirect(URI original) {
    	if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.alwaysAskForCustomCatalog){
    		return customCatalog == null ? null : CatalogUtilities.getRedirect(original, customCatalog);
		}
		URI redirect = null;
    	for (XMLCatalog catalog : getCatalogs()) {
    		redirect = CatalogUtilities.getRedirect(original, catalog);
    		if (redirect != null) {
    			break;
    		}
    	}
    	return redirect;
    }
    
    public boolean update(XMLCatalog catalog) throws IOException {
    	boolean modified = false;
    	for (Entry entry : catalog.getEntries()) {
    		for (CatalogEntryManager updater : entryManagers) {
    			if (updater.isSuitable(entry)) {
    				modified = modified | updater.update(entry);
    			}
    		}
    	}
    	return modified;
    }
    
    private Collection<XMLCatalog> getCatalogs() {
    	return localCatalogs.values();
    }

    public XMLCatalog getActiveCatalog(){
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.useDefaultCatalog){
			return defaultCatalog;
		}

		if(customCatalog != null){
			return customCatalog;
		}
		try{
			customCatalog = changeActiveCatalog(chooseCatalogFile());
			if(customCatalog != null){
				return customCatalog;
			}
		}
		catch (Exception e){
			logger.warn("No valid XML Catalog were selected: "+e);
		}
		return null;
    }

	private void saveCatalog(@Nonnull XMLCatalog catalog){
		try {
			CatalogUtilities.save(catalog, new File(catalog.getXmlBaseContext().getXmlBase()));
		} catch (IOException e) {
			logger.error("Could not save user supplied import redirection to catalog.", e);
		}
	}

    private File chooseCatalogFile(){
    	File cf = UIUtil.openFile(JFrame.getFrames()[0], "Choose XML Catalog file", "XML Catalog File", XMLCATALOG_EXTENSIONS);
    	if(cf == null){
    		return null;
		}
		try {
			changeActiveCatalog(cf);
		} catch (IOException e) {
			return null;
		}
		return cf;
	}

	public XMLCatalog changeActiveCatalog(@Nonnull File newCatalogFile) throws IOException {
		customCatalog = CatalogUtilities.parseDocument(newCatalogFile.toURI().toURL());
		return customCatalog;
	}
    
    public XMLCatalog addFolder(File dir) {
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.alwaysAskForCustomCatalog && customCatalog != null){
			return customCatalog;
		}
        XMLCatalog lib = localCatalogs.get(dir);
        // Add the parent file which will be the folder
        if (lib == null) {
            // Add automapped library
        	lib = ensureCatalogExists(dir);
        	if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.useDefaultCatalog){
				localCatalogs.put(dir, lib);
			}
        }

		defaultCatalog = lib;
        defaultCatalogFolder = dir;

        return lib;
    }
    
    public void reloadFolder(File dir) throws IOException {
		if(catalogPreferences.getXmlCatalogOptions() == XmlCatalogPreferences.Choice.useDefaultCatalog) {
			localCatalogs.remove(dir);
			localCatalogs.put(dir, CatalogUtilities.parseDocument(getDefaultCatalogFile(getActiveCatalogFile(dir)).toURI().toURL()));
			defaultCatalog = localCatalogs.get(defaultCatalogFolder);
		}
		else {
			if(customCatalog != null){
				File cf = getCatalogFile(customCatalog);
				if(cf != null){
					changeActiveCatalog(cf);
				}
			}
		}
    }
}
