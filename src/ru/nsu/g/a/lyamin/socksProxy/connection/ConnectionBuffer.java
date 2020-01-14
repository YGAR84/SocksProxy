package ru.nsu.g.a.lyamin.socksProxy.connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ConnectionBuffer
{
    private final int capacity = 16384;

    private byte[] buffer = new byte[capacity];

    private int writeOffset = 0;
    private int readOffset = 0;

    private boolean shutdown = false;

    private ByteBuffer[] getByteBuffersArrayToRead()
    {
        if(writeOffset < readOffset)
        {
            ByteBuffer result[] = new ByteBuffer[1];
            result[0] = ByteBuffer.wrap(buffer, writeOffset, readOffset - writeOffset);
            return result;
        }
        else
        {
            if(readOffset == 0)
            {
                ByteBuffer result[] = new ByteBuffer[1];
                result[0] = ByteBuffer.wrap(buffer, writeOffset, capacity - writeOffset);
                return result;
            }
            else
            {
                ByteBuffer result[] = new ByteBuffer[2];
                result[0] = ByteBuffer.wrap(buffer, writeOffset, capacity - writeOffset);
                result[1] = ByteBuffer.wrap(buffer, 0, readOffset);
                return result;
            }

        }
    }

    private ByteBuffer[] getByteBuffersArrayToWrite()
    {
        if(writeOffset < readOffset)
        {
            ByteBuffer result[] = new ByteBuffer[2];
            result[0] = ByteBuffer.wrap(buffer, readOffset, capacity - readOffset);
            result[1] = ByteBuffer.wrap(buffer, 0, writeOffset);
            return result;
        }
        else
        {
            ByteBuffer result[] = new ByteBuffer[1];
            result[0] = ByteBuffer.wrap(buffer, readOffset, writeOffset - readOffset);
            return result;
//            if(readOffset == 0)
//            {
//                ByteBuffer result[] = new ByteBuffer[1];
//                result[0] = ByteBuffer.wrap(buffer, writeOffset, size - writeOffset - 1);
//                return result;
//            }
//            else
//            {
//                ByteBuffer result[] = new ByteBuffer[2];
//                result[0] = ByteBuffer.wrap(buffer, writeOffset, size - writeOffset - 1);
//                result[1] = ByteBuffer.wrap(buffer, 0, readOffset);
//                return result;
//            }
        }
    }

    public boolean readFromChannelToBuffer(SocketChannel channel) throws IOException
    {
        ByteBuffer buffers[] = getByteBuffersArrayToRead();

        long readen = channel.read(buffers);

        if(readen == -1)
        {
            //System.out.println("EOF");
            shutdown = true;
            return false;
        }

//        System.out.println("DIRECT read: " + readen);
//        for(var v : buffers)
//        {
//            if(v.position() != 0)
//            {
//                System.out.println(Arrays.toString(v.array()));
//            }
//        }

        writeOffset = (writeOffset + (int)readen) % capacity;

        return true;
    }

    public void writeToChannelFromBuffer(SocketChannel channel) throws IOException
    {
        ByteBuffer buffers[] = getByteBuffersArrayToWrite();

        long written = channel.write(buffers);

        if(shutdown && (written == 0))
        {
            channel.shutdownOutput();
            return;
        }

        readOffset = (readOffset + (int)written) % capacity;
    }

}
