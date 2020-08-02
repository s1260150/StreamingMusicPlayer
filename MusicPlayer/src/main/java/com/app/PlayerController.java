package com.app;

import java.io.*;
import java.net.*;

import javax.sound.sampled.*;

import it.sauronsoftware.jave.*;

import com.mylib.*;

public class PlayerController extends Thread {
    public static String HOST = "localhost";
    public static final int PORT = 50576;

    static final String MSG_PLAY = "PLAY";
    static final String MSG_STOP = "STOP";
    static final String MSG_FINISH = "FINISH";

    public PlayerController() {
    }

    @Override
    public void run() {
        MyReader reader = new MyReader(System.in);

        try (Socket cs = new Socket(HOST, PORT);
            MyWriter writer = new MyWriter(cs.getOutputStream());) 
        {
            while (true) {
                String op = reader.readLine();

                if (op.equals("p")) {
                    System.out.println("ミュージックを再生します。\n1.wavtest.wav\n2.HIRAHIRA.mp3\n3.m4aTest.m4a");

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

                        default:
                            path = new File("Resources/m4aTest.m4a");
                            break;
                    }

                    
                    System.out.println("音楽ファイルをコンバートします");
                    File source = path;
                    File target = new File("target.wav");
                    AudioAttributes audio = new AudioAttributes();
                    audio.setCodec("pcm_s16le");    //s : 符号付, 16 : 16bit,  le : リトルエンディアン
                    EncodingAttributes attrs = new EncodingAttributes();
                    attrs.setFormat("wav");
                    attrs.setAudioAttributes(audio);
                    Encoder encoder = new Encoder();
                    try {
                        encoder.encode(source, target, attrs);
                    } catch (IllegalArgumentException | EncoderException e) {
                        e.printStackTrace();
                        return;
                    }

                    writer.writeLine(MSG_PLAY);

                    System.out.println("音楽用ソケットの接続を開始します");
                    try (Socket s = new Socket(HOST, PORT);
                            AudioInputStream ais = AudioSystem.getAudioInputStream(target);
                            MyReader r = new MyReader(ais);
                            MyWriter w = new MyWriter(s.getOutputStream());) 
                    {
                        AudioFormat format = ais.getFormat();
                        w.writeFloat(format.getSampleRate());
                        w.writeInt(format.getSampleSizeInBits());
                        w.writeInt(format.getChannels());
                        w.writeBoolean(true);
                        w.writeBoolean(format.isBigEndian());
                        
						//総フレーム数
						long frames = ais.getFrameLength();
						//一秒あたりに処理するフレーム数
						double frameRate = format.getSampleRate();
	
                        System.out.println("再生時間 : " + (int)(frames / frameRate));

                        int bytesRead = 0;
                        byte[] bytes = new byte[4096];
                        while((bytesRead = r.readBinary(bytes)) != -1)
                        {
                            w.writeBinary(bytes, 0, bytesRead);
                        }
                        
                        // while(!music.isFinished())
                        // {
                        // 	double sec = music.getPlayedBytes() / ( format.getSampleRate() * format.getSampleSizeInBits() * format.getChannels() / 8 );
                        // 	System.out.println(sec);
                        // 	Thread.sleep(100);
                        // }
                    }
                }
                else if(op.equals("s"))
                {
                    System.out.println("ミュージックを一時停止します");
                    writer.writeLine(MSG_STOP);
                }
                else if(op.equals("f"))
                {
                    System.out.println("終了します");
                    writer.writeLine(MSG_FINISH);
                    break;
                }
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
        catch(UnsupportedAudioFileException e)
        {
            e.printStackTrace();
        }
        finally
        {
            System.out.println("Connection Closed");
        }
    }
}