package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class FirstPhaseConnection extends PhaseConnection
{

    int answerWrittenAll = 0;

    private ByteBuffer buffer = ByteBuffer.wrap(new byte[257]);

    private byte[] answer = new byte[2];

    public FirstPhaseConnection(ConnectionSelector connectionSelector, SocketChannel channel)
    {
        super(connectionSelector, channel);
        System.out.println("FIRST PHASE CTOR");
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isReadable())
        {
            int read = channel.read(buffer);
            if(read == -1)
            {
                terminate(key);
                return;
            }

            if(!checkReaden()) return;

            byte[] firstPhaseRequest = new byte[buffer.position()];
            System.arraycopy(buffer.array(), 0, firstPhaseRequest, 0, firstPhaseRequest.length);
            System.out.println("FIRST PHASE REQUEST: " + Arrays.toString(firstPhaseRequest));

            if(buffer.get(0) != 0x05)
            {
                terminate(key);
            }

            int numOfModes = (int)buffer.get(1);

            boolean hasAuth = false;
            for(int i = 2; i < buffer.position(); ++i)
            {
                if(buffer.get(i) == 0x00)
                {
                    hasAuth = true;
                    break;
                }
            }

            createAnswer(hasAuth);
        }

        if(key.isValid() && key.isWritable())
        {

            int written = channel.write(ByteBuffer.wrap(answer));

            answerWrittenAll += written;

            if(answerWrittenAll != answer.length)
            {
                return;
            }

            System.out.println("FIRST PHASE ANSWER: " + Arrays.toString(answer));

            SecondPhaseConnection secondPhaseConnection = new SecondPhaseConnection(connectionSelector, channel);
            connectionSelector.registerConnection(channel, secondPhaseConnection, SelectionKey.OP_READ);

        }
    }

    private boolean checkReaden()
    {
        if(buffer.position() < 1) return false;

        int length = buffer.get(1);

        return buffer.position() >= 2 + length;
    }

    private void createAnswer(boolean hasAuth) throws ClosedChannelException
    {
        connectionSelector.registerConnection(channel, this, SelectionKey.OP_WRITE);
        answer[0] = 0x05;
        answer[1] = (byte) ( (hasAuth) ? 0x00 : 0xFF );
    }

    @Override
    public void terminate(SelectionKey key)
    {
        super.terminate(key);
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
