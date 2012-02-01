package hjb4u.launch;

import hjb4u.Main;
import hjb4u.Pair;
import hjb4u.SettingsStore;
import hjb4u.config.DBList;
import hjb4u.config.HJB4UConfiguration;
import hjb4u.logging.Log4j_Init;
import hjb4u.logging.MemoryAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.lf5.LF5Appender;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import static hjb4u.Util.copyResource;
import static hjb4u.Util.joinPath;
import static hjb4u.config.Constants.*;
import static hjb4u.config.Constants.PaneAppenderName;

/**
 * Date: 2/1/12
 * Time: 11:42 AM
 *
 * @Author Nigel Bajema
 */
public abstract class AbstractLaunch {
    private static String conf_dir;

    static HJB4UConfiguration initializeHAJJ4U(boolean gui) throws IOException, JAXBException {
        ArrayList<Pair<Level, String>> preLoggingMessages = new ArrayList<Pair<Level, String>>();

        //Load the schema.properties file.
        Properties schema = new Properties();
        String pgkPath = Main.class.getPackage().getName().replace('.', '/');
        String conf_path = pgkPath + "/conf";
        String loc = conf_path + "/schema.properties";
        ClassLoader cl = Launch.class.getClassLoader();
        schema.load((cl.getResource(loc).openStream()));
        if (schema.getProperty(UUID) == null) {
            System.out.println("Could not get UUID. Exiting.");
            System.exit(1);
        }
        conf_dir = System.getProperty("user.home") + File.separator + HJB4U_PATH + File.separator + schema.getProperty(UUID);

        //Copy resources from classpath (generally inside the JAR) to the
        //local storage area. ReThrows Null pointers if the file is required.
        File confdir = new File(conf_dir);
        if (!confdir.exists()) {
            if (confdir.mkdirs()) {
                for (Pair<String, Boolean> res : new Pair[]{
                        new Pair<String, Boolean>("log4j.xml", true),
                        new Pair<String, Boolean>("log4j.dtd", true),
                        new Pair<String, Boolean>("persistence.properties", true),
                        new Pair<String, Boolean>("settings.xml", false)}) {
                    try {
                        copyResource(cl.getResource(String.format("%s/%s", conf_path, res.getItem1())), new File(joinPath(conf_dir, res.getItem1())));
                    } catch (NullPointerException npe) {
                        if (res.getItem2()) {
                            throw npe;
                        } else {
                            preLoggingMessages.add(new Pair<Level, String>(Level.INFO, String.format("%s not included in classpath, ignoring.", res.getItem1())));
                        }
                    }
                }
            }
        }

        //Initialise the logging.
        new Log4j_Init(new FileInputStream(joinPath(conf_dir, "log4j.xml"))).logConf();
        Logger logger = Logger.getLogger(Launch.class);

        //Log all messages that were qued up before logging was available.
        for (Pair<Level, String> preLoggingMessage : preLoggingMessages) {
            logger.log(preLoggingMessage.getItem1(), preLoggingMessage.getItem2());
        }
        //Instantiate the settings store.
        SettingsStore.instanciate(conf_dir, "settings.xml");
        HJB4UConfiguration settings = SettingsStore.getInstance().getSettings();
        if (settings.getSchema() == null) {
            settings.setSchema(schema.getProperty(SCHEMA_FILE));
        }
        if (gui) {
            //More initialization for logging.
            if (settings.isEnableLF5()) {
                LF5Appender appender = new LF5Appender();
                appender.setMaxNumberOfRecords(settings.getLf5Size());
                appender.setThreshold(settings.getLf5LogLevel().getLevel());
                appender.setName(LF5AppenderName);
                Logger.getRootLogger().addAppender(appender);
            }

            if (settings.isEnableLoggingPane()) {
                PatternLayout layout = new PatternLayout(settings.getPanePattern());
                MemoryAppender appender = new MemoryAppender(layout, settings.getPaneSize());
                appender.setThreshold(settings.getPaneLogLevel().getLevel());
                appender.setName(PaneAppenderName);
                Logger.getRootLogger().addAppender(appender);
            }
        }
        logger.info("Logging Initialized.");

        //Get the Database Templates.
        try {
            Unmarshaller umas = JAXBContext.newInstance(DBList.class).createUnmarshaller();
            DBList templates = (DBList) umas.unmarshal(cl.getResourceAsStream(conf_path + "/databases.xml"));
            SettingsStore.getInstance().setDatabaseTemplates(templates);
        } catch (Exception e) {
            logger.warn("Could not load database templates: " + e, e);
        }

        return settings;
    }
}