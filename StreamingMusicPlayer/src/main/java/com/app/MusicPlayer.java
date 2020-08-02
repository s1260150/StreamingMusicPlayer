package com.app;

import java.io.*;
import java.net.*;

import com.mylib.*;

import javax.sound.sampled.*;

import it.sauronsoftware.jave.*;

public class MusicPlayer{
	// PORT Number
	public static final int PORT = 50576; // 待ち受けポート番号

	static final String RESOURCES_FILE_PATH = "./Resources";

    static final String MSG_PLAY = "PLAY";
    static final String MSG_STOP = "STOP";
    static final String MSG_FINISH = "FINISH";

	public MusicPlayer() throws Exception 
	{

	}

	public void run() throws IOException, InterruptedException, IllegalArgumentException , EncoderException {
		
		try(ServerSocket ss = new ServerSocket(PORT))
		{
			try(Socket cs = ss.accept();
				MyReader reader = new MyReader(cs.getInputStream());)
			{
				System.out.println("Welcome!");

				
				while(true)
				{
					String msg = reader.readLine();
					System.out.println("受信 : " + msg);
					if(msg.equals(MSG_FINISH))
					{
						break;
					}
					else if(msg.equals(MSG_PLAY))
					{
							System.out.println("リスニング");
						try(Socket ms = ss.accept();
							MyReader mreader = new MyReader(ms.getInputStream());)
						{
							System.out.println("音楽再生用ソケットを接続しました");
							
							float sampleRate = mreader.readFloat();
							int sampleSizeInBits = mreader.readInt();
							int channels = mreader.readInt();
							boolean signed = mreader.readBoolean();
							boolean bigEndian = mreader.readBoolean();
		
							//リニアPCMエンコーディングでフォーマット作成
							AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
		
							//単一のオーディオ形式を含む指定した情報からデータラインの情報オブジェクトを構築
							DataLine.Info dataLine = new DataLine.Info(SourceDataLine.class, format);
				
							//指定された Line.Info オブジェクトの記述に一致するラインを取得
							SourceDataLine s = (SourceDataLine)AudioSystem.getLine(dataLine);
		
							System.out.println("フォーマットの設定を完了");

							try(Music music = new Music(s))
							{
								music.play();
								
								int bytesRead = 0;
								byte[] data = new byte[s.getBufferSize()];	
								while((bytesRead = mreader.readBinary(data)) != -1)
								{
									byte[] buf = new byte[bytesRead];
									System.arraycopy(data, 0, buf, 0, bytesRead);
									music.put(buf);
									Thread.yield();
								}

								music.finish();
							}
						}
					}
				}
			}
		}
		catch (MalformedURLException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException | LineUnavailableException e)
		{
			e.printStackTrace();
		}
    }

}