package ru.nsu.g.a.lyamin.socksProxy.connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ConnectionBuffer
{
	private final int capacity = 16384;

	private byte[] buffer = new byte[capacity];

	private DirectConnection reader;
	private DirectConnection writer;

	private int writeOffset = 0;
	private int readOffset = 0;
	private int filled = 0;

	private boolean shutdown = false;

	public void setReader(DirectConnection _reader)
	{
		reader = _reader;
	}

	public void setWriter(DirectConnection _writer)
	{
		writer = _writer;
	}

	private ByteBuffer[] getByteBuffersArrayToRead()
	{
		if (writeOffset < readOffset)
		{
			ByteBuffer result[] = new ByteBuffer[1];
			result[0] = ByteBuffer.wrap(buffer, writeOffset, readOffset - writeOffset);
			return result;
		} else
		{
			if (readOffset == 0)
			{
				ByteBuffer result[] = new ByteBuffer[1];
				result[0] = ByteBuffer.wrap(buffer, writeOffset, capacity - writeOffset);
				return result;
			} else
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
		if (writeOffset < readOffset)
		{
			ByteBuffer result[] = new ByteBuffer[2];
			result[0] = ByteBuffer.wrap(buffer, readOffset, capacity - readOffset);
			result[1] = ByteBuffer.wrap(buffer, 0, writeOffset);
			return result;
		} else
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

	private void enableOption(DirectConnection directConnection, int opt)
	{
		if(directConnection == null) return;

		directConnection.connectionSelector.registerConnection(reader.getChannel(), reader,
				reader.getChannel().validOps() | opt);
	}

	private void disableOption(DirectConnection directConnection, int opt)
	{
		if(directConnection == null) return;

		directConnection.connectionSelector.registerConnection(reader.getChannel(), reader,
				reader.getChannel().validOps() & ~opt);
	}


	public boolean readFromChannelToBuffer(SocketChannel channel) throws IOException
	{
		ByteBuffer buffers[] = getByteBuffersArrayToRead();

		long read = channel.read(buffers);

		filled += read;

		if (read == -1)
		{
			//System.out.println("EOF");
			shutdown = true;
			return false;
		}

		if(read == 0)
		{
			return true;
		}

		if(filled == capacity)
		{
			disableOption(writer, SelectionKey.OP_READ);
//			writer.connectionSelector.registerConnection(writer.getChannel(), writer, writer.getChannel().validOps() & ~SelectionKey.OP_READ);
		}

		enableOption(reader, SelectionKey.OP_WRITE);
//		reader.connectionSelector.registerConnection(reader.getChannel(), reader, reader.getChannel().validOps() | SelectionKey.OP_WRITE);

//        System.out.println("DIRECT read: " + readen);
//        for(var v : buffers)
//        {
//            if(v.position() != 0)
//            {
//                System.out.println(Arrays.toString(v.array()));
//            }
//        }

		writeOffset = (writeOffset + (int) read) % capacity;

		return true;
	}

	public void writeToChannelFromBuffer(SocketChannel channel) throws IOException
	{
		ByteBuffer buffers[] = getByteBuffersArrayToWrite();

		long written = channel.write(buffers);

		filled -= written;

		if (shutdown && (written == 0))
		{
			channel.shutdownOutput();
			return;
		}


		if(filled == 0)
		{
			disableOption(reader, SelectionKey.OP_WRITE);
//			reader.connectionSelector.registerConnection(reader.getChannel(), reader, reader.getChannel().validOps() & ~SelectionKey.OP_WRITE);
		}

		if(written > 0)
		{
			enableOption(reader, SelectionKey.OP_READ);
			writer.connectionSelector.registerConnection(writer.getChannel(), writer, writer.getChannel().validOps() | SelectionKey.OP_READ);
		}


		readOffset = (readOffset + (int) written) % capacity;
	}

}
