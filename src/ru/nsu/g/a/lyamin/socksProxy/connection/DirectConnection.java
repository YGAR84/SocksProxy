package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class DirectConnection extends Connection
{
	private SocketChannel channel;
	private ConnectionBuffer bufferToReadFrom;
	private ConnectionBuffer bufferToWriteTo;


	public DirectConnection(ConnectionSelector connectionSelector, SocketChannel _channel, ConnectionBuffer _bufferToRead, ConnectionBuffer _bufferToWrite)
	{
		super(connectionSelector);
		channel = _channel;
		bufferToReadFrom = _bufferToRead;
		bufferToWriteTo = _bufferToWrite;

		bufferToReadFrom.setReader(this);
		bufferToWriteTo.setWriter(this);

		//System.out.println("DIRECT PHASE CTOR");
	}

	@Override
	public void perform(SelectionKey key)
	{
		if (key.isValid() && key.isReadable())
		{
			try
			{
				if (!bufferToWriteTo.readFromChannelToBuffer(channel))
				{
					terminate(key);
					return;
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
				terminate(key);
				return;

			}
		}

		if (key.isValid() && key.isWritable())
		{
			try
			{
				bufferToReadFrom.writeToChannelFromBuffer(channel);
			}
			catch (AsynchronousCloseException eBoy)
			{
				eBoy.printStackTrace();
				connectionSelector.registerConnection(channel, this, SelectionKey.OP_READ);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				terminate(key);
			}
		}


//        if(key.isValid() && key.isConnectable())
//        {
//            try
//            {
//                channel.finishConnect();
//
//                connectionSelector.registerConnection(channel, this, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
//            }
//            catch (IOException e)
//            {
//                e.printStackTrace();
//            }
//        }
	}

	public SocketChannel getChannel()
	{
		return channel;
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
}
