package ru.nsu.g.a.lyamin.socksProxy;

import ru.nsu.g.a.lyamin.socksProxy.connection.*;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConnectionSelector
{
	private HashMap<AbstractSelectableChannel, Connection> connectionMap = new HashMap<>();
	private Selector selector;

	public ConnectionSelector() throws IOException
	{
		selector = Selector.open();
	}

	public void addConnection(AbstractSelectableChannel channel, Connection connection)
	{
		connectionMap.put(channel, connection);
	}

	public void deleteConnection(AbstractSelectableChannel channel)
	{
		connectionMap.remove(channel);
	}

	public void enableOpt(AbstractSelectableChannel channel, int opt)
	{
		if(channel != null)
		{
			try
			{
				channel.register(selector, channel.validOps() | opt);
			}
			catch (ClosedChannelException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void disableOpt(AbstractSelectableChannel channel, int opt)
	{
		if(channel != null)
		{
			try
			{
				channel.register(selector, channel.validOps() & ~opt);
			}
			catch (ClosedChannelException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void registerConnection(AbstractSelectableChannel channel, Connection connection, int opts)
	{
//        System.out.println("is registered:" + channel.isRegistered() + " " + channel.isOpen());
//        if(!channel.isRegistered())
//        {
		try
		{
			channel.register(selector, opts);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			connection.terminate();
			return;
		}

		connectionMap.put(channel, connection);
	}

	public void iterateOverConnections() throws IOException
	{
		selector.select();

//		System.out.println("KEY SIZE: " + selector.keys().size());
//		System.out.println("CONNECTION MAP SIZE: " + connectionMap.size());
//        for(var v : selector.keys())
//        {
//        	Connection connection = connectionMap.get(v.channel());
//            System.out.println(connectionToString(connection) + "; v: " + v.isValid() +"; a: " + v.isAcceptable() + "; r: " + v.isReadable() +
//                    "; w: " + v.isWritable() + "; c: " + v.isConnectable());
//        }

		Set<SelectionKey> selectedKeys = selector.selectedKeys();

//        int size = selectedKeys.size();
//        if(size >= 2)
//            System.out.println("size: " + size);

		Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

		while (keyIterator.hasNext())
		{
			SelectionKey key = keyIterator.next();

//            if(size >= 2)
//                System.out.println("a: " + key.isAcceptable() + "; r: " + key.isReadable() + " ; w: " + key.isWritable() + " ; v: " + key.isValid());

			Connection connection = connectionMap.get(key.channel());
			if (connection != null)
			{
				connection.perform(key);
			}

			keyIterator.remove();
		}

		//System.out.println("iter is over");
	}

	private String connectionToString(Connection connection)
	{
		if(connection instanceof DNSConnection)
		{
			return "DNS CONNECTION";
		}
		else if(connection instanceof FirstPhaseConnection)
		{
			return "FIRST PHASE CONNECTION";
		}
		else if(connection instanceof SecondPhaseConnection)
		{
			return "SECOND PHASE CONNECTION";
		}
		else if(connection instanceof PendingConnection)
		{
			return "PENDING CONNECTION";
		}
		else if(connection instanceof DirectConnection)
		{
			return "DIRECT CONNECTION";
		}
		else if(connection instanceof ServerConnection)
		{
			return "SERVER CONNECTION";
		}
		else if(connection == null)
		{
			return "NULL CONNECTION";
		}
		else
		{
			return "UNKNOWN CONNECTION";
		}
	}

	public void shutdown()
	{
		if (selector != null)
		{
			try
			{
				selector.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		for (Map.Entry<AbstractSelectableChannel, Connection> entry : connectionMap.entrySet())
		{
			try
			{
				entry.getKey().close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		connectionMap.clear();
	}
}
