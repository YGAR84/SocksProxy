package ru.nsu.g.a.lyamin.socksProxy.connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ConnectionBuffer
{
    private final int capacity = 16384;

    private byte[] buffer = new byte[capacity];

    private int size = 0;
    private int free = capacity;
    private int head = 0;
    private int tail = 0;

    private ByteBuffer[] getByteBuffersArrayToRead()
    {
        if(head < tail)
        {
            ByteBuffer result[] = new ByteBuffer[1];
            result[0] = ByteBuffer.wrap(buffer, head + 1, tail - head - 1);
            return result;
        }
        else
        {
            if(tail == 0)
            {
                ByteBuffer result[] = new ByteBuffer[1];
                result[0] = ByteBuffer.wrap(buffer, head + 1, capacity - head - 1);
                return result;
            }
            else
            {
                ByteBuffer result[] = new ByteBuffer[2];
                result[0] = ByteBuffer.wrap(buffer, head + 1, capacity - head - 1);
                result[1] = ByteBuffer.wrap(buffer, 0, tail - 1);
                return result;
            }

        }
    }

    private ByteBuffer[] getByteBuffersArrayToWrite()
    {
        if(head < tail)
        {
            ByteBuffer result[] = new ByteBuffer[2];
            result[0] = ByteBuffer.wrap(buffer, tail, capacity - tail);
            result[1] = ByteBuffer.wrap(buffer, 0, head);
            return result;
        }
        else
        {
            ByteBuffer result[] = new ByteBuffer[1];
            result[0] = ByteBuffer.wrap(buffer, tail, head - tail + 1);
            return result;
//            if(tail == 0)
//            {
//                ByteBuffer result[] = new ByteBuffer[1];
//                result[0] = ByteBuffer.wrap(buffer, head, size - head - 1);
//                return result;
//            }
//            else
//            {
//                ByteBuffer result[] = new ByteBuffer[2];
//                result[0] = ByteBuffer.wrap(buffer, head, size - head - 1);
//                result[1] = ByteBuffer.wrap(buffer, 0, tail);
//                return result;
//            }
        }
    }

    public void readFromChannel(SocketChannel channel) throws IOException
    {
        ByteBuffer buffers[] = getByteBuffersArrayToRead();

        long readen = channel.read(buffers);

        head = (head + (int)readen) % capacity;
    }

    public void writeToChannel(SocketChannel channel) throws IOException
    {
        ByteBuffer buffers[] = getByteBuffersArrayToWrite();

        long written = channel.write(buffers);

        tail = (tail + (int)written) % capacity;
    }

}
