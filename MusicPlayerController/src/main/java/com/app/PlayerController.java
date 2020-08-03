package com.app;

import java.io.*;
import java.net.*;

import com.xuggle.xuggler.*;

import com.mylib.*;

public class PlayerController extends Thread {
    public static String HOST = "localhost";
    //public static String HOST = "192.168.10.120";
    public static final int PORT = 50576;

    static final String MSG_PLAY = "PLAY";
    static final String MSG_STOP = "STOP";
    static final String MSG_RESUME = "RESUME";
    static final String MSG_MUSIC_FINISH = "MUSIC_FINISH";
    static final String MSG_FINISH = "FINISH";

    private Task task;

    public PlayerController() {
    }

    @Override
    public void run() {
        
        try(MyReader reader = new MyReader(System.in);)
        {
            try (Socket cs = new Socket(HOST, PORT); MyWriter writer = new MyWriter(cs.getOutputStream());) {
                while (true) {
                    String op = reader.readLine();

                    if (op.equals("p")) {
                        System.out.println("ミュージックを再生します。\n1.wavtest.wav\n2.HIRAHIRA.mp3\n3.m4aTest.m4a\n4.bgm_maoudamashii_healing15.ogg\n5.Debussy_Clair_de_lune_Stokowski_57.flac");

                        File path;

                        int choose = Integer.parseInt(reader.readLine());
                        switch (choose) {
                            case 1:
                                path = new File("Resources/wavtest.wav");
                                break;

                            case 2:
                                path = new File("Resources/HIRAHIRA.mp3");
                                break;

                            case 3:
                                path = new File("Resources/m4aTest.m4a");
                                break;
                                
                            case 4:
                                path = new File("Resources/bgm_maoudamashii_healing15.ogg");
                                break;

                            case 5:
                                path = new File("Resources/Debussy_Clair_de_lune_Stokowski_57.flac");
                                break;

                            default:
                                path = new File("Resources/m4aTest.m4a");
                                break;
                        }

                        String filename = path.toString();

                        writer.writeLine(MSG_PLAY);

                        Socket s = new Socket(HOST, PORT);

                        task = new Task(filename, s);
                        new Thread(task).start();
                    }
                    else if (op.equals("s"))
                    {
                        System.out.println("ミュージックを一時停止します");
                        
                        writer.writeLine(MSG_STOP);
                        task.stop();
                    }
                    else if (op.equals("r"))
                    {
                        System.out.println("ミュージックを再開します");
                        
                        task.resume();
                        writer.writeLine(MSG_RESUME);
                    }
                    else if (op.equals("f"))
                    {
                        System.out.println("ミュージックを終了します");

                        if (task != null)
                            task.close();
                        task = null;

                        writer.writeLine(MSG_MUSIC_FINISH);
                    } 
                    else if (op.equals("exit")) 
                    {
                        System.out.println("終了します");
                        writer.writeLine(MSG_FINISH);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                System.out.println("Connection Closed");
            } 
        }   
        catch(IOException e)
        {

        }
    }

    class Task implements Runnable {

        private String filename;
        private Socket socket;

        private boolean closed, stopped;

        public Task(String filename, Socket socket) {
            this.filename = filename;
            this.socket = socket;
            closed = stopped = false;
        }

        public void close() {
            closed = true;
        }

        public void stop()
        {
            stopped = true;
        }

        public void resume()
        {
            stopped = false;
        }

        @Override
        public void run() {

            IContainer container = IContainer.make();
            IStreamCoder audioCoder = null;
            try(MyWriter writer = new MyWriter(socket.getOutputStream())) 
            {
                System.out.println("接続を完了しました");
                
                System.out.println("音楽ファイルを開きます");
                if (container.open(filename, IContainer.Type.READ, null) < 0)
                    throw new IllegalArgumentException("could not open file: " + filename);

                System.out.println("ストリームの最大数を取得します");
                // query how many streams the call to open found
                int numStreams = container.getNumStreams();

                System.out.println("オーディオストリームを取得します");
                // and iterate through the streams to find the first audio stream
                int audioStreamId = -1;
                for(int i = 0; i < numStreams; i++)
                {
                    // Find the stream object
                    IStream stream = container.getStream(i);
                    // Get the pre-configured decoder that can decode this stream;
                    IStreamCoder coder = stream.getStreamCoder();
                    
                    if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO)
                    {
                        audioStreamId = i;
                        audioCoder = coder;
                        break;
                    }
                }
                if (audioStreamId == -1)
                    throw new RuntimeException("could not find audio stream in container: "+filename);

                if (audioCoder.open(null, null) < 0)
                    throw new RuntimeException("could not open audio decoder for container: "+filename);

                
                System.out.println("音楽ファイルフォーマットを送信します");
                writer.writeFloat((float)audioCoder.getSampleRate());
                writer.writeInt((int)IAudioSamples.findSampleBitDepth(audioCoder.getSampleFormat()));
                writer.writeInt(audioCoder.getChannels());
                writer.writeBoolean(true);
                writer.writeBoolean(false);

                System.out.println("sample rate : " + (float)audioCoder.getSampleRate());
                System.out.println("bits : " + (int)IAudioSamples.findSampleBitDepth(audioCoder.getSampleFormat()));
                System.out.println("channels : " + audioCoder.getChannels());
                System.out.println("frame size : " + audioCoder.getAudioFrameSize());
                System.out.println("codec id : " + audioCoder.getCodecID().toString());

                
                System.out.println("音楽ファイルの伝送を開始します");
                IPacket packet = IPacket.make();
                while(container.readNextPacket(packet) >= 0 && !closed)
                {
                    if (packet.getStreamIndex() != audioStreamId) 
                        continue;
                    
                    IAudioSamples samples = IAudioSamples.make(1024, audioCoder.getChannels());

                    int offset = 0;
                    while(offset < packet.getSize() && !closed)
                    {
                        while(stopped)
                        {
                            Thread.yield();
                        }

                        int bytesDecoded = audioCoder.decodeAudio(samples, packet, offset);
                        //System.out.println("bytesDecoded : " + bytesDecoded);

                        if (bytesDecoded < 0)
                            throw new RuntimeException("got error decoding audio in: " + filename);
                            
                        offset += bytesDecoded;

                        if (samples.isComplete())
                            writer.writeBinary(samples.getData().getByteArray(0, samples.getSize()));
                    }
                    samples.delete();
                }
                System.out.println("音楽ファイルの伝送を終了しました");
                
                audioCoder.close();
            }
            catch (IOException e)
            {
            }
            finally
            {
                if(audioCoder != null)
                {
                    if(audioCoder.isOpen())
                        audioCoder.close();

                    audioCoder.delete();
                }
                container.close();
                container.delete();
            }
        }
    }
}