package com.lkl.activitydetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import javax.ws.rs.core.UriBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.xml.sax.InputSource;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin implements IStartup {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.lkl.ActivityDetector"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	// constants
	private static final int UPDATE_TIME_INTERVAL=5000;	//constant - an update is initiated after a 5 second pause
	private static final int CHECK_TIME_INTERVAL=1000;	//constant - the daemon checks every second for user activity

	// variables (* means synchronized)
	private static int updateTimeInterval=0;
	private static int checkTimeInterval=0;
	private static Timestamp startTime=null;			//timestamp to store the start of the session
	private static Timestamp timeStamp=null;			//timestamp to store the last time a key was pressed (*)
	private static boolean initComplete=false;			//flag that ensures that initilisation is executed only once
	private static long totalCharsInserted=0;			//the total number of keys pressed since the session started (*)
	private static long charsInserted=0;				//the number of keys pressed since the last update (*)
	private static Timer timer = null; 					//timer used to scheduled checks for user in-activity
	private static MessageConsoleStream stream = null;

	//private static final String URL = "http://localhost:8080/dataUpdateService";
	private static final String URL = "http://talos.dcs.bbk.ac.uk:8888/com.lkl.eclipsedata";
	
	/**
	 * The constructor
	 */
	public Activator() {
		MessageConsole myConsole = new MessageConsole("Activity Detector", null);
		ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{ myConsole });
		ConsolePlugin.getDefault().getConsoleManager().showConsoleView(myConsole);
		stream = myConsole.newMessageStream();
		//stream.setActivateOnWrite(true);
		stream.println("Initialisation (activator): plugin instantiated!");
	}
	
	@Override
	public void earlyStartup() {
		// TODO Auto-generated method stub
		stream.println("Initialisation (load after workbench): early load finished!");
	}

	private void updateInactivity(Timestamp newTimestamp)
	{
		//update the total number of chars inserted so far
		totalCharsInserted+=charsInserted;
		
		//compute the time difference since the beginning of the session
		long timedifference=newTimestamp.getTime()-startTime.getTime();
		
		//compute chars/second ratio for the whole lot inserted so far
		double tratio=totalCharsInserted/(timedifference/1000);
		
		//compute the time difference since the last update
		timedifference=newTimestamp.getTime()-timeStamp.getTime();

		//compute chars/second ratio for the characters inserted since the last update
		double ratio=charsInserted/(timedifference/1000);
		
		//reset the counter for the next measurement
		charsInserted=0;
		
		//get username
		String userName = System.getProperty("user.name");
		
		//get machine name
		String machineName="";
		
		try
		{
			machineName = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e)
		{
			// TODO Auto-generated catch block
			//e.printStackTrace();
			machineName = "Unknown Host";
		}
		
		//update the database with the new values
		//System.out.println(userName + "@" + machineName + " total ratio: "+tratio+" current ratio: "+ratio);
		updateDatabase(userName + "@" + machineName + "@inactive");
	}

	private void updateActivity(Timestamp newTimestamp)
	{	
		//get username
		String userName = System.getProperty("user.name");
		
		//get machine name
		String machineName="";
		
		try
		{
			machineName = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e)
		{
			// TODO Auto-generated catch block
			//e.printStackTrace();
			machineName = "Unknown Host";
		}
		
		//update the database with the new values
		//System.out.println(userName + "@" + machineName + " active");
		updateDatabase(userName + "@" + machineName + "@active");
	}
	
	private void updateDatabase(String userData)
	{
		Job updater=new UpdaterThread("UpdateThread",userData,stream==null?null:stream.getConsole().newMessageStream());
		updater.schedule();
		/*
		ClientConfig configuration=new DefaultClientConfig();
		Client client=Client.create(configuration);
		WebResource service=client.resource(getBaseURI());
		ClientResponse response=(ClientResponse)service.path("rest").path("lkl").accept(new String[]{"text/plain"}).put(ClientResponse.class,userData);
		System.out.println(response.toString());
		Scanner stream=new Scanner(response.getEntityInputStream());
		
		while(stream.hasNext())
		{
			System.out.println(stream.nextLine());
		}
		
		stream.close();
		*/
	}
	
	private static URI getBaseURI(){
		return UriBuilder.fromUri(URL).build((Object)null);
	}	
	
	public void finishInit()
	{
		 if(initComplete==true)
		 {
			 return;
		 }
		 
		 IWorkbench workbench=this.getWorkbench();
		 
		 if(workbench==null)
			 workbench=PlatformUI.getWorkbench();
		 
		 Display display=workbench.getDisplay();
		 
		 if(display==null)
		 {
			 display=Display.getDefault();
		 }
		 	 
		 Listener listener=new Listener()
		 {
			@Override
			public void handleEvent(Event event)
			{
				// TODO Auto-generated method stub
				//get the next character
				char c=event.character;
							
				//get the new timestamp
				Timestamp newTimestamp = getTimestamp();		

				synchronized(this)
				{
					//initialise the start time if applicable
					if(startTime==null)
					{
						startTime=newTimestamp;
					}

					if(charsInserted==0)
					{
						updateActivity(newTimestamp);
					}
					
					//update the counter
					charsInserted++;		

					//update the timestamp
					timeStamp=newTimestamp;
				}
				
				stream.println("Activity Listener:["+timeStamp.toString() + "]: " + c);
			}
		 };
		 
		 try
		 {
			 display.addFilter(SWT.KeyDown, listener);
		 }
		 catch(IllegalArgumentException e)
		 {
			 stream.println("Initialisation (async UI thread): IllegalArgument " + e.getMessage());
		 }
		 catch(SWTException e)
		 {
			 stream.println("Initialisation (async UI thread): SWTException " + e.getMessage());			 
		 }
		 
		 setDefaults();
		 //initFromFile();
		 
		 timer=new Timer();
		 timer.schedule(new Task("(task) user activity tracker"), 0, checkTimeInterval);
		 
		 initComplete = true;
		 
		 stream.println("Initialisation (async UI thread): initialisation finished!");
	}
	
	private void initFromFile()
	{
		URL url;
		Scanner in=null;
		
		try
		{
		    url = new URL("platform:/plugin/com.test.events/files/init.xml");
		    InputStream inputStream = url.openConnection().getInputStream();
		    in=new Scanner(inputStream);
		    in.useDelimiter("\\A");
		    
		    String xml=in.hasNext() ? in.next() : "";
		    
		    XPathFactory xpathFactory = XPathFactory.newInstance();
		    XPath xpath = xpathFactory.newXPath();

		    String updateInterval="";
		    String checkInterval="";
		       
	    	updateInterval = xpath.evaluate("//timeinterval[@type='update']/@value", new InputSource(new StringReader(xml)));
	    	checkInterval = xpath.evaluate("//timeinterval[@type='check']/@value", new InputSource(new StringReader(xml)));

	    	updateTimeInterval=Integer.parseInt(updateInterval);
	    	checkTimeInterval=Integer.parseInt(checkInterval);
	    	
	    	//System.out.println(updateInterval + " " + checkInterval);	
		}
		catch (IOException e)
		{
			setDefaults();
			stream.println("Initialisation (file):" + e.getMessage());
		}
	    catch (XPathExpressionException e)
	    {
	    	setDefaults();
			stream.println("Initialisation (file):" + e.getMessage());
		}
		catch (Exception e)
		{
	    	setDefaults();
			stream.println("Initialisation (file):" + e.getMessage());		
		}
	    finally
	    {
		    in.close();	    	
	    }
	}
	
	private void setDefaults()
	{
		updateTimeInterval=UPDATE_TIME_INTERVAL;
		checkTimeInterval=CHECK_TIME_INTERVAL;
	}
	
	private Timestamp getTimestamp()
	{
		Date date= new Date();
		return new Timestamp(date.getTime());
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		stream.println("Initialisation (execution start): plugin started!");
		//stream.close();

		Runnable r=new Runnable()
		{
			public void run()
			{
				finishInit();
			}
		};
		
		PlatformUI.getWorkbench().getDisplay().asyncExec(r);
	
		stream.println("Initialisation (execution start): main thread started!");
		//System.out.println("normal startup finished!");
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		stream.close();
		stream = null;
		
		Timestamp newTimestamp = getTimestamp();		

		synchronized(this)
		{
			updateActivity(newTimestamp);
		}
		
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	class Task extends TimerTask
	{

		private String name;                 // A string to output

		/**
		* Constructs the object, sets the string to be output in function run()
		* @param str
		*/
		Task(String name)
		{
			this.name = name;
		}

		/**
		* When the timer executes, this code is run.
		*/
		public void run()
		{
			//get the new timestamp
			Timestamp newTimestamp = getTimestamp();
					
			//if there is previous timestamp, compute the difference and update the database
			synchronized(Activator.this)
			{
				if(timeStamp!=null)
				{
					long timedifference=newTimestamp.getTime()-timeStamp.getTime();
				
					if(timedifference>=updateTimeInterval&&charsInserted!=0)
					{
						updateInactivity(newTimestamp);
						//System.out.println(name + ": updated database successfully (inactivity)");
					}
				}
			}
		}
	}
}
