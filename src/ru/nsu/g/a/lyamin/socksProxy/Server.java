package ru.nsu.g.a.lyamin.socksProxy;

import ru.nsu.g.a.lyamin.socksProxy.connection.DNSConnection;
import ru.nsu.g.a.lyamin.socksProxy.connection.ServerConnection;

import java.io.IOException;

public class Server implements Runnable
{

	private final int timeout = 1000;

	private ConnectionSelector connectionSelector;

	public Server(int port)
	{
		try
		{
			connectionSelector = new ConnectionSelector();
			new ServerConnection(connectionSelector, port);
			DNSConnection.createInstance(connectionSelector);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		try
		{
			while (!Thread.interrupted())
			{
				connectionSelector.iterateOverConnections();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.exit(-41);
		}
		finally
		{
			connectionSelector.shutdown();
		}
	}

}
