package org.protege.editor.owl.model.library;

import org.protege.editor.core.prefs.PreferencesManager;

/**
 * Created by vblagodarov on 10-10-17.
 */
public class XmlCatalogPreferences {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.xmlcatalog";

    private static final String XMLCATALOG_OPTIONS_KEY = "xmlcatalog.options";

    private static XmlCatalogPreferences instance;

    public enum Choice {
        useDefaultCatalog, alwaysAskForCustomCatalog;

        public static Choice getDefaultValue(){
            return useDefaultCatalog;
        }
    }

    public void setXmlCatalogOptions(Choice catalogOptions) {
        PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY).putString(
                XMLCATALOG_OPTIONS_KEY,
                catalogOptions.name());
    }

    public Choice getXmlCatalogOptions() {
        String prefs = PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY)
                .getString(XMLCATALOG_OPTIONS_KEY, null);
        if(prefs == null) {
            return Choice.getDefaultValue();
        }

        return Choice.valueOf(prefs);
    }

    public static synchronized XmlCatalogPreferences getPreferences() {
        if(instance == null) {
            instance = new XmlCatalogPreferences();
        }
        return instance;
    }
}
