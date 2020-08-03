package com.app;

import java.io.*;
import java.net.*;

import com.mylib.*;

import javax.sound.sampled.*;

public class MusicPlayer {
	// PORT Number
	public static final int PORT = 50576; // 待ち受けポート番号

	static final String RESOURCES_FILE_PATH = "./Resources";

	static final String MSG_PLAY = "PLAY";
	static final String MSG_STOP = "STOP";
    static final String MSG_RESUME = "RESUME";
    static final String MSG_MUSIC_FINISH = "MUSIC_FINISH";
	static final String MSG_FINISH = "FINISH";

	private Task task;

	public MusicPlayer() throws Exception 
	{
	}

	public void run()
	{
		try (ServerSocket ss = new ServerSocket(PORT)) 
		{
			while(true)
			{
				try (Socket cs = ss.accept(); MyReader reader = new MyReader(cs.getInputStream());) 
				{
					System.out.println("Welcome!");

					while (true) {
						String msg = reader.readLine();
						System.out.println("受信 : " + msg);
						if(msg.equals(MSG_FINISH))
						{
							if (task != null)
								task.close();
							task = null;
							break;
						}
						else if (msg.equals(MSG_MUSIC_FINISH)) 
						{
							if (task != null)
								task.close();
							task = null;
						}  
						else if (msg.equals(MSG_PLAY))
						{
							task = new Task(ss.accept());
							new Thread(task).start();
						}
						else if (msg.equals(MSG_STOP))
						{
							if (task != null)
								task.stop();
						}
						else if (msg.equals(MSG_RESUME))
						{
							if (task != null)
								task.resume();
						}
					}
				}
			}
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}  
		catch (LineUnavailableException e) 
		{
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			System.out.println("Disconnected");
		}
	}

	class Task implements Runnable 
	{
		private Socket socket;

		private boolean closed;

		private Music music;

		public Task(Socket socket) {
			this.socket = socket;
			closed = false;
		}

		public void close()
		{
			closed = true;
		}

		public void stop()
		{
			if(music != null)
				music.stop();
		}

		public void resume() throws LineUnavailableException
		{
			if(music != null)
				music.play();
		}

		@Override
		public void run() 
		{
			try(MyReader reader = new MyReader(socket.getInputStream());)
			{
				float sampleRate = reader.readFloat();
				int sampleSizeInBits = reader.readInt();
				int channels = reader.readInt();
				boolean signed = reader.readBoolean();
				boolean bigEndian = reader.readBoolean();

				// リニアPCMフォーマット作成
				AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);

				// 単一のオーディオ形式を含む指定した情報からデータラインの情報オブジェクトを構築
				DataLine.Info dataLine = new DataLine.Info(SourceDataLine.class, format);

				// 指定された Line.Info オブジェクトの記述に一致するラインを取得
				SourceDataLine s = (SourceDataLine) AudioSystem.getLine(dataLine);

				try(Music music = new Music(s))
				{
					this.music = music;
					music.play();
					
					int bytesRead = 0;
					byte[] data = new byte[s.getBufferSize()];	
					while((bytesRead = reader.readBinary(data)) != -1 && !closed)
					{
						byte[] buf = new byte[bytesRead];
						System.arraycopy(data, 0, buf, 0, bytesRead);
						music.put(buf);
						Thread.yield();
					}
					
					if(closed)
						music.finish();

					music.noMoreData();
					while(!music.isFinished())
						Thread.yield();
				}
			}
			catch (LineUnavailableException e)
			{
				e.printStackTrace();
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
			}
			finally
			{
				this.music = null;
			}
		}
	}
}