package com.lkl.activitydetector;

import java.net.URI;
import java.util.Scanner;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.console.MessageConsoleStream;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class UpdaterThread extends Job
{
	private String userData;
	private static final String URL = "http://talos.dcs.bbk.ac.uk:8282/com.lkl.eclipsedata";
	private static MessageConsoleStream consoleStream = null;
	
	// Get UISynchronize injected as field
	
	public UpdaterThread(String name, String userData, MessageConsoleStream consoleStream)
	{
		super(name);
		this.userData=userData;
		this.consoleStream = consoleStream;
	}
	
	private static URI getBaseURI()
	{
		return UriBuilder.fromUri(URL).build((Object)null);
	}	
	
	@Override
	protected IStatus run(IProgressMonitor monitor)
	{	
		try
		{
			ClientConfig configuration=new DefaultClientConfig();
			Client client=Client.create(configuration);
			WebResource service=client.resource(getBaseURI());
			ClientResponse response=(ClientResponse)service.path("rest").path("service").accept(new String[]{"text/plain"}).put(ClientResponse.class,userData);
			System.out.println(response.toString());
			Scanner stream=new Scanner(response.getEntityInputStream());
			
			if(consoleStream!=null)
			{
				consoleStream.print("Rest Client (DBUpdate thread): ");
			
				while(stream.hasNext())
				{
					consoleStream.println(stream.nextLine());
				}
			}
			
			stream.close();
		}
		catch (UniformInterfaceException e)
		{
			if(consoleStream!=null)
			{
				consoleStream.println("Rest Client (DBUpdate thread): CANCEL_STATUS");
			}
			
			return Status.CANCEL_STATUS;
		}
		catch (Exception e)
		{
			if(consoleStream!=null)
			{
				consoleStream.println("Rest Client (DBUpdate thread): CANCEL_STATUS");
			}
			
			return Status.CANCEL_STATUS;
		}
		
		if(consoleStream!=null)
		{
			consoleStream.println("Rest Client (DBUpdate thread): OK_STATUS");
		}
		
		return Status.OK_STATUS;
	}
}
