package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PendingConnection extends Connection
{

	private SocketChannel channel;
	private SecondPhaseConnection secondPhaseConnection;

	public PendingConnection(ConnectionSelector _connectionSelector, SocketChannel _channel, SecondPhaseConnection _secondPhaseConnection, SocketAddress address) throws IOException
	{
		super(_connectionSelector);
		channel = _channel;
		secondPhaseConnection = _secondPhaseConnection;

		//System.out.println("PENDING CTOR");

		boolean connected;

		try
		{
			connected = channel.connect(address);
		}
		catch (java.nio.channels.AlreadyConnectedException ignored)
		{
			secondPhaseConnection.setIsConnected(true, this);
			return;
		}


		if (connected)
		{
			secondPhaseConnection.setIsConnected(true, this);
		} else
		{
			connectionSelector.registerConnection(channel, this, SelectionKey.OP_CONNECT);
		}

	}

	@Override
	public void perform(SelectionKey key) throws IOException
	{
		if (key.isValid() && key.isConnectable())
		{
			try
			{
				boolean result = channel.finishConnect();
				secondPhaseConnection.setIsConnected(result, this);
			}
			catch (SocketException e)
			{
				secondPhaseConnection.setIsConnected(false, this);
			}

			key.cancel();
		}
	}

	@Override
	public void terminate()
	{
		connectionSelector.deleteConnection(channel);
		try
		{
			channel.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public SocketChannel getChannel()
	{
		return channel;
	}
}
