package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class DirectConnection extends Connection
{
    private SocketChannel channel;
    private ConnectionBuffer bufferToRead;
    private ConnectionBuffer bufferToWrite;


    public DirectConnection(ConnectionSelector connectionSelector, SocketChannel _channel, ConnectionBuffer _bufferToRead, ConnectionBuffer _bufferToWrite)
    {
        super(connectionSelector);
        channel = _channel;
        bufferToRead = _bufferToRead;
        bufferToWrite = _bufferToWrite;
    }

    @Override
    public void perform(SelectionKey key)
    {
        if(!key.isValid())
        {
            terminate(key);
        }

        if(key.isReadable())
        {
            try
            {
                bufferToWrite.readFromChannel(channel);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        if(key.isWritable())
        {
            try
            {
                bufferToRead.writeToChannel(channel);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }


    }
}
