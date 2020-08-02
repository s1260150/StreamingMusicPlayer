package com.app;

import java.io.*;
import java.util.concurrent.locks.*;

import javax.sound.sampled.*;

public class Music implements Runnable, Closeable
{
    private SourceDataLine line;

    private boolean finished, playing;

    private int playedBytes;

    private Thread playThread = new Thread(this);

    private MyByteStream stream = new MyByteStream(131072);

    public Music(SourceDataLine line) throws LineUnavailableException
    {
        if(line == null) throw new LineUnavailableException();

        this.line = line;
        playedBytes = 0;

        finished = playing = false;
        line.open();
    }

    @Override
    public void run()
    {
        try 
        {
            //int numBytesRead;
            //byte[] data = new byte[line.getBufferSize()];	
            
            while(!finished)
            {
                if(playing)
                {
                    // if((numBytesRead = reader.readBinary(data, 0, line.available())) == -1)
                    // {  
                    //     break;
                    // }

                    byte[] bytes = stream.take(line.available());
                    //if(bytes.length == 0)
                    //    break;

                    line.write(bytes, 0, bytes.length);
                    playedBytes += bytes.length;

                    // int sum = 0, numBytesRead = 0;
                    // while(bytes.length > sum)
                    // {
                    //     numBytesRead = line.available();
                    //     if(sum + numBytesRead > bytes.length)
                    //     {
                    //         numBytesRead -= sum + numBytesRead - bytes.length;
                    //     }

                    //     playedBytes += numBytesRead;

                    //     line.write(bytes, sum, numBytesRead);
                    //     //System.out.println(line.isActive() && line.isOpen() && line.isRunning());
                    //     System.out.println("write " + numBytesRead);
                        
                    //     sum += numBytesRead;
                    // }
                }
                else
                {
                    Thread.yield();
                }
            }
            
            line.drain();
            line.stop();
            playing = false;
            finished = true;
        }
        finally
        {
            System.out.println("再生が終了しました");
        }
    }

    public void put(byte[] bytes) throws InterruptedException
    {
        stream.put(bytes);
    }

    public void play() throws LineUnavailableException
    {
        System.out.println("play");
        playing = true;
        if(!playThread.isAlive()) playThread.start();
        line.start();
    }

    public void stop()
    {
        System.out.println("stop");
        playing = false;
        line.stop();
    }

    public void finish()
    {
        System.out.println("finish");
        finished = true;
    }

    public int getPlayedBytes()
    {
        return playedBytes;
    }

    public boolean isFinished()
    {
        return finished;
    }

    @Override
    public void close()
    {
        System.out.println("close");
        line.stop();
        if(line.isOpen()) line.close();
    }

    class MyByteStream
    {
        private int length;

        private byte[] bytes;

        private int head, tail;

        private Lock lock = new ReentrantLock();
        private Condition cond_put = lock.newCondition();
        private Condition cond_take = lock.newCondition();

        public MyByteStream(int length)
        {
            this.length = length;

            bytes = new byte[length];
            head = tail = 0;
        }

        public void put(byte[] b) throws IllegalArgumentException
        {
            if(b.length >= this.length) throw new IllegalArgumentException("バッファのサイズを超えました (要求値 : " + b.length + ")");
            lock.lock();
            try
            {
                int seek = (tail + b.length) % length;
                while((head <= tail && (head <= seek && seek < tail)) ||
                        (head > tail && !(tail <= seek && seek < head)))
                {
                    cond_put.await();
                }

                if(tail + b.length > length)
                {
                    System.arraycopy(b, 0, bytes, tail, length - tail);
                    System.arraycopy(b, length - tail, bytes, 0, tail + b.length - length);
                    tail = (tail + b.length) % length;
                }
                else
                {
                    System.arraycopy(b, 0, bytes, tail, b.length);
                    tail = tail + b.length;
                }
                cond_take.signal();
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
            }
            finally
            {
                lock.unlock();
            }
            System.out.println("put : " + b.length);
        }

        public byte[] take(int len)
        {
            if(len >= this.length) throw new IllegalArgumentException("バッファのサイズを超えました (要求値 : " + len + ")");
            byte[] b = new byte[len];
            lock.lock();
            try
            {
                int seek = (head + len) % length;
                while((tail < head && (seek < head && tail < seek)) ||
                        (tail >= head && !(head <= seek && seek <= tail)))
                {
                    cond_take.await();
                }

                if(head + len > length)
                {
                    System.arraycopy(bytes, head, b, 0, length - head);
                    System.arraycopy(bytes, 0, b, length - head, head + len - length);
                    head = (head + len) % length;
                }
                else
                {
                    System.arraycopy(bytes, head, b, 0, len);
                    head = head + len;
                }
                cond_put.signal();
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
                return null;
            }
            finally
            {
                lock.unlock();
            }
            System.out.println("take : " + len);
            return b;
        }
    }
}