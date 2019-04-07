package ca.phon.libreoffice;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Random;

import com.sun.star.bridge.UnoUrlResolver;
import com.sun.star.bridge.XUnoUrlResolver;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import ca.phon.util.OSInfo;
import ca.phon.util.PrefHelper;

/**
 * Customized version of {@link com.sun.star.comp.helper.Bootstrap}
 *
 */
public class OfficeBootstrap {
	
	private final static String OFFICE_EXECUTABLE = "soffice"  + (OSInfo.isWindows() ? ".exe" : "");
	
	public final static String OFFICE_LOCATION_PROP = 
			OfficeBootstrap.class.getName() + ".libreOfficeLocation";
	public final static String OFFICE_LOCATION = PrefHelper.get(OFFICE_LOCATION_PROP, defaultLibreOfficeInstallLocation());

	public static String defaultLibreOfficeInstallLocation() {
		String retVal = null;
		
		if(OSInfo.isMacOs()) {
			retVal = "/Applications/LibreOffice.app/Contents/Macos";
		} else if(OSInfo.isNix()) {
			retVal = "/usr/lib/libreoffice/program";
		} else if(OSInfo.isWindows()) {
			retVal = System.getenv("ProgramFiles") + "\\LibreOffice\\program";
		}
		
		return retVal;
	}
	
	public static XComponentContext bootstrap() throws BootstrapException {
		return bootstrap(new String[0]);
	}

	public static XComponentContext quickstart() throws BootstrapException {
		return bootstrap(new String[] {"--nologo", "--quickstart", "--norestore"});
	}
	
	public static XComponentContext bootstrap(String[] argArray) throws BootstrapException {
		XComponentContext xContext = null;

		Random randomPipeName = new Random();

		try {
			// create default local component context
			XComponentContext xLocalContext = Bootstrap.createInitialComponentContext((Map<String, Object>) null);
			if (xLocalContext == null)
				throw new BootstrapException("no local component context!");

			// find office executable relative to this class's class loader
			File fOffice = new File(OFFICE_LOCATION, OFFICE_EXECUTABLE);
			if (!fOffice.exists())
				throw new BootstrapException("no office executable found!");

			// create random pipe name
			String sPipeName = "uno" + Long.toString(randomPipeName.nextLong() & 0x7fffffffffffffffL);

			// create call with arguments
			String[] cmdArray = new String[argArray.length + 2];
			cmdArray[0] = fOffice.getPath();
			cmdArray[1] = ("--accept=pipe,name=" + sPipeName + ";urp;");

			System.arraycopy(argArray, 0, cmdArray, 2, argArray.length);

			// start office process
			ProcessBuilder pb = new ProcessBuilder(cmdArray);
			Process p = pb.start();
			pipe(p.getInputStream(), System.out, "CO> ");
			pipe(p.getErrorStream(), System.err, "CE> ");

			// initial service manager
			XMultiComponentFactory xLocalServiceManager = xLocalContext.getServiceManager();
			if (xLocalServiceManager == null)
				throw new BootstrapException("no initial service manager!");

			// create a URL resolver
			XUnoUrlResolver xUrlResolver = UnoUrlResolver.create(xLocalContext);

			// connection string
			String sConnect = "uno:pipe,name=" + sPipeName + ";urp;StarOffice.ComponentContext";

			// wait until office is started
			for (int i = 0;; ++i) {
				try {
					// try to connect to office
					Object context = xUrlResolver.resolve(sConnect);
					xContext = UnoRuntime.queryInterface(XComponentContext.class, context);
					if (xContext == null)
						throw new BootstrapException("no component context!");
					break;
				} catch (com.sun.star.connection.NoConnectException ex) {
					// Wait 500 ms, then try to connect again, but do not wait
					// longer than 5 min (= 600 * 500 ms) total:
					if (i == 600) {
						throw new BootstrapException(ex);
					}
					Thread.sleep(500);
				}
			}
		} catch (BootstrapException e) {
			throw e;
		} catch (java.lang.RuntimeException e) {
			throw e;
		} catch (java.lang.Exception e) {
			throw new BootstrapException(e);
		}

		return xContext;
	}

	private static void pipe(final InputStream in, final PrintStream out, final String prefix) {
		new Thread("Pipe: " + prefix) {
			@Override
			public void run() {
				try {
					BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));

					for (;;) {
						String s = r.readLine();
						if (s == null) {
							break;
						}
						out.println(prefix + s);
					}
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace(System.err);
				} catch (java.io.IOException e) {
					e.printStackTrace(System.err);
				}
			}
		}.start();
	}

}
